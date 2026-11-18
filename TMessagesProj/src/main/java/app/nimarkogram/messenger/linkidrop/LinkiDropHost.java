package app.nimarkogram.messenger.linkidrop;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Sender side of Linki Drop: creates a temporary hotspot (or an RFCOMM
 * listener), publishes a payload over NFC and serves exactly one file.
 */
public final class LinkiDropHost {

    public static final UUID RFCOMM_UUID = UUID.fromString("8f2a1c4e-3b6d-4c2a-9e51-7d0b6f3a9c11");
    public static final String RFCOMM_NAME = "LinkiDrop";

    private static final int ACCEPT_TIMEOUT_MS = 5 * 60 * 1000;
    private static final int SOCKET_TIMEOUT_MS = 60 * 1000;

    public interface Callback {
        void onReady(LinkiDropPayload payload);

        void onProgress(long done, long total);

        void onDone();

        void onError(int messageRes);
    }

    private final Activity activity;
    private final File source;
    private final String displayName;
    private final String kind;
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());

    private volatile boolean cancelled;
    private Object reservation;
    private ServerSocket wifiServer;
    private BluetoothServerSocket bluetoothServer;
    private Thread worker;

    public LinkiDropHost(Activity activity, File source, String displayName, String kind, Callback callback) {
        this.activity = activity;
        this.source = source;
        this.displayName = LinkiDropWire.sanitizeName(displayName);
        this.kind = kind;
        this.callback = callback;
    }

    public void startWifi() {
        start(true);
    }

    public void startBluetooth() {
        start(false);
    }

    private void start(final boolean wifi) {
        cancelled = false;
        worker = new Thread(() -> {
            try {
                final String sha = LinkiDropWire.sha256Of(source);
                if (sha == null) {
                    fail(R.string.NM_LD_Error);
                    return;
                }
                final LinkiDropPayload payload = wifi
                        ? prepareWifi(sha)
                        : prepareBluetooth(sha);
                if (payload == null || cancelled) {
                    return;
                }
                main.post(() -> {
                    if (!cancelled) {
                        callback.onReady(payload);
                    }
                });
                serve(payload);
            } catch (Throwable e) {
                if (!cancelled) {
                    FileLog.e(e);
                    fail(wifi ? R.string.NM_LD_WifiFailed : R.string.NM_LD_BluetoothFailed);
                }
            }
        }, "LinkiDropHost");
        worker.start();
    }

    public void stop() {
        cancelled = true;
        closeQuietly(wifiServer);
        closeQuietly(bluetoothServer);
        final Object current = reservation;
        reservation = null;
        LinkiDropHotspot.close(current);
    }

    //region Transports

    private LinkiDropPayload prepareWifi(String sha) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            fail(R.string.NM_LD_NoWifi);
            return null;
        }
        final LinkiDropHotspot.Result hotspot = LinkiDropHotspot.start(activity);
        if (hotspot == null) {
            fail(R.string.NM_LD_WifiFailed);
            return null;
        }
        if (cancelled) {
            LinkiDropHotspot.close(hotspot.reservation);
            return null;
        }
        reservation = hotspot.reservation;
        try {
            wifiServer = new ServerSocket(0);
            wifiServer.setSoTimeout(ACCEPT_TIMEOUT_MS);
        } catch (IOException e) {
            FileLog.e(e);
            fail(R.string.NM_LD_WifiFailed);
            return null;
        }
        return new LinkiDropPayload(LinkiDropPayload.TRANSPORT_WIFI, hotspot.ssid, hotspot.password,
                localAddresses(), wifiServer.getLocalPort(), LinkiDropWire.randomToken(), "",
                displayName, source.length(), sha, kind);
    }

    private LinkiDropPayload prepareBluetooth(String sha) {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            fail(R.string.NM_LD_BluetoothOff);
            return null;
        }
        String deviceName = "";
        try {
            deviceName = adapter.getName();
        } catch (Throwable e) {
            FileLog.e(e);
        }
        if (deviceName == null) {
            deviceName = "";
        }
        try {
            bluetoothServer = adapter.listenUsingInsecureRfcommWithServiceRecord(RFCOMM_NAME, RFCOMM_UUID);
        } catch (Throwable e) {
            FileLog.e(e);
            fail(R.string.NM_LD_BluetoothFailed);
            return null;
        }
        return new LinkiDropPayload(LinkiDropPayload.TRANSPORT_BLUETOOTH, "", "", new String[0], 0,
                LinkiDropWire.randomToken(), deviceName, displayName, source.length(), sha, kind);
    }

    private void serve(LinkiDropPayload payload) throws IOException {
        if (payload.isWifi()) {
            final ServerSocket server = wifiServer;
            if (server == null) {
                return;
            }
            final Socket socket = server.accept();
            try (Socket closed = socket) {
                socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                transfer(socket.getInputStream(), socket.getOutputStream(), payload);
            }
        } else {
            final BluetoothServerSocket server = bluetoothServer;
            if (server == null) {
                return;
            }
            final BluetoothSocket socket = server.accept(ACCEPT_TIMEOUT_MS);
            if (socket == null) {
                return;
            }
            // BluetoothSocket has no SO_TIMEOUT; the accept timeout bounds the wait instead.
            try (BluetoothSocket closed = socket) {
                transfer(socket.getInputStream(), socket.getOutputStream(), payload);
            }
        }
    }

    private void transfer(InputStream in, OutputStream out, LinkiDropPayload payload) throws IOException {
        if (!LinkiDropWire.readMagic(in)) {
            return;
        }
        final String token = LinkiDropWire.readLine(in, 128);
        if (!payload.token.isEmpty() && !payload.token.equals(token)) {
            FileLog.e("linkidrop: rejected peer with a wrong token");
            return;
        }
        final JSONObject header = new JSONObject();
        try {
            header.put("n", payload.name);
            header.put("s", payload.size);
            header.put("x", payload.sha256);
            header.put("g", payload.kind);
        } catch (Exception e) {
            throw new IOException(e);
        }
        LinkiDropWire.writeHeader(out, header);
        LinkiDropWire.sendFile(source, out, payload.size, (done, total) -> main.post(() -> {
            if (!cancelled) {
                callback.onProgress(done, total);
            }
        }));
        final String ack = LinkiDropWire.readLine(in, 32);
        if ("OK".equalsIgnoreCase(ack)) {
            main.post(() -> {
                if (!cancelled) {
                    callback.onDone();
                }
            });
        } else {
            fail(R.string.NM_LD_Error);
        }
    }

    //endregion

    private static String[] localAddresses() {
        final List<String> found = new ArrayList<>();
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
                        final String host = address.getHostAddress();
                        if (host != null && !found.contains(host)) {
                            found.add(host);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        // The hotspot gateway is the usual answer; keep it first when present.
        if (found.contains("192.168.43.1")) {
            found.remove("192.168.43.1");
            found.add(0, "192.168.43.1");
        }
        final int keep = Math.min(found.size(), 4);
        return found.subList(0, keep).toArray(new String[0]);
    }

    private void fail(int messageRes) {
        main.post(() -> {
            if (!cancelled) {
                callback.onError(messageRes);
            }
        });
    }

    private static void closeQuietly(ServerSocket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void closeQuietly(BluetoothServerSocket socket) {
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