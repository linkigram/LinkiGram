package app.nimarkogram.messenger.preferences;

import android.view.View;

import java.util.ArrayList;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import app.nimarkogram.messenger.NimarkoConfig;
import app.nimarkogram.messenger.margy.MargyAvatars;
import app.nimarkogram.messenger.margy.MargyConfig;
import app.nimarkogram.messenger.margy.MargyGhost;
import app.nimarkogram.messenger.preferences.helpers.PopupHelper;
import app.nimarkogram.messenger.preferences.helpers.SettingsHelper;

/**
 * Настройки перенесённых функций MargyGram: формы аватарок, история правок,
 * колонка Discord, палитра из обоев, музыка, серии и питомец, поле ввода.
 */
public class MargyPreferencesActivity extends BasePreferencesActivity {

    private static final int ROW_AVATARS = 1;
    private static final int ROW_AVATAR_SHAPE = 2;
    private static final int ROW_EDIT_HISTORY = 3;
    private static final int ROW_DISCORD_RAIL = 4;
    private static final int ROW_MONET = 5;
    private static final int ROW_MUSIC = 6;
    private static final int ROW_MINI_PLAYER = 7;
    private static final int ROW_STREAKS = 8;
    private static final int ROW_PET = 9;
    private static final int ROW_INPUT_ON_TOP = 10;
    private static final int ROW_INPUT_LINES = 11;
    private static final int ROW_INPUT_TEXT_SIZE = 12;

    private static final int ROW_GHOST_MASTER = 13;
    private static final int ROW_GHOST_TYPING = 14;
    private static final int ROW_GHOST_RECORDING = 15;
    private static final int ROW_GHOST_ONLINE = 16;
    private static final int ROW_GHOST_READ = 17;
    private static final int ROW_GHOST_STORIES = 18;
    private static final int ROW_GHOST_OFFLINE_AFTER = 19;
    private static final int ROW_GHOST_READ_AFTER = 20;
    private static final int ROW_GHOST_EXCLUSIONS = 21;

    private static final int ROW_DELETED_MASTER = 22;
    private static final int ROW_DELETED_PRIVATE = 23;
    private static final int ROW_DELETED_GROUPS = 24;
    private static final int ROW_DELETED_CHANNELS = 25;
    private static final int ROW_DELETED_BOT = 26;
    private static final int ROW_DELETED_BOT_USER = 27;
    private static final int ROW_DELETED_ARCHIVED = 28;
    private static final int ROW_DELETED_MEDIA = 29;
    private static final int ROW_DELETED_TRANSLUCENT = 30;

