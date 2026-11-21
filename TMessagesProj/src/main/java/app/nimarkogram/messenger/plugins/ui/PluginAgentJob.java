package app.nimarkogram.messenger.plugins.ui;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.TypefaceSpan;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ForegroundDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.exteraless.ai.AiConfig;
import app.exteraless.ai.data.Message;
import app.exteraless.ai.data.Role;
import app.exteraless.ai.data.Service;
import app.exteraless.ai.network.Client;
import app.exteraless.ai.network.GenerationCallback;

/**
 * Process-wide state of the plugin AI agent.
 *
 * <p>The conversation used to live inside {@link PluginAgentActivity}: closing the
 * screen cancelled the request and dropped the answer. Here the request belongs to
 * the process, so it keeps running while the screen is gone, and the activity only
 * renders {@link #entries()}. Completion is announced with a bulletin when the app
 * is in the foreground and with a system notification otherwise.
 */
public final class PluginAgentJob {

    public interface Listener {
        void onAgentChanged();
    }

    /** One rendered turn of the conversation. */
    public static final class Entry {
        public final CharSequence text;
        public final boolean user;
        public final boolean error;
        /** Full plugin source when the answer contains an installable file. */
        public final String code;

        Entry(CharSequence text, boolean user, boolean error, String code) {
            this.text = text;
            this.user = user;
            this.error = error;
            this.code = code;
        }
    }

    private static final int MAX_HISTORY = 16;
    private static final int MAX_HISTORY_CHARS = 120000;
    private static final int MAX_ENTRIES = 240;
    private static final Pattern FENCE = Pattern.compile("```[a-zA-Z0-9_+.-]*\\n?");

    private static final PluginAgentJob INSTANCE = new PluginAgentJob();

    public static PluginAgentJob getInstance() {
        return INSTANCE;
    }

    private final ArrayList<Entry> entries = new ArrayList<>();
    private final ArrayList<Message> history = new ArrayList<>();
    private final ArrayList<Listener> listeners = new ArrayList<>();

    private Client client;
    private String requestId;
    private boolean busy;
    private int unseen;
    private int visibleScreens;
    private String modelLabel;

    private PluginAgentJob() {
    }

    public List<Entry> entries() {
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }

    public boolean isBusy() {
        synchronized (entries) {
            return busy;
        }
    }

    /** True when at least one agent screen is on screen right now. */
    public boolean hasVisibleScreen() {
        synchronized (entries) {
            return visibleScreens > 0;
        }
    }

    /** The agent screen reports resume/pause so completion knows what to show. */
    public void setScreenVisible(boolean visible) {
        synchronized (entries) {
            visibleScreens = Math.max(0, visibleScreens + (visible ? 1 : -1));
        }
    }

    public int unseenCount() {
        synchronized (entries) {
            return unseen;
        }
    }

    public void markSeen() {
        synchronized (entries) {
            if (unseen != 0) {
                unseen = 0;
            }
        }
        NotificationsController.getInstance(UserConfig.selectedAccount)
                .cancelPluginAgentNotification();
    }

    public String modelLabel() {
        synchronized (entries) {
            return modelLabel;
        }
    }

    public void addListener(Listener listener) {
        if (listener == null) {
            return;
        }
        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
    }

