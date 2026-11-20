package app.nimarkogram.messenger.margy;

import android.content.SharedPreferences;

import app.nimarkogram.messenger.NimarkoConfig;

/**
 * Переключатели функций MargyGram. Все ключи живут в общих настройках LinkiGram,
 * отдельного хранилища нет, чтобы резервное копирование настроек осталось одним.
 */
public final class MargyConfig {

    private static final String PREFIX = "margy_";

    public static final String KEY_AVATARS = PREFIX + "avatars";
    public static final String KEY_AVATAR_SHAPE = PREFIX + "avatar_shape";
    public static final String KEY_INPUT_MAX_LINES = PREFIX + "input_max_lines";
    public static final String KEY_INPUT_TEXT_SIZE = PREFIX + "input_text_size";
    public static final String KEY_INPUT_ON_TOP = PREFIX + "input_on_top";
    public static final String KEY_EDIT_HISTORY = PREFIX + "edit_history";
    public static final String KEY_DISCORD_RAIL = PREFIX + "discord_rail";
    public static final String KEY_MONET = PREFIX + "monet";
    public static final String KEY_MUSIC_TAB = PREFIX + "music_tab";
    public static final String KEY_MINI_PLAYER = PREFIX + "mini_player";
    public static final String KEY_STREAK_FLAMES = PREFIX + "streak_flames";
    public static final String KEY_PET = PREFIX + "pet";

    /** Значения по умолчанию у повторов поля ввода. */
    public static final int[] INPUT_LINES = {3, 4, 6, 8, 12, 20};
    /** Значения по умолчанию у размера текста поля ввода. */
    public static final int[] INPUT_TEXT_SIZES = {15, 16, 17, 18, 19, 20, 22};

    private MargyConfig() {}

    public static SharedPreferences prefs() {
        return NimarkoConfig.getPreferences();
    }

    public static boolean avatars() {
        return prefs().getBoolean(KEY_AVATARS, false);
    }

    public static void toggleAvatars() {
        prefs().edit().putBoolean(KEY_AVATARS, !avatars()).apply();
    }

    public static int avatarShape() {
        return Math.max(0, Math.min(MargyAvatars.ALL.length - 1,
                prefs().getInt(KEY_AVATAR_SHAPE, 0)));
    }

    public static void setAvatarShape(int shape) {
        prefs().edit().putInt(KEY_AVATAR_SHAPE,
                Math.max(0, Math.min(MargyAvatars.ALL.length - 1, shape))).apply();
    }

    public static int inputMaxLines() {
        int value = prefs().getInt(KEY_INPUT_MAX_LINES, 6);
        return value < 1 || value > 30 ? 6 : value;
    }

    public static void setInputMaxLines(int lines) {
        prefs().edit().putInt(KEY_INPUT_MAX_LINES, Math.max(1, Math.min(30, lines))).apply();
    }

    public static int inputTextSize() {
        int value = prefs().getInt(KEY_INPUT_TEXT_SIZE, 18);
        return value < 10 || value > 30 ? 18 : value;
    }

    public static void setInputTextSize(int size) {
        prefs().edit().putInt(KEY_INPUT_TEXT_SIZE, Math.max(10, Math.min(30, size))).apply();
    }

    /** Поле ввода над списком сообщений, как в Discord и Slack. */
    public static boolean inputOnTop() {
        return prefs().getBoolean(KEY_INPUT_ON_TOP, false);
    }

    public static void toggleInputOnTop() {
        prefs().edit().putBoolean(KEY_INPUT_ON_TOP, !inputOnTop()).apply();
    }

    public static boolean editHistory() {
        return prefs().getBoolean(KEY_EDIT_HISTORY, true);
    }

    public static void toggleEditHistory() {
        prefs().edit().putBoolean(KEY_EDIT_HISTORY, !editHistory()).apply();
    }

    /** Колонка чатов слева внутри экрана чата, как серверы в Discord. */
    public static boolean discordRail() {
        return prefs().getBoolean(KEY_DISCORD_RAIL, false);
    }

    public static void toggleDiscordRail() {
        prefs().edit().putBoolean(KEY_DISCORD_RAIL, !discordRail()).apply();
    }

    /** Палитра LinkiGram You берётся из системных цветов обоев, а не из своего seed. */
    public static boolean monet() {
        return prefs().getBoolean(KEY_MONET, false);
    }

    public static void toggleMonet() {
        prefs().edit().putBoolean(KEY_MONET, !monet()).apply();
    }

    /** Отдельная вкладка музыки в нижней панели. */
    public static boolean musicTab() {
        return prefs().getBoolean(KEY_MUSIC_TAB, false);
    }

    public static void toggleMusicTab() {
        prefs().edit().putBoolean(KEY_MUSIC_TAB, !musicTab()).apply();
    }

    /** Плавающий мини-плеер поверх списка чатов. */
    public static boolean miniPlayer() {
        return prefs().getBoolean(KEY_MINI_PLAYER, false);
    }

    public static void toggleMiniPlayer() {
        prefs().edit().putBoolean(KEY_MINI_PLAYER, !miniPlayer()).apply();
    }

    public static boolean streakFlames() {
        return prefs().getBoolean(KEY_STREAK_FLAMES, true);
    }

    public static void toggleStreakFlames() {
        prefs().edit().putBoolean(KEY_STREAK_FLAMES, !streakFlames()).apply();
    }

    public static boolean pet() {
        return prefs().getBoolean(KEY_PET, false);
    }

    public static void togglePet() {
        prefs().edit().putBoolean(KEY_PET, !pet()).apply();
    }
}