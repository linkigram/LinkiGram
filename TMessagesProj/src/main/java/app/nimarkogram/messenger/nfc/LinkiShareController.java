package app.nimarkogram.messenger.nfc;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import java.lang.ref.WeakReference;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import app.nimarkogram.messenger.NimarkoConfig;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

/** Opt-in foreground NFC exchange outside the dedicated screen. No background service. */
public final class LinkiShareController implements NfcAdapter.ReaderCallback {
    private static final LinkiShareController INSTANCE = new LinkiShareController();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final AtomicBoolean reading = new AtomicBoolean();
    private WeakReference<Activity> activity = new WeakReference<>(null);
    private NfcAdapter adapter;
    private boolean screenActive, readerOn, cardPhase;
    private volatile long generation;
    private long cooldownUntil;
    private String lastDropToken = "";
    private long lastDropAt;

    private LinkiShareController() {}

    public static void onAppResume(Activity host) {
        INSTANCE.activity = new WeakReference<>(host);
        INSTANCE.adapter = NfcAdapter.getDefaultAdapter(host);
        INSTANCE.schedule();
    }

    public static void onAppPause(Activity host) {
        if (INSTANCE.activity.get() != host) return;
        INSTANCE.stop();
        INSTANCE.activity.clear();
    }

    public static void setScreenActive(boolean active) {
        INSTANCE.screenActive = active;
        INSTANCE.stop();
        if (!active) INSTANCE.handler.post(INSTANCE::schedule);
    }

    public static boolean isSharingAllowed() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null || ApplicationLoader.mainInterfacePaused
                || SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter) return false;
        KeyguardManager lock = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return lock != null && !lock.isKeyguardLocked() && power != null && power.isInteractive();
    }

    private void stop() {
        ++generation;
        handler.removeCallbacks(tick);
        Activity host = activity.get();
        if (readerOn && host != null && adapter != null) {
            try { adapter.disableReaderMode(host); } catch (Exception e) { FileLog.e(e); }
        }
        readerOn = false;
        reading.set(false);
        LinkiShareNdefService.setArmed(false);
    }

    private void schedule() {
        handler.removeCallbacks(tick);
        if (screenActive || !NimarkoConfig.linkiShareEverywhere || activity.get() == null) return;
        handler.post(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Activity host = activity.get();
            if (screenActive || host == null || host.isFinishing()) {
                stop();
                return;
            }
            if (!isSharingAllowed() || adapter == null || !adapter.isEnabled()
                    || !UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated()) {
                stop();
                scheduleLater();
                return;
            }
            LinkiShareNdefService.setOverrideUri(null);
            try {
                if (System.currentTimeMillis() < cooldownUntil) {
                    if (readerOn) adapter.disableReaderMode(host);
                    readerOn = false;
                    LinkiShareNdefService.setArmed(true, UserConfig.selectedAccount);
                } else if (cardPhase && NimarkoConfig.linkiShareEverywhere) {
                    if (readerOn) adapter.disableReaderMode(host);
                    readerOn = false;
                    LinkiShareNdefService.setArmed(true, UserConfig.selectedAccount);
                } else {
                    LinkiShareNdefService.setArmed(false);
                    adapter.enableReaderMode(host, INSTANCE, NfcAdapter.FLAG_READER_NFC_A, null);
                    readerOn = true;
                }
                cardPhase = !cardPhase;
            } catch (Exception e) {
                FileLog.e(e);
                stop();
            }
            scheduleLater();
        }

        private void scheduleLater() { handler.postDelayed(this, 1200 + random.nextInt(600)); }
    };

    @Override public void onTagDiscovered(Tag tag) {
        final long session = generation;
        final int account = UserConfig.selectedAccount;
        final long owner = UserConfig.getInstance(account).getClientUserId();
        if (!reading.compareAndSet(false, true)) return;
        final String uri = LinkiShareActivity.readUriString(tag);
        // A Linki Drop offer is a different protocol on the same card; handle it first.
        final app.nimarkogram.messenger.linkidrop.LinkiDropPayload drop =
                app.nimarkogram.messenger.linkidrop.LinkiDropPayload.fromUri(uri);
        if (drop != null) {
            FileLog.d("LinkiDrop: global offer received");
            handler.post(() -> {
                reading.set(false);
                final Activity host = activity.get();
                if (session != generation || screenActive || host == null || host.isFinishing()
                        || !isSharingAllowed() || !app.nimarkogram.messenger.linkidrop.LinkiDropTransfer
                        .isGlobalReceiveAllowed()) {
                    return;
                }
                final long now = System.currentTimeMillis();
                if (drop.token.equals(lastDropToken) && now - lastDropAt < 15000) {
                    return;
                }
                lastDropToken = drop.token;
                lastDropAt = now;
                cooldownUntil = now + 10000;
                cardPhase = true;
                handler.removeCallbacks(tick);
                handler.post(tick);
                app.nimarkogram.messenger.linkidrop.LinkiDropTransfer.get().receive(host, drop);
            });
            return;
        }
        final LinkiSharePayload payload = LinkiShareActivity.readPayload(tag);
        handler.post(() -> {
            reading.set(false);
            Activity host = activity.get();
            if (session != generation || screenActive || !NimarkoConfig.linkiShareEverywhere
                    || host == null || host.isFinishing() || payload == null || !isSharingAllowed()
                    || UserConfig.selectedAccount != account || UserConfig.getInstance(account).getClientUserId() != owner
                    || System.currentTimeMillis() < cooldownUntil) return;
            BaseFragment top = LaunchActivity.getSafeLastFragment();
            if (top == null || top.getParentActivity() != host || top.getCurrentAccount() != account) return;
            if (top.visibleDialog != null && top.visibleDialog.isShowing()) return;
            cooldownUntil = System.currentTimeMillis() + 10000;
            cardPhase = true;
            handler.removeCallbacks(tick);
            handler.post(tick);
            top.showDialog(new AlertDialog.Builder(host, top.getResourceProvider())
                    .setTitle(LocaleController.getString(R.string.NM_LS_Received))
                    .setMessage(LocaleController.formatString(R.string.NM_LS_OpenReceived, payload.name))
                    .setPositiveButton(LocaleController.getString(R.string.Open), (dialog, which) -> {
                        if (isSharingAllowed() && LaunchActivity.getSafeLastFragment() == top
                                && UserConfig.selectedAccount == account
                                && UserConfig.getInstance(account).getClientUserId() == owner) {
                            top.presentFragment(new LinkiShareActivity(payload));
                        }
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
        });
    }
}
