package app.nimarkogram.messenger.linkidrop;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.lang.ref.WeakReference;

/**
 * Application-wide transfer indicator: a floating pill that shows which file
 * moves and how much of it already arrived, visible on every screen while the
 * Linki Drop transfer is running.
 */
public final class LinkiDropProgress implements Application.ActivityLifecycleCallbacks {

    private static LinkiDropProgress instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WeakReference<Activity> activity = new WeakReference<>(null);
    private boolean registered;

    private FrameLayout hostParent;
    private View pill;
    private TextView titleView;
    private TextView sizeView;
    private TextView percentView;
    private ProgressLine progressLine;

    private boolean active;
    private boolean success;
    private int hideToken;
    private String pendingName;
    private long pendingSize = -1;
    private long lastDone = -1;
    private long lastTotal = -1;

    private LinkiDropProgress() {
    }

    public static LinkiDropProgress get() {
        if (instance == null) {
            instance = new LinkiDropProgress();
        }
        return instance;
    }

    private void ensureRegistered() {
        if (registered) {
            return;
        }
        final Context context = ApplicationLoader.applicationContext;
        if (context instanceof Application) {
            ((Application) context).registerActivityLifecycleCallbacks(this);
            registered = true;
        }
    }

    /** Starts (or restarts) the indicator for one outgoing or incoming file. */
    public void begin(final String name, final long size) {
        ensureRegistered();
        pendingName = name;
        pendingSize = size;
        success = false;
        active = true;
        ++hideToken;
        handler.post(() -> {
            if (!active) {
                return;
            }
            final View view = ensurePill();
            if (view == null) {
                return;
            }
            if (titleView != null) {
                titleView.setText(name == null ? "" : name);
            }
            if (sizeView != null) {
                sizeView.setText(AndroidUtilities.formatFileSize(Math.max(0, size)));
            }
            if (percentView != null) {
                percentView.setText("");
            }
            if (progressLine != null) {
                progressLine.setProgress(0f);
            }
            view.setVisibility(View.VISIBLE);
            view.setAlpha(0f);
            view.animate().alpha(1f).setDuration(160).start();
        });
    }

    public void update(final long done, final long total) {
        lastDone = done;
        lastTotal = total;
        handler.post(() -> {
            if (!active || progressLine == null) {
                return;
            }
            final float progress = total > 0 ? Math.min(1f, (float) done / total) : 0f;
            progressLine.setTargetProgress(progress);
            if (sizeView != null) {
                sizeView.setText(AndroidUtilities.formatFileSize(Math.max(0, done))
                        + " / " + AndroidUtilities.formatFileSize(Math.max(0, total)));
            }
            if (percentView != null) {
                percentView.setText(Math.round(progress * 100f) + "%");
            }
        });
    }

    public void finish(boolean ok, String label) {
        active = false;
        success = ok;
        final int token = ++hideToken;
        handler.post(() -> {
            if (progressLine != null) {
                progressLine.setTargetProgress(ok ? 1f : progressLine.getTargetProgress());
            }
            if (ok && titleView != null) {
                titleView.setText(label == null ? "" : label);
            }
            if (percentView != null && ok) {
                percentView.setText("");
            }
            handler.postDelayed(() -> {
                if (token != hideToken) {
                    return;
                }
                final View view = pill;
                if (view != null) {
                    view.animate().alpha(0f).setDuration(220).withEndAction(() -> {
                        if (token == hideToken) {
                            detach();
                        }
                    }).start();
                }
            }, ok ? 1400 : 400);
        });
    }

    public void cancel() {
        active = false;
        ++hideToken;
        handler.post(this::detach);
    }

    //region pill

