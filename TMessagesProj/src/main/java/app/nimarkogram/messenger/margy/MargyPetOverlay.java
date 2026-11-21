package app.nimarkogram.messenger.margy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;
import android.widget.FrameLayout;

import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Питомец прямо в чате: его можно тащить в любую точку, а с включённой
 * гравитацией после отпускания он падает вниз и остаётся там. Позиция
 * запоминается долями экрана, поэтому переживает поворот и перезапуск.
 */
@SuppressLint("ViewConstructor")
public class MargyPetOverlay extends FrameLayout {

    private static final int SIZE_DP = 68;

    private final ChatActivity chat;
    private final MargyPetView pet;
    private final int account;
    private final long dialogId;

    private boolean placed;
    private boolean dragging;
    private boolean moved;
    private float downRawX;
    private float downRawY;
    private int downLeft;
    private int downTop;

    /** Полёт от встряхивания: питомец скачет по экрану по законам физики. */
    private boolean flying;
    private float flightVX;
    private float flightVY;
    private float flightX;
    private float flightY;
    private long flightStart;
    private long flightLast;
    private long lastShakeAt;

    private SensorManager sensors;
    private boolean shakeRegistered;

    private MargyPetOverlay(Context context, ChatActivity chat, int account, long dialogId) {
        super(context);
        this.chat = chat;
        this.account = account;
        this.dialogId = dialogId;
        setClipChildren(false);
        setClipToPadding(false);
        setWillNotDraw(true);

        final MargyStreaks.State state = MargyStreaks.state(account, dialogId);
        final int stage = MargyStreaks.stage(state.streak);
        pet = new MargyPetView(context, stage < 0 ? MargyPetView.STAGE_EGG : stage);
        pet.setAnimating(true);
        pet.setClickable(true);
        pet.setOnTouchListener(this::onPetTouch);
        addView(pet, LayoutHelper.createFrame(SIZE_DP, SIZE_DP, Gravity.TOP | Gravity.LEFT));
    }

    /** Добавить питомца в готовый экран чата. Возвращает null, если он выключен. */
    public static MargyPetOverlay attach(ChatActivity chat, ViewGroup parent, int account, long dialogId) {
        if (chat == null || parent == null || !MargyConfig.pet() || !MargyConfig.petInChat()) {
            Log.i(TAG, "skipped pet=" + MargyConfig.pet() + " inChat=" + MargyConfig.petInChat()
                    + " parent=" + (parent != null));
            return null;
        }
        final MargyPetOverlay overlay = new MargyPetOverlay(parent.getContext(), chat, account, dialogId);
        parent.addView(overlay, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        // Порядок детей в contentView меняется после нас (undoView, плавающие
        // подсказки), поэтому поднимаем слой явно, иначе питомец рисуется под
        // ними и выглядит отсутствующим.
        parent.bringChildToFront(overlay);
        overlay.post(overlay::firstPosition);
        Log.i(TAG, "attach stage=" + MargyPetView.stageName(
                MargyStreaks.stage(MargyStreaks.state(account, dialogId).streak))
                + " parent=" + parent.getClass().getSimpleName()
                + " children=" + parent.getChildCount()
                + " index=" + parent.indexOfChild(overlay));
        return overlay;
    }

    private static final String TAG = "MargyPet";

    private int positionTries;

    private void firstPosition() {
        if (getWidth() <= 0 || getHeight() <= 0) {
            // Экран ещё не измерен: повторяем, иначе позиция считается от нуля.
            if (positionTries++ < 8) {
                post(this::firstPosition);
            }
            Log.i(TAG, "post retry size=" + getWidth() + "x" + getHeight()
                    + " try=" + positionTries);
            return;
        }
        if (!placed || !visibleBounds()) {
            // Первый проход после layout: здесь уже известна видимая часть
            // слоя, поэтому позиция считается по экрану, а не по getHeight().
            placePet(true);
            placed = true;
        }
        final int[] loc = new int[2];
        pet.getLocationOnScreen(loc);
        Log.i(TAG, "post size=" + getWidth() + "x" + getHeight()
                + " pet=" + pet.getWidth() + "x" + pet.getHeight()
                + " petVis=" + pet.getVisibility() + " petAlpha=" + pet.getAlpha()
                + " petShown=" + pet.isShown() + " overlayShown=" + isShown()
                + " petScreen=" + loc[0] + "," + loc[1]
                + " maxTop=" + maxTop() + " maxLeft=" + maxLeft());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Прозрачный слой: касания мимо питомца уходят списку сообщений.
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!placed || changed) {
            placePet(!placed);
            placed = true;
        }
    }

