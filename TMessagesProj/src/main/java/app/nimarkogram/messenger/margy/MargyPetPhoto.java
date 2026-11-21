package app.nimarkogram.messenger.margy;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Своё фото питомца вместо нарисованного зверя. Файл лежит во внутреннем
 * каталоге приложения, поэтому переживает перезапуск и не теряется при чистке
 * кэша. Хранится квадратным и уменьшенным: рисовать 4K-фото на плашке 68dp
 * бессмысленно и дорого.
 */
public final class MargyPetPhoto {

    private static final String FILE_NAME = "margy_pet.png";
    private static final int MAX_SIDE = 512;

    private static Bitmap cached;
    private static boolean loaded;

    private MargyPetPhoto() {}

    public static File file() {
        File dir = ApplicationLoader.getFilesDirFixed("margy");
        return dir == null ? null : new File(dir, FILE_NAME);
    }

    public static boolean hasPhoto() {
        final File file = file();
        return file != null && file.exists();
    }

    public static void clear() {
        final File file = file();
        if (file != null) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        invalidate();
    }

    /** Сбросить кэш: после смены файла питомец перечитает картинку. */
    public static void invalidate() {
        // Старое фото не освобождаем вручную: на него ещё могут ссылаться
        // нарисованные питомцы, а сборщик заберёт его сам.
        cached = null;
        loaded = false;
    }

    /** Готовый к рисованию квадрат или null, если фото не задано. */
    public static synchronized Bitmap bitmap() {
        if (loaded) {
            return cached;
        }
        loaded = true;
        final File file = file();
        if (file == null || !file.exists()) {
            cached = null;
            return null;
        }
        try {
            final BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE);
            cached = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable t) {
            FileLog.e("MargyPetPhoto: cannot read photo", t);
            cached = null;
        }
        return cached;
    }

    /**
     * Разобрать выбранную картинку и записать её квадратом. Работа идёт в
     * фоне, готовый результат приходит в колбэк на UI-потоке.
     */
    public static void save(final Context context, final Uri uri, final Runnable onDone, final Runnable onError) {
        if (context == null || uri == null) {
            if (onError != null) {
                onError.run();
            }
            return;
        }
        final Context app = context.getApplicationContext();
        Utilities.globalQueue.postRunnable(() -> {
            boolean ok = false;
            InputStream stream = null;
            try {
                final BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                stream = app.getContentResolver().openInputStream(uri);
                if (stream != null) {
                    BitmapFactory.decodeStream(stream, null, bounds);
                    stream.close();
                }
                stream = app.getContentResolver().openInputStream(uri);
                if (stream != null) {
                    final BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE);
                    final Bitmap source = BitmapFactory.decodeStream(stream, null, options);
                    if (source != null && source.getWidth() > 0 && source.getHeight() > 0) {
                        ok = write(square(source));
                    }
                }
            } catch (Throwable t) {
                FileLog.e("MargyPetPhoto: cannot save photo", t);
            } finally {
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (Throwable ignore) {
                }
            }
            final boolean saved = ok;
            AndroidUtilities.runOnUIThread(() -> {
                if (saved) {
                    invalidate();
                    if (onDone != null) {
                        onDone.run();
                    }
                } else if (onError != null) {
                    onError.run();
                }
            });
        });
    }

    private static boolean write(Bitmap bitmap) {
        FileOutputStream out = null;
        try {
            final File file = file();
            if (file == null) {
                return false;
            }
            out = new FileOutputStream(file);
            return bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Throwable t) {
            FileLog.e("MargyPetPhoto: cannot write file", t);
            return false;
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Throwable ignore) {
            }
        }
    }

    /** Центральный квадрат, ужатый до MAX_SIDE — ровно то, что увидит плашка. */
    private static Bitmap square(Bitmap source) {
        final int side = Math.min(source.getWidth(), source.getHeight());
        final int left = (source.getWidth() - side) / 2;
        final int top = (source.getHeight() - side) / 2;
        final int target = Math.min(MAX_SIDE, side);
        final Bitmap result = Bitmap.createBitmap(target, target, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(result);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source,
                new Rect(left, top, left + side, top + side),
                new RectF(0, 0, target, target), paint);
        if (source != result) {
            source.recycle();
        }
        return result;
    }

    private static int sampleSize(int width, int height, int maxSide) {
        int sample = 1;
        if (width <= 0 || height <= 0) {
            return sample;
        }
        while (width / (sample * 2) >= maxSide && height / (sample * 2) >= maxSide) {
            sample *= 2;
        }
        return sample;
    }
}