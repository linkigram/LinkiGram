package app.nimarkogram.messenger.margy;

import static org.telegram.messenger.AndroidUtilities.dp;

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
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import app.nimarkogram.messenger.appearance.ClientTheme;

/**
 * Плавающая плашка плеера над списком чатов: обложка-заглушка, название,
 * кнопка воспроизведения. Отдельный переключатель, независимый от экрана музыки.
 */
public final class MargyMiniPlayer {

    private MargyMiniPlayer() {}

    private static FrameLayout host;
    private static View current;
    private static TextView labelView;
    private static ImageView playView;
    private static BaseFragment currentFragment;
    private static boolean observing;

    private static final NotificationCenter.NotificationCenterDelegate delegate =
            (id, account, args) -> AndroidUtilities.runOnUIThread(MargyMiniPlayer::refresh);

    /** Вызывать из onResume экрана списка чатов. */
    public static void sync(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        currentFragment = fragment;
        if (!MargyConfig.miniPlayer()) {
            remove();
            return;
        }
        if (!observing) {
            observing = true;
            NotificationCenter.getInstance(fragment.getCurrentAccount()).addObserver(
                    delegate, NotificationCenter.messagePlayingDidStart,
                    NotificationCenter.messagePlayingPlayStateChanged,
                    NotificationCenter.messagePlayingDidReset);
        }
        refresh();
    }

    /** Вызывать из onPause. */
    public static void detach() {
        remove();
        if (observing && currentFragment != null) {
            observing = false;
            NotificationCenter.getInstance(currentFragment.getCurrentAccount()).removeObserver(delegate);
        }
    }

    private static void refresh() {
        final BaseFragment fragment = currentFragment;
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        if (!MargyConfig.miniPlayer() || !(fragment.getFragmentView() instanceof FrameLayout)) {
            remove();
            return;
        }
        final MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (playing == null) {
            remove();
            return;
        }
        final FrameLayout parent = (FrameLayout) fragment.getFragmentView();
        if (current == null || host != parent) {
            remove();
            host = parent;
            current = build(parent.getContext(), fragment);
            parent.addView(current, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, 64, Gravity.LEFT | Gravity.BOTTOM, 12, 0, 12, 96));
        }
        bind(playing);
    }

    private static void remove() {
        if (current != null && host != null) {
            host.removeView(current);
        }
        current = null;
        host = null;
        labelView = null;
        playView = null;
    }

    private static View build(Context context, BaseFragment fragment) {
        final LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(10), dp(12), dp(10));
        final GradientDrawable back = new GradientDrawable();
        back.setCornerRadius(dp(22));
        back.setColor(ClientTheme.isEnabled()
                ? ClientTheme.color(ClientTheme.SURFACE_HIGH)
                : Theme.getColor(Theme.key_chat_serviceBackgroundSelector));
        bar.setBackground(back);
        bar.setElevation(dp(6));

        final TextView label = new TextView(context);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        label.setTextColor(ClientTheme.isEnabled()
                ? ClientTheme.color(ClientTheme.ON_SURFACE)
                : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        bar.addView(label, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        labelView = label;

        final ImageView play = new ImageView(context);
        play.setScaleType(ImageView.ScaleType.CENTER);
        play.setColorFilter(new PorterDuffColorFilter(ClientTheme.isEnabled()
                ? ClientTheme.color(ClientTheme.PRIMARY)
                : Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon), PorterDuff.Mode.SRC_IN));
        play.setOnClickListener(v -> {
            final MessageObject object = MediaController.getInstance().getPlayingMessageObject();
            if (object == null) {
                return;
            }
            if (MediaController.getInstance().isPlayingMessage(object)) {
                MediaController.getInstance().pauseMessage(object);
            } else {
                MediaController.getInstance().playMessage(object, 0);
            }
        });
        bar.addView(play, LayoutHelper.createLinear(40, 40, 8, 0, 0, 0));
        playView = play;

        bar.setOnClickListener(v -> fragment.presentFragment(
                new app.nimarkogram.messenger.margy.MargyMusicActivity()));
        return bar;
    }

    private static void bind(MessageObject playing) {
        final TextView label = labelView;
        final ImageView play = playView;
        if (label != null) {
            label.setText((playing.getMusicAuthor() == null ? "" : playing.getMusicAuthor() + " \u2014 ")
                    + (playing.getMusicTitle() == null ? "" : playing.getMusicTitle()));
        }
        if (play != null) {
            play.setImageResource(MediaController.getInstance().isPlayingMessage(playing)
                    ? R.drawable.ic_action_pause : R.drawable.ic_action_play);
        }
    }
}