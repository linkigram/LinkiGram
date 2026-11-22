package org.telegram.ui.Components.glass;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

/** Samples an offscreen backdrop for the idle and dragged tab capsule. */
@RequiresApi(api = 33)
public final class TabCapsuleLens {

    private final RuntimeShader shader;
    private final RuntimeShader dragShader;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private RenderNode dragScene;
    private int dragScenePad;
    private BitmapShader content;
    private Bitmap bound;

    public TabCapsuleLens() {
        shader = compile(R.raw.tab_capsule_lens);
        dragShader = compile(R.raw.tab_capsule_drag_lens);
        if (shader != null) paint.setShader(shader);
    }

    private static RuntimeShader compile(int resource) {
        try {
            return new RuntimeShader(AndroidUtilities.readRes(resource));
        } catch (IllegalArgumentException e) {
            android.util.Log.e("LinkiGlass", "capsule shader failed: " + resource, e);
            FileLog.e(e);
            return null;
        }
    }

    public boolean isReady() {
        return shader != null;
    }

    /**
     * @param content   the captured panel background
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
        shader.setInputShader("img", bind(content));
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

    public boolean isDragReady() {
        return dragShader != null;
    }

    public Canvas beginDragScene(int width, int height) {
        if (dragScene == null) dragScene = new RenderNode("LinkiTabDragScene");
        dragScenePad = AndroidUtilities.dp(12);
        dragScene.setPosition(-dragScenePad, -dragScenePad, width + dragScenePad, height + dragScenePad);
        final Canvas canvas = dragScene.beginRecording();
        canvas.translate(dragScenePad, dragScenePad);
        return canvas;
    }

    public void endDragScene() {
        dragScene.endRecording();
    }

    public void clearDragScene() {
        if (dragScene != null) {
            dragScene.discardDisplayList();
            dragScene.setRenderEffect(null);
        }
    }

    public void drawDrag(Canvas canvas, float x, float y, float width, float height) {
        final float unit = height / DRAG_REFERENCE_HEIGHT;
        dragShader.setFloatUniform("center", x + dragScenePad, y + dragScenePad);
        dragShader.setFloatUniform("size", width / 2f, height / 2f);
        dragShader.setFloatUniform("scene_size", dragScene.getWidth(), dragScene.getHeight());
        dragShader.setFloatUniform("scene_pad", dragScenePad);
        dragShader.setFloatUniform("unit", unit);
        dragScene.setRenderEffect(RenderEffect.createRuntimeShaderEffect(dragShader, "img"));
        canvas.drawRenderNode(dragScene);
    }

    private static final float DRAG_REFERENCE_HEIGHT = 52f;

    private BitmapShader bind(Bitmap bitmap) {
        if (bound != bitmap) {
            bound = bitmap;
            content = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            content.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
        }
        return content;
    }
}
