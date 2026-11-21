package app.nimarkogram.messenger.preferences;

import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import app.exteraless.ai.AiController;
import app.exteraless.ai.data.Service;
import app.nimarkogram.messenger.automation.ChatAssistant;
import app.nimarkogram.messenger.preferences.helpers.SettingsHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.getString;

/**
 * Настройки ИИ-ассистента для конкретного чата. Включение видно сразу: строка
 * перерисовывается через {@link SettingsHelper#updateCheckState}, а не только
 * через adapter.update, который не учитывает {@code checked} в diff.
 */
public class ChatAssistantPreferencesActivity extends NimarkoUniversalPreferencesActivity {

    private static final int ROW_ENABLED = 1;
    private static final int ROW_MENTIONS = 2;
    private static final int ROW_PROMPT = 3;
    private static final int ROW_AI_SETTINGS = 4;
    private static final int ROW_CLEAR = 5;

    private final long dialog;

    public ChatAssistantPreferencesActivity(int account, long dialog) {
        this.currentAccount = account;
        this.dialog = dialog;
    }

    @Override
    public String getTitle() {
        return getString(R.string.LinkiChatAssistant);
    }

    @Override
    public boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private boolean encrypted() {
        return DialogObject.isEncryptedDialog(dialog);
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final boolean enabled = ChatAssistant.enabled(currentAccount, dialog);

        items.add(SettingsHelper.asSwitchCG(ROW_ENABLED,
                getString(R.string.LinkiChatAssistantEnabled)).setChecked(enabled));

        if (encrypted()) {
            items.add(UItem.asShadow(getString(R.string.LinkiChatAssistantEncrypted)));
        } else if (!AiController.canUseAI()) {
            items.add(UItem.asShadow(getString(R.string.LinkiChatAssistantNotReady)));
        } else if (enabled) {
            Service service = AiController.getSelected();
            items.add(UItem.asShadow(LocaleController.formatString(R.string.LinkiChatAssistantReady,
                    service.getShortModel())));
        } else {
            items.add(UItem.asShadow(getString(R.string.LinkiChatAssistantInfo)));
        }

        if (dialog < 0) {
            items.add(SettingsHelper.asSwitchCG(ROW_MENTIONS,
                    getString(R.string.LinkiChatAssistantMentions))
                    .setChecked(mentions()));
        }
        items.add(UItem.asButton(ROW_PROMPT, getString(R.string.LinkiChatAssistantPrompt),
                ChatAssistant.prompt(currentAccount, dialog)));
        items.add(UItem.asButton(ROW_AI_SETTINGS, getString(R.string.OEAiTitle)));
        items.add(UItem.asButton(ROW_CLEAR, getString(R.string.OEAiClearHistory)));

        String error = ChatAssistant.prefs().getString(
                ChatAssistant.key(currentAccount, dialog) + ":error", "");
        if (!error.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.OEAiFailed) + " · " + error));
        }
    }

    private boolean mentions() {
        return ChatAssistant.prefs().getBoolean(
                ChatAssistant.key(currentAccount, dialog) + ":mentions", true);
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ROW_ENABLED) {
            final boolean next = !ChatAssistant.enabled(currentAccount, dialog);
            if (next) {
                if (encrypted()) {
                    BulletinFactory.of(this)
                            .createErrorBulletin(getString(R.string.LinkiChatAssistantEncrypted)).show();
                    return;
                }
                if (!AiController.canUseAI()) {
                    BulletinFactory.of(this)
                            .createErrorBulletin(getString(R.string.OEAiNotConfigured)).show();
                    return;
                }
            }
            ChatAssistant.enable(currentAccount, dialog, next);
            item.setChecked(next);
            SettingsHelper.updateCheckState(view, next);
            if (next) {
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.info,
                                getString(R.string.LinkiChatAssistantEnabledNow)).show();
            }
            listView.adapter.update(false);
            return;
        }
        if (item.id == ROW_MENTIONS) {
            final boolean next = !mentions();
            ChatAssistant.prefs().edit()
                    .putBoolean(ChatAssistant.key(currentAccount, dialog) + ":mentions", next).apply();
            item.setChecked(next);
            SettingsHelper.updateCheckState(view, next);
            return;
        }
        if (item.id == ROW_AI_SETTINGS) {
            presentFragment(new app.exteraless.ai.ui.AiSettingsActivity());
            return;
        }
        if (item.id == ROW_CLEAR) {
            ChatAssistant.clear(currentAccount, dialog);
            ChatAssistant.prefs().edit().remove(
                    ChatAssistant.key(currentAccount, dialog) + ":error").apply();
            BulletinFactory.of(this)
                    .createSimpleBulletin(R.raw.info, getString(R.string.OEAiClearHistory)).show();
            listView.adapter.update(false);
            return;
        }
        if (item.id == ROW_PROMPT) {
            showPromptEditor();
        }
    }

    private void showPromptEditor() {
        final String key = ChatAssistant.key(currentAccount, dialog);
        EditText input = new EditText(getParentActivity());
        input.setText(ChatAssistant.prompt(currentAccount, dialog));
        input.setHint(getString(R.string.LinkiChatAssistantPrompt));
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setFilters(new android.text.InputFilter[] {
                new android.text.InputFilter.LengthFilter(8000)
        });
        FrameLayout container = new FrameLayout(getParentActivity());
        container.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(8),
                AndroidUtilities.dp(22), AndroidUtilities.dp(8));
        container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT));
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.LinkiChatAssistantPrompt));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.OK), (d, w) -> {
            ChatAssistant.prefs().edit().putString(key + ":prompt", input.getText().toString()).apply();
            ChatAssistant.clear(currentAccount, dialog);
            listView.adapter.update(true);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }
}