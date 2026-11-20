package app.nimarkogram.messenger.preferences;

import android.view.View;

import java.util.ArrayList;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import app.nimarkogram.messenger.margy.MargyAvatars;
import app.nimarkogram.messenger.margy.MargyConfig;
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
        }
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