    @Override
    public String getTitle() {
        return LocaleController.getString(R.string.NM_MargyTitle);
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_MargySectionLook)));

        items.add(SettingsHelper.asSwitchCG(ROW_AVATARS,
                        LocaleController.getString(R.string.NM_MargyAvatars),
                        LocaleController.getString(R.string.NM_MargyAvatars_Desc))
                .setChecked(MargyConfig.avatars()));
        items.add(UItem.asButton(ROW_AVATAR_SHAPE,
                LocaleController.getString(R.string.NM_MargyAvatarShape),
                shapeName(MargyConfig.avatarShape())));

        items.add(SettingsHelper.asSwitchCG(ROW_MONET,
                        LocaleController.getString(R.string.NM_MargyMonet),
                        LocaleController.getString(R.string.NM_MargyMonet_Desc))
                .setChecked(MargyConfig.monet()));

        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_MargySectionChat)));

        items.add(SettingsHelper.asSwitchCG(ROW_EDIT_HISTORY,
                        LocaleController.getString(R.string.NM_MargyEditHistory),
                        LocaleController.getString(R.string.NM_MargyEditHistory_Desc))
                .setChecked(MargyConfig.editHistory()));
        items.add(SettingsHelper.asSwitchCG(ROW_DISCORD_RAIL,
                        LocaleController.getString(R.string.NM_MargyRail),
                        LocaleController.getString(R.string.NM_MargyRail_Desc))
                .setChecked(MargyConfig.discordRail()));
        items.add(SettingsHelper.asSwitchCG(ROW_INPUT_ON_TOP,
                        LocaleController.getString(R.string.NM_MargyInputTop),
                        LocaleController.getString(R.string.NM_MargyInputTop_Desc))
                .setChecked(MargyConfig.inputOnTop()));
        items.add(UItem.asButton(ROW_INPUT_LINES,
                LocaleController.getString(R.string.NM_MargyInputLines),
                String.valueOf(MargyConfig.inputMaxLines())));
        items.add(UItem.asButton(ROW_INPUT_TEXT_SIZE,
                LocaleController.getString(R.string.NM_MargyInputTextSize),
                String.valueOf(MargyConfig.inputTextSize())));
        items.add(UItem.asShadow(LocaleController.getString(R.string.NM_MargyInputInfo)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_MargySectionMusic)));

        items.add(SettingsHelper.asSwitchCG(ROW_MUSIC,
                        LocaleController.getString(R.string.NM_MargyMusic),
                        LocaleController.getString(R.string.NM_MargyMusic_Desc))
                .setChecked(MargyConfig.musicTab()));
        items.add(SettingsHelper.asSwitchCG(ROW_MINI_PLAYER,
                        LocaleController.getString(R.string.NM_MargyMiniPlayer),
                        LocaleController.getString(R.string.NM_MargyMiniPlayer_Desc))
                .setChecked(MargyConfig.miniPlayer()));

        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_MargySectionStreaks)));

        items.add(SettingsHelper.asSwitchCG(ROW_STREAKS,
                        LocaleController.getString(R.string.NM_MargyStreaks),
                        LocaleController.getString(R.string.NM_MargyStreaks_Desc))
                .setChecked(MargyConfig.streakFlames()));
        items.add(SettingsHelper.asSwitchCG(ROW_PET,
                        LocaleController.getString(R.string.NM_MargyPet),
                        LocaleController.getString(R.string.NM_MargyPet_Desc))
                .setChecked(MargyConfig.pet()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.NM_MargyPetInfo)));

        // ===== Призрак: те же флаги, что и в AyuMoments, плюс исключения по чатам
        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_GhostMode)));
        items.add(SettingsHelper.asSwitchCG(ROW_GHOST_MASTER,
                        LocaleController.getString(R.string.NM_GhostMaster),
                        LocaleController.getString(R.string.NM_GhostMaster_Desc))
                .setChecked(MargyGhost.isOn()));
        items.add(SettingsHelper.asSwitchCG(ROW_GHOST_TYPING,
                        LocaleController.getString(R.string.NM_GhostTyping),
                        LocaleController.getString(R.string.NM_GhostTyping_Desc))
                .setChecked(NimarkoConfig.ghostTyping));
        items.add(SettingsHelper.asSwitchCG(ROW_GHOST_RECORDING,
                        LocaleController.getString(R.string.NM_GhostRecording),
                        LocaleController.getString(R.string.NM_GhostRecording_Desc))
                .setChecked(NimarkoConfig.ghostRecording));
        items.add(SettingsHelper.asSwitchCG(ROW_GHOST_ONLINE,
                        LocaleController.getString(R.string.NM_GhostOnline),
                        LocaleController.getString(R.string.NM_GhostOnline_Desc))
                .setChecked(NimarkoConfig.ghostOnline));
        items.add(SettingsHelper.asSwitchCG(ROW_GHOST_READ,
                        LocaleController.getString(R.string.NM_GhostReadReceipts),
                        LocaleController.getString(R.string.NM_GhostReadReceipts_Desc))
                .setChecked(NimarkoConfig.ghostReadReceipts));
        items.add(SettingsHelper.asSwitchCG(ROW_GHOST_STORIES,
                        LocaleController.getString(R.string.NM_GhostStories),
                        LocaleController.getString(R.string.NM_GhostStories_Desc))
                .setChecked(NimarkoConfig.ghostStories));
        items.add(SettingsHelper.asSwitchCG(ROW_GHOST_OFFLINE_AFTER,
                        LocaleController.getString(R.string.NM_GhostOfflineAfterSend),
                        LocaleController.getString(R.string.NM_GhostOfflineAfterSend_Desc))
                .setChecked(NimarkoConfig.ghostOfflineAfterSend));
        items.add(SettingsHelper.asSwitchCG(ROW_GHOST_READ_AFTER,
                        LocaleController.getString(R.string.NM_GhostReadAfterSend),
                        LocaleController.getString(R.string.NM_GhostReadAfterSend_Desc))
                .setChecked(NimarkoConfig.ghostReadAfterSend));
        items.add(UItem.asButton(ROW_GHOST_EXCLUSIONS,
                LocaleController.getString(R.string.NM_MargyGhostExclusions),
                MargyGhost.countLabel(getCurrentAccount())));
        items.add(UItem.asShadow(LocaleController.getString(R.string.NM_MargyGhostExclusionsInfo)));

        // ===== Удалённые сообщения: сохранение и восстановление из своей базы
        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_Ayu_HeaderSaving)));
        items.add(SettingsHelper.asSwitchCG(ROW_DELETED_MASTER,
                        LocaleController.getString(R.string.NM_SaveDeletedMessages),
                        LocaleController.getString(R.string.NM_SaveDeletedMessages_Desc))
                .setChecked(NimarkoConfig.saveDeletedMessages));
        if (NimarkoConfig.saveDeletedMessages) {
            items.add(SettingsHelper.asSwitchCG(ROW_DELETED_PRIVATE,
                            LocaleController.getString(R.string.NM_Ayu_SaveDeleted_Private))
                    .setChecked(NimarkoConfig.saveDeletedInPrivateChats));
            items.add(SettingsHelper.asSwitchCG(ROW_DELETED_GROUPS,
                            LocaleController.getString(R.string.NM_Ayu_SaveDeleted_Groups))
                    .setChecked(NimarkoConfig.saveDeletedInGroups));
            items.add(SettingsHelper.asSwitchCG(ROW_DELETED_CHANNELS,
                            LocaleController.getString(R.string.NM_Ayu_SaveDeleted_Channels))
                    .setChecked(NimarkoConfig.saveDeletedInChannels));
            items.add(SettingsHelper.asSwitchCG(ROW_DELETED_BOT_USER,
                            LocaleController.getString(R.string.NM_Ayu_SaveDeleted_BotUser))
                    .setChecked(NimarkoConfig.saveDeletedMessageForBotUser));
            items.add(SettingsHelper.asSwitchCG(ROW_DELETED_BOT,
                            LocaleController.getString(R.string.NM_Ayu_SaveDeleted_BotChat))
                    .setChecked(NimarkoConfig.saveDeletedMessageForBot));
            items.add(SettingsHelper.asSwitchCG(ROW_DELETED_ARCHIVED,
                            LocaleController.getString(R.string.NM_Ayu_SaveInArchived))
                    .setChecked(NimarkoConfig.saveInArchivedChats));
            items.add(SettingsHelper.asSwitchCG(ROW_DELETED_MEDIA,
                            LocaleController.getString(R.string.NM_Ayu_SaveMedia),
                            LocaleController.getString(R.string.NM_Ayu_SaveMedia_Desc))
                    .setChecked(NimarkoConfig.saveMediaFiles));
            items.add(SettingsHelper.asSwitchCG(ROW_DELETED_TRANSLUCENT,
                            LocaleController.getString(R.string.NM_Ayu_Translucent),
                            LocaleController.getString(R.string.NM_Ayu_Translucent_Desc))
                    .setChecked(NimarkoConfig.translucentDeletedMessages));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.NM_Ayu_ShadowSaving)));
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ROW_AVATARS) {
            MargyConfig.toggleAvatars();
            listView.adapter.update(false);
        } else if (item.id == ROW_AVATAR_SHAPE) {
            final ArrayList<CharSequence> shapes = new ArrayList<>();
            for (int i = 0; i < MargyAvatars.ALL.length; i++) {
                shapes.add(shapeName(i));
            }
            PopupHelper.show(shapes, LocaleController.getString(R.string.NM_MargyAvatarShape),
                    MargyConfig.avatarShape(), getContext(), index -> {
                        MargyConfig.setAvatarShape(index);
                        SettingsHelper.updateButtonValue(view, shapeName(index).toString());
                        if (listView != null) {
                            for (int k = 0; k < listView.getChildCount(); k++) {
                                final View child = listView.getChildAt(k);
                                if (child != null) {
                                    child.invalidate();
                                }
                            }
                        }
                    });
        } else if (item.id == ROW_EDIT_HISTORY) {
            MargyConfig.toggleEditHistory();
            listView.adapter.update(false);
        } else if (item.id == ROW_DISCORD_RAIL) {
            MargyConfig.toggleDiscordRail();
            listView.adapter.update(false);
        } else if (item.id == ROW_MONET) {
            MargyConfig.toggleMonet();
            listView.adapter.update(false);
        } else if (item.id == ROW_MUSIC) {
            MargyConfig.toggleMusicTab();
            listView.adapter.update(false);
        } else if (item.id == ROW_MINI_PLAYER) {
            MargyConfig.toggleMiniPlayer();
            listView.adapter.update(false);
        } else if (item.id == ROW_STREAKS) {
            MargyConfig.toggleStreakFlames();
            listView.adapter.update(false);
        } else if (item.id == ROW_PET) {
            MargyConfig.togglePet();
            listView.adapter.update(false);
        } else if (item.id == ROW_INPUT_ON_TOP) {
            MargyConfig.toggleInputOnTop();
            listView.adapter.update(false);
        } else if (item.id == ROW_INPUT_LINES) {
            final ArrayList<CharSequence> lines = new ArrayList<>();
            int current = -1;
            for (int i = 0; i < MargyConfig.INPUT_LINES.length; i++) {
                lines.add(String.valueOf(MargyConfig.INPUT_LINES[i]));
                if (MargyConfig.INPUT_LINES[i] == MargyConfig.inputMaxLines()) {
                    current = i;
                }
            }
            PopupHelper.show(lines, LocaleController.getString(R.string.NM_MargyInputLines),
                    current, getContext(), index -> {
                        MargyConfig.setInputMaxLines(MargyConfig.INPUT_LINES[index]);
                        SettingsHelper.updateButtonValue(view,
                                String.valueOf(MargyConfig.INPUT_LINES[index]));
                    });
        } else if (item.id == ROW_INPUT_TEXT_SIZE) {
            final ArrayList<CharSequence> sizes = new ArrayList<>();
            int current = -1;
            for (int i = 0; i < MargyConfig.INPUT_TEXT_SIZES.length; i++) {
                sizes.add(String.valueOf(MargyConfig.INPUT_TEXT_SIZES[i]));
                if (MargyConfig.INPUT_TEXT_SIZES[i] == MargyConfig.inputTextSize()) {
                    current = i;
                }
            }
            PopupHelper.show(sizes, LocaleController.getString(R.string.NM_MargyInputTextSize),
                    current, getContext(), index -> {
                        MargyConfig.setInputTextSize(MargyConfig.INPUT_TEXT_SIZES[index]);
                        SettingsHelper.updateButtonValue(view,
                                String.valueOf(MargyConfig.INPUT_TEXT_SIZES[index]));
                    });
        } else if (item.id == ROW_GHOST_MASTER) {
            MargyGhost.setOn(!MargyGhost.isOn());
            listView.adapter.update(false);
        } else if (item.id == ROW_GHOST_TYPING) {
            NimarkoConfig.toggleGhostTyping();
            listView.adapter.update(false);
        } else if (item.id == ROW_GHOST_RECORDING) {
            NimarkoConfig.toggleGhostRecording();
            listView.adapter.update(false);
        } else if (item.id == ROW_GHOST_ONLINE) {
            NimarkoConfig.toggleGhostOnline();
            listView.adapter.update(false);
        } else if (item.id == ROW_GHOST_READ) {
            NimarkoConfig.toggleGhostReadReceipts();
            listView.adapter.update(false);
        } else if (item.id == ROW_GHOST_STORIES) {
            NimarkoConfig.toggleGhostStories();
            listView.adapter.update(false);
        } else if (item.id == ROW_GHOST_OFFLINE_AFTER) {
            NimarkoConfig.toggleGhostOfflineAfterSend();
            listView.adapter.update(false);
        } else if (item.id == ROW_GHOST_READ_AFTER) {
            NimarkoConfig.toggleGhostReadAfterSend();
            listView.adapter.update(false);
        } else if (item.id == ROW_GHOST_EXCLUSIONS) {
            showGhostExclusions();
        } else if (item.id == ROW_DELETED_MASTER) {
            NimarkoConfig.toggleSaveDeletedMessages();
            listView.adapter.update(false);
        } else if (item.id == ROW_DELETED_PRIVATE) {
            NimarkoConfig.toggleSaveDeletedInPrivateChats();
            view.invalidate();
        } else if (item.id == ROW_DELETED_GROUPS) {
            NimarkoConfig.toggleSaveDeletedInGroups();
            view.invalidate();
        } else if (item.id == ROW_DELETED_CHANNELS) {
            NimarkoConfig.toggleSaveDeletedInChannels();
            view.invalidate();
        } else if (item.id == ROW_DELETED_BOT) {
            NimarkoConfig.toggleSaveDeletedMessageForBot();
            view.invalidate();
        } else if (item.id == ROW_DELETED_BOT_USER) {
            NimarkoConfig.toggleSaveDeletedMessageForBotUser();
            view.invalidate();
        } else if (item.id == ROW_DELETED_ARCHIVED) {
            NimarkoConfig.toggleSaveInArchivedChats();
            view.invalidate();
        } else if (item.id == ROW_DELETED_MEDIA) {
            NimarkoConfig.toggleSaveMediaFiles();
            view.invalidate();
        } else if (item.id == ROW_DELETED_TRANSLUCENT) {
            NimarkoConfig.toggleTranslucentDeletedMessages();
            view.invalidate();
        }
    }

    /** Исключения призрака: список чатов, где статусы отправляются как обычно. */
    private void showGhostExclusions() {
        if (getParentActivity() == null) {
            return;
        }
        final ArrayList<Long> ids = new ArrayList<>(MargyGhost.excludedDialogIds(getCurrentAccount()));
        final CharSequence[] entries = new CharSequence[ids.size() + 2];
        entries[0] = LocaleController.getString(R.string.NM_MargyGhostExclusionsAdd);
        entries[1] = LocaleController.getString(R.string.NM_MargyGhostExclusionsClear);
        for (int i = 0; i < ids.size(); i++) {
            entries[i + 2] = MargyGhost.titleFor(getCurrentAccount(), ids.get(i));
        }
        new AlertDialog.Builder(getParentActivity())
                .setTitle(LocaleController.getString(R.string.NM_MargyGhostExclusions))
                .setItems(entries, (dialog, which) -> {
                    if (which == 0) {
                        showAddGhostExclusion();
                    } else if (which == 1) {
                        MargyGhost.clearExclusions(getCurrentAccount());
                        listView.adapter.update(false);
                    } else {
                        MargyGhost.setExcluded(getCurrentAccount(), ids.get(which - 2), false);
                        listView.adapter.update(false);
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.Close), null)
                .show();
    }

    /** Добавление исключения: первые диалоги списка чатов и контакты. */
    private void showAddGhostExclusion() {
        if (getParentActivity() == null) {
            return;
        }
        final ArrayList<Long> ids = new ArrayList<>();
        final ArrayList<CharSequence> titles = new ArrayList<>();
        final MessagesController controller = MessagesController.getInstance(getCurrentAccount());
        final ArrayList<TLRPC.Dialog> dialogs = controller.getDialogs(0);
        for (int i = 0; i < dialogs.size() && ids.size() < 60; i++) {
            final long id = dialogs.get(i).id;
            if (id == 0 || org.telegram.messenger.DialogObject.isFolderDialogId(id)) {
                continue;
            }
            final CharSequence title = MargyGhost.titleFor(getCurrentAccount(), Math.abs(id));
            if (title == null || title.length() == 0) {
                continue;
            }
            ids.add(id);
            titles.add(title);
        }
        if (ids.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(getParentActivity())
                .setTitle(LocaleController.getString(R.string.NM_MargyGhostExclusionsAdd))
                .setItems(titles.toArray(new CharSequence[0]), (dialog, which) -> {
                    MargyGhost.setExcluded(getCurrentAccount(), ids.get(which), true);
                    listView.adapter.update(false);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private static CharSequence shapeName(int shape) {
        switch (shape) {
            case 0: return LocaleController.getString(R.string.NM_MargyShapeCircle);
            case 1: return LocaleController.getString(R.string.NM_MargyShapeRounded);
            case 2: return LocaleController.getString(R.string.NM_MargyShapeSquare);
            case 3: return LocaleController.getString(R.string.NM_MargyShapeCookie);
            case 4: return LocaleController.getString(R.string.NM_MargyShapeClover);
            case 5: return LocaleController.getString(R.string.NM_MargyShapeBurst);
            case 6: return LocaleController.getString(R.string.NM_MargyShapeSun);
            case 7: return LocaleController.getString(R.string.NM_MargyShapePentagon);
            case 8: return LocaleController.getString(R.string.NM_MargyShapeHexagon);
            default: return LocaleController.getString(R.string.NM_MargyShapeHeart);
        }
    }
}