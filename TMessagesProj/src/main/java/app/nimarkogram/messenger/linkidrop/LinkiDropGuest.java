package app.nimarkogram.messenger.linkidrop;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Receiver side of Linki Drop: joins the peer hotspot (or connects over
 * Bluetooth RFCOMM) and pulls the single offered file.
 */
public final class LinkiDropGuest {

    public static final String DROP_FOLDER = "LinkiDrop";

    private static final int JOIN_TIMEOUT_MS = 45000;
    private static final int SOCKET_TIMEOUT_MS = 60000;
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int DISCOVERY_TIMEOUT_MS = 20000;

    public interface Callback {
        void onProgress(long done, long total);

        void onDone(File file, boolean plugin);

        void onError(int messageRes);
    }

    private final Context context;
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());

    private volatile boolean cancelled;
    private ConnectivityManager.NetworkCallback networkCallback;
    private BroadcastReceiver discoveryReceiver;
    private int legacyNetworkId = -1;
    private Network boundNetwork;

    public LinkiDropGuest(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    public static File dropDir(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) {
            base = context.getFilesDir();
        }
        final File dir = new File(base, DROP_FOLDER);
        if (!dir.exists() && !dir.mkdirs()) {
            FileLog.e("linkidrop: cannot create " + dir);
        }
        return dir;
    }

    public void start(LinkiDropPayload payload) {
        cancelled = false;
        if (payload.isWifi()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                joinModern(payload);
            } else {
                joinLegacy(payload);
            }
        } else {
            startBluetooth(payload);
        }
    }

    public void stop() {
        cancelled = true;
        unregisterNetwork();
        stopLegacyWifi();
        stopDiscovery();
    }

    //region Wi-Fi

    private void joinModern(LinkiDropPayload payload) {
        final ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            fail(R.string.NM_LD_JoinFailed);
            return;
        }
        final WifiNetworkSpecifier.Builder builder = new WifiNetworkSpecifier.Builder().setSsid(payload.ssid);
        if (!payload.password.isEmpty()) {
            builder.setWpa2Passphrase(payload.password);
        }
        final NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                // A local-only hotspot has no internet; without this the request never matches.
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(builder.build())
                .build();
        final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                if (cancelled) {
                    return;
                }
                // Bind only our socket: a process-wide bind would also push Telegram
                // traffic onto the internet-less hotspot.
                boundNetwork = network;
                startDownload(payload);
            }

            @Override
            public void onUnavailable() {
                if (!cancelled) {
                    fail(R.string.NM_LD_JoinFailed);
                }
            }
        };
        this.networkCallback = networkCallback;
        try {
            manager.requestNetwork(request, networkCallback, JOIN_TIMEOUT_MS);
        } catch (Throwable e) {
            FileLog.e(e);
            fail(R.string.NM_LD_JoinFailed);
        }
    }

    @SuppressWarnings("deprecation")
    private void joinLegacy(LinkiDropPayload payload) {
        final WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            fail(R.string.NM_LD_JoinFailed);
            return;
        }
        final WifiConfiguration configuration = new WifiConfiguration();
        configuration.SSID = "\"" + payload.ssid + "\"";
        configuration.allowedKeyManagement.clear();
        if (payload.password.isEmpty()) {
            configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        } else {
            configuration.preSharedKey = "\"" + payload.password + "\"";
            configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        }
        try {
            final int id = wifiManager.addNetwork(configuration);
            if (id < 0) {
                fail(R.string.NM_LD_JoinFailed);
                return;
            }
            legacyNetworkId = id;
            wifiManager.disconnect();
            if (!wifiManager.enableNetwork(id, true)) {
                fail(R.string.NM_LD_JoinFailed);
                return;
            }
            wifiManager.reconnect();
        } catch (Throwable e) {
            FileLog.e(e);
            fail(R.string.NM_LD_JoinFailed);
            return;
        }
        new Thread(() -> {
            final long deadline = System.currentTimeMillis() + JOIN_TIMEOUT_MS;
            while (!cancelled && System.currentTimeMillis() < deadline) {
                if (wifiManager.getConnectionInfo() != null
                        && payload.ssid.equals(unquoteSsid(wifiManager.getConnectionInfo().getSSID()))) {
                    startDownload(payload);
                    return;
                }
                try {
                    Thread.sleep(700);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (!cancelled) {
                fail(R.string.NM_LD_JoinFailed);
            }
        }, "LinkiDropJoin").start();
    }

    @SuppressWarnings("deprecation")
    private void stopLegacyWifi() {
        final int id = legacyNetworkId;
        legacyNetworkId = -1;
        if (id < 0) {
            return;
        }
        try {
            final WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                wifiManager.disableNetwork(id);
                wifiManager.removeNetwork(id);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void unregisterNetwork() {
        final ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager != null) {
            final ConnectivityManager.NetworkCallback callback = networkCallback;
            networkCallback = null;
            if (callback != null) {
                try {
                    manager.unregisterNetworkCallback(callback);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        }
        boundNetwork = null;
    }

    //endregion

    //region Bluetooth

    private void startBluetooth(LinkiDropPayload payload) {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            fail(R.string.NM_LD_BluetoothOff);
            return;
        }
        final Set<String> candidates = new LinkedHashSet<>();
        // Already paired phones can be tried right away.
        try {
            for (BluetoothDevice device : adapter.getBondedDevices()) {
                if (!TextUtils.isEmpty(payload.deviceName) && payload.deviceName.equals(safeName(device))) {
                    candidates.add(device.getAddress());
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                    final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && !TextUtils.isEmpty(payload.deviceName)
                            && payload.deviceName.equals(safeName(device))) {
                        synchronized (candidates) {
                            candidates.add(device.getAddress());
                        }
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                    connect(candidates, payload);
                }
            }
        };
        discoveryReceiver = receiver;
        final IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            if (adapter.isDiscovering()) {
                adapter.cancelDiscovery();
            }
            adapter.startDiscovery();
        } catch (Throwable e) {
            FileLog.e(e);
        }
        // Discovery may never report "finished" on some builds; connect anyway.
        main.postDelayed(() -> {
            if (!cancelled && discoveryReceiver == receiver) {
                connect(candidates, payload);
            }
        }, DISCOVERY_TIMEOUT_MS);
    }

    private void stopDiscovery() {
        final BroadcastReceiver receiver = discoveryReceiver;
        discoveryReceiver = null;
        if (receiver == null) {
            return;
        }
        try {
            context.unregisterReceiver(receiver);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        try {
            final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null && adapter.isDiscovering()) {
                adapter.cancelDiscovery();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void connect(Set<String> candidates, LinkiDropPayload payload) {
        final List<String> targets;
        synchronized (candidates) {
            targets = new ArrayList<>(candidates);
        }
        stopDiscovery();
        if (targets.isEmpty()) {
            fail(R.string.NM_LD_BluetoothNotFound);
            return;
        }
        new Thread(() -> {
            for (String address : targets) {
                if (cancelled) {
                    return;
                }
                BluetoothSocket socket = null;
                try {
                    final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                    if (adapter == null) {
                        break;
                    }
                    final BluetoothDevice device = adapter.getRemoteDevice(address);
                    socket = device.createInsecureRfcommSocketToServiceRecord(LinkiDropHost.RFCOMM_UUID);
                    socket.connect();
                    try (BluetoothSocket closed = socket) {
                        transfer(socket.getInputStream(), socket.getOutputStream(), payload);
                    }
                    return;
                } catch (Throwable e) {
                    FileLog.e(e);
                    closeQuietly(socket);
                }
            }
            if (!cancelled) {
                fail(R.string.NM_LD_BluetoothFailed);
            }
        }, "LinkiDropBtClient").start();
    }

    private static String safeName(BluetoothDevice device) {
        try {
            return device.getName();
        } catch (Throwable e) {
            return null;
        }
    }

    //endregion

    private void startDownload(LinkiDropPayload payload) {
        new Thread(() -> {
            IOException last = null;
            final String[] hosts = payload.addresses.length > 0
                    ? payload.addresses
                    : new String[]{"192.168.43.1"};
            for (String host : hosts) {
                if (cancelled) {
                    return;
                }
                final Socket socket = newSocket();
                try {
                    socket.connect(new InetSocketAddress(host, payload.port), CONNECT_TIMEOUT_MS);
                    socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                    try (Socket closed = socket) {
                        transfer(socket.getInputStream(), socket.getOutputStream(), payload);
                    }
                    return;
                } catch (IOException e) {
                    last = e;
                    closeQuietly(socket);
                }
            }
            if (!cancelled) {
                if (last != null) {
                    FileLog.e(last);
                }
                fail(R.string.NM_LD_ConnectFailed);
            }
        }, "LinkiDropDownload").start();
    }

    /** A socket bound to the hotspot when we know it, a plain one otherwise. */
    private Socket newSocket() {
        final Network network = boundNetwork;
        if (network != null) {
            try {
                return network.getSocketFactory().createSocket();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return new Socket();
    }

    /** Works for both a TCP socket and an RFCOMM one: only streams are used. */
    private void transfer(InputStream in, OutputStream out, LinkiDropPayload payload) throws IOException {
        LinkiDropWire.writeMagic(out);
        out.write((payload.token + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
        final JSONObject header = LinkiDropWire.readHeader(in);
        final String name = LinkiDropWire.sanitizeName(header.optString("n", payload.name));
        final long size = header.optLong("s", payload.size);
        final String sha = header.optString("x", payload.sha256);
        final boolean plugin = LinkiDropPayload.KIND_PLUGIN.equals(header.optString("g", payload.kind));
        if (size <= 0 || size > 2L * 1024 * 1024 * 1024) {
            throw new IOException("bad size");
        }
        final File target = uniqueTarget(name);
        try (FileOutputStream fileOut = new FileOutputStream(target)) {
            LinkiDropWire.receiveFile(in, fileOut, size, (done, total) -> main.post(() -> {
                if (!cancelled) {
                    callback.onProgress(done, total);
                }
            }));
        }
        final String actual = LinkiDropWire.sha256Of(target);
        if (actual == null || !actual.equalsIgnoreCase(sha)) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw new IOException("checksum mismatch");
        }
        out.write("OK\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();
        unregisterNetwork();
        stopLegacyWifi();
        main.post(() -> {
            if (!cancelled) {
                callback.onDone(target, plugin);
            }
        });
    }

    private File uniqueTarget(String name) {
        final File dir = dropDir(context);
        File target = new File(dir, name);
        if (!target.exists()) {
            return target;
        }
        final int dot = name.lastIndexOf('.');
        final String base = dot > 0 ? name.substring(0, dot) : name;
        final String tail = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < 1000; i++) {
            target = new File(dir, base + " (" + i + ")" + tail);
            if (!target.exists()) {
                return target;
            }
        }
        return new File(dir, System.currentTimeMillis() + tail);
    }

    private static String unquoteSsid(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private void fail(int messageRes) {
        // Never leave the process bound to a hotspot that is going away.
        unregisterNetwork();
        stopLegacyWifi();
        main.post(() -> {
            if (!cancelled) {
                callback.onError(messageRes);
            }
        });
    }

    private static void closeQuietly(java.io.Closeable socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}