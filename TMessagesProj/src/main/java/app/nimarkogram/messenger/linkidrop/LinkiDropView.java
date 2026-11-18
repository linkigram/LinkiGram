package app.nimarkogram.messenger.linkidrop;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * Linki Drop visual: two phones, a file card hopping from one to the other,
 * and a progress arc while the bytes are actually moving.
 */
public class LinkiDropView extends View {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint phonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final ValueAnimator animator;
    private final AccelerateDecelerateInterpolator ease = new AccelerateDecelerateInterpolator();

    private int accentColor;
    private int surfaceColor;
    private int textColor;
    private int mutedColor;

    private float phase;
    private boolean success;
    private boolean transferring;
    private float progress = -1f;

    public LinkiDropView(Context context) {
        super(context);
        ringPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2400);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
    }

    public void setColors(int accent, int surface, int text, int muted) {
        accentColor = accent;
        surfaceColor = surface;
        textColor = text;
        mutedColor = muted;
        invalidate();
    }

    public void setTransferring(boolean value) {
        transferring = value;
        invalidate();
    }

    public void setProgress(float value) {
        progress = value;
        invalidate();
    }

    public void setSuccess(boolean value) {
        success = value;
        if (value) {
            transferring = false;
        }
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!animator.isStarted()) {
            animator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        final float cx = w / 2f;
        final float cy = h / 2f;
        final float phoneW = Math.min(w * 0.16f, h * 0.30f);
        final float phoneH = phoneW * 1.75f;
        final float gap = phoneW * 2.1f;

        final float leftX = cx - gap / 2f - phoneW / 2f;
        final float rightX = cx + gap / 2f + phoneW / 2f;

        // Pulsing rings behind the left phone while we wait.
        if (!transferring && !success) {
            for (int i = 0; i < 3; i++) {
                final float p = (phase + i / 3f) % 1f;
                ringPaint.setColor(accentColor);
                ringPaint.setAlpha((int) (120 * (1f - p)));
                ringPaint.setStrokeWidth(Math.max(1f, phoneW * 0.05f));
                canvas.drawCircle(leftX, cy, phoneW * (0.85f + p * 1.9f), ringPaint);
            }
        }

        drawPhone(canvas, leftX, cy, phoneW, phoneH, true);
        drawPhone(canvas, rightX, cy, phoneW, phoneH, success);

        // The beam and the travelling card.
        final float beamLeft = leftX + phoneW * 0.6f;
        final float beamRight = rightX - phoneW * 0.6f;
        beamPaint.setShader(new LinearGradient(beamLeft, cy, beamRight, cy,
                new int[]{accentColor, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        beamPaint.setStrokeWidth(Math.max(2f, phoneW * 0.10f));
        beamPaint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(beamLeft, cy, beamRight, cy, beamPaint);
        beamPaint.setShader(null);

        final float cardSize = phoneW * 0.62f;
        float cardX;
        float alpha = 1f;
        if (success) {
            cardX = rightX;
            alpha = 0f;
        } else if (transferring) {
            cardX = leftX + (rightX - leftX) * Math.min(1f, Math.max(0f, progress));
        } else {
            // Ease the card back and forth between the phones.
            final float t = (float) (0.5 - 0.5 * Math.cos(phase * Math.PI * 2));
            cardX = leftX + (rightX - leftX) * ease.getInterpolation(t);
        }
        if (alpha > 0f) {
            cardPaint.setColor(surfaceColor);
            cardPaint.setAlpha(255);
            rect.set(cardX - cardSize / 2f, cy - cardSize * 0.66f, cardX + cardSize / 2f, cy + cardSize * 0.66f);
            canvas.drawRoundRect(rect, cardSize * 0.18f, cardSize * 0.18f, cardPaint);

            cardPaint.setColor(accentColor);
            final float lineHeight = Math.max(1.5f, cardSize * 0.09f);
            for (int i = 0; i < 3; i++) {
                final float left = rect.left + cardSize * 0.18f;
                final float right = rect.right - cardSize * (i == 2 ? 0.42f : 0.18f);
                final float top = rect.top + cardSize * (0.28f + i * 0.22f);
                rect.set(left, top, right, top + lineHeight);
                canvas.drawRoundRect(rect, lineHeight, lineHeight, cardPaint);
            }
        }

        if (transferring && progress >= 0f) {
            final float radius = Math.min(w, h) * 0.36f;
            final RectF arc = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            arcPaint.setColor(mutedColor);
            arcPaint.setAlpha(70);
            arcPaint.setStrokeWidth(Math.max(2f, phoneW * 0.09f));
            canvas.drawArc(arc, 0, 360, false, arcPaint);
            arcPaint.setColor(accentColor);
            arcPaint.setAlpha(255);
            canvas.drawArc(arc, -90, 360 * Math.min(1f, Math.max(0f, progress)), false, arcPaint);
        }
    }

    private void drawPhone(Canvas canvas, float x, float y, float w, float h, boolean active) {
        rect.set(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f);
        phonePaint.setStyle(Paint.Style.FILL);
        phonePaint.setColor(surfaceColor);
        canvas.drawRoundRect(rect, w * 0.22f, w * 0.22f, phonePaint);
        phonePaint.setStyle(Paint.Style.STROKE);
        phonePaint.setStrokeWidth(Math.max(1.5f, w * 0.06f));
        phonePaint.setColor(active ? accentColor : mutedColor);
        phonePaint.setAlpha(active ? 255 : 150);
        canvas.drawRoundRect(rect, w * 0.22f, w * 0.22f, phonePaint);
        phonePaint.setAlpha(255);
        // Speaker slit.
        rect.set(x - w * 0.16f, y - h * 0.42f, x + w * 0.16f, y - h * 0.42f + Math.max(1.5f, h * 0.012f));
        phonePaint.setStyle(Paint.Style.FILL);
        phonePaint.setColor(textColor);
        phonePaint.setAlpha(active ? 180 : 90);
        canvas.drawRoundRect(rect, h * 0.01f, h * 0.01f, phonePaint);
        phonePaint.setAlpha(255);
    }
}