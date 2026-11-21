package app.nimarkogram.messenger.margy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
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
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint horn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mouth = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final RectF rect = new RectF();

    private int accent = 0xFF5F9B4A;
    private int stage = STAGE_EGG;
    private float phase;
    private boolean animating;

    public MargyPetView(Context context, int stage) {
        super(context);
        this.stage = Math.max(STAGE_EGG, Math.min(STAGE_ADULT, stage));
        eye.setColor(Color.WHITE);
        cheek.setColor(0x70FFB6C4);
        cheek.setMaskFilter(new android.graphics.BlurMaskFilter(dp(1.5f), android.graphics.BlurMaskFilter.Blur.NORMAL));
        mouth.setStyle(Paint.Style.STROKE);
        mouth.setStrokeCap(Paint.Cap.ROUND);
        specular.setColor(0xE6FFFFFF);
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
        if (w <= 0 || h <= 0) {
            return;
        }
        final float u = Math.min(w, h);
        final float cx = w / 2f;
        final float cy = h * 0.545f;
        final float breathe = 1f + 0.02f * (float) Math.sin(phase);
        accent = ClientTheme.isEnabled()
                ? ClientTheme.color(ClientTheme.PRIMARY)
                : Theme.getColor(Theme.key_chats_actionBackground);
        final int ink = Theme.isCurrentThemeDark() ? 0xFF14151A : 0xFF23242A;

        if (stage == STAGE_EGG || stage == STAGE_HATCHING) {
            drawEgg(canvas, u, cx, cy, breathe);
            return;
        }
        drawCreature(canvas, u, cx, cy, breathe, ink);
    }

    private void drawEgg(Canvas canvas, float u, float cx, float cy, float breathe) {
        final float ex = u * 0.215f * breathe;
        final float ey = u * 0.255f;

        // мягкая тень под яйцом
        shade.setColor(0x2E000000);
        shade.setMaskFilter(new android.graphics.BlurMaskFilter(u * 0.05f, android.graphics.BlurMaskFilter.Blur.NORMAL));
        rect.set(cx - ex * 0.9f, cy + ey * 0.78f, cx + ex * 0.9f, cy + ey * 1.12f);
        canvas.drawOval(rect, shade);
        shade.setMaskFilter(null);

        // объём яйца градиентом
        paint.setShader(new LinearGradient(cx - ex, cy - ey, cx + ex, cy + ey,
                new int[]{blend(accent, Color.WHITE, 0.62f), blend(accent, Color.WHITE, 0.30f), blend(accent, Color.BLACK, 0.18f)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        rect.set(cx - ex, cy - ey, cx + ex, cy + ey);
        canvas.drawOval(rect, paint);
        paint.setShader(null);

        // крапинки
        paint.setColor(0x55FFFFFF);
        final float[] spots = {-0.35f, -0.45f, 0.075f, 0.30f, -0.25f, 0.06f, -0.05f, 0.10f, 0.05f,
                0.38f, 0.28f, 0.045f, -0.42f, 0.30f, 0.04f};
        for (int i = 0; i < spots.length; i += 3) {
            canvas.drawCircle(cx + ex * spots[i], cy + ey * spots[i + 1], u * spots[i + 2], paint);
        }

        // блик сверху
        sheen.setColor(0x40FFFFFF);
        sheen.setMaskFilter(new android.graphics.BlurMaskFilter(u * 0.02f, android.graphics.BlurMaskFilter.Blur.NORMAL));
        rect.set(cx - ex * 0.55f, cy - ey * 0.72f, cx + ex * 0.12f, cy - ey * 0.18f);
        canvas.drawOval(rect, sheen);
        sheen.setMaskFilter(null);

        if (stage == STAGE_HATCHING) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(u * 0.022f);
            paint.setColor(blend(accent, Color.BLACK, 0.55f));
            shape.rewind();
            shape.moveTo(cx - u * 0.055f, cy - u * 0.16f);
            shape.lineTo(cx + u * 0.028f, cy - u * 0.045f);
            shape.lineTo(cx - u * 0.045f, cy + u * 0.035f);
            shape.lineTo(cx + u * 0.025f, cy + u * 0.15f);
            canvas.drawPath(shape, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawCreature(Canvas canvas, float u, float cx, float cy, float breathe, int ink) {
        final float body = u * (stage == STAGE_ADULT ? 0.295f : 0.285f) * breathe;

        // контактная тень
        shade.setColor(0x30000000);
        shade.setMaskFilter(new android.graphics.BlurMaskFilter(u * 0.045f, android.graphics.BlurMaskFilter.Blur.NORMAL));
        rect.set(cx - body * 0.78f, cy + body * 0.72f, cx + body * 0.78f, cy + body * 1.12f);
        canvas.drawOval(rect, shade);
        shade.setMaskFilter(null);

        // уши/рожки рисуются ДО тела, чтобы голова перекрыла их основание
        horn.setColor(blend(accent, Color.BLACK, 0.22f));
        if (stage == STAGE_ADULT) {
            shape.rewind();
            for (int s = -1; s <= 1; s += 2) {
                shape.moveTo(cx + s * body * 0.72f, cy - body * 0.72f);
                shape.lineTo(cx + s * body * 0.44f, cy - body * 1.40f);
                shape.lineTo(cx + s * body * 0.08f, cy - body * 0.88f);
                shape.close();
            }
            canvas.drawPath(shape, horn);
        } else {
            canvas.drawCircle(cx - body * 0.62f, cy - body * 0.78f, body * 0.27f, horn);
            canvas.drawCircle(cx + body * 0.62f, cy - body * 0.78f, body * 0.27f, horn);
        }

        // тело с вертикальным объёмом
        rect.set(cx - body, cy - body * 0.90f, cx + body, cy + body);
        paint.setShader(new LinearGradient(cx, cy - body, cx, cy + body,
                new int[]{blend(accent, Color.WHITE, 0.16f), accent, blend(accent, Color.BLACK, 0.16f)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, body, body, paint);
        paint.setShader(null);

        // блик по верхней кромке, обрезанный по телу
        sheen.setColor(0x30FFFFFF);
        sheen.setMaskFilter(new android.graphics.BlurMaskFilter(u * 0.02f, android.graphics.BlurMaskFilter.Blur.NORMAL));
        final int save = canvas.save();
        canvas.clipRect(rect);
        rect.set(cx - body * 0.62f, cy - body * 0.84f, cx + body * 0.30f, cy - body * 0.30f);
        canvas.drawOval(rect, sheen);
        canvas.restoreToCount(save);
        sheen.setMaskFilter(null);

        // глаза
        final float eyeR = body * 0.245f;
        final float eyeY = cy - body * 0.13f;
        final float eyeD = body * 0.36f;
        final float blink = 0.30f + 0.70f * Math.abs((float) Math.cos(phase * 0.65f));
        canvas.drawCircle(cx - eyeD, eyeY, eyeR, eye);
        canvas.drawCircle(cx + eyeD, eyeY, eyeR, eye);
        final float pupil = eyeR * 0.56f;
        paint.setColor(ink);
        for (int s = -1; s <= 1; s += 2) {
            rect.set(cx + s * eyeD - pupil, eyeY - pupil * blink, cx + s * eyeD + pupil, eyeY + pupil * blink);
            canvas.drawOval(rect, paint);
        }
        for (int s = -1; s <= 1; s += 2) {
            canvas.drawCircle(cx + s * eyeD + eyeR * 0.18f, eyeY - eyeR * 0.22f, eyeR * 0.16f, specular);
        }

        // румянец
        canvas.drawCircle(cx - body * 0.66f, cy + body * 0.22f, body * 0.17f, cheek);
        canvas.drawCircle(cx + body * 0.66f, cy + body * 0.22f, body * 0.17f, cheek);

        // улыбка
        mouth.setStrokeWidth(u * 0.020f);
        mouth.setColor(ink);
        shape.rewind();
        shape.moveTo(cx - body * 0.17f, cy + body * 0.16f);
        shape.quadTo(cx, cy + body * 0.36f, cx + body * 0.17f, cy + body * 0.16f);
        canvas.drawPath(shape, mouth);
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