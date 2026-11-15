package app.nimarkogram.messenger.preferences;

import android.view.View;
import java.util.ArrayList;
import java.util.Locale;
import org.telegram.messenger.R;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import app.nimarkogram.messenger.appearance.LinkiGramYouTheme;

public class YouCustomizationActivity extends NimarkoUniversalPreferencesActivity {
    @Override public String getTitle() { return LocaleController.getString(R.string.LinkiYouCustomize); }
    @Override public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        int color = LinkiGramYouTheme.getSeedColor();
        items.add(UItem.asHeader(LocaleController.getString(R.string.LinkiYouColors)));
        items.add(UItem.asButton(1, LocaleController.getString(R.string.LinkiYouAccent),
                color == 0 ? LocaleController.getString(R.string.LinkiYouSystemColor) : String.format(Locale.ROOT, "#%06X", color & 0xffffff)));
        items.add(UItem.asButton(2, LocaleController.getString(R.string.LinkiYouSystemColor)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.LinkiYouColorsInfo)));
        items.add(UItem.asHeader(LocaleController.getString(R.string.LinkiYouLayout)));
        items.add(UItem.asButton(3, LocaleController.getString(R.string.NM_AP_BottomTabs)));
        items.add(UItem.asButton(4, LocaleController.getString(R.string.LF_Menu)));
        items.add(UItem.asButton(20, "Пункты меню сообщения"));
        items.add(UItem.asButton(21, "Порядок действий сообщения"));
        items.add(UItem.asButton(5, "Поделиться палитрой You"));
        items.add(UItem.asButton(6, "Импортировать палитру You"));
        items.add(UItem.asHeader("Форма элементов"));
        String[] labels = {"Сообщения", "Меню", "Диалоги", "Секции"};
        String[] keys = {"bubble", "popup", "dialog", "section"};
        int[] defaults = {24, 16, 28, 24};
        for (int i = 0; i < keys.length; i++)
            items.add(UItem.asButton(10 + i, labels[i], LinkiGramYouTheme.radius(keys[i], defaults[i]) + " dp"));

        boolean dark = org.telegram.ui.ActionBar.Theme.isCurrentThemeDark();
        String[] roleLabels = {
                LocaleController.getString(R.string.LinkiYouRoleBackground),
                LocaleController.getString(R.string.LinkiYouRoleSurface),
                LocaleController.getString(R.string.LinkiYouRoleSurfaceHigh),
                LocaleController.getString(R.string.LinkiYouRoleOnSurface),
                LocaleController.getString(R.string.LinkiYouRoleSecondary),
                LocaleController.getString(R.string.LinkiYouRolePrimary),
                LocaleController.getString(R.string.LinkiYouRoleContainer),
                LocaleController.getString(R.string.LinkiYouRoleOutline),
                LocaleController.getString(R.string.LinkiYouRoleOutBubble),
                LocaleController.getString(R.string.LinkiYouRoleInBubble),
        };
        items.add(UItem.asHeader(LocaleController.getString(R.string.LinkiYouRoles)));
        for (int i = 0; i < roleLabels.length; i++) {
            int value = LinkiGramYouTheme.getRoleOverride(LinkiGramYouTheme.OVERRIDABLE_ROLES[i], dark);
            items.add(UItem.asButton(30 + i, roleLabels[i],
                    value == 0 ? LocaleController.getString(R.string.LinkiYouRoleAuto)
                            : String.format(Locale.ROOT, "#%06X", value & 0xffffff)));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.LinkiYouRolesInfo)));
        items.add(UItem.asButton(40, LocaleController.getString(R.string.LinkiYouResetColors)));
    }
    @Override public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 1) {
            ColorPicker picker = new ColorPicker(getParentActivity(), false, (color, n, applyNow) -> {
                if (applyNow) { LinkiGramYouTheme.setSeedColor(color); refresh(); }
            });
            int current = LinkiGramYouTheme.getSeedColor();
            picker.setColor(current == 0 ? 0xff6750a4 : current, 0);
            picker.setType(-1, true, 1, 1, false, 0, false);
            BottomSheet sheet = new BottomSheet(getParentActivity(), false);
            sheet.setCustomView(picker);
            sheet.show();
        } else if (item.id == 2) { LinkiGramYouTheme.setSeedColor(0); refresh(); }
        else if (item.id == 3) presentFragment(new BottomTabsPreferencesActivity());
        else if (item.id == 4) presentFragment(new DrawerPreferencesActivity());
        else if (item.id == 20) app.nimarkogram.messenger.NimarkoMessageMenuInjector.showMessageMenuItemsConfigurator(this);
        else if (item.id == 21) presentFragment(new MessageMenuOrderPreferencesActivity());
        else if (item.id == 5) {
            try {
                org.json.JSONObject preset = new org.json.JSONObject()
                        .put("format", "linkigram-you").put("version", 2)
                        .put("seed", LinkiGramYouTheme.getSeedColor());
                org.json.JSONObject colors = new org.json.JSONObject();
                for (int role : LinkiGramYouTheme.OVERRIDABLE_ROLES) {
                    for (boolean dark : new boolean[]{false, true}) {
                        int value = LinkiGramYouTheme.getRoleOverride(role, dark);
                        if (value != 0) colors.put(role + (dark ? "_dark" : "_light"), value);
                    }
                }
                if (colors.length() > 0) preset.put("colors", colors);
                android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(android.content.Intent.EXTRA_TEXT, preset.toString());
                getParentActivity().startActivity(android.content.Intent.createChooser(send, "Палитра LinkiGram You"));
            } catch (Exception error) { showPresetError(); }
        } else if (item.id == 6) {
            org.telegram.ui.Components.EditTextBoldCursor input = new org.telegram.ui.Components.EditTextBoldCursor(getParentActivity());
            input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
            input.setBackgroundDrawable(null);
            input.setHintColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextHint, getResourceProvider()));
            input.setCursorColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextLink, getResourceProvider()));
            input.setPadding(0, org.telegram.messenger.AndroidUtilities.dp(12), 0, org.telegram.messenger.AndroidUtilities.dp(12));
            android.widget.FrameLayout field = new android.widget.FrameLayout(getParentActivity());
            field.addView(input, org.telegram.ui.Components.LayoutHelper.createFrame(-1, -2, android.view.Gravity.TOP, 24, 8, 24, 8));
            input.setHint("Вставьте код палитры");
            input.setMaxLines(5);
            input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4096)});
            input.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack, getResourceProvider()));
            showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider())
                    .setTitle("Импорт палитры You").setView(field)
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
                        try {
                            org.json.JSONObject preset = new org.json.JSONObject(input.getText().toString());
                            if (!"linkigram-you".equals(preset.getString("format")))
                                throw new IllegalArgumentException();
                            int version = preset.getInt("version");
                            if (version != 1 && version != 2) throw new IllegalArgumentException();
                            Object value = preset.get("seed");
                            if (!(value instanceof Number)) throw new IllegalArgumentException();
                            long seed = ((Number) value).longValue();
                            if (seed < Integer.MIN_VALUE || seed > Integer.MAX_VALUE) throw new IllegalArgumentException();
                            LinkiGramYouTheme.setSeedColor((int) seed);
                            LinkiGramYouTheme.clearRoleOverrides();
                            if (version >= 2 && preset.has("colors")) {
                                org.json.JSONObject colors = preset.getJSONObject("colors");
                                for (int role : LinkiGramYouTheme.OVERRIDABLE_ROLES) {
                                    for (boolean dark : new boolean[]{false, true}) {
                                        String key = role + (dark ? "_dark" : "_light");
                                        if (!colors.has(key)) continue;
                                        long c = colors.getLong(key);
                                        if (c >= Integer.MIN_VALUE && c <= Integer.MAX_VALUE)
                                            LinkiGramYouTheme.setRoleOverride(role, dark, (int) c);
                                    }
                                }
                            }
                            refresh();
                        } catch (Exception error) { showPresetError(); }
                    }).create());
        } else if (item.id >= 30 && item.id < 40) {
            openRolePicker(LinkiGramYouTheme.OVERRIDABLE_ROLES[item.id - 30]);
        } else if (item.id == 40) {
            LinkiGramYouTheme.clearRoleOverrides(); refresh();
        } else if (item.id >= 10 && item.id <= 13) {
            String key = new String[]{"bubble", "popup", "dialog", "section"}[item.id - 10];
            int[] values = {4, 8, 12, 16, 20, 24, 28, 32};
            String[] labels = new String[values.length];
            for (int i = 0; i < values.length; i++) labels[i] = values[i] + " dp";
            showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider())
                    .setTitle("Скругление").setItems(labels, (dialog, selected) -> {
                        LinkiGramYouTheme.setRadius(key, values[selected]); refresh();
                    }).create());
        }
    }
    @Override public boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= 30 && item.id < 40) {
            LinkiGramYouTheme.clearRoleOverride(LinkiGramYouTheme.OVERRIDABLE_ROLES[item.id - 30],
                    org.telegram.ui.ActionBar.Theme.isCurrentThemeDark());
            refresh();
            return true;
        }
        return false;
    }

    private void openRolePicker(int role) {
        boolean dark = org.telegram.ui.ActionBar.Theme.isCurrentThemeDark();
        int current = LinkiGramYouTheme.getRoleOverride(role, dark);
        if (current == 0) current = effectiveRoleColor(role, dark) | 0xff000000;
        ColorPicker picker = new ColorPicker(getParentActivity(), false, (color, n, applyNow) -> {
            if (applyNow) { LinkiGramYouTheme.setRoleOverride(role, dark, color); refresh(); }
        });
        picker.setColor(current, 0);
        picker.setType(-1, true, 1, 1, false, 0, false);
        BottomSheet sheet = new BottomSheet(getParentActivity(), false);
        sheet.setCustomView(picker);
        sheet.show();
    }

    private int effectiveRoleColor(int role, boolean dark) {
        int seed = LinkiGramYouTheme.paletteSeed();
        if (role == LinkiGramYouTheme.CUSTOM_OUT_BUBBLE)
            return LinkiGramYouTheme.resolveMessageColor(org.telegram.ui.ActionBar.Theme.key_chat_outBubble, dark, seed);
        if (role == LinkiGramYouTheme.CUSTOM_IN_BUBBLE)
            return LinkiGramYouTheme.resolveMessageColor(org.telegram.ui.ActionBar.Theme.key_chat_inBubble, dark, seed);
        return LinkiGramYouTheme.roleColor(seed, dark, role);
    }
    private void refresh() {
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
        if (getParentLayout() != null) getParentLayout().rebuildAllFragmentViews(false, false);
    }
    private void showPresetError() {
        showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle("Палитра LinkiGram You").setMessage("Не удалось прочитать палитру. Проверьте код и версию формата.")
                .setPositiveButton(LocaleController.getString(R.string.OK), null).create());
    }
}
