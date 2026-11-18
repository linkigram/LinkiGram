package app.nimarkogram.messenger.linkidrop;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;

import app.nimarkogram.messenger.NimarkoConfig;

/**
 * Owns the receive side of Linki Drop outside the dedicated screen, so a tap on
 * a sending phone works from any screen and the transfer keeps going while the
 * user navigates. The floating pill reports progress app-wide.
 */
public final class LinkiDropTransfer {

    private static final LinkiDropTransfer INSTANCE = new LinkiDropTransfer();

    private LinkiDropGuest guest;
    private boolean running;

    private LinkiDropTransfer() {
    }

    public static LinkiDropTransfer get() {
        return INSTANCE;
    }

    public boolean isRunning() {
        return running;
    }

    /** Receives one offer in the background; safe to call from the NFC thread. */
    public void receive(Context context, LinkiDropPayload payload) {
        if (payload == null || running) {
            return;
        }
        running = true;
        final Context appContext = context.getApplicationContext();
        LinkiDropProgress.get().begin(payload.name, payload.size);
        final LinkiDropGuest session = new LinkiDropGuest(appContext, new LinkiDropGuest.Callback() {
            @Override
            public void onProgress(long done, long total) {
                LinkiDropProgress.get().update(done, total);
            }

            @Override
            public void onDone(File file, boolean plugin) {
                running = false;
                guest = null;
                LinkiDropProgress.get().finish(true,
                        LocaleController.getString(R.string.NM_LD_ReceivedTitle));
                try {
                    BulletinFactory.global().createSimpleBulletin(R.raw.contact_check,
                            LocaleController.formatString(R.string.NM_LD_Received, file.getName())).show();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }

            @Override
            public void onError(int messageRes) {
                running = false;
                guest = null;
                LinkiDropProgress.get().finish(false,
                        LocaleController.getString(R.string.NM_LD_Error));
                try {
                    BulletinFactory.global().createSimpleBulletin(R.raw.error,
                            LocaleController.getString(messageRes)).show();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        });
        guest = session;
        AndroidUtilities.runOnUIThread(() -> session.start(payload));
    }

    public void stop() {
        running = false;
        final LinkiDropGuest session = guest;
        guest = null;
        if (session != null) {
            session.stop();
        }
    }

    /** Whether the global receiver should look for offers at all. */
    public static boolean isGlobalReceiveAllowed() {
        return NimarkoConfig.linkiDropReceiveEverywhere;
    }
}