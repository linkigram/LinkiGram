package app.nimarkogram.messenger.margy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

import app.nimarkogram.messenger.appearance.ClientTheme;

/** Экран музыки: текущий трек, управление и очередь. Отдельная функция за своим переключателем. */
@SuppressLint("ViewConstructor")
public class MargyMusicActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private LinearLayout list;
    private TextView nowPlaying;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.NM_MargyMusicTitle));
        actionBar.setActionBarMenuOnItemClick(item -> {
            if (item.getId() == -1) {
                finishFragment();
            }
        });

        final FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(surface(ClientTheme.BACKGROUND, Theme.key_windowBackgroundGray));

        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        nowPlaying = new TextView(context);
        nowPlaying.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        nowPlaying.setGravity(Gravity.CENTER);
        nowPlaying.setTextColor(surface(ClientTheme.SECONDARY, Theme.key_windowBackgroundWhiteGrayText));
        nowPlaying.setPadding(dp(16), dp(12), dp(16), dp(8));
        content.addView(nowPlaying, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.addView(button(context, R.drawable.ic_action_previous, () -> {
            final MediaController controller = MediaController.getInstance();
            if (controller.getPlaylist() != null && !controller.getPlaylist().isEmpty()) {
                controller.playMessageAtIndex(
                        Math.max(0, controller.getPlaylist().indexOf(controller.getPlayingMessageObject()) - 1));
            }
        }), LayoutHelper.createLinear(56, 56, 4, 0, 4, 0));
        final ImageView play = button(context, R.drawable.ic_action_play, null);
        play.setOnClickListener(v -> {
            final MediaController controller = MediaController.getInstance();
            final MessageObject playing = controller.getPlayingMessageObject();
            if (playing == null) {
                return;
            }
            if (controller.isPlayingMessage(playing)) {
                controller.pauseMessage(playing);
            } else {
                controller.playMessage(playing, 0);
            }
        });
        controls.addView(play, LayoutHelper.createLinear(64, 64, 6, 0, 6, 0));
        controls.addView(button(context, R.drawable.ic_action_next, () -> MediaController.getInstance().playNextMessage()),
                LayoutHelper.createLinear(56, 56, 4, 0, 4, 0));
        content.addView(controls, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 8));

        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(4), dp(10), dp(28));
        final ScrollView scroll = new ScrollView(context);
        scroll.addView(list, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(scroll, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 0, 1f));

        root.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fragmentView = root;
        reload();
        return fragmentView;
    }

    private ImageView button(Context context, int icon, Runnable action) {
        final ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setImageResource(icon);
        view.setColorFilter(new PorterDuffColorFilter(
                surface(ClientTheme.ON_SURFACE, Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        view.setBackground(Theme.createSelectorDrawable(
                surface(ClientTheme.SELECTOR, Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        if (action != null) {
            view.setOnClickListener(v -> {
                action.run();
                AndroidUtilities.runOnUIThread(this::reload, 160);
            });
        }
        return view;
    }

    private static int surface(int role, int fallbackKey) {
        return ClientTheme.isEnabled()
                ? ClientTheme.color(role) : Theme.getColor(fallbackKey);
    }

    private void reload() {
        if (list == null) {
            return;
        }
        list.removeAllViews();
        final MediaController controller = MediaController.getInstance();
        final MessageObject playing = controller.getPlayingMessageObject();
        nowPlaying.setText(playing == null
                ? getString(R.string.NM_MargyMusicNothing)
                : (playing.getMusicAuthor() == null ? "" : playing.getMusicAuthor() + " \u2014 ")
                    + (playing.getMusicTitle() == null ? "" : playing.getMusicTitle()));
        final ArrayList<MessageObject> playlist = controller.getPlaylist();
        if (playlist == null || playlist.isEmpty()) {
            final TextView empty = new TextView(getParentActivity());
            empty.setText(getString(R.string.NM_MargyMusicEmpty));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(24), dp(16), dp(24));
            empty.setTextColor(surface(ClientTheme.SECONDARY, Theme.key_windowBackgroundWhiteGrayText));
            list.addView(empty);
            return;
        }
        for (int i = 0; i < playlist.size(); i++) {
            final MessageObject track = playlist.get(i);
            final boolean current = track.equals(playing);
            final TextView row = new TextView(getParentActivity());
            row.setText((current ? "\u25B6  " : "     ")
                    + (track.getMusicAuthor() == null ? "" : track.getMusicAuthor() + " \u2014 ")
                    + (track.getMusicTitle() == null ? "" : track.getMusicTitle()));
            row.setSingleLine(true);
            row.setEllipsize(TextUtils.TruncateAt.END);
            row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            row.setTextColor(surface(current ? ClientTheme.PRIMARY : ClientTheme.ON_SURFACE,
                    current ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteBlackText));
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            final GradientDrawable back = new GradientDrawable();
            back.setCornerRadius(dp(16));
            if (current) {
                back.setColor(surface(ClientTheme.SURFACE_HIGH, Theme.key_listSelector));
            } else {
                back.setColor(0);
            }
            row.setBackground(back);
            final int index = i;
            row.setOnClickListener(v -> {
                controller.playMessageAtIndex(index);
                AndroidUtilities.runOnUIThread(this::reload, 200);
            });
            list.addView(row, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 2));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        reload();
    }

    @Override
    public void onPause() {
        super.onPause();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        reload();
    }

    @Override
    public boolean isLightStatusBar() {
        return Theme.isCurrentThemeDark() ? false : super.isLightStatusBar();
    }
}