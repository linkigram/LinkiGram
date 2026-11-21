package app.nimarkogram.messenger.plugins.ui;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import app.exteraless.ai.data.Message;
import app.nimarkogram.messenger.plugins.Plugin;
import app.nimarkogram.messenger.plugins.PluginsController;

/**
 * Chat with an AI agent that writes LinkiGram Python plugins.
 *
 * <p>Every answer that contains a full plugin file gets an "install" action; the
 * file goes through the regular {@code showInstallDialog} path, so metadata
 * validation, updates and dependency installation stay the same as for plugins
 * imported from a file.
 *
 * <p>The "report" action attaches the saved crash report and the current source
 * of a plugin, which is what makes a broken plugin debuggable inside the chat.
 */
public class PluginAgentActivity extends BaseFragment {

    private static final int MENU_REPORT = 1;
    private static final int MENU_SETTINGS = 2;
    private static final int MENU_NEW_CHAT = 3;
    private static final int REQUEST_PICK_IMAGE = 4101;
    private static final int REQUEST_PICK_FILE = 4102;
    /** Text attachments are pasted into the prompt, so they stay small. */
    private static final int MAX_ATTACHMENT_BYTES = 256 * 1024;
    /** Raw camera pictures are often bigger than the compressed result. */
    private static final int MAX_READ_IMAGE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_ATTACHMENT_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_IMAGE_SIDE = 1600;

    private final String pendingPluginId;
    private final PluginAgentJob job = PluginAgentJob.getInstance();
    private final PluginAgentJob.Listener jobListener = this::onAgentChanged;
    private int renderedEntries;
    private boolean attachmentLoading;
    private boolean screenVisible;

    private ScrollView scrollView;
    private LinearLayout messagesView;
    private EditText inputField;
    private View sendButton;
    private ImageView sendIconView;
    private View attachButton;
    private LinearLayout inputPanel;
    private LinearLayout attachmentBar;
    private LinearLayout statusBar;
    private TextView statusText;
    private TextView attachmentTitle;
    private Context context;
    private String modelLabel;
    private int bottomInset;

    /** Pending attachment: either an image or the text of a file. */
    private byte[] attachmentImage;
    private String attachmentImageMime;
    private String attachmentText;
    private String attachmentName;

    public PluginAgentActivity() {
        this(null);
    }

    public PluginAgentActivity(String pluginId) {
        super();
        this.pendingPluginId = pluginId;
    }

