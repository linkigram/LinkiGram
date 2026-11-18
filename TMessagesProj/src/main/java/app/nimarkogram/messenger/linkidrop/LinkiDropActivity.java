package app.nimarkogram.messenger.linkidrop;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import app.nimarkogram.messenger.nfc.LinkiShareNdefService;
import app.nimarkogram.messenger.plugins.PluginsController;

/**
 * Linki Drop: touch two phones together to move a file or a plugin.
 *
 * The offering phone publishes a tiny record over NFC and serves one file over
 * either a temporary hotspot or Bluetooth RFCOMM. The receiving phone only
 * reads the record and pulls the bytes; NFC here is one-way per session, so the
 * two roles never fight over the single NFC controller.
 */
public class LinkiDropActivity extends BaseFragment implements NfcAdapter.ReaderCallback {

    private static final int REQ_PICK_FILE = 5411;
    private static final int REQ_PERMISSIONS = 5412;
    private static final int REQ_SHARE_FILE = 5413;

    private static final String PREF_TRANSPORT = "linkiDropTransport";
    private static final String TRANSPORT_WIFI = "wifi";
    private static final String TRANSPORT_BLUETOOTH = "bluetooth";

    private NfcAdapter nfcAdapter;
    private LinkiDropView heroView;
    private TextView titleView;
    private TextView subtitleView;
    private TextView enableNfcView;
    private TextCheckCell wifiCell;
    private TextCheckCell bluetoothCell;
    private TextCheckCell receiveCell;
    private TextSettingsCell receivedCell;

    private LinkiDropHost host;
    private LinkiDropGuest guest;

    private volatile boolean resumed;
    private volatile boolean readerModeOn;
    private boolean sending;
    private long sessionGeneration;
    private long sessionOwner;
    private Runnable pendingAction;
    private boolean pendingIsSend;
    private File receivedFile;
    private boolean receivedIsPlugin;
    private String sentName = "";
    private String lastOfferToken = "";
    private long lastOfferAt;
    private File pickedCache;

    public LinkiDropActivity() {
    }

    //region UI

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setTitle(LocaleController.getString(R.string.NM_LD_Title));
        actionBar.setAllowOverlayTitle(false);
        actionBar.setOccupyStatusBar(!AndroidUtilities.isTablet());

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        final FrameLayout root = (FrameLayout) fragmentView;

