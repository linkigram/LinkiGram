package app.nimarkogram.messenger.margy;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;

/**
 * Формы аватарок: круг, скруглённый квадрат, квадрат, печенье, клевер, вспышка,
 * солнце, пятиугольник, шестиугольник, сердце. Форма вырезается по контуру
 * вокруг обычной отрисовки аватарки, поэтому фото и инициалы остаются как есть.
 */
public final class MargyAvatars {

    public static final String[] ALL = {"circle", "rounded", "square", "cookie", "clover", "burst", "sun",
            "pentagon", "hexagon", "heart"};

    private static final int POINTS = 144;
    private static final Path[] UNIT = new Path[ALL.length];

    /** Выставляется на время отрисовки: фон тогда заливает весь квадрат. */
    public static boolean drawing;

    private MargyAvatars() {}

    public static int chosen() {
        return MargyConfig.avatarShape();
    }

    public static void choose(int shape) {
        MargyConfig.setAvatarShape(shape);
    }

    /** Выбрана форма, отличная от круга, и сама функция включена. */
    public static boolean shaped() {
        return MargyConfig.avatars() && chosen() != 0;
    }

    private static final Path placed = new Path();
    private static final Matrix place = new Matrix();

    /** Контур выбранной формы, вписанный в указанный квадрат. Только главный поток. */
    public static Path path(float x, float y, float w, float h) {
        placed.rewind();
        place.setScale(w / 2f, h / 2f);
        place.postTranslate(x + w / 2f, y + h / 2f);
        placed.addPath(unit(chosen()), place);
        return placed;
    }

    public static Path unit(int shape) {
        Path known = UNIT[shape];
        if (known != null) {
            return known;
        }
        Path made = new Path();
        switch (ALL[shape]) {
            case "rounded":
                made.addRoundRect(new RectF(-1, -1, 1, 1), 0.44f, 0.44f, Path.Direction.CW);
                break;
            case "square":
                made.addRect(-1, -1, 1, 1, Path.Direction.CW);
                break;
            case "heart":
                made.arcTo(new RectF(-1f, -1f, 0.1f, 0.1f), -35.1f, -174.9f, true);
                made.cubicTo(-0.70f, 0.25f, -0.28f, 0.70f, 0f, 1f);
                made.cubicTo(0.28f, 0.70f, 0.70f, 0.25f, 0.926f, -0.175f);
                made.arcTo(new RectF(-0.1f, -1f, 1f, 0.1f), 30f, -174.9f, false);
                made.close();
                break;
            case "circle":
                made.addCircle(0, 0, 1, Path.Direction.CW);
                break;
            default:
                for (int i = 0; i < POINTS; i++) {
                    double around = 2 * Math.PI * i / POINTS - Math.PI / 2;
                    float reach = reach(ALL[shape], around + Math.PI / 2);
                    float px = (float) (reach * Math.cos(around));
                    float py = (float) (reach * Math.sin(around));
                    if (i == 0) {
                        made.moveTo(px, py);
                    } else {
                        made.lineTo(px, py);
                    }
                }
                made.close();
        }
        UNIT[shape] = made;
        return made;
    }

    private static float reach(String shape, double around) {
        switch (shape) {
            case "cookie":
                return 0.92f + 0.08f * (float) Math.cos(9 * around);
            case "clover":
                return 0.84f + 0.16f * (float) Math.cos(4 * around + Math.PI);
            case "burst":
                return 0.90f + 0.10f * (float) Math.cos(12 * around);
            case "sun":
                return 0.87f + 0.13f * (float) Math.cos(8 * around);
            case "pentagon":
                return sided(5, around);
            case "hexagon":
                return sided(6, around);
            default:
                return 1f;
        }
    }

    private static float sided(int sides, double around) {
        double slice = 2 * Math.PI / sides;
        double within = around % slice;
        if (within < 0) {
            within += slice;
        }
        double flat = Math.cos(Math.PI / sides) / Math.cos(within - slice / 2);
        double cap = 0.93;
        double sharp = 36.0;
        double soft = -Math.log(Math.exp(-sharp * flat) + Math.exp(-sharp * cap)) / sharp;
        return (float) (soft / cap);
    }

    private static final Path ringPath = new Path();
    private static final PathMeasure measure = new PathMeasure();

    /** Кольцо истории в выбранной форме вокруг прямоугольника. */
    public static void ring(Canvas canvas, RectF rect, Paint paint) {
        canvas.drawPath(path(rect.left, rect.top, rect.width(), rect.height()), paint);
    }

    /**
     * Кусок кольца по углам, как у {@link Canvas#drawArc}: 0 на трёх часах по
     * часовой стрелке. Формы начинаются сверху, поэтому углы сдвинуты на четверть.
     */
    public static void segment(Canvas canvas, RectF rect, Paint paint, float startAngle, float endAngle) {
        Path whole = path(rect.left, rect.top, rect.width(), rect.height());
        measure.setPath(whole, true);
        float length = measure.getLength();
        float from = (((startAngle + 90f) % 360f) + 360f) % 360f / 360f * length;
        float sweep = Math.max(0f, Math.min(360f, endAngle - startAngle)) / 360f * length;
        ringPath.rewind();
        if (from + sweep <= length) {
            measure.getSegment(from, from + sweep, ringPath, true);
        } else {
            measure.getSegment(from, length, ringPath, true);
            measure.getSegment(0, from + sweep - length, ringPath, true);
        }
        canvas.drawPath(ringPath, paint);
    }

    /** Название формы для списка настроек. */
    public static String name(int shape) {
        switch (ALL[shape]) {
            case "circle": return "circle";
            case "rounded": return "rounded";
            case "square": return "square";
            case "cookie": return "cookie";
            case "clover": return "clover";
            case "burst": return "burst";
            case "sun": return "sun";
            case "pentagon": return "pentagon";
            case "hexagon": return "hexagon";
            case "heart": return "heart";
            default: return ALL[shape];
        }
    }
}