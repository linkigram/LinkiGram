package app.nimarkogram.messenger.components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.FileLog;

/**
 * Big rounded banner used by the update screen and the "About" screen: an
 * accent gradient with soft light sweeps and a hairline rim, like the cover of
 * the ColorOS "About phone" page. Children are added on top of the drawing.
 */
public class LinkiHeroBanner extends FrameLayout {

    public static final int RADIUS_DP = 28;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int colorTop;
    private final int colorBottom;

    public LinkiHeroBanner(Context context, int accentColor) {
        super(context);
        colorTop = ColorUtils.blendARGB(accentColor, 0xFFFFFFFF, 0.18f);
        colorBottom = ColorUtils.blendARGB(accentColor, 0xFF000000, 0.40f);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(RADIUS_DP));
            }
        });
        setClipToOutline(true);
        setElevation(dp(1));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final float w = getWidth();
        final float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, w * 0.85f, h,
                colorTop, colorBottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        paint.setColor(0x24FFFFFF);
        canvas.save();
        canvas.rotate(-22, w * 0.5f, h * 0.5f);
        canvas.drawOval(w * 0.20f, -h * 0.42f, w * 1.12f, h * 0.58f, paint);
        canvas.restore();

        paint.setColor(0x16FFFFFF);
        canvas.save();
        canvas.rotate(19, w * 0.5f, h * 0.5f);
        canvas.drawOval(-w * 0.24f, h * 0.36f, w * 0.86f, h * 1.42f, paint);
        canvas.restore();

        paint.setColor(0x0DFFFFFF);
        canvas.drawCircle(w * 0.86f, h * 0.12f, h * 0.55f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0x26FFFFFF);
        canvas.drawRoundRect(dp(0.5f), dp(0.5f), w - dp(0.5f), h - dp(0.5f),
                dp(RADIUS_DP), dp(RADIUS_DP), paint);
        paint.setStyle(Paint.Style.FILL);

        super.onDraw(canvas);
    }

    /** App icon cropped into a rounded square, as used inside the banner. */
    public static ImageView createLogo(Context context, int sizeDp) {
        final ImageView logo = new ImageView(context);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try {
            final Drawable icon = context.getPackageManager().getApplicationIcon(context.getPackageName());
            logo.setImageDrawable(icon);
        } catch (Exception e) {
            FileLog.e(e);
        }
        final float radius = dp(sizeDp / 4f);
        logo.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        logo.setClipToOutline(true);
        logo.setElevation(dp(2));
        return logo;
    }
}