        final ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(24));
        scroll.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        heroView = new LinkiDropView(context);
        heroView.setColors(
                Theme.getColor(Theme.key_actionBarDefault),
                Theme.getColor(Theme.key_windowBackgroundWhite),
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText),
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        content.addView(heroView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 220, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));

        titleView = new TextView(context);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setText(LocaleController.getString(R.string.NM_LD_Hint));
        titleView.setPadding(AndroidUtilities.dp(28), 0, AndroidUtilities.dp(28), 0);
        content.addView(titleView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 24, 0, 0));

        subtitleView = new TextView(context);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setText(LocaleController.getString(R.string.NM_LD_HintDetail));
        subtitleView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        content.addView(subtitleView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 10, 0, 0));

        receivedCell = new TextSettingsCell(context);
        receivedCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        receivedCell.setVisibility(View.GONE);
        receivedCell.setOnClickListener(v -> openReceived());
        content.addView(receivedCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 14, 0, 0));

        final TextSettingsCell sendFileCell = new TextSettingsCell(context);
        sendFileCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        sendFileCell.setText(LocaleController.getString(R.string.NM_LD_SendFile), true);
        sendFileCell.setOnClickListener(v -> pickFile());
        content.addView(sendFileCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0, 0));

        final TextSettingsCell sendPluginCell = new TextSettingsCell(context);
        sendPluginCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        sendPluginCell.setText(LocaleController.getString(R.string.NM_LD_SendPlugin), true);
        sendPluginCell.setOnClickListener(v -> pickPlugin());
        content.addView(sendPluginCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        wifiCell = new TextCheckCell(context);
        content.addView(wifiCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 18, 0, 0));
        wifiCell.setOnClickListener(v -> setTransport(TRANSPORT_WIFI));

        bluetoothCell = new TextCheckCell(context);
        content.addView(bluetoothCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        bluetoothCell.setOnClickListener(v -> setTransport(TRANSPORT_BLUETOOTH));

        receiveCell = new TextCheckCell(context);
        content.addView(receiveCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        receiveCell.setOnClickListener(v -> {
            app.nimarkogram.messenger.NimarkoConfig.toggleLinkiDropReceiveEverywhere();
            updateReceiveCell();
        });

        final TextView transportInfo = new TextView(context);
        transportInfo.setText(LocaleController.getString(R.string.NM_LD_TransportInfo));
        transportInfo.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        transportInfo.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        transportInfo.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), 0);
        content.addView(transportInfo, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 10, 20, 0));

        enableNfcView = new TextView(context);
        enableNfcView.setGravity(Gravity.CENTER);
        enableNfcView.setTypeface(Typeface.DEFAULT_BOLD);
        enableNfcView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        enableNfcView.setTextColor(Theme.getColor(Theme.key_actionBarDefault));
        enableNfcView.setText(LocaleController.getString(R.string.NM_LS_EnableNfc));
        enableNfcView.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(14));
        enableNfcView.setOnClickListener(v -> openNfcSettings());
        enableNfcView.setVisibility(View.GONE);
        content.addView(enableNfcView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

        nfcAdapter = NfcAdapter.getDefaultAdapter(context);
        if (nfcAdapter == null) {
            titleView.setText(LocaleController.getString(R.string.NM_LS_NoNfc));
            subtitleView.setText("");
        }
        updateTransportCells();
        updateReceiveCell();
        return fragmentView;
    }

    private void updateTransportCells() {
        final boolean wifi = TRANSPORT_WIFI.equals(transport());
        wifiCell.setTextAndValueAndCheck(LocaleController.getString(R.string.NM_LD_TransportWifi),
                LocaleController.getString(R.string.NM_LD_TransportWifiInfo), wifi, true, true);
        bluetoothCell.setTextAndValueAndCheck(LocaleController.getString(R.string.NM_LD_TransportBluetooth),
                LocaleController.getString(R.string.NM_LD_TransportBluetoothInfo), !wifi, false, false);
    }

    private void updateReceiveCell() {
        if (receiveCell == null) {
            return;
        }
        receiveCell.setTextAndValueAndCheck(
                LocaleController.getString(R.string.NM_LD_ReceiveEverywhere),
                LocaleController.getString(R.string.NM_LD_ReceiveEverywhereInfo),
                app.nimarkogram.messenger.NimarkoConfig.linkiDropReceiveEverywhere, true, false);
    }

    private SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    private String transport() {
        final String value = prefs().getString(PREF_TRANSPORT, TRANSPORT_WIFI);
        return TRANSPORT_BLUETOOTH.equals(value) ? TRANSPORT_BLUETOOTH : TRANSPORT_WIFI;
    }

    private void setTransport(String value) {
        prefs().edit().putString(PREF_TRANSPORT, value).apply();
        updateTransportCells();
    }

    //endregion

    //region Lifecycle

    @Override
    public void onResume() {
        super.onResume();
        ++sessionGeneration;
        sessionOwner = getUserConfig().getClientUserId();
        resumed = true;
        // The opt-in Linki-Share controller must not compete for the NFC controller.
        app.nimarkogram.messenger.nfc.LinkiShareController.setScreenActive(true);
        if (enableNfcView == null) {
            return;
        }
        if (nfcAdapter == null) {
            nfcAdapter = NfcAdapter.getDefaultAdapter(getContext());
        }
        if (nfcAdapter == null) {
            return;
        }
        if (!nfcAdapter.isEnabled()) {
            enableNfcView.setVisibility(View.VISIBLE);
            titleView.setText(LocaleController.getString(R.string.NM_LS_NfcOff));
            return;
        }
        enableNfcView.setVisibility(View.GONE);
        if (!sending) {
            startReader();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        resumed = false;
        ++sessionGeneration;
        stopEverything();
        app.nimarkogram.messenger.nfc.LinkiShareController.setScreenActive(false);
    }

    @Override
    public void onFragmentDestroy() {
        stopEverything();
        super.onFragmentDestroy();
        if (pickedCache != null) {
            //noinspection ResultOfMethodCallIgnored
            pickedCache.delete();
            pickedCache = null;
        }
    }

    private void stopEverything() {
        stopReader();
        stopHost();
        stopGuest();
    }

    //endregion

    //region NFC

    private void startReader() {
        final Activity activity = getParentActivity();
        if (activity == null || nfcAdapter == null || readerModeOn) {
            return;
        }
        try {
            nfcAdapter.enableReaderMode(activity, this, NfcAdapter.FLAG_READER_NFC_A, null);
            readerModeOn = true;
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void stopReader() {
        if (!readerModeOn) {
            return;
        }
        readerModeOn = false;
        try {
            final Activity activity = getParentActivity();
            if (activity != null && nfcAdapter != null) {
                nfcAdapter.disableReaderMode(activity);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void openNfcSettings() {
        try {
            final Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            final Activity activity = getParentActivity();
            if (activity != null) {
                activity.startActivity(intent);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        final long generation = sessionGeneration;
        FileLog.d("LinkiDrop: tag discovered, resumed=" + resumed + " sending=" + sending);
        if (!resumed || sending) {
            return;
        }
        final LinkiDropPayload payload = readOffer(tag);
        if (payload == null) {
            FileLog.d("LinkiDrop: tag is not a drop offer");
            return;
        }
        FileLog.d("LinkiDrop: offer " + payload.name + " " + payload.size + " via " + payload.transport);
        AndroidUtilities.runOnUIThread(() -> {
            if (generation == sessionGeneration && resumed && !sending) {
                onOffer(payload);
            }
        });
    }

    private static LinkiDropPayload readOffer(Tag tag) {
        Ndef ndef = null;
        try {
            ndef = Ndef.get(tag);
            if (ndef == null) {
                return null;
            }
            ndef.connect();
            NdefMessage message = ndef.getNdefMessage();
            if (message == null) {
                message = ndef.getCachedNdefMessage();
            }
            if (message == null) {
                return null;
            }
            for (NdefRecord record : message.getRecords()) {
                final Uri uri = record.toUri();
                final LinkiDropPayload payload = uri == null ? null : LinkiDropPayload.fromUri(uri.toString());
                if (payload != null) {
                    return payload;
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (ndef != null) {
                try {
                    ndef.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    //endregion

    //region Receiving

    private void onOffer(LinkiDropPayload payload) {
        if (!isScreenAlive()) {
            return;
        }
        FileLog.d("LinkiDrop: accepting offer " + payload.name);
        // The sender keeps its tag armed; ignore the same offer right after a transfer.
        final long now = System.currentTimeMillis();
        if (payload.token.equals(lastOfferToken) && now - lastOfferAt < 15000) {
            startReader();
            return;
        }
        lastOfferToken = payload.token;
        lastOfferAt = now;
        final ArrayList<String> missing = missingReceivePermissions(payload);
        if (!missing.isEmpty()) {
            pendingIsSend = false;
            pendingAction = () -> startGuest(payload);
            final Activity activity = getParentActivity();
            if (activity != null) {
                activity.requestPermissions(missing.toArray(new String[0]), REQ_PERMISSIONS);
            }
            return;
        }
        startGuest(payload);
    }

    /** Pulling over a link needs the same runtime grants as offering it. */
    public static ArrayList<String> missingReceivePermissions(LinkiDropPayload payload) {
        final ArrayList<String> needed = new ArrayList<>();
        if (payload.isWifi()) {
            needed.add(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? Manifest.permission.NEARBY_WIFI_DEVICES
                    : Manifest.permission.ACCESS_FINE_LOCATION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed.add(Manifest.permission.BLUETOOTH_SCAN);
            needed.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        final ArrayList<String> missing = new ArrayList<>();
        for (String permission : needed) {
            if (ApplicationLoader.applicationContext.checkSelfPermission(permission)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        return missing;
    }

    private void startGuest(LinkiDropPayload payload) {
        if (!isScreenAlive()) {
            return;
        }
        stopReader();
        heroView.setSuccess(false);
        heroView.setTransferring(true);
        heroView.setProgress(0f);
        titleView.setText(LocaleController.formatString(R.string.NM_LD_Receiving, payload.name));
        subtitleView.setText(AndroidUtilities.formatFileSize(payload.size));
        AndroidUtilities.runOnUIThread(() -> heroView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM));

        final LinkiDropGuest session = new LinkiDropGuest(getContext(), new LinkiDropGuest.Callback() {
            @Override
            public void onProgress(long done, long total) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (isScreenAlive()) {
                        heroView.setProgress(total <= 0 ? 0f : (float) done / total);
                        subtitleView.setText(AndroidUtilities.formatFileSize(done) + " / " + AndroidUtilities.formatFileSize(total));
                        LinkiDropProgress.get().update(done, total);
                    }
                });
            }

            @Override
            public void onDone(File file, boolean plugin) {
                AndroidUtilities.runOnUIThread(() -> handleReceived(file, plugin));
            }

            @Override
            public void onError(int messageRes) {
                AndroidUtilities.runOnUIThread(() -> handleReceiveError(messageRes));
            }
        });
        guest = session;
        LinkiDropProgress.get().begin(payload.name, payload.size);
        session.start(payload);
    }

    private void handleReceived(File file, boolean plugin) {
        LinkiDropProgress.get().finish(true, LocaleController.getString(R.string.NM_LD_ReceivedTitle));
        if (!isScreenAlive()) {
            return;
        }
        guest = null;
        receivedFile = file;
        receivedIsPlugin = plugin;
        heroView.setSuccess(true);
        titleView.setText(LocaleController.getString(R.string.NM_LD_ReceivedTitle));
        subtitleView.setText(LocaleController.formatString(R.string.NM_LD_SavedTo, file.getName()));
        receivedCell.setTextAndValue(file.getName(),
                LocaleController.getString(plugin ? R.string.NM_LD_InstallPlugin : R.string.NM_LD_Open), false);
        receivedCell.setVisibility(View.VISIBLE);
        BulletinFactory.of(LinkiDropActivity.this)
                .createSimpleBulletin(R.raw.contact_check,
                        LocaleController.formatString(R.string.NM_LD_Received, file.getName()))
                .show();
        // Back to listening so the peer can offer the next file.
        final long generation = sessionGeneration;
        AndroidUtilities.runOnUIThread(() -> {
            if (generation == sessionGeneration && resumed && !sending) {
                startReader();
            }
        }, 1500);
    }

    private void handleReceiveError(int messageRes) {
        LinkiDropProgress.get().finish(false, LocaleController.getString(R.string.NM_LD_Error));
        if (!isScreenAlive()) {
            return;
        }
        guest = null;
        heroView.setTransferring(false);
        heroView.setSuccess(false);
        titleView.setText(LocaleController.getString(R.string.NM_LD_Hint));
        subtitleView.setText(LocaleController.getString(messageRes));
        startReader();
    }

    private void stopGuest() {
        final LinkiDropGuest session = guest;
        guest = null;
        if (session != null) {
            session.stop();
        }
    }

    private void openReceived() {
        final File file = receivedFile;
        if (file == null || !file.exists()) {
            return;
        }
        if (receivedIsPlugin) {
            PluginsController.getInstance().showInstallDialog(this, file.getAbsolutePath(), false);
            return;
        }
        try {
            final Uri uri = androidx.core.content.FileProvider.getUriForFile(getContext(),
                    getContext().getPackageName() + ".provider", file);
            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(Intent.createChooser(intent,
                    LocaleController.getString(R.string.NM_LD_Open)), REQ_SHARE_FILE);
        } catch (Throwable e) {
            FileLog.e(e);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                    LocaleController.getString(R.string.NM_LD_Error)).show();
        }
    }

    //endregion

    //region Sending

    private void pickFile() {
        if (sending) {
            stopSending();
            return;
        }
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_PICK_FILE);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void pickPlugin() {
        if (sending) {
            stopSending();
            return;
        }
        final File[] all = PluginsController.getInstance().getPluginsDir().listFiles();
        final ArrayList<File> plugins = new ArrayList<>();
        if (all != null) {
            for (File file : all) {
                final String name = file.getName().toLowerCase();
                if (file.isFile() && (name.endsWith(".plugin") || name.endsWith(".py"))) {
                    plugins.add(file);
                }
            }
        }
        if (plugins.isEmpty()) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                    LocaleController.getString(R.string.NM_LD_NoPlugins)).show();
            return;
        }
        plugins.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        final CharSequence[] names = new CharSequence[plugins.size()];
        for (int i = 0; i < plugins.size(); i++) {
            names[i] = plugins.get(i).getName();
        }
        showDialog(new AlertDialog.Builder(getParentActivity())
                .setTitle(LocaleController.getString(R.string.NM_LD_SendPlugin))
                .setItems(names, (dialog, which) -> beginSend(plugins.get(which), plugins.get(which).getName(),
                        LinkiDropPayload.KIND_PLUGIN))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create());
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_PICK_FILE || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        final String name = displayName(uri);
        final File cache;
        try {
            cache = new File(getContext().getCacheDir(), "linkidrop-in" + (name.contains(".") ? name.substring(name.lastIndexOf('.')) : ""));
            try (InputStream in = getContext().getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(cache)) {
                if (in == null) {
                    throw new IllegalStateException("no stream");
                }
                final byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                    LocaleController.getString(R.string.NM_LD_Error)).show();
            return;
        }
        if (pickedCache != null) {
            //noinspection ResultOfMethodCallIgnored
            pickedCache.delete();
        }
        pickedCache = cache;
        beginSend(cache, name, LinkiDropPayload.KIND_FILE);
    }

    private String displayName(Uri uri) {
        String name = null;
        Cursor cursor = null;
        try {
            cursor = getContext().getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    name = cursor.getString(index);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (TextUtils.isEmpty(name)) {
            name = uri.getLastPathSegment();
        }
        return LinkiDropWire.sanitizeName(name);
    }

    private void beginSend(File file, String name, String kind) {
        if (!isScreenAlive()) {
            return;
        }
        if (file == null || !file.exists() || file.length() <= 0) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                    LocaleController.getString(R.string.NM_LD_Error)).show();
            return;
        }
        final boolean wifi = TRANSPORT_WIFI.equals(transport());
        final Runnable start = () -> reallyBeginSend(file, name, kind, wifi);
        pendingIsSend = true;
        if (wifi) {
            final String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? Manifest.permission.NEARBY_WIFI_DEVICES
                    : Manifest.permission.ACCESS_FINE_LOCATION;
            ensurePermissions(start, permission);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ensurePermissions(start, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT);
            } else {
                assertBluetoothEnabled(start);
            }
        }
    }

    private void assertBluetoothEnabled(Runnable start) {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                    LocaleController.getString(R.string.NM_LD_BluetoothOff)).show();
            return;
        }
        start.run();
    }

    private void reallyBeginSend(File file, String name, String kind, boolean wifi) {
        if (!isScreenAlive()) {
            return;
        }
        stopReader();
        stopGuest();
        stopHost();
        sending = true;
        heroView.setSuccess(false);
        heroView.setTransferring(false);
        heroView.setProgress(-1f);
        sentName = name;
        titleView.setText(LocaleController.getString(R.string.NM_LD_Preparing));
        subtitleView.setText(name);
        if (!wifi) {
            requestDiscoverable(null);
        }

        final LinkiDropHost session = new LinkiDropHost(getParentActivity(), file, name, kind, new LinkiDropHost.Callback() {
            @Override
            public void onReady(LinkiDropPayload payload) {
                FileLog.d("LinkiDrop: host ready " + payload.toUri());
                if (!isScreenAlive() || !sending) {
                    return;
                }
                LinkiShareNdefService.setOverrideUri(payload.toUri());
                LinkiShareNdefService.setArmed(true, currentAccount);
                heroView.setSuccess(false);
                titleView.setText(LocaleController.getString(R.string.NM_LD_Waiting));
                subtitleView.setText(LocaleController.formatString(R.string.NM_LD_WaitingDetail, name));
                heroView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
            }

            @Override
            public void onProgress(long done, long total) {
                if (!isScreenAlive()) {
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (!isScreenAlive()) {
                        return;
                    }
                    heroView.setTransferring(true);
                    heroView.setProgress(total <= 0 ? 0f : (float) done / total);
                    subtitleView.setText(AndroidUtilities.formatFileSize(done) + " / " + AndroidUtilities.formatFileSize(total));
                    LinkiDropProgress.get().update(done, total);
                });
            }

            @Override
            public void onDone() {
                AndroidUtilities.runOnUIThread(() -> handleSent());
            }

            @Override
            public void onError(int messageRes) {
                AndroidUtilities.runOnUIThread(() -> handleSendError(messageRes));
            }
        });
        LinkiDropProgress.get().begin(name, file.length());
        host = session;
        if (wifi) {
            session.startWifi();
        } else {
            session.startBluetooth();
        }
    }

    private void handleSent() {
        LinkiDropProgress.get().finish(true, LocaleController.getString(R.string.NM_LD_SentTitle));
        sending = false;
        if (!isScreenAlive()) {
            return;
        }
        heroView.setSuccess(true);
        titleView.setText(LocaleController.getString(R.string.NM_LD_SentTitle));
        subtitleView.setText(sentName);
        BulletinFactory.of(LinkiDropActivity.this)
                .createSimpleBulletin(R.raw.contact_check,
                        LocaleController.formatString(R.string.NM_LD_Sent, sentName))
                .show();
        final long generation = sessionGeneration;
        AndroidUtilities.runOnUIThread(() -> {
            if (generation == sessionGeneration) {
                stopSending();
            }
        }, 1600);
    }

    private void handleSendError(int messageRes) {
        LinkiDropProgress.get().finish(false, LocaleController.getString(R.string.NM_LD_Error));
        if (!isScreenAlive()) {
            return;
        }
        BulletinFactory.of(LinkiDropActivity.this).createSimpleBulletin(R.raw.error,
                LocaleController.getString(messageRes)).show();
        stopSending();
    }

    private void requestDiscoverable(Runnable after) {
        try {
            final Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            final Activity activity = getParentActivity();
            if (activity != null) {
                activity.startActivity(intent);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        if (after != null) {
            after.run();
        }
    }

    private void stopSending() {
        sending = false;
        stopHost();
        LinkiShareNdefService.setOverrideUri(null);
        LinkiShareNdefService.setArmed(false, currentAccount);
        if (!isScreenAlive()) {
            return;
        }
        heroView.setSuccess(false);
        heroView.setTransferring(false);
        heroView.setProgress(-1f);
        titleView.setText(LocaleController.getString(R.string.NM_LD_Hint));
        subtitleView.setText(LocaleController.getString(R.string.NM_LD_HintDetail));
        startReader();
    }

    private void stopHost() {
        final LinkiDropHost session = host;
        host = null;
        if (session != null) {
            session.stop();
        }
        LinkiShareNdefService.setOverrideUri(null);
        LinkiShareNdefService.setArmed(false, currentAccount);
    }

    //endregion

    //region Permissions

    private void ensurePermissions(Runnable action, String... permissions) {
        final Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        final ArrayList<String> missing = new ArrayList<>();
        for (String permission : permissions) {
            if (activity.checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        if (missing.isEmpty()) {
            action.run();
            return;
        }
        pendingAction = action;
        activity.requestPermissions(missing.toArray(new String[0]), REQ_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQ_PERMISSIONS) {
            return;
        }
        final Runnable action = pendingAction;
        final boolean wasSend = pendingIsSend;
        pendingAction = null;
        pendingIsSend = false;
        boolean granted = grantResults.length > 0;
        for (int result : grantResults) {
            if (result != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }
        if (granted && action != null) {
            action.run();
            return;
        }
        BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                LocaleController.getString(R.string.NM_LD_Permission)).show();
        if (!wasSend && isScreenAlive()) {
            titleView.setText(LocaleController.getString(R.string.NM_LD_Hint));
            subtitleView.setText(LocaleController.getString(R.string.NM_LD_Permission));
            startReader();
        }
    }

    //endregion

    private boolean isScreenAlive() {
        return resumed && !isFinished && getParentActivity() != null
                && !getParentActivity().isFinishing()
                && getUserConfig().getClientUserId() == sessionOwner;
    }
}