    private View ensurePill() {
        final Activity current = activity.get();
        if (current == null || current.isFinishing()) {
            return null;
        }
        final ViewGroup decor = (ViewGroup) current.getWindow().getDecorView();
        final FrameLayout parent = hostParent != null && hostParent.getParent() == decor
                ? hostParent
                : findOverlayParent(decor);
        if (parent == null) {
            return null;
        }
        hostParent = parent;
        if (pill != null && pill.getParent() == parent) {
            return pill;
        }
        if (pill != null && pill.getParent() instanceof ViewGroup) {
            ((ViewGroup) pill.getParent()).removeView(pill);
        }
        final Context context = current;
        final LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10),
                AndroidUtilities.dp(14), AndroidUtilities.dp(10));

        final int surface = Theme.getColor(Theme.key_windowBackgroundWhite);
        final boolean dark = androidx.core.graphics.ColorUtils.calculateLuminance(surface) < 0.5;
        card.setBackground(new PillBackground(dark));
        card.setElevation(AndroidUtilities.dp(10));

        final ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.msg_download_solar);
        icon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        card.addView(icon, LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        card.addView(column, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        titleView = new TextView(context);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        column.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        sizeView = new TextView(context);
        sizeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        sizeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        column.addView(sizeView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

        percentView = new TextView(context);
        percentView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        percentView.setTypeface(Typeface.DEFAULT_BOLD);
        percentView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        card.addView(percentView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

        progressLine = new ProgressLine(context);
        final FrameLayout wrapper = new FrameLayout(context);
        wrapper.addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        wrapper.addView(progressLine, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3,
                Gravity.BOTTOM, 12, 0, 12, 6));

        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                AndroidUtilities.dp(300), FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = AndroidUtilities.dp(78);
        parent.addView(wrapper, params);
        pill = wrapper;
        pill.setAlpha(0f);
        return pill;
    }

    private FrameLayout findOverlayParent(ViewGroup decor) {
        for (int a = 0; a < decor.getChildCount(); a++) {
            final View child = decor.getChildAt(a);
            if (child instanceof FrameLayout) {
                return (FrameLayout) child;
            }
        }
        return decor instanceof FrameLayout ? (FrameLayout) decor : null;
    }

    private void detach() {
        final View view = pill;
        pill = null;
        titleView = null;
        sizeView = null;
        percentView = null;
        progressLine = null;
        hostParent = null;
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private void attachToCurrentActivity() {
        if (!active && pill == null) {
            return;
        }
        handler.post(this::ensurePill);
    }

    //endregion

    //region lifecycle

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
        final Activity previous = this.activity.get();
        this.activity = new WeakReference<>(activity);
        if (previous != activity) {
            // The pill belongs to the previous window; re-attach to the current one.
            detach();
            if (active) {
                attachToCurrentActivity();
            }
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (this.activity.get() == activity) {
            detach();
            this.activity.clear();
        }
    }

    //endregion

    /** Rounded card with a soft outline, matching the in-app surfaces. */
    private static final class PillBackground extends android.graphics.drawable.Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        PillBackground(boolean dark) {
            fill.setColor(dark ? 0xF21C1C1E : 0xF7FFFFFF);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(AndroidUtilities.dp(0.7f));
            stroke.setColor(dark ? 0x33FFFFFF : 0x1A000000);
        }

        @Override
        public void draw(Canvas canvas) {
            rect.set(getBounds());
            rect.inset(AndroidUtilities.dp(0.5f), AndroidUtilities.dp(0.5f));
            final float radius = rect.height() / 2f;
            canvas.drawRoundRect(rect, radius, radius, fill);
            canvas.drawRoundRect(rect, radius, radius, stroke);
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            stroke.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    /** Thin accent line with a smooth animated fill. */
    private static final class ProgressLine extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float target;
        private float shown;
        private ValueAnimator animator;

        ProgressLine(Context context) {
            super(context);
            track.setColor(0x22000000);
            fill.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        }

        void setTargetProgress(float value) {
            target = Math.max(0f, Math.min(1f, value));
            if (animator != null) {
                animator.cancel();
            }
            animator = ValueAnimator.ofFloat(shown, target);
            animator.setDuration(220);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                shown = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        float getTargetProgress() {
            return target;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float h = Math.min(getHeight(), AndroidUtilities.dp(3));
            final float top = getHeight() - h;
            rect.set(0, top, getWidth(), getHeight());
            final float r = h / 2f;
            track.setColor(Color.argb(48, 128, 128, 128));
            canvas.drawRoundRect(rect, r, r, track);
            if (shown > 0) {
                rect.right = Math.max(h, getWidth() * shown);
                canvas.drawRoundRect(rect, r, r, fill);
            }
        }
    }
}