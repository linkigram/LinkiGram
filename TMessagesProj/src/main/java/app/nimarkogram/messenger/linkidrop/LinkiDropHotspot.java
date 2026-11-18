package app.nimarkogram.messenger.linkidrop;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.telegram.messenger.FileLog;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The temporary hotspot used by Linki Drop.
 *
 * Kept in its own class on purpose: {@code LocalOnlyHotspotReservation} only
 * exists from API 26, and the type must not sit in a field or a method
 * signature of a class that also loads on older releases.
 */
final class LinkiDropHotspot {

    private static final int START_TIMEOUT_SECONDS = 25;

    static final class Result {
        final String ssid;
        final String password;
        final Object reservation;

        Result(String ssid, String password, Object reservation) {
            this.ssid = ssid;
            this.password = password;
            this.reservation = reservation;
        }
    }

    private LinkiDropHotspot() {
    }

    /** Returns null when the platform refuses to start a hotspot. */
    static Result start(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null;
        }
        final Context app = context.getApplicationContext();
        final Object service = app.getSystemService(Context.WIFI_SERVICE);
        if (!(service instanceof WifiManager)) {
            return null;
        }
        final WifiManager manager = (WifiManager) service;
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<WifiManager.LocalOnlyHotspotReservation> ready = new AtomicReference<>();
        final AtomicInteger failure = new AtomicInteger();
        final WifiManager.LocalOnlyHotspotCallback callback = new WifiManager.LocalOnlyHotspotCallback() {
            @Override
            public void onStarted(WifiManager.LocalOnlyHotspotReservation value) {
                ready.set(value);
                latch.countDown();
            }

            @Override
            public void onStopped() {
                latch.countDown();
            }

            @Override
            public void onFailed(int reason) {
                failure.set(reason);
                latch.countDown();
            }
        };
        try {
            manager.startLocalOnlyHotspot(callback, new Handler(Looper.getMainLooper()));
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
        try {
            if (!latch.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                FileLog.e("linkidrop: hotspot start timed out");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        final WifiManager.LocalOnlyHotspotReservation reservation = ready.get();
        if (reservation == null) {
            FileLog.e("linkidrop: hotspot failed, reason=" + failure.get());
            return null;
        }
        String ssid = "";
        String password = "";
        final WifiConfiguration configuration = reservation.getWifiConfiguration();
        if (configuration != null) {
            ssid = unquote(configuration.SSID);
            password = unquote(configuration.preSharedKey);
        }
        if (ssid.isEmpty()) {
            close(reservation);
            return null;
        }
        return new Result(ssid, password, reservation);
    }

    static void close(Object reservation) {
        if (!(reservation instanceof WifiManager.LocalOnlyHotspotReservation)) {
            return;
        }
        try {
            ((WifiManager.LocalOnlyHotspotReservation) reservation).close();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static String unquote(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        if (result.length() >= 2 && result.startsWith("\"") && result.endsWith("\"")) {
            result = result.substring(1, result.length() - 1);
        }
        return result;
    }
}