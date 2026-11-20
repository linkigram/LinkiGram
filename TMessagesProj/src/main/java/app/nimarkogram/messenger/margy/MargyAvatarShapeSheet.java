package app.nimarkogram.messenger.margy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import app.nimarkogram.messenger.appearance.ClientTheme;

/**
 * Выбор формы аватарок с живым превью: большая аватарка сверху и ряд форм
 * снизу. Тап по форме сразу применяет её — и в превью, и в настройках.
 */
public final class MargyAvatarShapeSheet {

    private MargyAvatarShapeSheet() {}

    public static void show(BaseFragment fragment, Runnable onApply) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        final Context context = fragment.getParentActivity();

        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(16));

        final TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.NM_MargyAvatarShape));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(textColor());
        content.addView(title, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final ShapePreview preview = new ShapePreview(context);
        preview.setShape(MargyConfig.avatarShape());
        preview.setSelected(true);
        content.addView(preview, LayoutHelper.createLinear(
                140, 140, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 16));

        final HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setPadding(0, 0, 0, dp(4));

        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final ShapePreview[] thumbs = new ShapePreview[MargyAvatars.ALL.length];
        for (int i = 0; i < MargyAvatars.ALL.length; i++) {
            final int shape = i;
            final ShapePreview thumb = new ShapePreview(context);
            thumb.setShape(i);
            thumb.setSelected(i == MargyConfig.avatarShape());
            thumb.setContentDescription(shapeName(i));
            thumb.setOnClickListener(v -> {
                MargyConfig.setAvatarShape(shape);
                MargyAvatars.invalidate();
                preview.setShape(shape);
                for (int k = 0; k < thumbs.length; k++) {
                    thumbs[k].setSelected(k == shape);
                }
                if (onApply != null) {
                    onApply.run();
                }
            });
            thumbs[i] = thumb;
            row.addView(thumb, LayoutHelper.createLinear(58, 58, Gravity.CENTER_VERTICAL,
                    i == 0 ? 0 : 10, 0, 0, 0));
        }
        scroller.addView(row, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        content.addView(scroller, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final TextView hint = new TextView(context);
        hint.setText(LocaleController.getString(R.string.NM_MargyShapePreviewHint));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hint.setTextColor(mutedColor());
        hint.setPadding(0, dp(10), 0, 0);
        content.addView(hint, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final BottomSheet sheet = new BottomSheet(context, false);
        sheet.setCustomView(content);
        sheet.show();
    }

    private static int textColor() {
        return ClientTheme.isEnabled() ? ClientTheme.color(ClientTheme.ON_SURFACE)
                : Theme.getColor(Theme.key_dialogTextBlack);
    }

    private static int mutedColor() {
        return ClientTheme.isEnabled() ? ClientTheme.color(ClientTheme.SECONDARY)
                : Theme.getColor(Theme.key_dialogTextGray3);
    }

    private static CharSequence shapeName(int shape) {
        switch (shape) {
            case 0: return LocaleController.getString(R.string.NM_MargyShapeCircle);
            case 1: return LocaleController.getString(R.string.NM_MargyShapeRounded);
            case 2: return LocaleController.getString(R.string.NM_MargyShapeSquare);
            case 3: return LocaleController.getString(R.string.NM_MargyShapeCookie);
            case 4: return LocaleController.getString(R.string.NM_MargyShapeClover);
            case 5: return LocaleController.getString(R.string.NM_MargyShapeBurst);
            case 6: return LocaleController.getString(R.string.NM_MargyShapeSun);
            case 7: return LocaleController.getString(R.string.NM_MargyShapePentagon);
            case 8: return LocaleController.getString(R.string.NM_MargyShapeHexagon);
            default: return LocaleController.getString(R.string.NM_MargyShapeHeart);
        }
    }

    /** Аватарка выбранной формы с градиентом и силуэтом человека. */
    private static class ShapePreview extends View {

        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shapePath = new Path();
        private final Matrix matrix = new Matrix();
        private final Path glyphPath = new Path();
        private final RectF rect = new RectF();
        private int shape;
        private boolean selected;
        private Shader gradient;

        ShapePreview(Context context) {
            super(context);
            glyph.setColor(0xF2FFFFFF);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(dp(2));
            ring.setColor(ClientTheme.isEnabled()
                    ? ClientTheme.color(ClientTheme.PRIMARY)
                    : Theme.getColor(Theme.key_chats_actionBackground));
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        public void setShape(int shape) {
            this.shape = Math.max(0, Math.min(MargyAvatars.ALL.length - 1, shape));
            gradient = null;
            invalidate();
        }

        @Override
        public void setSelected(boolean selected) {
            super.setSelected(selected);
            this.selected = selected;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final float size = Math.min(getWidth(), getHeight());
            final float pad = dp(6);
            final float cx = getWidth() / 2f;
            final float cy = getHeight() / 2f;
            final float half = (size - pad * 2) / 2f;

            if (gradient == null) {
                final int from = ClientTheme.isEnabled()
                        ? ClientTheme.color(ClientTheme.PRIMARY)
                        : Theme.getColor(Theme.key_chats_actionBackground);
                final int to = ClientTheme.isEnabled()
                        ? ClientTheme.color(ClientTheme.CONTAINER)
                        : Theme.getColor(Theme.key_chats_actionPressedBackground);
                gradient = new LinearGradient(cx - half, cy - half, cx + half, cy + half,
                        from, to, Shader.TileMode.CLAMP);
            }

            // Контур формы.
            shapePath.rewind();
            matrix.reset();
            matrix.setScale(half, half);
            matrix.postTranslate(cx, cy);
            shapePath.addPath(MargyAvatars.unit(shape), matrix);

            canvas.save();
            canvas.clipPath(shapePath);
            fill.setShader(gradient);
            canvas.drawRect(cx - half, cy - half, cx + half, cy + half, fill);
            // Мягкий блик сверху.
            fill.setShader(null);
            fill.setColor(0x22FFFFFF);
            canvas.drawCircle(cx - half * 0.32f, cy - half * 0.42f, half * 0.62f, fill);

            // Силуэт: голова и плечи.
            glyphPath.rewind();
            glyphPath.addCircle(cx, cy - half * 0.16f, half * 0.30f, Path.Direction.CW);
            rect.set(cx - half * 0.52f, cy + half * 0.16f, cx + half * 0.52f, cy + half * 1.02f);
            glyphPath.addRoundRect(rect, half * 0.5f, half * 0.5f, Path.Direction.CW);
            canvas.drawPath(glyphPath, glyph);
            canvas.restore();

            if (selected) {
                canvas.save();
                matrix.reset();
                final float ringHalf = half + dp(3);
                matrix.setScale(ringHalf, ringHalf);
                matrix.postTranslate(cx, cy);
                final Path ringPath = new Path();
                ringPath.addPath(MargyAvatars.unit(shape), matrix);
                canvas.drawPath(ringPath, ring);
                canvas.restore();
            }
        }
    }
}