package org.telegram.ui.Components.glass;

import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

/** Replays the existing tab backdrop into the lifted selector's coordinate space. */
@RequiresApi(api = 33)
public final class TabCapsuleLens {
    private final RuntimeShader shader;
    private final RenderNode scene = new RenderNode("LinkiTabLensScene");

    public TabCapsuleLens() {
        RuntimeShader compiled = null;
        try {
            compiled = new RuntimeShader(AndroidUtilities.readRes(R.raw.tab_capsule_drag_lens));
        } catch (IllegalArgumentException e) {
            FileLog.e(e);
        }
        shader = compiled;
    }

    public static int scenePadding() {
        return AndroidUtilities.dp(20);
    }

    public boolean isReady() {
        return shader != null;
    }

    public Canvas beginScene(int width, int height) {
        final int padding = scenePadding();
        scene.setPosition(-padding, -padding, width + padding, height + padding);
        final Canvas canvas = scene.beginRecording();
        canvas.translate(padding, padding);
        return canvas;
    }

    public void endScene() {
        scene.endRecording();
    }

    public void clearScene() {
        scene.discardDisplayList();
        scene.setRenderEffect(null);
    }

    public void draw(Canvas canvas, float x, float y, float width, float height, float lift, boolean dark) {
        final int padding = scenePadding();
        shader.setFloatUniform("center", x + padding, y + padding);
        shader.setFloatUniform("half_size", width * 0.5f, height * 0.5f);
        shader.setFloatUniform("scene_size", scene.getWidth(), scene.getHeight());
        shader.setFloatUniform("density", AndroidUtilities.density);
        shader.setFloatUniform("lift", lift);
        shader.setFloatUniform("dark_theme", dark ? 1f : 0f);
        scene.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "img"));
        canvas.drawRenderNode(scene);
    }
}
