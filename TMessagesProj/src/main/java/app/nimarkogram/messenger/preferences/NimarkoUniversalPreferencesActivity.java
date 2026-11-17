package app.nimarkogram.messenger.preferences;

/**
 * LinkiGram settings screen. It used to have its own list container, which
 * made these screens differ from the rest of the settings: no wallpaper, an
 * overlay title and no adaptive header. Now it is the same base as the other
 * 25 screens, so every LinkiGram preference screen shares one chrome and one
 * section rendering path.
 */
public abstract class NimarkoUniversalPreferencesActivity extends BasePreferencesActivity {

    protected void showRestartBulletin() {
        org.telegram.ui.Components.BulletinFactory.of(this).createSimpleBulletin(
                org.telegram.messenger.R.raw.info,
                org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.NM_RestartRequired),
                org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.NM_Restart),
                () -> {
                    android.content.Context ctx = getParentActivity() != null ? getParentActivity() : getContext();
                    app.nimarkogram.messenger.utils.AppRestartHelper.triggerRebirth(ctx);
                }
        ).show();
    }

    protected void setMD3(boolean enabled) {
        // Compatibility hook retained for the LinkiGram settings activities.
    }

    protected static void updateCheckState(android.view.View view, boolean checked) {
        if (view instanceof org.telegram.ui.Cells.TextCheckCell) {
            ((org.telegram.ui.Cells.TextCheckCell) view).setChecked(checked);
        } else if (view instanceof org.telegram.ui.Cells.NotificationsCheckCell) {
            ((org.telegram.ui.Cells.NotificationsCheckCell) view).setChecked(checked);
        }
    }
}
