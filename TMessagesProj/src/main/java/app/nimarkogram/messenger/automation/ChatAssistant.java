package app.nimarkogram.messenger.automation;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import app.exteraless.ai.AiController;
import app.exteraless.ai.data.Message;
import app.exteraless.ai.data.Role;
import app.exteraless.ai.network.Client;
import app.exteraless.ai.network.GenerationCallback;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;

/** Explicitly enabled per dialog; conversation context is never shared with other chats. */
public final class ChatAssistant {

    private static final String TAG = "ChatAssistant";

    private static final HashMap<String, Client> active = new HashMap<>();
    private static final HashMap<String, ArrayList<Message>> history = new HashMap<>();
    private static final HashMap<String, Long> cooldown = new HashMap<>();

    private ChatAssistant() {
    }

    public static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("linki_chat_assistant", Context.MODE_PRIVATE);
    }

    public static String key(int account, long dialog) {
        return UserConfig.getInstance(account).getClientUserId() + ":"
                + ConnectionsManager.getInstance(account).isTestBackend() + ":" + dialog;
    }

    public static boolean enabled(int account, long dialog) {
        return prefs().getBoolean(key(account, dialog) + ":enabled", false);
    }

    public static String prompt(int account, long dialog) {
        return prefs().getString(key(account, dialog) + ":prompt", "");
    }

    /** Сколько чатов сейчас отвечают автоматически — для подписи в настройках. */
    public static boolean enabledAnywhere(int account) {
        String prefix = UserConfig.getInstance(account).getClientUserId() + ":"
                + ConnectionsManager.getInstance(account).isTestBackend() + ":";
        for (String name : prefs().getAll().keySet()) {
            if (name.startsWith(prefix) && name.endsWith(":enabled")
                    && Boolean.TRUE.equals(prefs().getAll().get(name))) {
                return true;
            }
        }
        return false;
    }

    public static void enable(int account, long dialog, boolean value) {
        String key = key(account, dialog);
        prefs().edit().putBoolean(key + ":enabled", value)
                .putLong(key + ":since", System.currentTimeMillis() / 1000).apply();
        clear(account, dialog);
        Log.d(TAG, "enable " + dialog + " -> " + value + " key=" + key);
    }

    public static void clear(int account, long dialog) {
        String key = key(account, dialog);
        history.remove(key);
        Client client = active.remove(key);
        if (client != null) client.cancel(key);
    }

    public static void onMessages(int account, long dialog, ArrayList<MessageObject> messages) {
        if (!enabled(account, dialog)) {
            return;
        }
        if (!AiController.canUseAI()) {
            Log.d(TAG, "skip " + dialog + ": AI is not configured");
            return;
        }
        if (DialogObject.isEncryptedDialog(dialog)) {
            return;
        }
        String key = key(account, dialog);
        long user = UserConfig.getInstance(account).getClientUserId();
        long since = prefs().getLong(key + ":since", Long.MAX_VALUE);
        int last = prefs().getInt(key + ":last", 0);
        MessageObject candidate = null;
        for (MessageObject message : messages) {
            TLRPC.Message owner = message.messageOwner;
            if (owner == null) {
                continue;
            }
            String text = body(owner);
            if (message.getId() <= last || message.isOut() || owner instanceof TLRPC.TL_messageService
                    || TextUtils.isEmpty(text) || owner.date < since
                    || Math.abs(System.currentTimeMillis() / 1000 - owner.date) > 90
                    || owner.from_id == null || owner.from_id.user_id == 0 || owner.from_id.user_id == user) continue;
            TLRPC.User sender = MessagesController.getInstance(account).getUser(owner.from_id.user_id);
            if (sender == null || sender.bot) continue;
            if (dialog < 0 && prefs().getBoolean(key + ":mentions", true) && !owner.mentioned) continue;
            if (candidate == null || message.getId() > candidate.getId()) candidate = message;
        }
        if (candidate == null) {
            Log.d(TAG, "skip " + dialog + ": no fresh incoming message (last=" + last + ")");
            return;
        }
        prefs().edit().putInt(key + ":last", candidate.getId()).apply();
        if (active.containsKey(key)) {
            Log.d(TAG, "skip " + dialog + ": request in flight");
            return;
        }
        if (System.currentTimeMillis() - cooldown.getOrDefault(key, 0L) < 30000) {
            Log.d(TAG, "skip " + dialog + ": cooldown");
            return;
        }
        cooldown.put(key, System.currentTimeMillis());
        MessageObject target = candidate;
        ArrayList<Message> conversation = new ArrayList<>(history.getOrDefault(key, new ArrayList<>()));
        String input = body(target.messageOwner);
        if (input.length() > 8000) input = input.substring(0, 8000);
        conversation.add(new Message("user", input));
        String instruction = prompt(account, dialog);
        Role role = TextUtils.isEmpty(instruction) ? AiController.getSelectedRole() : new Role("Chat assistant", instruction);
        Client client = new Client.Builder().serviceOverride(AiController.getSelected()).roleOverride(role).build();
        active.put(key, client);
        Log.d(TAG, "request " + dialog + " msg=" + target.getId());
        client.generate(key, conversation, false, new GenerationCallback() {
            @Override public void onChunk(String text) {}
            @Override public void onResponse(String response) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (active.get(key) != client) return;
                    active.remove(key);
                    if (!enabled(account, dialog) || UserConfig.getInstance(account).getClientUserId() != user
                            || !key.equals(key(account, dialog)) || TextUtils.isEmpty(response)) return;
                    String output = response.length() > 4000 ? response.substring(0, 4000) : response;
                    conversation.add(new Message("assistant", output));
                    while (conversation.size() > 12) conversation.remove(0);
                    history.put(key, conversation);
                    prefs().edit().remove(key + ":error").apply();
                    Log.d(TAG, "send " + dialog + " reply=" + output.length() + " chars");
                    SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(
                            output, dialog, target, null, (TLRPC.WebPage) null, false,
                            (ArrayList<TLRPC.MessageEntity>) null, null, null, true, 0, 0, null, false));
                });
            }
            @Override public void onError(int code, String message) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (active.get(key) != client) return;
                    active.remove(key);
                    prefs().edit().putString(key + ":error", String.valueOf(code)).apply();
                    Log.d(TAG, "error " + dialog + " code=" + code + " text=" + message);
                });
            }
        });
    }

    /** Текст сообщения или подпись к медиа — фото без подписи тоже считается. */
    private static String body(TLRPC.Message owner) {
        if (owner == null) {
            return "";
        }
        if (!TextUtils.isEmpty(owner.message)) {
            return owner.message;
        }
        if (owner.media instanceof TLRPC.TL_messageMediaPhoto) {
            return "[photo]";
        }
        return "";
    }
}