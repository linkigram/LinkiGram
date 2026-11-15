package app.nimarkogram.messenger.appearance;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.Gravity;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;

/**
 * The single Material appearance layer for Telegram's existing native views
 * (formerly "LinkiGram You"). It carries the Yougram-style expressive scheme:
 * an accent seed drives every tonal role, an accent-tinted surface ladder, a
 * contrast-checked bubble palette, grouped section cards and the floating
 * navigation pill.
 */
public final class LinkiGramYouTheme {
    private static final String PREF_ENABLED = "linkigramYouEnabled";
    public static final int NAV_COLOR_SURFACE = 0;
    public static final int NAV_COLOR_INDICATOR = 1;
    public static final int NAV_COLOR_SELECTED = 2;
    public static final int NAV_COLOR_UNSELECTED = 3;

    public static final int NAV_HEIGHT_DP = 64;
    public static final int ACTION_BAR_HEIGHT_DP = 56;
    public static final int SMALL_SHAPE_DP = 8;
    public static final int MEDIUM_SHAPE_DP = 16;
    public static final int LARGE_SHAPE_DP = 28;
    public static final int EXTRA_LARGE_SHAPE_DP = 32;
    public static final int BUBBLE_SHAPE_DP = 20;
    private static final int DEFAULT_SEED = 0xFF3F6FD8;
    private static final float SURFACE_DARKEN = 0.30f;

    private static volatile boolean enabled;
    private static volatile boolean loaded;
    private static final String PREF_SEED = "linkigramYouSeedColor";

    /** Pseudo-roles for the two chat bubble colours, which are keyed separately. */
    public static final int CUSTOM_OUT_BUBBLE = 100;
    public static final int CUSTOM_IN_BUBBLE = 101;
    /** Colour roles the customization screen can override on top of the seeded palette. */
    public static final int[] OVERRIDABLE_ROLES = {
            ClientTheme.BACKGROUND,
            ClientTheme.SURFACE,
            ClientTheme.SURFACE_HIGH,
            ClientTheme.ON_SURFACE,
            ClientTheme.SECONDARY,
            ClientTheme.PRIMARY,
            ClientTheme.CONTAINER,
            ClientTheme.OUTLINE,
            CUSTOM_OUT_BUBBLE,
            CUSTOM_IN_BUBBLE,
    };

    private LinkiGramYouTheme() { }

    public static int radius(String element, int fallback) {
        return Math.max(4, Math.min(32, app.nimarkogram.messenger.NimarkoConfig.getPreferences()
                .getInt("linkigramYouRadius_" + element, fallback)));
    }

    public static void setRadius(String element, int value) {
        app.nimarkogram.messenger.NimarkoConfig.getPreferences().edit()
                .putInt("linkigramYouRadius_" + element, Math.max(4, Math.min(32, value))).apply();
    }

    public static int getSeedColor() {
        return app.nimarkogram.messenger.NimarkoConfig.getPreferences().getInt(PREF_SEED, 0);
    }

    public static void setSeedColor(int color) {
        app.nimarkogram.messenger.NimarkoConfig.getPreferences().edit().putInt(PREF_SEED, color == 0 ? 0 : color | 0xff000000).apply();
    }

    private static String roleOverrideKey(int role, boolean dark) {
        return "linkigramYouRole_" + role + (dark ? "_dark" : "_light");
    }

    /** A user colour override for one role in one theme mode; {@code 0} means none. */
    public static int getRoleOverride(int role, boolean dark) {
        try {
            return app.nimarkogram.messenger.NimarkoConfig.getPreferences()
                    .getInt(roleOverrideKey(role, dark), 0);
        } catch (Throwable ignore) {
            return 0;
        }
    }

    public static boolean hasRoleOverride(int role, boolean dark) {
        return getRoleOverride(role, dark) != 0;
    }

    public static void setRoleOverride(int role, boolean dark, int color) {
        app.nimarkogram.messenger.NimarkoConfig.getPreferences().edit()
                .putInt(roleOverrideKey(role, dark), color == 0 ? 0 : color | 0xff000000).apply();
    }

    public static void clearRoleOverride(int role, boolean dark) {
        app.nimarkogram.messenger.NimarkoConfig.getPreferences().edit()
                .remove(roleOverrideKey(role, dark)).apply();
    }

    /** Drop every per-role override for both light and dark themes. */
    public static void clearRoleOverrides() {
        for (int role : OVERRIDABLE_ROLES) {
            clearRoleOverride(role, false);
            clearRoleOverride(role, true);
        }
    }

