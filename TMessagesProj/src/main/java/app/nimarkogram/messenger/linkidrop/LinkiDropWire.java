package app.nimarkogram.messenger.linkidrop;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Wire format shared by the Linki Drop host and guest.
 *
 * <pre>
 * guest  -> MAGIC + token + "\n"
 * host   -> header JSON + "\n"
 * host   -> exactly "size" bytes
 * guest  -> "OK\n" or "FAIL\n"
 * </pre>
 */
public final class LinkiDropWire {

    public static final String MAGIC = "LGD1";

    private static final int MAX_HEADER = 4096;
    private static final int BUFFER = 64 * 1024;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public interface Progress {
        void onProgress(long done, long total);
    }

    private LinkiDropWire() {
    }

    public static String randomToken() {
        final byte[] raw = new byte[6];
        new SecureRandom().nextBytes(raw);
        final StringBuilder out = new StringBuilder();
        for (byte b : raw) {
            out.append(HEX[(b >> 4) & 0x0F]).append(HEX[b & 0x0F]);
        }
        return out.toString();
    }

    public static void writeMagic(OutputStream out) throws IOException {
        out.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    public static boolean readMagic(InputStream in) throws IOException {
        final byte[] buffer = new byte[MAGIC.length()];
        int read = 0;
        while (read < buffer.length) {
            final int n = in.read(buffer, read, buffer.length - read);
            if (n < 0) {
                return false;
            }
            read += n;
        }
        return MAGIC.equals(new String(buffer, StandardCharsets.US_ASCII));
    }

    public static void writeHeader(OutputStream out, JSONObject header) throws IOException {
        final byte[] body = header.toString().getBytes(StandardCharsets.UTF_8);
        if (body.length > MAX_HEADER) {
            throw new IOException("header too large");
        }
        out.write(body);
        out.write('\n');
        out.flush();
    }

    public static JSONObject readHeader(InputStream in) throws IOException {
        final String line = readLine(in, MAX_HEADER);
        if (line == null || line.isEmpty()) {
            throw new IOException("empty header");
        }
        try {
            return new JSONObject(line);
        } catch (Exception e) {
            throw new IOException("bad header");
        }
    }

    /** Reads one UTF-8 line; returns null at EOF. */
    public static String readLine(InputStream in, int max) throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (true) {
            final int c = in.read();
            if (c < 0) {
                return buffer.size() == 0 ? null : new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
            if (c == '\n') {
                return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
            if (buffer.size() >= max) {
                throw new IOException("line too long");
            }
            buffer.write(c);
        }
    }

    public static String sha256Of(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] buffer = new byte[BUFFER];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return hex(digest.digest());
        } catch (Exception e) {
            return null;
        }
    }

    public static void sendFile(File file, OutputStream out, long size, Progress progress) throws IOException {
        final byte[] buffer = new byte[BUFFER];
        long done = 0;
        try (FileInputStream in = new FileInputStream(file)) {
            while (done < size) {
                final int want = (int) Math.min(buffer.length, size - done);
                final int read = in.read(buffer, 0, want);
                if (read < 0) {
                    throw new IOException("file shrank");
                }
                out.write(buffer, 0, read);
                done += read;
                if (progress != null) {
                    progress.onProgress(done, size);
                }
            }
            out.flush();
        }
    }

    public static void receiveFile(InputStream in, OutputStream out, long size, Progress progress) throws IOException {
        final byte[] buffer = new byte[BUFFER];
        long done = 0;
        while (done < size) {
            final int want = (int) Math.min(buffer.length, size - done);
            final int read = in.read(buffer, 0, want);
            if (read < 0) {
                throw new IOException("connection closed");
            }
            out.write(buffer, 0, read);
            done += read;
            if (progress != null) {
                progress.onProgress(done, size);
            }
        }
        out.flush();
    }

    /** Keeps only a safe base name so a peer cannot escape the drop folder. */
    public static String sanitizeName(String raw) {
        String name = raw == null ? "" : raw.replace('\\', '/');
        final int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.isEmpty()) {
            name = "linki-drop-file";
        }
        if (name.length() > 120) {
            final int dot = name.lastIndexOf('.');
            final String tail = dot > 0 && name.length() - dot <= 12 ? name.substring(dot) : "";
            name = name.substring(0, 120 - tail.length()) + tail;
        }
        return name;
    }

    private static String hex(byte[] bytes) {
        final StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(HEX[(b >> 4) & 0x0F]).append(HEX[b & 0x0F]);
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }
}