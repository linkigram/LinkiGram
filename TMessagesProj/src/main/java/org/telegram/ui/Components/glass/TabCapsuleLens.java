package org.telegram.ui.Components.glass;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.graphics.Shader;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

/**
 * The lens of the moving tab capsule. Unlike the old canvas bubble it does not fake the
 * refraction with gradients: the tab row is captured into a small bitmap and pulled through
 * the same bend model the bottom bar uses, so the icons really bend and blur at the rim.
 */
@RequiresApi(api = 33)
public final class TabCapsuleLens {

    private final RuntimeShader shader;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private BitmapShader content;
    private Bitmap bound;

    public TabCapsuleLens() {
        RuntimeShader compiled = null;
        try {
            compiled = new RuntimeShader(AndroidUtilities.readRes(R.raw.tab_capsule_lens));
            android.util.Log.i("LinkiGlass", "capsule lens compiled");
        } catch (Throwable e) {
            android.util.Log.e("LinkiGlass", "capsule lens failed: " + e);
            FileLog.e(e);
        }
        shader = compiled;
        if (shader != null) {
            paint.setShader(shader);
        }
    }

    public boolean isReady() {
        return shader != null;
    }

    /**
     * @param content   the captured tab row, premultiplied, its origin at {@code drawLeft/drawTop}
     * @param bitmapW   bitmap width in px
     * @param bitmapH   bitmap height in px
     * @param capsuleW  capsule width in px
     * @param capsuleH  capsule height in px
     * @param light     0..1 position of the moving light along the panel
     * @param press     0..1 press progress
     */
    public void draw(Canvas canvas, Bitmap content, float bitmapW, float bitmapH,
                     float capsuleW, float capsuleH, float radius, float thickness,
                     float light, float press) {
        if (shader == null || content == null || content.isRecycled()) {
            return;
        }
        if (bound != content) {
            bound = content;
            this.content = new BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }
        shader.setInputShader("img", this.content);
        shader.setFloatUniform("resolution", Math.max(1f, bitmapW), Math.max(1f, bitmapH));
        shader.setFloatUniform("center", bitmapW / 2f, bitmapH / 2f);
        shader.setFloatUniform("size", Math.max(0.5f, capsuleW / 2f - 0.5f), Math.max(0.5f, capsuleH / 2f - 0.5f));
        shader.setFloatUniform("radius", Math.max(0.5f, radius));
        shader.setFloatUniform("thickness", Math.max(1f, thickness));
        shader.setFloatUniform("refract_index", 1.5f);
        shader.setFloatUniform("refract_intensity", 1f);
        shader.setFloatUniform("light_position", Math.max(0f, Math.min(1f, light)));
        shader.setFloatUniform("press_progress", Math.max(0f, Math.min(1f, press)));
        canvas.drawRect(0, 0, bitmapW, bitmapH, paint);
    }
}