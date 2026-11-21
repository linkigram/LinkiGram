package app.nimarkogram.messenger.appearance;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import app.nimarkogram.messenger.NimarkoConfig;

/**
 * Снег поверх всего приложения: тонкий слой частиц поверх содержимого
 * каждой активности. Включается настройкой «Снег везде» в оформлении.
 */
public final class SnowOverlay implements Application.ActivityLifecycleCallbacks {

    private static SnowOverlay instance;

    private final ArrayList<Activity> activities = new ArrayList<>();
    private WeakReference<Activity> resumed = new WeakReference<>(null);
    private boolean registered;

    private SnowOverlay() {
    }

    public static SnowOverlay get() {
        if (instance == null) {
            instance = new SnowOverlay();
        }
        return instance;
    }

    /** Перечитывает настройку и сразу включает/выключает снег. */
    public static void apply() {
        get().applyInternal();
    }

    /** Ставится один раз при старте: подписка на активности. */
    public static void install() {
        final SnowOverlay overlay = get();
        overlay.ensureRegistered();
        overlay.applyInternal();
    }

    private void applyInternal() {
        if (!NimarkoConfig.drawSnowEverywhere
                || !LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND)
                || LiteMode.isPowerSaverApplied()) {
            detachAll();
            return;
        }
        ensureRegistered();
        final Activity activity = resumed.get();
        if (activity != null) {
            attach(activity);
        }
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

    private void attach(Activity activity) {
        final ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) {
            return;
        }
        for (int a = 0, n = content.getChildCount(); a < n; a++) {
            if (content.getChildAt(a) instanceof SnowView) {
                return;
            }
        }
        final SnowView view = new SnowView(activity);
        content.addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (!activities.contains(activity)) {
            activities.add(activity);
        }
    }

    private void detach(Activity activity) {
        final ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        for (int a = content.getChildCount() - 1; a >= 0; a--) {
            final View child = content.getChildAt(a);
            if (child instanceof SnowView) {
                content.removeViewAt(a);
            }
        }
        activities.remove(activity);
    }

    private void detachAll() {
        for (int a = activities.size() - 1; a >= 0; a--) {
            detach(activities.get(a));
        }
        activities.clear();
    }

    @Override
    public void onActivityResumed(Activity activity) {
        resumed = new WeakReference<>(activity);
        if (!NimarkoConfig.drawSnowEverywhere) {
            return;
        }
        applyInternal();
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (resumed.get() == activity) {
            resumed = new WeakReference<>(null);
        }
        detach(activity);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        detach(activity);
    }

    private static final class SnowView extends View {

        private static final long FRAME_MAX = 48;
        private static final long FRAME_DELAY = 33;
        private static final int MAX_FLAKES = 140;

        private static final class Flake {
            float x;
            float y;
            float radius;
            float speed;
            float drift;
            float driftPhase;
            float driftSpeed;
            float alpha;
        }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArrayList<Flake> flakes = new ArrayList<>();
        private long lastFrame;
        private boolean attached;

        SnowView(Context context) {
            super(context);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            paint.setStyle(Paint.Style.FILL);
        }

        private int flakeCount() {
            final float density = Math.max(1f, AndroidUtilities.density);
            final int byArea = (int) (getWidth() * getHeight() / (26000f * density));
            return Math.max(26, Math.min(MAX_FLAKES, byArea));
        }

        private void reset(Flake flake, boolean anywhere) {
            final float density = Math.max(1f, AndroidUtilities.density);
            flake.radius = (0.9f + Utilities.random.nextFloat() * 2.4f) * density;
            flake.x = Utilities.random.nextFloat() * Math.max(1, getWidth());
            flake.y = anywhere
                    ? Utilities.random.nextFloat() * Math.max(1, getHeight())
                    : -flake.radius - Utilities.random.nextFloat() * getHeight() * 0.25f;
            flake.speed = (16f + Utilities.random.nextFloat() * 40f) * density;
            flake.drift = (4f + Utilities.random.nextFloat() * 16f) * density;
            flake.driftPhase = Utilities.random.nextFloat() * 6.283f;
            flake.driftSpeed = 0.5f + Utilities.random.nextFloat() * 1.1f;
            flake.alpha = 0.35f + Utilities.random.nextFloat() * 0.5f;
        }

        private void ensureFlakes() {
            final int count = flakeCount();
            while (flakes.size() < count) {
                final Flake flake = new Flake();
                reset(flake, true);
                flakes.add(flake);
            }
            while (flakes.size() > count) {
                flakes.remove(flakes.size() - 1);
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            ensureFlakes();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            lastFrame = SystemClock.uptimeMillis();
            ensureFlakes();
            postInvalidate();
        }

        @Override
        protected void onDetachedFromWindow() {
            attached = false;
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!attached || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            final long now = SystemClock.uptimeMillis();
            long delta = lastFrame == 0 ? 16 : now - lastFrame;
            lastFrame = now;
            if (delta <= 0) {
                delta = 16;
            } else if (delta > FRAME_MAX) {
                delta = FRAME_MAX;
            }
            final float dt = delta / 1000f;

            final boolean dark = Theme.isCurrentThemeDark();
            final int baseColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
            paint.setColor(dark ? 0xFFFFFFFF : baseColor);

            ensureFlakes();
            final float width = getWidth();
            final float height = getHeight();
            for (int a = 0, n = flakes.size(); a < n; a++) {
                final Flake flake = flakes.get(a);
                flake.y += flake.speed * dt;
                flake.driftPhase += flake.driftSpeed * dt;
                flake.x += Math.sin(flake.driftPhase) * flake.drift * dt;
                if (flake.x < -flake.radius) {
                    flake.x = width + flake.radius;
                } else if (flake.x > width + flake.radius) {
                    flake.x = -flake.radius;
                }
                if (flake.y - flake.radius > height) {
                    reset(flake, false);
                }
                paint.setAlpha((int) (255 * flake.alpha * (dark ? 0.85f : 0.7f)));
                canvas.drawCircle(flake.x, flake.y, flake.radius, paint);
            }

            if (LiteMode.isPowerSaverApplied()) {
                attached = false;
                return;
            }
            // 30 кадров в секунду достаточно для снега и заметно дешевле
            // полноэкранной перерисовки каждый vsync.
            postInvalidateDelayed(FRAME_DELAY);
        }
    }
}