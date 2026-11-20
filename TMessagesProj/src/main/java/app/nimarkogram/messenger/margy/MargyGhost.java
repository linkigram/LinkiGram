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

    /**
     * Диалог исключений: добавить чат, убрать один, очистить все. Вынесен сюда,
     * чтобы им пользовались и AyuMoments, и меню сообщения.
     */
    public static void showExclusions(org.telegram.ui.ActionBar.BaseFragment fragment,
                                      int account, Runnable onChanged) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        final java.util.ArrayList<Long> ids = new java.util.ArrayList<>(excludedDialogIds(account));
        final CharSequence[] entries = new CharSequence[ids.size() + 2];
        entries[0] = org.telegram.messenger.LocaleController.getString(
                org.telegram.messenger.R.string.NM_MargyGhostExclusionsAdd);
        entries[1] = org.telegram.messenger.LocaleController.getString(
                org.telegram.messenger.R.string.NM_MargyGhostExclusionsClear);
        for (int i = 0; i < ids.size(); i++) {
            entries[i + 2] = titleFor(account, ids.get(i));
        }
        new org.telegram.ui.ActionBar.AlertDialog.Builder(fragment.getParentActivity())
                .setTitle(org.telegram.messenger.LocaleController.getString(
                        org.telegram.messenger.R.string.NM_MargyGhostExclusions))
                .setItems(entries, (dialog, which) -> {
                    if (which == 0) {
                        showAddExclusion(fragment, account, onChanged);
                    } else if (which == 1) {
                        clearExclusions(account);
                        if (onChanged != null) {
                            onChanged.run();
                        }
                    } else {
                        setExcluded(account, ids.get(which - 2), false);
                        if (onChanged != null) {
                            onChanged.run();
                        }
                    }
                })
                .setNegativeButton(org.telegram.messenger.LocaleController.getString(
                        org.telegram.messenger.R.string.Close), null)
                .show();
    }

    private static void showAddExclusion(org.telegram.ui.ActionBar.BaseFragment fragment,
                                         int account, Runnable onChanged) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        final java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
        final java.util.ArrayList<CharSequence> titles = new java.util.ArrayList<>();
        final java.util.ArrayList<TLRPC.Dialog> dialogs =
                MessagesController.getInstance(account).getDialogs(0);
        for (int i = 0; i < dialogs.size() && ids.size() < 60; i++) {
            final long id = dialogs.get(i).id;
            if (id == 0 || org.telegram.messenger.DialogObject.isFolderDialogId(id)) {
                continue;
            }
            final CharSequence title = titleFor(account, Math.abs(id));
            if (title == null || title.length() == 0) {
                continue;
            }
            ids.add(id);
            titles.add(title);
        }
        if (ids.isEmpty()) {
            return;
        }
        new org.telegram.ui.ActionBar.AlertDialog.Builder(fragment.getParentActivity())
                .setTitle(org.telegram.messenger.LocaleController.getString(
                        org.telegram.messenger.R.string.NM_MargyGhostExclusionsAdd))
                .setItems(titles.toArray(new CharSequence[0]), (dialog, which) -> {
                    setExcluded(account, ids.get(which), true);
                    if (onChanged != null) {
                        onChanged.run();
                    }
                })
                .setNegativeButton(org.telegram.messenger.LocaleController.getString(
                        org.telegram.messenger.R.string.Cancel), null)
                .show();
    }
}