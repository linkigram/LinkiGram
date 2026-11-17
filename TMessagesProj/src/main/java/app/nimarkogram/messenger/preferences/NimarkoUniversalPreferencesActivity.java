package app.nimarkogram.messenger.preferences;

/**
 * LinkiGram settings screen. It used to have its own list container, which
 * made these screens differ from the rest of the settings: no wallpaper, an
 * overlay title and no adaptive header. Now it is the same base as the other
 * 25 screens, so every LinkiGram preference screen shares one chrome and one
 * section rendering path. Restart bulletin and switch-state helpers already
 * live in {@link BasePreferencesActivity}.
 */
public abstract class NimarkoUniversalPreferencesActivity extends BasePreferencesActivity {

    protected void setMD3(boolean enabled) {
        // Compatibility hook retained for the LinkiGram settings activities.
    }
}