package app.nimarkogram.messenger.margy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
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
        if (!placed) {
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
        return Math.max(dp(8), getHeight() - size() - bottomInset());
    }

    private int maxLeft() {
        return Math.max(0, getWidth() - size() - dp(6));
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
            left = Math.round(savedX * maxLeft());
            top = Math.round(savedY * maxTop());
        }
        applyPosition(left, top);
        if (initial) {
            Log.i(TAG, "place left=" + left + " top=" + top
                    + " w=" + getWidth() + " h=" + getHeight() + " maxTop=" + maxTop());
        }
    }

    private void applyPosition(int left, int top) {
        left = Math.max(0, Math.min(maxLeft(), left));
        top = Math.max(0, Math.min(maxTop(), top));
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
        if (maxLeft() > 0 && maxTop() > 0) {
            MargyConfig.setPetPosition(
                    (float) left / maxLeft(), (float) top / maxTop());
        }
    }

    /** Обновить стадию, если серия выросла за время открытого чата. */
    public void refreshStage() {
        final int stage = MargyStreaks.stage(MargyStreaks.state(account, dialogId).streak);
        pet.setStage(stage < 0 ? MargyPetView.STAGE_EGG : stage);
    }
}