    public static boolean isEnabled() {
        if (loaded) return enabled;
        try {
            synchronized (LinkiGramYouTheme.class) {
                if (!loaded) {
                    enabled = app.nimarkogram.messenger.NimarkoConfig.getPreferences().getBoolean(PREF_ENABLED, true);
                    loaded = true;
                }
                return enabled;
            }
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static void setEnabled(boolean enabled) {
        if (enabled) {
            ColorOs17Theme.setEnabled(false);
        }
        LinkiGramYouTheme.enabled = enabled;
        loaded = true;
        app.nimarkogram.messenger.NimarkoConfig.getPreferences().edit().putBoolean(PREF_ENABLED, enabled).apply();
    }

    /** One role map serves dialogs, chat content, profiles, media and controls. */
    public static int resolveColor(int key, int original) {
        return ClientTheme.resolveColor(key, original);
    }

    /**
     * The accent the expressive scheme is built from: the explicit user seed when
     * set, otherwise the system wallpaper accent, otherwise the LinkiGram default.
     */
    public static int paletteSeed() {
        int custom = getSeedColor();
        if (custom != 0) return custom;
        Context context = ApplicationLoader.applicationContext;
        if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { return context.getColor(android.R.color.system_accent1_500); }
            catch (RuntimeException ignored) { }
        }
        return DEFAULT_SEED;
    }

    public static int accentColor() { return ClientTheme.color(ClientTheme.PRIMARY); }
    public static int controlTrackColor() { return ClientTheme.color(ClientTheme.TRACK); }

    /** Yougram expressive tonal roles, computed directly from the accent seed. */
    public static int roleColor(int seed, boolean dark, int role) {
        if (role == ClientTheme.BACKGROUND || role == ClientTheme.SURFACE || role == ClientTheme.SURFACE_HIGH) {
            return surfaceRole(seed, dark, role);
        }
        final int override = getRoleOverride(role, dark);
        if (override != 0) return override;
        final int primary = primary(seed, dark);
        switch (role) {
            case ClientTheme.PRIMARY:
                return primary;
            case ClientTheme.ON_PRIMARY:
                return ensureContrast(dark ? Color.BLACK : Color.WHITE, primary, 4.5f);
            case ClientTheme.CONTAINER:
                return dark ? mix(seed, Color.BLACK, 0.35f) : mix(seed, Color.WHITE, 0.75f);
            case ClientTheme.ON_CONTAINER: {
                int containerOverride = getRoleOverride(ClientTheme.CONTAINER, dark);
                if (containerOverride != 0) return ensureContrast(dark ? Color.WHITE : Color.BLACK, containerOverride, 4.5f);
                return dark ? mix(seed, Color.WHITE, 0.90f) : mix(seed, Color.BLACK, 0.65f);
            }
            case ClientTheme.SECONDARY:
                // onSurfaceVariant in Yougram: light grey on dark, muted grey on light.
                return dark ? 0xFFD0D0D0 : 0xFF4A4A4A;
            case ClientTheme.CONTAINER_SELECTED:
                return dark ? mix(0xFF121212, seed, 0.28f) : mix(0xFFFCFCFC, seed, 0.25f);
            case ClientTheme.ON_SURFACE:
                return dark ? 0xFFF2F2F2 : 0xFF141414;
            case ClientTheme.TRACK:
                return dark ? mix(0xFF242424, primary, 0.09f) : mix(0xFFE6E6E6, primary, 0.09f);
            case ClientTheme.OUTLINE:
                return dark ? 0xFF8A8A8A : 0xFF7A7A7A;
            case ClientTheme.SELECTOR:
                return Theme.multAlpha(primary, dark ? 0.14f : 0.10f);
            case ClientTheme.ERROR:
                return dark ? 0xFFFFB4AB : 0xFFBA1A1A;
            case ClientTheme.INVERSE:
                return dark ? 0xFFE5E5E7 : 0xFF303033;
            case ClientTheme.ON_INVERSE:
                return dark ? 0xFF222224 : 0xFFF5F5F5;
            case ClientTheme.SURFACE_SELECTED:
                return mix(surfaceRole(seed, dark, ClientTheme.SURFACE), primary, 0.08f);
            case ClientTheme.DIVIDER:
                return mix(surfaceRole(seed, dark, ClientTheme.BACKGROUND), dark ? 0xFF8A8A8A : 0xFF7A7A7A, 0.24f);
            default:
                return primary;
        }
    }

    /** Dark primary is lightened from the seed; light primary is the seed itself. */
    private static int primary(int seed, boolean dark) {
        int override = getRoleOverride(ClientTheme.PRIMARY, dark);
        if (override != 0) return override;
        return dark ? mix(seed, Color.WHITE, 0.20f) : seed;
    }

    /** Yougram derives every background from the accent rather than using a neutral grey. */
    private static int surfaceRole(int seed, boolean dark, int role) {
        int override = getRoleOverride(role, dark);
        if (override != 0) return override;
        final int primary = primary(seed, dark);
        int surface = mix(dark ? 0xFF121212 : 0xFFFCFCFC, primary, dark ? 0.12f : 0.07f);
        if (dark) surface = mix(surface, Color.BLACK, SURFACE_DARKEN);
        if (role == ClientTheme.BACKGROUND || role == ClientTheme.SURFACE) return surface;
        // surfaceContainerHigh: one tonal step above the base surface.
        if (dark) {
            int lift = mix(primary, Color.WHITE, 0.20f);
            int container = mix(mix(0xFF121212, primary, 0.12f), lift, 0.13f);
            return mix(container, Color.BLACK, SURFACE_DARKEN);
        }
        return mix(surface, primary, 0.10f);
    }

    public static int mix(int from, int to, float fraction) {
        return ColorUtils.blendARGB(from, to, fraction);
    }

    /** Compose-style hue/saturation/value shift, used for the secondary accent. */
    public static int hsv(int color, float hueShift, float saturation, float value) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + hueShift + 360f) % 360f;
        hsv[1] = Math.max(0f, Math.min(1f, hsv[1] * saturation));
        hsv[2] = Math.max(0f, Math.min(1f, hsv[2] * value));
        return Color.HSVToColor(hsv);
    }

    /** Contrast correction ported from Yougram's {@code ensureContrast}. */
    public static int ensureContrast(int foreground, int background, float minimum) {
        if (contrastRatio(foreground, background) >= minimum) return foreground;
        final int target = ColorUtils.calculateLuminance(background) < 0.4f ? Color.WHITE : Color.BLACK;
        int result = foreground;
        for (int i = 0; i < 10; i++) {
            result = mix(result, target, 0.2f);
            if (contrastRatio(result, background) >= minimum) return result;
        }
        return target;
    }

    public static float contrastRatio(int first, int second) {
        double a = ColorUtils.calculateLuminance(first) + 0.05;
        double b = ColorUtils.calculateLuminance(second) + 0.05;
        return (float) (Math.max(a, b) / Math.min(a, b));
    }

    /**
     * Yougram bubble palette. The argument is the Telegram theme key (not a
     * resolved colour), so incoming and outgoing bubbles, their text, links and
     * metadata each get their own contrast-checked colour.
     *
     * @return the colour to use, or {@code 0} when the key is not a bubble colour.
     */
    public static int resolveMessageColor(int key, boolean dark, int seed) {
        final boolean outgoing = key == Theme.key_chat_outBubble || key == Theme.key_chat_outBubbleSelected
                || key == Theme.key_chat_messageTextOut || key == Theme.key_chat_messageLinkOut
                || key == Theme.key_chat_outTimeText || key == Theme.key_chat_outTimeSelectedText
                || key == Theme.key_chat_outViews || key == Theme.key_chat_outViewsSelected
                || key == Theme.key_chat_outReplyLine || key == Theme.key_chat_outReplyLine2
                || key == Theme.key_chat_outReplyNameText || key == Theme.key_chat_outReplyMessageText
                || key == Theme.key_chat_outForwardedNameText || key == Theme.key_chat_outAdminText
                || key == Theme.key_chat_outAdminSelectedText;
        final boolean incoming = key == Theme.key_chat_inBubble || key == Theme.key_chat_inBubbleSelected
                || key == Theme.key_chat_messageTextIn || key == Theme.key_chat_messageLinkIn
                || key == Theme.key_chat_inTimeText || key == Theme.key_chat_inTimeSelectedText
                || key == Theme.key_chat_inViews || key == Theme.key_chat_inViewsSelected
                || key == Theme.key_chat_inReplyLine || key == Theme.key_chat_inReplyNameText
                || key == Theme.key_chat_inReplyMessageText || key == Theme.key_chat_inForwardedNameText
                || key == Theme.key_chat_inAdminText || key == Theme.key_chat_inAdminSelectedText;
        if (!outgoing && !incoming) return 0;
        final int accent = primary(seed, dark);
        final int bubbleOverride = getRoleOverride(outgoing ? CUSTOM_OUT_BUBBLE : CUSTOM_IN_BUBBLE, dark);
        int bubble = bubbleOverride != 0 ? bubbleOverride : (outgoing
                ? (dark ? mix(0xFF26282E, accent, 0.38f) : mix(0xFFE2ECF8, accent, 0.28f))
                : (dark ? 0xFF2A2C31 : 0xFFE9EAEE));
        if (dark) bubble = ensureContrast(bubble, Color.WHITE, 4.5f);
        if (key == Theme.key_chat_inBubbleSelected || key == Theme.key_chat_outBubbleSelected
                || key == Theme.key_chat_inTimeSelectedText || key == Theme.key_chat_outTimeSelectedText
                || key == Theme.key_chat_inViewsSelected || key == Theme.key_chat_outViewsSelected
                || key == Theme.key_chat_inAdminSelectedText || key == Theme.key_chat_outAdminSelectedText) {
            bubble = mix(bubble, dark ? Color.WHITE : Color.BLACK, 0.10f);
            if (dark) bubble = ensureContrast(bubble, Color.WHITE, 4.5f);
        }
        final int text = dark ? Color.WHITE
                : (contrastRatio(bubble, Color.WHITE) >= contrastRatio(bubble, Color.BLACK) ? Color.WHITE : Color.BLACK);
        final int secondary = ensureContrast(mix(text, bubble, 0.35f), bubble, 3.0f);
        final int link = ensureContrast(accent, bubble, 4.5f);
        if (key == Theme.key_chat_inBubble || key == Theme.key_chat_inBubbleSelected
                || key == Theme.key_chat_outBubble || key == Theme.key_chat_outBubbleSelected) return bubble;
        if (key == Theme.key_chat_messageTextIn || key == Theme.key_chat_messageTextOut
                || key == Theme.key_chat_inReplyMessageText || key == Theme.key_chat_outReplyMessageText) return text;
        if (key == Theme.key_chat_inTimeText || key == Theme.key_chat_outTimeText
                || key == Theme.key_chat_inTimeSelectedText || key == Theme.key_chat_outTimeSelectedText
                || key == Theme.key_chat_inViews || key == Theme.key_chat_outViews
                || key == Theme.key_chat_inViewsSelected || key == Theme.key_chat_outViewsSelected
                || key == Theme.key_chat_inAdminText || key == Theme.key_chat_outAdminText
                || key == Theme.key_chat_inAdminSelectedText || key == Theme.key_chat_outAdminSelectedText) return secondary;
        return link;
    }

    public static int navigationBarColor(Context context, int role, int fallback) {
        if (!ClientTheme.isEnabled()) return fallback;
        if (ColorOs17Theme.isEnabled()) return ColorOs17Theme.navigationBarColor(role, fallback);
        switch (role) {
            case NAV_COLOR_SURFACE: return ClientTheme.color(ClientTheme.SURFACE_HIGH);
            case NAV_COLOR_INDICATOR: return ClientTheme.color(ClientTheme.CONTAINER);
            case NAV_COLOR_SELECTED: return ClientTheme.color(ClientTheme.ON_CONTAINER);
            case NAV_COLOR_UNSELECTED: return ClientTheme.color(ClientTheme.SECONDARY);
            default: return fallback;
        }
    }

    /** The floating navigation pill is a {@code surfaceContainerHigh} capsule. */
    public static Drawable navigationBarBackgroundDrawable() {
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(ClientTheme.color(ClientTheme.SURFACE_HIGH));
        // tonal elevation at its lowest step carries a hairline outline only.
        GradientDrawable hairline = new GradientDrawable();
        hairline.setColor(navigationBarOutlineColor());
        LayerDrawable background = new LayerDrawable(new Drawable[]{hairline, surface});
        background.setLayerGravity(0, Gravity.TOP);
        background.setLayerHeight(0, AndroidUtilities.dp(1));
        return background;
    }

    /** Hairline separator between the navigation dock and the content above it. */
    public static int navigationBarOutlineColor() {
        return Theme.multAlpha(ClientTheme.color(ClientTheme.OUTLINE),
                Theme.isCurrentThemeDark() ? 0.24f : 0.16f);
    }
}
