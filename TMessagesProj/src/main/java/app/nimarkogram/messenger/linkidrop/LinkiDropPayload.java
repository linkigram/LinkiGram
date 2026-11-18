package app.nimarkogram.messenger.linkidrop;

import android.net.Uri;
import android.text.TextUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Linki Drop offer: everything the peer needs to pull one file over a local link.
 *
 * The NFC tag only carries this small record (well under the 4 KB NDEF limit);
 * the bytes themselves travel over a temporary Wi-Fi hotspot or Bluetooth
 * RFCOMM, whichever transport the sender picked.
 *
 * <pre>linkigram://linki-drop?t=w&amp;a=192.168.43.1&amp;p=45771&amp;k=ab12cd34&amp;n=photo.jpg&amp;s=123&amp;x=…</pre>
 */
public final class LinkiDropPayload {

    public static final String SCHEME = "linkigram";
    public static final String HOST = "linki-drop";

    public static final String TRANSPORT_WIFI = "w";
    public static final String TRANSPORT_BLUETOOTH = "b";

    public static final String KIND_FILE = "f";
    public static final String KIND_PLUGIN = "p";

    public final String transport;
    public final String ssid;
    public final String password;
    public final String[] addresses;
    public final int port;
    public final String token;
    public final String deviceName;
    public final String name;
    public final long size;
    public final String sha256;
    public final String kind;

    public LinkiDropPayload(String transport, String ssid, String password, String[] addresses,
                            int port, String token, String deviceName, String name, long size,
                            String sha256, String kind) {
        this.transport = transport == null ? TRANSPORT_WIFI : transport;
        this.ssid = ssid == null ? "" : ssid;
        this.password = password == null ? "" : password;
        this.addresses = addresses == null ? new String[0] : addresses;
        this.port = port;
        this.token = token == null ? "" : token;
        this.deviceName = deviceName == null ? "" : deviceName;
        this.name = name == null ? "" : name;
        this.size = size;
        this.sha256 = sha256 == null ? "" : sha256;
        this.kind = kind == null ? KIND_FILE : kind;
    }

    public boolean isWifi() {
        return TRANSPORT_WIFI.equals(transport);
    }

    public boolean isPlugin() {
        return KIND_PLUGIN.equals(kind);
    }

    public String toUri() {
        final StringBuilder out = new StringBuilder(SCHEME).append("://").append(HOST)
                .append("?t=").append(transport)
                .append("&k=").append(encode(token))
                .append("&n=").append(encode(name))
                .append("&s=").append(size)
                .append("&x=").append(sha256)
                .append("&g=").append(kind);
        if (isWifi()) {
            out.append("&ssid=").append(encode(ssid));
            if (!password.isEmpty()) {
                out.append("&pw=").append(encode(password));
            }
            out.append("&p=").append(port);
            if (addresses.length > 0) {
                final StringBuilder list = new StringBuilder();
                for (int i = 0; i < addresses.length && i < 4; i++) {
                    if (addresses[i] == null || addresses[i].isEmpty()) {
                        continue;
                    }
                    if (list.length() > 0) {
                        list.append(',');
                    }
                    list.append(addresses[i]);
                }
                if (list.length() > 0) {
                    out.append("&a=").append(encode(list.toString()));
                }
            }
        } else {
            out.append("&d=").append(encode(deviceName));
        }
        return out.toString();
    }

    public static LinkiDropPayload fromUri(String raw) {
        if (TextUtils.isEmpty(raw) || raw.length() > 2048) {
            return null;
        }
        final Uri uri;
        try {
            uri = Uri.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
        if (uri == null || !SCHEME.equals(uri.getScheme()) || !HOST.equals(uri.getHost())) {
            return null;
        }
        final String transport = uri.getQueryParameter("t");
        if (!TRANSPORT_WIFI.equals(transport) && !TRANSPORT_BLUETOOTH.equals(transport)) {
            return null;
        }
        final String name = uri.getQueryParameter("n");
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        final long size;
        try {
            size = Long.parseLong(uri.getQueryParameter("s") == null ? "-1" : uri.getQueryParameter("s"));
        } catch (NumberFormatException e) {
            return null;
        }
        if (size <= 0 || size > 2L * 1024 * 1024 * 1024) {
            return null;
        }
        final String sha = uri.getQueryParameter("x");
        if (sha == null || !sha.matches("[0-9a-f]{64}")) {
            return null;
        }
        final int port;
        try {
            port = Integer.parseInt(uri.getQueryParameter("p") == null ? "0" : uri.getQueryParameter("p"));
        } catch (NumberFormatException e) {
            return null;
        }
        if (TRANSPORT_WIFI.equals(transport) && (port <= 0 || port > 65535)) {
            return null;
        }
        final String addressList = uri.getQueryParameter("a");
        // Uri.getQueryParameter already URL-decodes, so '+' and '%' survive here.
        final String[] addresses = TextUtils.isEmpty(addressList)
                ? new String[0]
                : addressList.split(",");
        return new LinkiDropPayload(
                transport,
                uri.getQueryParameter("ssid"),
                uri.getQueryParameter("pw"),
                addresses,
                port,
                uri.getQueryParameter("k"),
                uri.getQueryParameter("d"),
                name,
                size,
                sha,
                uri.getQueryParameter("g"));
    }

    private static String encode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }
}