    public void removeListener(Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Appends the user turn and starts a request. Must be called on the UI thread;
     * generation callbacks are delivered there as well.
     */
    public void submit(Message message, CharSequence display) {
        synchronized (entries) {
            if (busy) {
                return;
            }
            appendLocked(new Entry(display, true, false, null));
            rememberLocked(message);
        }

        Service service = AiConfig.getSelectedService();
        if (service == null || !service.hasCredentials()) {
            append(new Entry(LocaleController.getString(R.string.NM_PluginAgentConfigure),
                    false, false, null));
            return;
        }

        Role role = new Role("PluginAgent",
                PluginAgentPrompt.SYSTEM + "\n\n" + PluginAgentPrompt.catalog());
        Client fresh = new Client.Builder()
                .roleOverride(role)
                .serviceOverride(service)
                .build();
        final String currentRequest = UUID.randomUUID().toString();

        synchronized (entries) {
            client = fresh;
            requestId = currentRequest;
            busy = true;
            modelLabel = service.getShortModel();
        }
        notifyChanged();

        final StringBuilder chunked = new StringBuilder();
        fresh.generate(currentRequest, historySnapshot(), false, new GenerationCallback() {
            @Override
            public void onChunk(String chunk) {
                chunked.append(chunk);
            }

            @Override
            public void onResponse(String response) {
                String answer = TextUtils.isEmpty(response) ? chunked.toString() : response;
                if (TextUtils.isEmpty(answer)) {
                    answer = LocaleController.getString(R.string.OEAiFailed);
                }
                boolean announced;
                synchronized (entries) {
                    if (!currentRequest.equals(requestId)) {
                        return;
                    }
                    busy = false;
                    requestId = null;
                    client = null;
                    rememberLocked(new Message("assistant", answer));
                    appendLocked(new Entry(formatAnswer(answer), false, false,
                            PluginAgentPrompt.extractCode(answer)));
                    announced = visibleScreens == 0;
                }
                notifyChanged();
                if (announced) {
                    announce(answer, false);
                }
            }

            @Override
            public void onError(int code, String message) {
                boolean announced;
                String detail = TextUtils.isEmpty(message)
                        ? LocaleController.getString(R.string.OEAiFailed)
                        : message;
                synchronized (entries) {
                    if (!currentRequest.equals(requestId)) {
                        return;
                    }
                    busy = false;
                    requestId = null;
                    client = null;
                    appendLocked(new Entry(code == 0 ? detail : detail + " · " + code,
                            false, true, null));
                    announced = visibleScreens == 0;
                }
                notifyChanged();
                if (announced) {
                    announce(detail, true);
                }
            }
        });
    }

    /** Stops the current request; the conversation stays. */
    public void cancel() {
        Client active;
        String id;
        synchronized (entries) {
            active = client;
            id = requestId;
            client = null;
            requestId = null;
            if (busy) {
                busy = false;
                appendLocked(new Entry(LocaleController.getString(R.string.NM_PluginAgentCancelled),
                        false, true, null));
            }
        }
        if (active != null && id != null) {
            try {
                active.cancel(id);
            } catch (Throwable t) {
                FileLog.e("PluginAgent: cannot cancel the request", t);
            }
        }
        notifyChanged();
    }

    /** Drops the whole conversation. Ignored while a request is running. */
    public void clearSession() {
        synchronized (entries) {
            if (busy) {
                return;
            }
            entries.clear();
            history.clear();
        }
        notifyChanged();
    }

    private List<Message> historySnapshot() {
        synchronized (entries) {
            return new ArrayList<>(history);
        }
    }

    private void append(Entry entry) {
        synchronized (entries) {
            appendLocked(entry);
        }
        notifyChanged();
    }

    private void appendLocked(Entry entry) {
        entries.add(entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
    }

    private void rememberLocked(Message message) {
        history.add(message);
        int chars = 0;
        int keepFrom = 0;
        for (int a = history.size() - 1; a >= 0; a--) {
            String content = history.get(a).content();
            chars += content == null ? 0 : content.length();
            boolean tooOld = history.size() - a > MAX_HISTORY
                    || (chars > MAX_HISTORY_CHARS && a < history.size() - 1);
            if (tooOld) {
                keepFrom = a + 1;
                break;
            }
        }
        if (keepFrom > 0) {
            history.subList(0, keepFrom).clear();
        }
        // Base64 pictures are heavy; only the newest two stay attached.
        int images = 0;
        for (int a = history.size() - 1; a >= 0; a--) {
            Message item = history.get(a);
            if (item.getImageData() != null) {
                images++;
                if (images > 2) {
                    item.clearImage();
                }
            }
        }
    }

    private void notifyChanged() {
        List<Listener> copy;
        synchronized (listeners) {
            copy = new ArrayList<>(listeners);
        }
        for (Listener listener : copy) {
            try {
                listener.onAgentChanged();
            } catch (Throwable t) {
                FileLog.e("PluginAgent: listener failed", t);
            }
        }
    }

    private void announce(String answer, boolean error) {
        String text = error
                ? LocaleController.getString(R.string.NM_PluginAgentFailedTitle)
                : LocaleController.getString(R.string.NM_PluginAgentDone);
        synchronized (entries) {
            unseen++;
        }
        if (ForegroundDetector.getInstance().isForeground()) {
            try {
                BulletinFactory.global()
                        .createSimpleBulletin(error ? R.raw.error : R.raw.info, text)
                        .show();
            } catch (Throwable t) {
                FileLog.e("PluginAgent: cannot show the completion bulletin", t);
            }
            return;
        }
        try {
            NotificationsController.getInstance(UserConfig.selectedAccount)
                    .showPluginAgentNotification(text);
        } catch (Throwable t) {
            FileLog.e("PluginAgent: cannot show the completion notification", t);
        }
    }

    /** Turns fenced code into monospace spans; the rest stays plain text. */
    static CharSequence formatAnswer(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "";
        }
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] parts = raw.split("```", -1);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (i % 2 == 1) {
                Matcher fence = FENCE.matcher(part);
                if (fence.lookingAt() && fence.end() <= 16) {
                    part = part.substring(fence.end());
                }
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                    out.append('\n');
                }
                int start = out.length();
                out.append(part.trim());
                out.setSpan(new TypefaceSpan("monospace"), start, out.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.append('\n');
            } else {
                out.append(part);
            }
        }
        return out;
    }
}