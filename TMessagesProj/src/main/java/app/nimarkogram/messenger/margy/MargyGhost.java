package app.nimarkogram.messenger.margy;

import android.content.SharedPreferences;

import com.radolyn.ayugram.utils.AyuGhostPreferences;

import app.nimarkogram.messenger.NimarkoConfig;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Призрак MargyGram. Сам механизм сокрытия статусов уже есть в приложении
 * (NimarkoConfig.ghost* и AyuGhostUtils), поэтому здесь только то, чего не
 * хватало в интерфейсе — исключения по чатам.
 *
 * Исключённый чат — тот, где призрак не действует: отметки «печатает» и
 * прочтения уходят как обычно, чтобы собеседник не решил, что с приложением
 * что-то не так. Ключи те же, что читает AyuGhostUtils.
 */
public final class MargyGhost {

    private static final String NORMAL_CHAT_PREFIX = "ghostNormalChat_";

    private MargyGhost() {}

    /** Включён ли призрак целиком. */
    public static boolean isOn() {
        return NimarkoConfig.isGhostModeActive();
    }

    public static void setOn(boolean enabled) {
        NimarkoConfig.setGhostMode(enabled);
    }

    public static boolean isExcluded(int account, long dialogId) {
        return AyuGhostPreferences.isReadExcluded(account, dialogId)
                || AyuGhostPreferences.isTypingExcluded(account, dialogId);
    }

    /**
     * Исключить чат из призрака или вернуть в него. Пишем и общий ключ, и
     * per-account «обычный чат»: второй имеет приоритет в AyuGhostUtils, и без
     * него уже созданное когда-то значение перебило бы исключение. Ключ чата
     * дополнительно пишем с обоими знаками — движок может спросить как raw id,
     * а хранится он по модулю.
     */
    public static void setExcluded(int account, long dialogId, boolean excluded) {
        AyuGhostPreferences.setGhostModeReadExclusion(dialogId, excluded);
        AyuGhostPreferences.setGhostModeTypingExclusion(dialogId, excluded);
        AyuGhostPreferences.setNormalChat(account, dialogId, excluded);
        AyuGhostPreferences.setNormalChat(account, -dialogId, excluded);
    }

    /** Диалоги аккаунта, где призрак отключён. */
    public static LinkedHashSet<Long> excludedDialogIds(int account) {
        final LinkedHashSet<Long> ids = new LinkedHashSet<>();
        final String ownPrefix = NORMAL_CHAT_PREFIX
                + UserConfig.getInstance(account).getClientUserId() + "_";
        try {
            final Map<String, ?> all = NimarkoConfig.getPreferences().getAll();
            for (Map.Entry<String, ?> kv : all.entrySet()) {
                final String key = kv.getKey();
                if (!(kv.getValue() instanceof Boolean) || !((Boolean) kv.getValue())) {
                    continue;
                }
                if (key.startsWith(ownPrefix)) {
                    addId(ids, key.substring(ownPrefix.length()));
                } else if (key.startsWith(AyuGhostPreferences.ghostReadExclusionPrefix)) {
                    addId(ids, key.substring(AyuGhostPreferences.ghostReadExclusionPrefix.length()));
                } else if (key.startsWith(AyuGhostPreferences.ghostTypingExclusionPrefix)) {
                    addId(ids, key.substring(AyuGhostPreferences.ghostTypingExclusionPrefix.length()));
                }
            }
        } catch (Throwable ignored) {
        }
        return ids;
    }

    private static void addId(LinkedHashSet<Long> ids, String raw) {
        try {
            final long id = Math.abs(Long.parseLong(raw));
            if (id != 0) {
                ids.add(id);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    public static int excludedCount(int account) {
        return excludedDialogIds(account).size();
    }

    /** Забыть все исключения: призрак снова действует во всех чатах. */
    public static void clearExclusions(int account) {
        for (Long id : excludedDialogIds(account)) {
            setExcluded(account, id, false);
        }
        try {
            final SharedPreferences.Editor editor = NimarkoConfig.getEditor();
            final String ownPrefix = NORMAL_CHAT_PREFIX
                    + UserConfig.getInstance(account).getClientUserId() + "_";
            final Map<String, ?> all = NimarkoConfig.getPreferences().getAll();
            for (String key : all.keySet()) {
                if (key.startsWith(ownPrefix)) {
                    editor.remove(key);
                }
            }
            editor.apply();
        } catch (Throwable ignored) {
        }
    }

    /** Подпись чата для списка исключений. id — по модулю, как он хранится. */
    public static CharSequence titleFor(int account, long absId) {
        final MessagesController controller = MessagesController.getInstance(account);
        final TLRPC.User user = controller.getUser(absId);
        if (user != null) {
            return org.telegram.messenger.UserObject.getUserName(user);
        }
        final TLRPC.Chat chat = controller.getChat(absId);
        if (chat != null && chat.title != null) {
            return chat.title;
        }
        return String.valueOf(absId);
    }

    /** Короткая подпись счётчика для строки настроек. */
    public static CharSequence countLabel(int account) {
        final int count = excludedCount(account);
        return count == 0
                ? org.telegram.messenger.LocaleController.getString(
                        org.telegram.messenger.R.string.NM_MargyGhostExclusionsEmpty)
                : String.valueOf(count);
    }
}