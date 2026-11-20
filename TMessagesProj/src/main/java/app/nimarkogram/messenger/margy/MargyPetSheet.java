package app.nimarkogram.messenger.margy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/** Экран питомца: стадия серии, имя и сброс серии. */
public final class MargyPetSheet {

    private MargyPetSheet() {}

    public static void show(BaseFragment fragment, int account, long dialogId) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        final Context context = fragment.getParentActivity();
        final MargyStreaks.State state = MargyStreaks.state(account, dialogId);
        final int stage = MargyStreaks.stage(state.streak);

        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(8), dp(22), dp(4));

        final MargyPetView pet = new MargyPetView(context, stage < 0 ? MargyPetView.STAGE_EGG : stage);
        pet.setAnimating(true);
        content.addView(pet, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL));

        final TextView title = new TextView(context);
        title.setGravity(Gravity.CENTER);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        title.setText(state.petName != null && state.petName.length() > 0
                ? state.petName
                : LocaleController.getString(R.string.NM_MargyPetNoName));
        content.addView(title, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        final TextView status = new TextView(context);
        status.setGravity(Gravity.CENTER);
        status.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        status.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        status.setText(statusText(state, stage));
        content.addView(status, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 12));

        final EditText name = new EditText(context);
        name.setHint(LocaleController.getString(R.string.NM_MargyPetNameHint));
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setText(state.petName == null ? "" : state.petName);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        name.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        name.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        content.addView(name, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.NM_MargyPetTitle))
                .setView(content)
                .setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
                    final String value = name.getText().toString().trim();
                    MargyStreaks.setPetName(account, dialogId, value.length() == 0 ? null : value, null);
                })
                .setNegativeButton(LocaleController.getString(R.string.NM_MargyPetBreak), (dialog, which) -> {
                    final AlertDialog.Builder confirm = new AlertDialog.Builder(context);
                    confirm.setTitle(LocaleController.getString(R.string.NM_MargyPetBreak));
                    confirm.setMessage(LocaleController.getString(R.string.NM_MargyPetBreakConfirm));
                    confirm.setPositiveButton(LocaleController.getString(R.string.Reset), (d, w) ->
                            MargyStreaks.breakStreak(account, dialogId, null));
                    confirm.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                    fragment.showDialog(confirm.create());
                })
                .show();
    }

    private static String statusText(MargyStreaks.State state, int stage) {
        if (state.streak <= 0) {
            return LocaleController.getString(R.string.NM_MargyPetNoStreak);
        }
        final int next = MargyStreaks.nextStageAt(state.streak);
        final String base = LocaleController.formatString(
                R.string.NM_MargyPetStreak, state.streak);
        if (next == 0) {
            return base;
        }
        return base + "\n" + LocaleController.formatString(
                R.string.NM_MargyPetNext, next - state.streak, next);
    }
}