    @Override
    public View createView(Context context) {
        this.context = context;
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.NM_PluginAgentTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_REPORT) {
                    showReportPicker(fragmentView);
                } else if (id == MENU_SETTINGS) {
                    presentFragment(new app.exteraless.ai.ui.AiSettingsActivity());
                } else if (id == MENU_NEW_CHAT) {
                    job.clearSession();
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem newChatItem = menu.addItem(MENU_NEW_CHAT, R.drawable.msg_clear);
        newChatItem.setContentDescription(LocaleController.getString(R.string.NM_PluginAgentNewChat));
        ActionBarMenuItem reportItem = menu.addItem(MENU_REPORT, R.drawable.msg_report);
        reportItem.setContentDescription(LocaleController.getString(R.string.NM_PluginAgentReport));
        ActionBarMenuItem settingsItem = menu.addItem(MENU_SETTINGS, R.drawable.msg_settings);
        settingsItem.setContentDescription(LocaleController.getString(R.string.Settings));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        ThemeBind.backgroundColor(root, Theme.key_windowBackgroundWhite);

        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        root.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        messagesView = new LinearLayout(context);
        messagesView.setOrientation(LinearLayout.VERTICAL);
        messagesView.setPadding(dp(12), dp(10), dp(12), dp(10));
        scrollView.addView(messagesView, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        inputPanel = new LinearLayout(context);
        inputPanel.setOrientation(LinearLayout.VERTICAL);
        inputPanel.setPadding(dp(8), dp(6), dp(8), dp(8) + bottomInset);
        ThemeBind.backgroundColor(inputPanel, Theme.key_windowBackgroundWhite);
        root.addView(inputPanel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        statusBar = new LinearLayout(context);
        statusBar.setOrientation(LinearLayout.HORIZONTAL);
        statusBar.setGravity(Gravity.CENTER_VERTICAL);
        statusBar.setVisibility(View.GONE);
        statusBar.setPadding(dp(12), dp(2), dp(12), dp(6));
        inputPanel.addView(statusBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT));

        statusText = new TextView(context);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        statusText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        statusText.setText(LocaleController.getString(R.string.NM_PluginAgentBackground));
        statusBar.addView(statusText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        attachmentBar = new LinearLayout(context);
        attachmentBar.setOrientation(LinearLayout.HORIZONTAL);
        attachmentBar.setGravity(Gravity.CENTER_VERTICAL);
        attachmentBar.setBackground(Theme.createRoundRectDrawable(dp(12),
                Theme.getColor(Theme.key_chat_messagePanelBackground)));
        attachmentBar.setPadding(dp(10), dp(4), dp(4), dp(4));
        attachmentBar.setVisibility(View.GONE);
        inputPanel.addView(attachmentBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 4, 4));

        ImageView attachmentIcon = new ImageView(context);
        attachmentIcon.setScaleType(ImageView.ScaleType.CENTER);
        attachmentIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.msg_filehq).mutate());
        attachmentIcon.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
        attachmentBar.addView(attachmentIcon, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        attachmentTitle = new TextView(context);
        attachmentTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        attachmentTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        attachmentTitle.setSingleLine(true);
        attachmentTitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        attachmentBar.addView(attachmentTitle, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        ImageView attachmentRemove = new ImageView(context);
        attachmentRemove.setScaleType(ImageView.ScaleType.CENTER);
        attachmentRemove.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.msg_close).mutate());
        attachmentRemove.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
        attachmentRemove.setContentDescription(LocaleController.getString(R.string.NM_PluginAgentAttachRemove));
        attachmentRemove.setOnClickListener(view -> clearAttachment());
        attachmentBar.addView(attachmentRemove, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL));

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputPanel.addView(inputRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout attachContainer = new FrameLayout(context);
        attachContainer.setBackground(Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        ImageView attachIcon = new ImageView(context);
        attachIcon.setScaleType(ImageView.ScaleType.CENTER);
        attachIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.msg_input_attach2).mutate());
        attachIcon.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
        attachContainer.addView(attachIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        attachContainer.setOnClickListener(view -> showAttachMenu());
        attachContainer.setContentDescription(LocaleController.getString(R.string.NM_PluginAgentAttach));
        attachButton = attachContainer;
        inputRow.addView(attachContainer, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 0, 0, 4, 0));

        inputField = new EditTextBoldCursor(context);
        inputField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        inputField.setHint(LocaleController.getString(R.string.NM_PluginAgentInputHint));
        inputField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        inputField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        inputField.setBackground(Theme.createRoundRectDrawable(dp(20), Theme.getColor(Theme.key_chat_messagePanelBackground)));
        inputField.setPadding(dp(14), dp(9), dp(14), dp(9));
        inputField.setMaxLines(5);
        inputField.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        inputField.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        inputField.setMinHeight(dp(40));
        inputField.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submit();
                return true;
            }
            return false;
        });
        inputRow.addView(inputField, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        FrameLayout sendContainer = new FrameLayout(context);
        sendContainer.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(40),
                Theme.getColor(Theme.key_chats_actionBackground),
                Theme.getColor(Theme.key_chats_actionPressedBackground)));
        ImageView sendIcon = new ImageView(context);
        sendIcon.setScaleType(ImageView.ScaleType.CENTER);
        sendIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_send).mutate());
        sendIcon.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
        sendContainer.addView(sendIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        sendIconView = sendIcon;
        sendContainer.setOnClickListener(view -> {
            if (job.isBusy()) {
                job.cancel();
            } else {
                submit();
            }
        });
        sendContainer.setContentDescription(LocaleController.getString(R.string.NM_PluginAgentSend));
        sendButton = sendContainer;
        inputRow.addView(sendContainer, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 8, 0, 2, 0));

        fragmentView = root;
        job.addListener(jobListener);
        job.markSeen();
        rebind();
        AndroidUtilities.runOnUIThread(this::reportPendingPlugin);
        return fragmentView;
    }

    private void reportPendingPlugin() {
        if (pendingPluginId == null || fragmentView == null) {
            return;
        }
        Plugin plugin = PluginsController.getInstance().plugins.get(pendingPluginId);
        if (plugin != null) {
            reportPlugin(plugin);
        }
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        bottomInset = bottom;
        if (inputPanel != null) {
            inputPanel.setPadding(dp(8), dp(6), dp(8), dp(8) + bottom);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!screenVisible) {
            screenVisible = true;
            job.setScreenVisible(true);
        }
        job.markSeen();
        onAgentChanged();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (screenVisible) {
            screenVisible = false;
            job.setScreenVisible(false);
        }
    }

    @Override
    public void onFragmentDestroy() {
        if (screenVisible) {
            screenVisible = false;
            job.setScreenVisible(false);
        }
        job.removeListener(jobListener);
        super.onFragmentDestroy();
    }

    private void submit() {
        if (job.isBusy() || inputField == null) {
            return;
        }
        String text = inputField.getText() == null ? "" : inputField.getText().toString().trim();
        if (TextUtils.isEmpty(text) && attachmentImage == null && attachmentText == null) {
            return;
        }
        final byte[] image = attachmentImage;
        final String imageMime = attachmentImageMime;
        final String fileText = attachmentText;
        final String fileName = attachmentName;
        inputField.setText("");
        clearAttachment();

        if (image != null && image.length > 0) {
            String prompt = TextUtils.isEmpty(text)
                    ? LocaleController.getString(R.string.NM_PluginAgentPhotoPrompt)
                    : text;
            Message message = new Message("user", prompt, image, imageMime);
            CharSequence display = format(prompt);
            if (!TextUtils.isEmpty(fileName)) {
                display = TextUtils.concat(display, "\n\n",
                        LocaleController.formatString(R.string.NM_PluginAgentAttachment, fileName));
            }
            job.submit(message, display);
            return;
        }
        if (fileText != null) {
            StringBuilder content = new StringBuilder();
            if (!TextUtils.isEmpty(text)) {
                content.append(text).append("\n\n");
            }
            content.append(LocaleController.formatString(R.string.NM_PluginAgentFilePrompt,
                    TextUtils.isEmpty(fileName)
                            ? LocaleController.getString(R.string.NM_PluginAgentAttachFile)
                            : fileName)).append("\n```\n");
            content.append(fileText);
            if (!fileText.endsWith("\n")) {
                content.append('\n');
            }
            content.append("```");
            job.submit(new Message("user", content.toString()), format(content.toString()));
            return;
        }
        job.submit(new Message("user", text), format(text));
    }

    private void send(String text) {
        job.submit(new Message("user", text), format(text));
    }

    /** Rebuilds the whole conversation from the process-wide job state. */
    private void rebind() {
        if (messagesView == null) {
            return;
        }
        messagesView.removeAllViews();
        renderedEntries = 0;
        List<PluginAgentJob.Entry> all = job.entries();
        if (all.isEmpty()) {
            addBubble(LocaleController.getString(R.string.NM_PluginAgentHint), false, null);
        } else {
            for (PluginAgentJob.Entry entry : all) {
                appendEntry(entry);
            }
        }
        modelLabel = job.modelLabel();
        setBusy(job.isBusy());
        scrollToBottom();
    }

    private void onAgentChanged() {
        if (fragmentView == null) {
            return;
        }
        List<PluginAgentJob.Entry> all = job.entries();
        if (all.isEmpty() || renderedEntries == 0 || all.size() < renderedEntries) {
            rebind();
        } else if (all.size() > renderedEntries) {
            for (int a = renderedEntries; a < all.size(); a++) {
                appendEntry(all.get(a));
            }
            modelLabel = job.modelLabel();
            setBusy(job.isBusy());
            scrollToBottom();
        } else {
            setBusy(job.isBusy());
        }
    }

    private void appendEntry(PluginAgentJob.Entry entry) {
        renderedEntries++;
        if (entry.error) {
            addError(String.valueOf(entry.text));
        } else {
            addBubble(entry.text, entry.user, entry.code);
        }
    }

    private void setBusy(boolean value) {
        if (sendButton != null) {
            sendButton.setAlpha(1f);
            sendButton.setContentDescription(LocaleController.getString(
                    value ? R.string.NM_PluginAgentStop : R.string.NM_PluginAgentSend));
        }
        if (sendIconView != null) {
            sendIconView.setImageDrawable(ContextCompat.getDrawable(context,
                    value ? R.drawable.msg_cancel : R.drawable.ic_send).mutate());
            sendIconView.setColorFilter(new PorterDuffColorFilter(
                    Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
        }
        if (attachButton != null) {
            attachButton.setEnabled(!value);
            attachButton.setAlpha(value ? 0.5f : 1f);
        }
        if (statusBar != null) {
            statusBar.setVisibility(value ? View.VISIBLE : View.GONE);
        }
        if (actionBar != null) {
            actionBar.setSubtitle(value
                    ? LocaleController.getString(R.string.NM_PluginAgentBackground)
                    : modelLabel);
        }
    }

    private void showAttachMenu() {
        if (job.isBusy() || attachmentLoading) {
            return;
        }
        ItemOptions options = ItemOptions.makeOptions(this,
                attachButton == null ? fragmentView : attachButton);
        options.add(R.drawable.msg_gallery,
                LocaleController.getString(R.string.NM_PluginAgentAttachPhoto), this::pickPhoto);
        options.add(R.drawable.msg_filehq,
                LocaleController.getString(R.string.NM_PluginAgentAttachFile), this::pickFile);
        options.show();
    }

    private void pickPhoto() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startPicker(intent, REQUEST_PICK_IMAGE);
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startPicker(intent, REQUEST_PICK_FILE);
    }

    private void startPicker(Intent intent, int requestCode) {
        try {
            startActivityForResult(intent, requestCode);
        } catch (Throwable t) {
            FileLog.e("PluginAgent: cannot open file picker", t);
            showAttachError(null);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        if (requestCode == REQUEST_PICK_IMAGE) {
            loadAttachment(uri);
        } else if (requestCode == REQUEST_PICK_FILE) {
            loadAttachment(uri);
        }
    }

    /**
     * Images are decoded, downscaled and re-encoded as JPEG; every other file must
     * be text, because only text can be pasted into the prompt.
     */
    private void loadAttachment(final Uri uri) {
        if (attachmentLoading) {
            return;
        }
        attachmentLoading = true;
        if (attachButton != null) {
            attachButton.setEnabled(false);
        }
        final Context app = ApplicationLoader.applicationContext;
        Utilities.globalQueue.postRunnable(() -> {
            String name = null;
            String mime = null;
            byte[] image = null;
            String text = null;
            String error = null;
            try {
                ContentResolver resolver = app.getContentResolver();
                name = queryName(resolver, uri);
                mime = resolver.getType(uri);
                if (isImage(mime, name)) {
                    byte[] raw = readAll(resolver, uri, MAX_READ_IMAGE_BYTES);
                    if (raw == null) {
                        error = "too_big";
                    } else if (raw.length == 0) {
                        error = "failed";
                    } else {
                        byte[] compressed = compressImage(raw);
                        if (compressed != null) {
                            image = compressed;
                            mime = "image/jpeg";
                        } else if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("image/")) {
                            image = raw;
                        } else {
                            error = "binary";
                        }
                    }
                } else {
                    byte[] raw = readAll(resolver, uri, MAX_ATTACHMENT_BYTES);
                    if (raw == null) {
                        error = "too_big";
                    } else if (isBinary(raw)) {
                        error = "binary";
                    } else {
                        text = new String(raw, StandardCharsets.UTF_8);
                    }
                }
            } catch (Throwable t) {
                FileLog.e("PluginAgent: cannot read attachment", t);
                error = "failed";
            }
            final String fileName = name;
            final String fileMime = mime;
            final byte[] imageData = image;
            final String fileText = text;
            final String failure = error;
            AndroidUtilities.runOnUIThread(() -> {
                attachmentLoading = false;
                if (attachButton != null) {
                    attachButton.setEnabled(!job.isBusy());
                }
                if (fragmentView == null) {
                    return;
                }
                if (failure != null) {
                    showAttachError(failure);
                    return;
                }
                if (imageData != null) {
                    setAttachmentImage(imageData, fileMime, fileName);
                } else if (fileText != null) {
                    setAttachmentText(fileText, fileName);
                }
            });
        });
    }

    private void setAttachmentImage(byte[] data, String mime, String name) {
        clearAttachment();
        attachmentImage = data;
        attachmentImageMime = TextUtils.isEmpty(mime) ? "image/jpeg" : mime;
        attachmentName = TextUtils.isEmpty(name)
                ? LocaleController.getString(R.string.NM_PluginAgentAttachPhoto) : name;
        showAttachment();
    }

    private void setAttachmentText(String text, String name) {
        clearAttachment();
        attachmentText = text;
        attachmentName = TextUtils.isEmpty(name)
                ? LocaleController.getString(R.string.NM_PluginAgentAttachFile) : name;
        showAttachment();
    }

    private void showAttachment() {
        if (attachmentBar != null) {
            attachmentBar.setVisibility(View.VISIBLE);
        }
        if (attachmentTitle != null) {
            attachmentTitle.setText(attachmentName);
        }
    }

    private void clearAttachment() {
        attachmentImage = null;
        attachmentImageMime = null;
        attachmentText = null;
        attachmentName = null;
        if (attachmentBar != null) {
            attachmentBar.setVisibility(View.GONE);
        }
        if (attachmentTitle != null) {
            attachmentTitle.setText("");
        }
    }

    private void showAttachError(String reason) {
        int res = R.string.NM_PluginAgentAttachFailed;
        if ("too_big".equals(reason)) {
            res = R.string.NM_PluginAgentAttachTooBig;
        } else if ("binary".equals(reason)) {
            res = R.string.NM_PluginAgentAttachBinary;
        }
        if (fragmentView != null) {
            BulletinFactory.of(this).createErrorBulletin(
                    LocaleController.getString(res)).show();
        }
    }

    private static String queryName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (!TextUtils.isEmpty(value)) {
                        return value;
                    }
                }
            }
        } catch (Throwable t) {
            FileLog.e("PluginAgent: cannot read the file name", t);
        }
        String fallback = uri.getLastPathSegment();
        return TextUtils.isEmpty(fallback) ? null : fallback;
    }

    private static byte[] readAll(ContentResolver resolver, Uri uri, int limit) throws Exception {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    return null;
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static boolean isImage(String mime, String name) {
        if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }
        if (TextUtils.isEmpty(name)) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp")
                || lower.endsWith(".heic") || lower.endsWith(".heif");
    }

    private static boolean isBinary(byte[] data) {
        int limit = Math.min(data.length, 4096);
        for (int a = 0; a < limit; a++) {
            if (data[a] == 0) {
                return true;
            }
        }
        return false;
    }

    private static byte[] compressImage(byte[] raw) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (bounds.outWidth / options.inSampleSize > MAX_IMAGE_SIDE
                    || bounds.outHeight / options.inSampleSize > MAX_IMAGE_SIDE) {
                options.inSampleSize *= 2;
            }
            Bitmap bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
            if (bitmap == null) {
                return null;
            }
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                for (int quality = 85; quality >= 55; quality -= 10) {
                    out.reset();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
                    if (out.size() <= MAX_ATTACHMENT_IMAGE_BYTES) {
                        return out.toByteArray();
                    }
                }
                return out.size() > 0 ? out.toByteArray() : null;
            } finally {
                bitmap.recycle();
            }
        } catch (Throwable t) {
            FileLog.e("PluginAgent: cannot compress the image", t);
            return null;
        }
    }

    private void addError(String text) {
        TextView view = createTextBubble(text, false);
        view.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        messagesView.addView(view, bubbleParams(false));
        scrollToBottom();
    }

    private void addBubble(CharSequence text, boolean user, String code) {
        TextView view = createTextBubble(text, user);
        messagesView.addView(view, bubbleParams(user));
        if (!user && !TextUtils.isEmpty(code)) {
            final String pluginCode = code;
            TextView install = new TextView(context);
            install.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            install.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            install.setText(LocaleController.getString(R.string.NM_PluginAgentInstall));
            install.setPadding(dp(12), dp(8), dp(12), dp(8));
            install.setOnClickListener(v -> installPlugin(pluginCode));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.START;
            lp.leftMargin = dp(4);
            lp.bottomMargin = dp(10);
            messagesView.addView(install, lp);
        }
    }

    private TextView createTextBubble(CharSequence text, boolean user) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        view.setTextIsSelectable(true);
        view.setText(text);
        if (user) {
            view.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText));
            view.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.getColor(Theme.key_chat_messagePanelBackground)));
            view.setPadding(dp(12), dp(9), dp(12), dp(9));
        } else {
            view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            view.setPadding(dp(4), dp(4), dp(4), dp(6));
        }
        return view;
    }

    private LinearLayout.LayoutParams bubbleParams(boolean user) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = user ? Gravity.END : Gravity.START;
        lp.leftMargin = user ? dp(40) : 0;
        lp.rightMargin = user ? 0 : dp(24);
        lp.bottomMargin = dp(8);
        return lp;
    }

    private void scrollToBottom() {
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    /** Turns fenced code into monospace spans; the rest stays plain text. */
    private CharSequence format(String raw) {
        return PluginAgentJob.formatAnswer(raw);
    }

    private void showReportPicker(View anchor) {
        List<Plugin> plugins = new ArrayList<>(PluginsController.getInstance().plugins.values());
        if (plugins.isEmpty()) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                    LocaleController.getString(R.string.NM_PluginAgentNoPlugins)).show();
            return;
        }
        plugins.sort(Comparator
                .comparing((Plugin plugin) -> !plugin.hasError())
                .thenComparing(plugin -> plugin.getName() == null ? "" : plugin.getName()));
        ItemOptions options = ItemOptions.makeOptions(this, anchor);
        for (Plugin plugin : plugins) {
            String name = TextUtils.isEmpty(plugin.getName()) ? plugin.getId() : plugin.getName();
            String title = name + (plugin.hasError() ? " ⚠" : "");
            options.add(R.drawable.msg_report, title, () -> reportPlugin(plugin));
        }
        options.show();
    }

    private void reportPlugin(Plugin plugin) {
        if (plugin == null || job.isBusy()) {
            return;
        }
        final String pluginId = plugin.getId();
        final String pluginName = plugin.getName();
        Utilities.globalQueue.postRunnable(() -> {
            String report;
            try {
                Plugin fresh = PluginsController.getInstance().plugins.get(pluginId);
                report = PluginAgentPrompt.report(fresh == null ? plugin : fresh);
            } catch (Throwable t) {
                FileLog.e("PluginAgent: cannot build report", t);
                report = String.valueOf(t);
            }
            final String detail = report;
            AndroidUtilities.runOnUIThread(() -> {
                if (fragmentView == null) {
                    return;
                }
                String prompt = LocaleController.formatString(R.string.NM_PluginAgentReportPrompt, pluginName);
                send(prompt + "\n\n" + detail);
            });
        });
    }

    private void installPlugin(String code) {
        final String id = PluginAgentPrompt.pluginIdOf(code);
        if (TextUtils.isEmpty(id)) {
            BulletinFactory.of(this).createErrorBulletin(
                    LocaleController.getString(R.string.NM_PluginAgentBadId)).show();
            return;
        }
        final File directory = new File(ApplicationLoader.getFilesDirFixed(), "plugin-agent");
        final File target = new File(directory, id + ".plugin");
        Utilities.globalQueue.postRunnable(() -> {
            boolean written = true;
            try {
                if (!directory.isDirectory() && !directory.mkdirs()) {
                    written = false;
                } else {
                    try (FileOutputStream output = new FileOutputStream(target)) {
                        output.write(code.getBytes("UTF-8"));
                    }
                }
            } catch (Throwable t) {
                FileLog.e("PluginAgent: cannot save plugin", t);
                written = false;
            }
            final boolean ok = written;
            AndroidUtilities.runOnUIThread(() -> {
                if (fragmentView == null) {
                    return;
                }
                if (!ok) {
                    BulletinFactory.of(this).createErrorBulletin(
                            LocaleController.getString(R.string.NM_AddPluginFailed)).show();
                    return;
                }
                try {
                    PluginsController.getInstance().showInstallDialog(
                            PluginAgentActivity.this, target.getAbsolutePath(), false);
                } catch (Throwable t) {
                    FileLog.e("PluginAgent: cannot show install dialog", t);
                    BulletinFactory.of(this).createErrorBulletin(
                            LocaleController.getString(R.string.NM_AddPluginFailed)).show();
                }
            });
        });
    }

    private static int dp(float value) {
        return AndroidUtilities.dp(value);
    }
}