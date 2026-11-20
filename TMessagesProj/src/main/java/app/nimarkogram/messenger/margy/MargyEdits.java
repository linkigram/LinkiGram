package app.nimarkogram.messenger.margy;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * История правок: когда приходит отредактированное сообщение, прежний текст
 * сохраняется, и в меню сообщения можно посмотреть все версии. Прежний текст
 * читается из базы этого телефона прямо перед записью нового, на той же
 * очереди, поэтому порядок гарантирован.
 *
 * Видно только то, что видел этот телефон: у сообщения, которое здесь никогда
 * не загружалось, прежней версии нет.
 */
public final class MargyEdits {

    private MargyEdits() {}

    private static final int MOST = 30;

    public static boolean enabled() {
        return MargyConfig.editHistory();
    }

    private static SharedPreferences store(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "margy_edits_" + account, Context.MODE_PRIVATE);
    }

    private static String key(long dialogId, int messageId) {
        return dialogId + "_" + messageId;
    }

    /**
     * Вызывается в очереди хранилища до записи отредактированных сообщений:
     * прежний текст успевает сохраниться, пока его не перезаписали.
     */
    public static void capture(int account, MessagesStorage storage, ArrayList<TLRPC.Message> messages) {
        if (!enabled() || storage == null || messages == null || messages.isEmpty()) {
            return;
        }
        for (TLRPC.Message now : messages) {
            if (now == null || now.edit_date == 0 || now.id <= 0) {
                continue;
            }
            final long dialogId = MessageObject.getDialogId(now);
            SQLiteCursor cursor = null;
            try {
                cursor = storage.getDatabase().queryFinalized(
                        "SELECT data FROM messages_v2 WHERE mid = " + now.id + " AND uid = " + dialogId);
                if (!cursor.next()) {
                    continue;
                }
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data == null) {
                    continue;
                }
                TLRPC.Message was = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                data.reuse();
                if (was == null) {
                    continue;
                }
                String before = was.message == null ? "" : was.message;
                String after = now.message == null ? "" : now.message;
                if (before.equals(after)) {
                    continue;
                }
                remember(account, dialogId, now.id, before, was.edit_date != 0 ? was.edit_date : was.date);
            } catch (Throwable error) {
                FileLog.e(error);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        }
    }

    private static synchronized void remember(int account, long dialogId, int messageId, String text, int date) {
        try {
            SharedPreferences prefs = store(account);
            JSONArray list = new JSONArray(prefs.getString(key(dialogId, messageId), "[]"));
            if (list.length() > 0 && text.equals(list.getJSONObject(list.length() - 1).optString("text"))) {
                return;
            }
            list.put(new JSONObject().put("text", text).put("date", date));
            while (list.length() > MOST) {
                list.remove(0);
            }
            prefs.edit().putString(key(dialogId, messageId), list.toString()).apply();
        } catch (Throwable error) {
            FileLog.e(error);
        }
    }

    /** Одна версия: что было написано и с какого времени (unix-секунды). */
    public static final class Version {
        public final String text;
        public final int date;

        Version(String text, int date) {
            this.text = text;
            this.date = date;
        }
    }

    /** Прежние версии сообщения, от старых к новым. */
    public static ArrayList<Version> history(MessageObject message) {
        ArrayList<Version> out = new ArrayList<>();
        if (message == null || message.messageOwner == null) {
            return out;
        }
        try {
            JSONArray list = new JSONArray(store(message.currentAccount).getString(
                    key(message.getDialogId(), message.getId()), "[]"));
            for (int i = 0; i < list.length(); i++) {
                JSONObject one = list.getJSONObject(i);
                out.add(new Version(one.optString("text"), one.optInt("date")));
            }
        } catch (Throwable error) {
            FileLog.e(error);
        }
        return out;
    }

    public static boolean has(MessageObject message) {
        return message != null && message.messageOwner != null
                && message.messageOwner.edit_date != 0
                && store(message.currentAccount).contains(key(message.getDialogId(), message.getId()));
    }
}