    private int size() {
        return dp(SIZE_DP);
    }

    private final Rect visible = new Rect();

    /**
     * Видимая часть слоя в его собственных координатах. contentView в чате
     * сдвинут вниз (status bar + action bar) и выше экрана, поэтому
     * getHeight() — это не экран: отсчёт от него уводил питомца за нижнюю
     * границу дисплея, и он оказывался за кадром.
     */
    private boolean visibleBounds() {
        final boolean global = getGlobalVisibleRect(visible);
        final View root = getRootView();
        if (root != null && root != this && root.getWidth() > 0 && root.getHeight() > 0) {
            // The window clamps the layer: contentView sits below the action bar
            // and is taller than the display, and its clipping is off.
            final int[] loc = new int[2];
            getLocationInWindow(loc);
            final int left = Math.max(0, loc[0]);
            final int top = Math.max(0, loc[1]);
            final int right = Math.min(root.getWidth(), loc[0] + getWidth());
            final int bottom = Math.min(root.getHeight(), loc[1] + getHeight());
            if (right <= left || bottom <= top) {
                return false;
            }
            final Rect clip = new Rect(left - loc[0], top - loc[1], right - loc[0], bottom - loc[1]);
            if (global) {
                return visible.intersect(clip) && !visible.isEmpty();
            }
            visible.set(clip);
            return true;
        }
        return global && !visible.isEmpty();
    }

    private int bottomInset() {
        final ChatActivityEnterView enterView = chat.getChatActivityEnterView();
        if (enterView != null && enterView.getHeight() > 0) {
            final int h = enterView.getHeight();
            // Защита от ещё не измеренной панели: она не может занимать треть экрана.
            if (h < getHeight() / 3) {
                return h + dp(6);
            }
        }
        return dp(64);
    }

    private int maxTop() {
        if (visibleBounds()) {
            return Math.max(visible.top, visible.bottom - size() - bottomInset());
        }
        return Math.max(dp(8), getHeight() - size() - bottomInset());
    }

    private int maxLeft() {
        if (visibleBounds()) {
            return Math.max(visible.left, visible.right - size() - dp(6));
        }
        return Math.max(0, getWidth() - size() - dp(6));
    }

    private int minTop() {
        return visibleBounds() ? visible.top : 0;
    }

    private int minLeft() {
        return visibleBounds() ? visible.left : 0;
    }

