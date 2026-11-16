package app.nimarkogram.messenger.updater;

import java.io.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/** Bounded, streaming COPY/DATA decoder. It reconstructs the signed APK byte for byte. */
public final class ApkDelta {
    public static final long MAX_BYTES = 512L * 1024 * 1024;
    private static final byte[] MAGIC = {'L', 'G', 'D', 'E', 'L', 'T', 'A', '1'};

    public interface Control {
        void check() throws IOException;
        void progress(long written, long total);
    }

    private ApkDelta() { }

    public static void apply(File base, File patch, File output, Control control) throws Exception {
        if (base.getCanonicalPath().equals(output.getCanonicalPath())
                || patch.getCanonicalPath().equals(output.getCanonicalPath())) {
            throw new IOException("Delta output aliases input");
        }
        boolean complete = false;
        try (DataInputStream header = new DataInputStream(new BufferedInputStream(new FileInputStream(patch)))) {
            byte[] magic = new byte[8];
            header.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) throw new IOException("Unknown delta format");
            long baseSize = header.readLong(), targetSize = header.readLong();
            if (baseSize <= 0 || baseSize > MAX_BYTES || baseSize != base.length()
                    || targetSize <= 0 || targetSize > MAX_BYTES) throw new IOException("Invalid delta size");
            byte[] baseHash = new byte[32], targetHash = new byte[32];
            header.readFully(baseHash);
            header.readFully(targetHash);
            if (!MessageDigest.isEqual(baseHash, digest(base, control))) throw new IOException("Wrong base APK");
            MessageDigest result = MessageDigest.getInstance("SHA-256");
            try (DataInputStream commands = new DataInputStream(new GZIPInputStream(header));
                 RandomAccessFile old = new RandomAccessFile(base, "r");
                 FileOutputStream out = new FileOutputStream(output)) {
                byte[] buffer = new byte[65536];
                long written = 0;
                int count = 0;
                while (true) {
                    control.check();
                    if (++count > 1000000) throw new IOException("Too many delta commands");
                    int op = commands.readUnsignedByte();
                    if (op == 0) {
                        if (written != targetSize || commands.read() != -1) throw new IOException("Invalid delta end");
                        break;
                    }
                    long offset = op == 1 ? commands.readLong() : 0;
                    if (op != 1 && op != 2) throw new IOException("Invalid delta operation");
                    int length = commands.readInt();
                    if (length <= 0 || length > targetSize - written
                            || op == 1 && (offset < 0 || offset > baseSize || length > baseSize - offset)) {
                        throw new IOException("Delta operation out of bounds");
                    }
                    if (op == 1) old.seek(offset);
                    int remaining = length;
                    while (remaining > 0) {
                        control.check();
                        int n = Math.min(remaining, buffer.length);
                        if (op == 1) old.readFully(buffer, 0, n);
                        else commands.readFully(buffer, 0, n);
                        out.write(buffer, 0, n);
                        result.update(buffer, 0, n);
                        written += n;
                        remaining -= n;
                        control.progress(written, targetSize);
                    }
                }
                if (!MessageDigest.isEqual(result.digest(), targetHash)) throw new IOException("Delta result hash mismatch");
                out.getFD().sync();
            }
            complete = true;
        } finally {
            if (!complete) output.delete();
        }
    }

    private static byte[] digest(File file, Control control) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) {
                control.check();
                md.update(buffer, 0, n);
            }
        }
        return md.digest();
    }
}
