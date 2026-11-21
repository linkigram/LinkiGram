package app.nimarkogram.messenger.automation;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * Готовит вопрос к ИИ по конкретному сообщению: подписывает, кто его написал,
 * и прикладывает фотографию, чтобы модель видела картинку, а не только подпись.
 */
public final class AiChatPrompt {

    /** Готовый запрос: текст с автором и, если было фото, сжатые байты картинки. */
    public static final class Prepared {
        public final String prompt;
        public final byte[] image;
        public final String mime;

        Prepared(String prompt, byte[] image, String mime) {
            this.prompt = prompt;
            this.image = image;
            this.mime = mime;
        }
    }

    private static final int MAX_SIDE = 1280;
    private static final int POLL_PERIOD = 400;
    private static final int POLL_ATTEMPTS = 50;

    private AiChatPrompt() {
    }

    public static void prepare(int account, MessageObject message,
                               Utilities.Callback<Prepared> done) {
        if (message == null || message.messageOwner == null) {
            return;
        }
        final String prompt = buildText(account, message);
        final TLRPC.Photo photo = message.isPhoto() ? MessageObject.getMedia(message.messageOwner).photo : null;
        if (photo == null) {
            dispatch(done, new Prepared(prompt, null, null));
            return;
        }
        loadPhoto(account, message, photo, bytes ->
                dispatch(done, new Prepared(prompt, bytes, bytes == null ? null : "image/jpeg")));
    }

    /** «Патрик пишет: …» — автор подставляется по отправителю сообщения. */
    public static String buildText(int account, MessageObject message) {
        final String body = body(message);
        if (message.isOut()) {
            return LocaleController.formatString(R.string.OEAiYouWrite, body);
        }
        final String author = author(account, message);
        return LocaleController.formatString(R.string.OEAiAuthorSays, author, body);
    }

    private static String body(MessageObject message) {
        String text = message.messageOwner == null ? null : message.messageOwner.message;
        if (TextUtils.isEmpty(text) && message.caption != null) {
            text = message.caption.toString();
        }
        if (TextUtils.isEmpty(text)) {
            text = message.isPhoto()
                    ? LocaleController.getString(R.string.OEAiPhotoOnly)
                    : "";
        }
        return text == null ? "" : text.trim();
    }

    private static String author(int account, MessageObject message) {
        TLRPC.Message owner = message.messageOwner;
        long userId = owner != null && owner.from_id != null ? owner.from_id.user_id : 0;
        TLRPC.User user = userId != 0 ? MessagesController.getInstance(account).getUser(userId) : null;
        if (user != null) {
            String name = UserObject.getUserName(user);
            if (!TextUtils.isEmpty(name)) {
                return name;
            }
        }
        return LocaleController.getString(R.string.OEAiSomeone);
    }

    private static void dispatch(Utilities.Callback<Prepared> done, Prepared prepared) {
        if (done == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> done.run(prepared));
    }

    // ------------------------------------------------------------------ фото

    private static void loadPhoto(int account, MessageObject message, TLRPC.Photo photo,
                                  Utilities.Callback<byte[]> done) {
        final TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(
                photo.sizes, MAX_SIDE, false, null, true);
        if (size == null) {
            done.run(null);
            return;
        }
        final FileLoader loader = FileLoader.getInstance(account);
        final File file = loader.getPathToAttach(size, true);
        if (file != null && file.exists()) {
            decode(file, done);
            return;
        }
        loader.loadFile(ImageLocation.getForPhoto(size, photo), message, "jpg",
                FileLoader.PRIORITY_HIGH, 0);
        pollFile(file, 0, done);
    }

    private static void pollFile(File file, int attempt, Utilities.Callback<byte[]> done) {
        if (file != null && file.exists()) {
            decode(file, done);
            return;
        }
        if (attempt >= POLL_ATTEMPTS) {
            done.run(null);
            return;
        }
        AndroidUtilities.runOnUIThread(() -> pollFile(file, attempt + 1, done), POLL_PERIOD);
    }

    private static void decode(File file, Utilities.Callback<byte[]> done) {
        Utilities.globalQueue.postRunnable(() -> {
            byte[] bytes = null;
            Bitmap source = null;
            Bitmap scaled = null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE);
                source = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                if (source != null) {
                    scaled = fit(source);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    int quality = 85;
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out);
                    bytes = out.toByteArray();
                }
            } catch (Throwable t) {
                FileLog.e("AiChatPrompt: cannot read photo", t);
                bytes = null;
            } finally {
                if (scaled != null && scaled != source) {
                    scaled.recycle();
                }
                if (source != null) {
                    source.recycle();
                }
            }
            final byte[] result = bytes;
            AndroidUtilities.runOnUIThread(() -> done.run(result));
        });
    }

    private static Bitmap fit(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int side = Math.max(width, height);
        if (side <= MAX_SIDE) {
            return source;
        }
        float scale = MAX_SIDE / (float) side;
        int targetWidth = Math.max(1, Math.round(width * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
    }

    private static int sampleSize(int width, int height, int target) {
        int side = Math.max(width, height);
        int sample = 1;
        while (side / sample > target * 2) {
            sample *= 2;
        }
        return Math.max(1, sample);
    }
}