    private void placePet(boolean initial) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        final float savedX = MargyConfig.petX();
        final float savedY = MargyConfig.petY();
        int left;
        int top;
        if (Float.isNaN(savedX) || Float.isNaN(savedY) || (initial && MargyConfig.petGravity())) {
            left = maxLeft();
            top = maxTop();
        } else {
            left = minLeft() + Math.round(savedX * Math.max(0, maxLeft() - minLeft()));
            top = minTop() + Math.round(savedY * Math.max(0, maxTop() - minTop()));
        }
        applyPosition(left, top);
        if (initial) {
            Log.i(TAG, "place left=" + left + " top=" + top
                    + " visible=" + visibleBounds() + " " + visible.left + "," + visible.top
                    + "-" + visible.right + "," + visible.bottom
                    + " maxTop=" + maxTop() + " maxLeft=" + maxLeft()
                    + " bottomInset=" + bottomInset());
        }
    }

    private void applyPosition(int left, int top) {
        left = Math.max(minLeft(), Math.min(maxLeft(), left));
        top = Math.max(minTop(), Math.min(maxTop(), top));
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pet.getLayoutParams();
        if (lp.leftMargin == left && lp.topMargin == top) {
            return;
        }
        lp.leftMargin = left;
        lp.topMargin = top;
        pet.setLayoutParams(lp);
    }

    private boolean onPetTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (flying) {
                    stopFlight();
                }
                if (pet.getTranslationX() != 0f || pet.getTranslationY() != 0f) {
                    final FrameLayout.LayoutParams current =
                            (FrameLayout.LayoutParams) pet.getLayoutParams();
                    if (current != null) {
                        applyPosition(Math.round(current.leftMargin + pet.getTranslationX()),
                                Math.round(current.topMargin + pet.getTranslationY()));
                    }
                    pet.setTranslationX(0f);
                    pet.setTranslationY(0f);
                    pet.setRotation(0f);
                }
                dragging = true;
                moved = false;
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) pet.getLayoutParams();
                downLeft = params.leftMargin;
                downTop = params.topMargin;
                pet.animate().cancel();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) {
                    return true;
                }
                final float dx = event.getRawX() - downRawX;
                final float dy = event.getRawY() - downRawY;
                if (!moved && Math.hypot(dx, dy) > dp(6)) {
                    moved = true;
                }
                if (moved) {
                    applyPosition(downLeft + Math.round(dx), downTop + Math.round(dy));
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!dragging) {
                    return true;
                }
                dragging = false;
                final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pet.getLayoutParams();
                final int left = lp.leftMargin;
                final int top = lp.topMargin;
                if (!moved && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    savePosition(left, top);
                    MargyPetSheet.show(chat, account, dialogId);
                } else if (MargyConfig.petGravity() && top != maxTop()) {
                    final int target = maxTop();
                    pet.animate().cancel();
                    pet.setTranslationY(0);
                    pet.animate().translationY(target - top).setDuration(260)
                            .setInterpolator(new org.telegram.ui.Components.CubicBezierInterpolator(
                                    0.22f, 1f, 0.36f, 1f))
                            .withEndAction(() -> {
                                pet.setTranslationY(0);
                                applyPosition(left, target);
                                savePosition(left, target);
                            }).start();
                } else {
                    savePosition(left, top);
                }
                return true;
            }
        }
        return true;
    }

    private void savePosition(int left, int top) {
        final int rangeX = Math.max(0, maxLeft() - minLeft());
        final int rangeY = Math.max(0, maxTop() - minTop());
        if (rangeX > 0 && rangeY > 0) {
            MargyConfig.setPetPosition(
                    (float) (left - minLeft()) / rangeX,
                    (float) (top - minTop()) / rangeY);
        }
    }

    /** Обновить стадию, если серия выросла за время открытого чата. */
    public void refreshStage() {
        final int stage = MargyStreaks.stage(MargyStreaks.state(account, dialogId).streak);
        pet.setStage(stage < 0 ? MargyPetView.STAGE_EGG : stage);
        pet.reloadPhoto();
    }

    // --- Встряхивание и полёт -------------------------------------------------

    private static final float FLIGHT_GRAVITY = 2100f;   // px/s^2
    private static final long FLIGHT_MAX_MS = 3400L;
    private static final float SHAKE_G = 2.15f;

    private final SensorEventListener shakeListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values == null || event.values.length < 3) {
                return;
            }
            if (!MargyConfig.petShake()) {
                unregisterShake();
                return;
            }
            final float x = event.values[0];
            final float y = event.values[1];
            final float z = event.values[2];
            final float g = (float) Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;
            final long now = SystemClock.uptimeMillis();
            if (g >= SHAKE_G && now - lastShakeAt > 1100) {
                lastShakeAt = now;
                flyAround();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private void registerShake() {
        if (shakeRegistered || !MargyConfig.petShake()) {
            return;
        }
        try {
            final SensorManager manager =
                    (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
            final Sensor accelerometer = manager == null
                    ? null : manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer == null) {
                return;
            }
            if (manager.registerListener(shakeListener, accelerometer, SensorManager.SENSOR_DELAY_GAME)) {
                sensors = manager;
                shakeRegistered = true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "shake sensor unavailable", t);
        }
    }

    private void unregisterShake() {
        if (!shakeRegistered || sensors == null) {
            return;
        }
        try {
            sensors.unregisterListener(shakeListener);
        } catch (Throwable ignore) {
        }
        shakeRegistered = false;
        sensors = null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        registerShake();
        // Пользователь мог включить настройку уже открытым чатом.
        pet.reloadPhoto();
    }

    @Override
    protected void onDetachedFromWindow() {
        unregisterShake();
        stopFlight();
        super.onDetachedFromWindow();
    }

    /** Бросок в случайную сторону: дальше питомец летит и отскакивает от краёв. */
    private void flyAround() {
        if (dragging || flying || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        if (!visibleBounds()) {
            return;
        }
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pet.getLayoutParams();
        if (lp == null) {
            return;
        }
        flying = true;
        flightStart = flightLast = SystemClock.uptimeMillis();
        flightX = lp.leftMargin;
        flightY = lp.topMargin;
        final float speed = Math.max(dp(900), getWidth() * 0.78f);
        final double angle = Math.random() * Math.PI * 2;
        flightVX = (float) Math.cos(angle) * speed;
        flightVY = (float) Math.sin(angle) * speed - speed * 0.45f;
        pet.animate().cancel();
        pet.setTranslationY(0);
        Log.i(TAG, "fly vx=" + Math.round(flightVX) + " vy=" + Math.round(flightVY));
        postOnAnimation(flightStep);
    }

    /** Позиция во время полёта идёт через translation: так нет relayout каждый кадр. */
    private void commitFlight() {
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pet.getLayoutParams();
        if (lp != null && flying) {
            applyPosition(Math.round(flightX), Math.round(flightY));
        }
        pet.setTranslationX(0f);
        pet.setTranslationY(0f);
        pet.setRotation(0f);
    }

    private void stopFlight() {
        if (!flying) {
            return;
        }
        flying = false;
        removeCallbacks(flightStep);
        commitFlight();
    }

    private final Runnable flightStep = new Runnable() {
        @Override
        public void run() {
            if (!flying) {
                return;
            }
            if (!isAttachedToWindow()) {
                flying = false;
                return;
            }
            final long now = SystemClock.uptimeMillis();
            float dt = (now - flightLast) / 1000f;
            flightLast = now;
            if (dt <= 0f || dt > 0.05f) {
                dt = 0.016f;
            }

            flightVY += FLIGHT_GRAVITY * dt;
            flightX += flightVX * dt;
            flightY += flightVY * dt;
            final int minL = minLeft(), maxL = maxLeft(), minT = minTop(), maxT = maxTop();
            final float bounce = 0.62f;
            if (flightX < minL) {
                flightX = minL + (minL - flightX) * bounce;
                flightVX = Math.abs(flightVX) * bounce;
            } else if (flightX > maxL) {
                flightX = maxL - (flightX - maxL) * bounce;
                flightVX = -Math.abs(flightVX) * bounce;
            }
            if (flightY < minT) {
                flightY = minT + (minT - flightY) * bounce;
                flightVY = Math.abs(flightVY) * bounce;
            } else if (flightY > maxT) {
                flightY = maxT - (flightY - maxT) * bounce;
                flightVY = -Math.abs(flightVY) * bounce;
            }

            final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pet.getLayoutParams();
            if (lp == null) {
                flying = false;
                return;
            }
            pet.setTranslationX(flightX - lp.leftMargin);
            pet.setTranslationY(flightY - lp.topMargin);
            pet.setRotation(Math.max(-26f, Math.min(26f, flightVX / 70f)));

            final boolean spent = now - flightStart > FLIGHT_MAX_MS;
            final boolean settled = Math.abs(flightVX) + Math.abs(flightVY) < dp(90);
            if (!spent && !settled) {
                postOnAnimationDelayed(this, 16);
                return;
            }

            flying = false;
            final int left = Math.round(flightX);
            final int top = Math.round(flightY);
            applyPosition(left, top);
            pet.setTranslationX(0f);
            pet.setTranslationY(0f);
            pet.setRotation(0f);
            if (MargyConfig.petGravity() && top != maxTop()) {
                final int target = maxTop();
                pet.animate().translationY(target - top).setDuration(320)
                        .setInterpolator(new org.telegram.ui.Components.CubicBezierInterpolator(
                                0.22f, 1f, 0.36f, 1f))
                        .withEndAction(() -> {
                            pet.setTranslationY(0);
                            applyPosition(left, target);
                            savePosition(left, target);
                        }).start();
            } else {
                savePosition(left, top);
            }
        }
    };
}
