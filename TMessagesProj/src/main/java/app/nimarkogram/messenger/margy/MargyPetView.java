package app.nimarkogram.messenger.margy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import org.telegram.ui.ActionBar.Theme;

import app.nimarkogram.messenger.appearance.ClientTheme;

/**
 * Питомец серии: яйцо, вылупление, малыш, взрослый. Рисуется кодом, без
 * шейдеров и мешей — маленький живой значок, который покачивается и моргает.
 */
public class MargyPetView extends View {

    public static final int STAGE_EGG = 0;
    public static final int STAGE_HATCHING = 1;
    public static final int STAGE_BABY = 2;
    public static final int STAGE_ADULT = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eye = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cheek = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final RectF rect = new RectF();

    private int stage = STAGE_EGG;
    private float phase;
    private boolean animating;

    public MargyPetView(Context context, int stage) {
        super(context);
        this.stage = Math.max(STAGE_EGG, Math.min(STAGE_ADULT, stage));
        eye.setColor(Color.WHITE);
        cheek.setColor(0x33FF5B7F);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setStage(int stage) {
        this.stage = Math.max(STAGE_EGG, Math.min(STAGE_ADULT, stage));
        invalidate();
    }

    public void setAnimating(boolean animating) {
        this.animating = animating;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float w = getWidth();
        final float h = getHeight();
        final float cx = w / 2f;
        final float cy = h / 2f;
        final float breathe = 1f + 0.03f * (float) Math.sin(phase);
        final int accent = ClientTheme.isEnabled()
                ? ClientTheme.color(ClientTheme.PRIMARY)
                : Theme.getColor(Theme.key_chats_actionBackground);

        paint.setStyle(Paint.Style.FILL);
        paint.setShadowLayer(dp(8), 0, dp(3), 0x33000000);

        if (stage == STAGE_EGG || stage == STAGE_HATCHING) {
            // Яйцо: овал с крапинками и трещиной на стадии вылупления.
            rect.set(cx - w * 0.24f * breathe, cy - h * 0.30f,
                    cx + w * 0.24f * breathe, cy + h * 0.30f);
            paint.setColor(blend(accent, Color.WHITE, 0.55f));
            canvas.drawOval(rect, paint);
            paint.setColor(blend(accent, Color.WHITE, 0.30f));
            for (int i = 0; i < 5; i++) {
                final float px = cx + (float) Math.cos(i * 2.1) * w * 0.12f;
                final float py = cy + (float) Math.sin(i * 1.7) * h * 0.16f;
                canvas.drawCircle(px, py, dp(2.2f), paint);
            }
            if (stage == STAGE_HATCHING) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.6f));
                paint.setShadowLayer(0, 0, 0, 0);
                paint.setColor(blend(accent, Color.BLACK, 0.45f));
                shape.rewind();
                shape.moveTo(cx - w * 0.10f, cy - h * 0.18f);
                shape.lineTo(cx + w * 0.02f, cy - h * 0.04f);
                shape.lineTo(cx - w * 0.06f, cy + h * 0.06f);
                shape.lineTo(cx + w * 0.08f, cy + h * 0.20f);
                canvas.drawPath(shape, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            return;
        }

        // Тело: капля, сверху ушки/рожки в зависимости от стадии.
        final float body = w * (stage == STAGE_ADULT ? 0.34f : 0.30f) * breathe;
        paint.setColor(accent);
        rect.set(cx - body, cy - body * 0.85f, cx + body, cy + body);
        canvas.drawRoundRect(rect, body, body, paint);

        paint.setColor(blend(accent, Color.WHITE, 0.10f));
        if (stage == STAGE_ADULT) {
            shape.rewind();
            shape.moveTo(cx - body * 0.75f, cy - body * 0.55f);
            shape.lineTo(cx - body * 0.55f, cy - body * 1.45f);
            shape.lineTo(cx - body * 0.15f, cy - body * 0.75f);
            shape.close();
            shape.moveTo(cx + body * 0.15f, cy - body * 0.75f);
            shape.lineTo(cx + body * 0.55f, cy - body * 1.45f);
            shape.lineTo(cx + body * 0.75f, cy - body * 0.55f);
            shape.close();
            canvas.drawPath(shape, paint);
        } else {
            canvas.drawCircle(cx - body * 0.6f, cy - body * 0.75f, body * 0.26f, paint);
            canvas.drawCircle(cx + body * 0.6f, cy - body * 0.75f, body * 0.26f, paint);
        }

        // Глаза: моргают по синусу.
        final float blink = 0.25f + 0.75f * Math.abs((float) Math.cos(phase * 0.7f));
        final float eyeR = body * 0.20f;
        eye.setColor(Color.WHITE);
        canvas.drawCircle(cx - body * 0.38f, cy - body * 0.12f, eyeR, eye);
        canvas.drawCircle(cx + body * 0.38f, cy - body * 0.12f, eyeR, eye);
        paint.setColor(Theme.isCurrentThemeDark() ? 0xFF15161A : 0xFF1B1C20);
        canvas.drawCircle(cx - body * 0.38f, cy - body * 0.12f, eyeR * 0.55f * blink, paint);
        canvas.drawCircle(cx + body * 0.38f, cy - body * 0.12f, eyeR * 0.55f * blink, paint);

        // Румянец и улыбка.
        canvas.drawCircle(cx - body * 0.62f, cy + body * 0.20f, body * 0.16f, cheek);
        canvas.drawCircle(cx + body * 0.62f, cy + body * 0.20f, body * 0.16f, cheek);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.6f));
        paint.setColor(Theme.isCurrentThemeDark() ? 0xFF15161A : 0xFF1B1C20);
        rect.set(cx - body * 0.16f, cy + body * 0.10f, cx + body * 0.16f, cy + body * 0.34f);
        canvas.drawArc(rect, 20, 140, false, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animating) {
            postOnAnimation(tick);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            phase += 0.045f;
            if (!isAttachedToWindow()) return;
            invalidate();
            postOnAnimationDelayed(this, 48);
        }
    };

    private static int blend(int from, int to, float fraction) {
        return Color.argb(
                Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * fraction),
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * fraction),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * fraction),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * fraction));
    }

    /** Человеческая подпись стадии. */
    public static String stageName(int stage) {
        switch (stage) {
            case STAGE_EGG: return "egg";
            case STAGE_HATCHING: return "hatching";
            case STAGE_BABY: return "baby";
            case STAGE_ADULT: return "adult";
            default: return "";
        }
    }
}