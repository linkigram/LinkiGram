/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import org.json.JSONObject;
import org.telegram.messenger.utils.Choreographer60FpsContent;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.ForegroundDetector;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.IUpdateLayout;
import org.telegram.ui.LauncherIconController;

import java.io.File;
import java.util.Locale;

public class ApplicationLoader extends Application {

    public static ApplicationLoader applicationLoaderInstance;

    // Icon pack plumbing. Telegram resolves icons straight from the application
    // resources, so the only place a replacement pack can take effect is the
    // Application's getResources(): wrap the base Resources there and swap the
    // selection on top of it. Without this the pack selection is stored but never shown.
    private volatile app.nimarkogram.messenger.icons.NimarkoIconResources nmAppResources;
    private volatile android.content.res.AssetManager nmAppAssets;
    private static final ThreadLocal<Boolean> nmWrappingResources = new ThreadLocal<>();
    private final Object nmAppResourcesLock = new Object();

    @Override
    public android.content.res.Resources getResources() {
        android.content.res.Resources base = super.getResources();
        try {
            if (base == null || applicationContext == null
                    || app.nimarkogram.messenger.NimarkoConfig.iconReplacement == app.nimarkogram.messenger.NimarkoConfig.ICON_REPLACE_NONE) {
                return base;
            }
            // The wrapper asks the base Resources for drawables, which lands back here.
            if (Boolean.TRUE.equals(nmWrappingResources.get())) {
                return base;
            }
            if (nmAppAssets != base.getAssets()) {
                synchronized (nmAppResourcesLock) {
                    if (nmAppAssets != base.getAssets()) {
                        nmWrappingResources.set(Boolean.TRUE);
                        try {
                            nmAppResources = new app.nimarkogram.messenger.icons.NimarkoIconResources(base);
                        } finally {
                            nmWrappingResources.set(Boolean.FALSE);
                        }
                        nmInstallIntoLoadedApk(nmAppResources);
                        nmAppAssets = base.getAssets();
                    }
                }
            }
            return nmAppResources != null ? nmAppResources : base;
        } catch (Throwable t) {
            return base;
        }
    }

    /**
     * Overriding {@code Application.getResources()} is not enough on its own: every
     * ContextImpl copies its Resources out of LoadedApk when the component is created, so
     * Activities, Services and BroadcastReceivers keep the stock instance and the pack is
     * never seen by the UI. LoadedApk.mResources is the single field all of them read, so
     * it is replaced once here instead of patching each context.
     *
     * The Application's own ContextImpl already captured the stock Resources before this
     * runs; that is what {@link #getResources()} keeps unwrapping and handing to the
     * wrapper, so there is no recursion.
     *
     * A locale change rewrites LoadedApk.mResources through ResourcesManager, so this has
     * to be callable again afterwards; {@link #reloadAppIconResources()} does that.
     */
    private static void nmInstallIntoLoadedApk(android.content.res.Resources wrapper) {
        if (wrapper == null || applicationLoaderInstance == null) return;
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
            if (activityThread == null) return;
            java.lang.reflect.Field packagesField = activityThreadClass.getDeclaredField("mPackages");
            packagesField.setAccessible(true);
            Object packages = packagesField.get(activityThread);
            if (!(packages instanceof java.util.Map)) return;
            Object ref = ((java.util.Map<?, ?>) packages).get(applicationLoaderInstance.getPackageName());
            Object loadedApk = ref instanceof java.lang.ref.WeakReference ? ((java.lang.ref.WeakReference<?>) ref).get() : ref;
            if (loadedApk == null) return;
            java.lang.reflect.Field resourcesField = loadedApk.getClass().getDeclaredField("mResources");
            resourcesField.setAccessible(true);
            if (resourcesField.get(loadedApk) == wrapper) return;
            resourcesField.set(loadedApk, wrapper);
            FileLog.d("nimarko-icons: replacement resources installed into LoadedApk");
        } catch (Throwable t) {
            FileLog.e("nimarko-icons: LoadedApk install failed", t);
        }
    }

    /** Applies a new icon pack selection to the installed resources, if any. */
    public static void reloadAppIconResources() {
        try {
            ApplicationLoader inst = applicationLoaderInstance;
            if (inst != null && inst.nmAppResources != null) {
                inst.nmAppResources.reloadReplacements(null);
                // A language switch makes the framework rebuild LoadedApk.mResources, which
                // drops the wrapper and silently reverts the pack to stock.
                nmInstallIntoLoadedApk(inst.nmAppResources);
            }
        } catch (Throwable ignore) {}
    }
    private static final int NG_PINE_MAX_TESTED_SDK = 36;
    private static final String NG_PINE_RUNTIME_PREFS = "nimarko_pine_runtime";
    private static final String NG_PINE_INIT_SIGNATURE = "init_signature";
    private static final String NG_PINE_INIT_STARTED_AT = "init_started_at";
    private static final String NG_PINE_BLOCKED_SIGNATURE = "blocked_signature";
    private static final long NG_PINE_HOOK_WAIT_BUDGET_MS = 30_000L;
    private static volatile boolean ngPineRuntimeGuardInstalled = false;
    private static volatile boolean ngPineRecoveryChecked = false;
    private static volatile boolean ngPineBlockedByRecovery = false;
    private static volatile boolean ngPineInitAttempted = false;
    private static volatile boolean ngPineInited = false;
    private static volatile long ngPineHookWaitDeadline;
    private static volatile String ngPineUnavailableReason;
    public static final java.util.concurrent.CountDownLatch ngPineReady = new java.util.concurrent.CountDownLatch(1);

    private static SharedPreferences pineRuntimePreferences() {
        Context context = applicationContext;
        if (context == null) return null;
        try {
            return context.getSharedPreferences(NG_PINE_RUNTIME_PREFS, Context.MODE_PRIVATE);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String pineArtModuleIdentity(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return "platform";
        }

        String[] packageNames = {
                "com.google.android.art",
                "com.android.art",
                "com.android.runtime"
        };
        for (String packageName : packageNames) {
            try {
                PackageInfo info = context.getPackageManager().getPackageInfo(
                        packageName, android.content.pm.PackageManager.MATCH_APEX);
                if (info != null) {
                    long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                            ? info.getLongVersionCode() : info.versionCode;
                    return packageName + ":" + versionCode + ":"
                            + (info.versionName == null ? "" : info.versionName);
                }
            } catch (Throwable ignored) {
            }
        }
        return "unknown";
    }

    private static String pineRuntimeSignature() {
        Context context = applicationContext;
        long lastUpdateTime = 0L;
        if (context != null) {
            try {
                PackageInfo info = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0);
                if (info != null) lastUpdateTime = info.lastUpdateTime;
            } catch (Throwable ignored) {
            }
        }
        String fingerprint;
        try {
            fingerprint = Build.FINGERPRINT;
        } catch (Throwable ignored) {
            fingerprint = Build.VERSION.INCREMENTAL;
        }

        return Build.VERSION.SDK_INT + ":" + fingerprint + ":"
                + pineArtModuleIdentity(context) + ":" + lastUpdateTime;
    }

    private static synchronized void preparePineRecoveryGuard() {
        if (ngPineRecoveryChecked) return;
        SharedPreferences preferences = pineRuntimePreferences();
        if (preferences == null) {
            ngPineRecoveryChecked = true;
            return;
        }
        String signature = pineRuntimeSignature();
        String blockedSignature = preferences.getString(NG_PINE_BLOCKED_SIGNATURE, null);
        if (signature.equals(blockedSignature)) {
            ngPineBlockedByRecovery = true;
            ngPineUnavailableReason =
                    "disabled after a native Pine initialization crash in this build";
        } else {
            String initSignature = preferences.getString(NG_PINE_INIT_SIGNATURE, null);
            long initStartedAt = preferences.getLong(NG_PINE_INIT_STARTED_AT, 0L);
            boolean confirmedNativeInitCrash = signature.equals(initSignature)
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && app.nimarkogram.messenger.plugins.utils.NativeCrashHandler
                            .lastExitWasLoadCrashAfter(initStartedAt);
            SharedPreferences.Editor editor = preferences.edit()
                    .remove(NG_PINE_INIT_SIGNATURE)
                    .remove(NG_PINE_INIT_STARTED_AT);
            if (confirmedNativeInitCrash) {
                ngPineBlockedByRecovery = true;
                ngPineUnavailableReason =
                        "disabled after a native Pine initialization crash in this build";
                editor.putString(NG_PINE_BLOCKED_SIGNATURE, signature);
                FileLog.e("nimarko: Pine recovery guard blocked a confirmed native init crash loop");
            } else {

                editor.remove(NG_PINE_BLOCKED_SIGNATURE);
            }
            editor.commit();
        }
        ngPineRecoveryChecked = true;
    }

    private static void markPineInitializationStarted() {
        SharedPreferences preferences = pineRuntimePreferences();
        if (preferences == null) return;
        preferences.edit()
                .putString(NG_PINE_INIT_SIGNATURE, pineRuntimeSignature())
                .putLong(NG_PINE_INIT_STARTED_AT, System.currentTimeMillis())
                .commit();
    }

    private static void clearPineInitializationMarker(boolean initialized) {
        SharedPreferences preferences = pineRuntimePreferences();
        if (preferences == null) return;
        SharedPreferences.Editor editor = preferences.edit()
                .remove(NG_PINE_INIT_SIGNATURE)
                .remove(NG_PINE_INIT_STARTED_AT);
        if (initialized) editor.remove(NG_PINE_BLOCKED_SIGNATURE);
        editor.commit();
    }

    private static synchronized void installPineRuntimeGuardIfNeeded() {
        preparePineRecoveryGuard();
        boolean unsupportedRuntime = Build.VERSION.SDK_INT > NG_PINE_MAX_TESTED_SDK;
        if ((!unsupportedRuntime && !ngPineBlockedByRecovery)
                || ngPineRuntimeGuardInstalled) {
            return;
        }
        final String reason = ngPineBlockedByRecovery
                ? "Pine recovery guard is active for this build"
                : "Pine is not validated on Android SDK " + Build.VERSION.SDK_INT;
        try {
            top.canyie.pine.PineConfig.sdkLevel = Build.VERSION.SDK_INT;
            top.canyie.pine.PineConfig.disableHooks = true;
            top.canyie.pine.PineConfig.libLoader = () -> {
                throw new UnsupportedOperationException(reason);
            };
        } catch (Throwable t) {
            FileLog.e("nimarko: unable to install unsupported-runtime Pine guard", t);
        } finally {
            ngPineRuntimeGuardInstalled = true;
            ngPineInitAttempted = true;
            ngPineInited = false;
            ngPineUnavailableReason = reason;
            ngPineReady.countDown();
        }
    }

    public static synchronized void ensurePineInited() {
        installPineRuntimeGuardIfNeeded();
        if (ngPineInited || ngPineInitAttempted) return;
        ngPineInitAttempted = true;
        ngPineHookWaitDeadline =
                SystemClock.elapsedRealtime() + NG_PINE_HOOK_WAIT_BUDGET_MS;
        boolean pineReady = false;
        markPineInitializationStarted();
        try {

            boolean hiddenApiBypassReady = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    hiddenApiBypassReady =
                            org.lsposed.hiddenapibypass.HiddenApiBypass
                                    .addHiddenApiExemptions("L");
                } catch (Throwable bypassError) {
                    FileLog.w("nimarko: HiddenApiBypass preflight failed: " + bypassError);
                }
                if (!hiddenApiBypassReady) {
                    FileLog.w("nimarko: hidden APIs remain enforced on this runtime");
                }
            }

            top.canyie.pine.PineConfig.sdkLevel = android.os.Build.VERSION.SDK_INT;
            top.canyie.pine.PineConfig.debug = false;
            try {
                top.canyie.pine.PineConfig.debuggable = applicationContext != null
                        && (applicationContext.getApplicationInfo().flags
                            & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            } catch (Throwable ignore) { top.canyie.pine.PineConfig.debuggable = false; }
            top.canyie.pine.PineConfig.disableHooks = false;

            top.canyie.pine.PineConfig.disableHiddenApiPolicy = false;
            top.canyie.pine.PineConfig.disableHiddenApiPolicyForPlatformDomain = false;
            top.canyie.pine.Pine.ensureInitialized();
            if (!top.canyie.pine.Pine.isInitialized()) {
                ngPineUnavailableReason = "Pine native initialization did not complete";
                FileLog.w("nimarko: Pine native initialisation did not complete");
                return;
            }

            try {
                top.canyie.pine.Pine.setHookMode(top.canyie.pine.Pine.HookMode.REPLACEMENT);
            } catch (Throwable hm) {
                throw new IllegalStateException(
                        "Pine replacement hook mode is unavailable", hm);
            }
            if (top.canyie.pine.Pine.getHookMode()
                    != top.canyie.pine.Pine.HookMode.REPLACEMENT) {
                throw new IllegalStateException(
                        "Pine did not enter replacement hook mode");
            }

            if (!verifyPineRuntimeHook()) {
                throw new IllegalStateException("Pine runtime hook smoke test failed");
            }
            pineReady = top.canyie.pine.Pine.isInitialized();
            ngPineUnavailableReason = pineReady
                    ? null : "Pine reported an uninitialized backend";
            org.telegram.messenger.FileLog.d("nimarko: Pine initialised (lazy), sdkLevel="
                    + top.canyie.pine.PineConfig.sdkLevel
                    + ", initialised=" + pineReady
                    + ", hookMode=" + top.canyie.pine.Pine.getHookMode());
        } catch (Throwable t) {
            ngPineUnavailableReason = t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : ": " + t.getMessage());
            org.telegram.messenger.FileLog.e("nimarko: Pine init failed", t);
        } finally {

            ngPineInited = pineReady;
            if (!pineReady) {
                try {
                    top.canyie.pine.PineConfig.disableHooks = true;
                } catch (Throwable ignored) {
                }
            }
            clearPineInitializationMarker(pineReady);
            ngPineReady.countDown();
        }
    }

    @androidx.annotation.Keep
    private static int pineRuntimeStaticProbe(int value) {
        return value + 1;
    }

    @androidx.annotation.Keep
    private static final class PineRuntimeProbe {
        int constructorValue;

        @androidx.annotation.Keep
        PineRuntimeProbe() {
        }

        @androidx.annotation.Keep
        PineRuntimeProbe(int value) {
            constructorValue = value;
        }

        @androidx.annotation.Keep
        long mixedProbe(
                Object marker1, int a, long b,
                Object marker2, int c, long d,
                Object marker3, int e, long f,
                Object marker4, int g, long h,
                double tail) {
            if (marker1 != marker2 || marker1 != marker3
                    || marker1 != marker4) {
                return Long.MIN_VALUE;
            }
            return a + b + c + d + e + f + g + h + (long) tail;
        }
    }

    private static boolean verifyPineRuntimeHook() {
        top.canyie.pine.callback.MethodHook.Unhook staticUnhook = null;
        top.canyie.pine.callback.MethodHook.Unhook mixedUnhook = null;
        top.canyie.pine.callback.MethodHook.Unhook constructorUnhook = null;
        boolean passed = false;
        try {
            final int staticInput = 0x4E47;
            java.lang.reflect.Method staticProbe = ApplicationLoader.class
                    .getDeclaredMethod("pineRuntimeStaticProbe", int.class);
            staticProbe.setAccessible(true);
            java.util.concurrent.atomic.AtomicBoolean staticBefore =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            java.util.concurrent.atomic.AtomicBoolean staticAfter =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            top.canyie.pine.callback.MethodHook staticCallback =
                    new top.canyie.pine.callback.MethodHook() {
                        @Override
                        public void beforeCall(top.canyie.pine.Pine.CallFrame frame) {
                            boolean valid = frame != null
                                    && frame.thisObject == null
                                    && frame.args != null
                                    && frame.args.length == 1
                                    && frame.args[0] instanceof Integer;
                            staticBefore.set(valid);
                            if (valid) {
                                frame.args[0] = staticInput;
                            }
                        }

                        @Override
                        public void afterCall(top.canyie.pine.Pine.CallFrame frame) {
                            boolean valid = frame != null
                                    && frame.getResult() instanceof Integer
                                    && ((Integer) frame.getResult())
                                    == staticInput + 1;
                            staticAfter.set(valid);
                            if (valid) {
                                frame.setResult(staticInput + 2);
                            }
                        }
                    };
            staticUnhook = top.canyie.pine.Pine.hook(
                    staticProbe, staticCallback);
            Object staticResult = staticProbe.invoke(null, 1);

            Object marker = new Object();
            PineRuntimeProbe receiver = new PineRuntimeProbe();
            java.lang.reflect.Method mixedProbe = PineRuntimeProbe.class
                    .getDeclaredMethod(
                            "mixedProbe",
                            Object.class, int.class, long.class,
                            Object.class, int.class, long.class,
                            Object.class, int.class, long.class,
                            Object.class, int.class, long.class,
                            double.class);
            mixedProbe.setAccessible(true);
            java.util.concurrent.atomic.AtomicBoolean mixedBefore =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            java.util.concurrent.atomic.AtomicBoolean mixedAfter =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            top.canyie.pine.callback.MethodHook mixedCallback =
                    new top.canyie.pine.callback.MethodHook() {
                        @Override
                        public void beforeCall(top.canyie.pine.Pine.CallFrame frame) {
                            boolean valid = frame != null
                                    && frame.thisObject == receiver
                                    && frame.args != null
                                    && frame.args.length == 13
                                    && frame.args[0] == marker
                                    && frame.args[3] == marker
                                    && frame.args[6] == marker
                                    && frame.args[9] == marker;
                            mixedBefore.set(valid);
                            if (valid) {
                                frame.args[1] = 7;
                            }
                        }

                        @Override
                        public void afterCall(top.canyie.pine.Pine.CallFrame frame) {
                            boolean valid = frame != null
                                    && frame.thisObject == receiver
                                    && frame.getResult() instanceof Long
                                    && ((Long) frame.getResult()) == 51L;
                            mixedAfter.set(valid);
                            if (valid) {
                                frame.setResult(52L);
                            }
                        }
                    };
            mixedUnhook = top.canyie.pine.Pine.hook(
                    mixedProbe, mixedCallback);
            Object mixedResult = mixedProbe.invoke(
                    receiver,
                    marker, 1, 2L,
                    marker, 3, 4L,
                    marker, 5, 6L,
                    marker, 7, 8L,
                    9.0d);

            java.lang.reflect.Constructor<PineRuntimeProbe> constructor =
                    PineRuntimeProbe.class.getDeclaredConstructor(int.class);
            constructor.setAccessible(true);
            java.util.concurrent.atomic.AtomicBoolean constructorBefore =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            java.util.concurrent.atomic.AtomicBoolean constructorAfter =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            top.canyie.pine.callback.MethodHook constructorCallback =
                    new top.canyie.pine.callback.MethodHook() {
                        @Override
                        public void beforeCall(top.canyie.pine.Pine.CallFrame frame) {
                            boolean valid = frame != null
                                    && frame.thisObject instanceof PineRuntimeProbe
                                    && frame.args != null
                                    && frame.args.length == 1;
                            constructorBefore.set(valid);
                            if (valid) {
                                frame.args[0] = 73;
                            }
                        }

                        @Override
                        public void afterCall(top.canyie.pine.Pine.CallFrame frame) {
                            boolean valid = frame != null
                                    && frame.thisObject instanceof PineRuntimeProbe
                                    && ((PineRuntimeProbe) frame.thisObject)
                                    .constructorValue == 73;
                            constructorAfter.set(valid);
                        }
                    };
            constructorUnhook = top.canyie.pine.Pine.hook(
                    constructor, constructorCallback);
            PineRuntimeProbe constructed = constructor.newInstance(1);

            passed = staticBefore.get()
                    && staticAfter.get()
                    && staticResult instanceof Integer
                    && ((Integer) staticResult) == staticInput + 2
                    && mixedBefore.get()
                    && mixedAfter.get()
                    && mixedResult instanceof Long
                    && ((Long) mixedResult) == 52L
                    && constructorBefore.get()
                    && constructorAfter.get()
                    && constructed.constructorValue == 73;
        } catch (Throwable t) {
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException
                    && t.getCause() != null ? t.getCause() : t;
            FileLog.e("nimarko: Pine runtime hook smoke test failed", cause);
        } finally {
            passed &= unhookPineRuntimeProbe(
                    constructorUnhook, "constructor");
            passed &= unhookPineRuntimeProbe(mixedUnhook, "mixed");
            passed &= unhookPineRuntimeProbe(staticUnhook, "static");
        }
        return passed;
    }

    private static boolean unhookPineRuntimeProbe(
            top.canyie.pine.callback.MethodHook.Unhook unhook,
            String name) {
        if (unhook == null) return true;
        try {
            unhook.unhook();
            return true;
        } catch (Throwable t) {
            FileLog.e("nimarko: Pine " + name
                    + " smoke-test unhook failed", t);
            return false;
        }
    }

    public static boolean isPineAvailable() {
        return ngPineInited;
    }

    public static boolean wasPineInitializationAttempted() {
        return ngPineInitAttempted;
    }

    public static String getPineUnavailableReason() {
        return ngPineUnavailableReason;
    }

    public static boolean awaitPineInitializationForHooks() {
        if (ngPineInited || ngPineReady.getCount() == 0L) {
            return ngPineInited;
        }
        long remaining = ngPineHookWaitDeadline - SystemClock.elapsedRealtime();
        if (remaining <= 0L) {
            ngPineUnavailableReason = "Pine initialization exceeded 30 seconds";
            return false;
        }
        try {
            ngPineReady.await(
                    remaining, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ngPineUnavailableReason = "interrupted while waiting for Pine initialization";
        }
        return ngPineInited;
    }

    public static void ngAwaitPine() { ensurePineInited(); }

    android.content.res.Resources nmRawResources() {
        return super.getResources();
    }

    /**
     * The unwrapped, stock resources. Icon pack code needs the originals: going through
     * {@code applicationContext.getResources()} would hand back the replacement wrapper and
     * the mapping would be applied twice.
     */
    public static android.content.res.Resources rawResources() {
        try {
            ApplicationLoader inst = applicationLoaderInstance;
            if (inst != null) {
                return inst.nmRawResources();
            }
        } catch (Throwable ignore) {
        }
        return applicationContext == null ? null : applicationContext.getResources();
    }


    @SuppressLint("StaticFieldLeak")
    public static volatile Context applicationContext;
    public static volatile NetworkInfo currentNetworkInfo;
    public static volatile Handler applicationHandler;

    private static ConnectivityManager connectivityManager;
    private static volatile boolean applicationInited = false;
    private static volatile  ConnectivityManager.NetworkCallback networkCallback;
    private static long lastNetworkCheckTypeTime;
    private static int lastKnownNetworkType = -1;

    public static long startTime;

    public static volatile boolean isScreenOn = false;
    public static volatile boolean mainInterfacePaused = true;
    public static volatile boolean mainInterfaceStopped = true;
    public static volatile boolean externalInterfacePaused = true;
    public static volatile boolean mainInterfacePausedStageQueue = true;
    public static boolean canDrawOverlays;
    public static volatile long mainInterfacePausedStageQueueTime;

    private static PushListenerController.IPushListenerServiceProvider pushProvider;
    private static IMapsProvider mapsProvider;
    private static ILocationServiceProvider locationServiceProvider;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }

    public static ILocationServiceProvider getLocationServiceProvider() {
        if (locationServiceProvider == null) {
            locationServiceProvider = applicationLoaderInstance.onCreateLocationServiceProvider();
            locationServiceProvider.init(applicationContext);
        }
        return locationServiceProvider;
    }

    protected ILocationServiceProvider onCreateLocationServiceProvider() {
        return new GoogleLocationProvider();
    }

    public static IMapsProvider getMapsProvider() {
        if (mapsProvider == null) {
            mapsProvider = applicationLoaderInstance.onCreateMapsProvider();
        }
        return mapsProvider;
    }

    protected IMapsProvider onCreateMapsProvider() {
        return new GoogleMapsProvider();
    }

    public static PushListenerController.IPushListenerServiceProvider getPushProvider() {
        if (pushProvider == null) {
            pushProvider = applicationLoaderInstance.onCreatePushProvider();
        }
        return pushProvider;
    }

    protected PushListenerController.IPushListenerServiceProvider onCreatePushProvider() {
        return PushListenerController.GooglePushListenerServiceProvider.INSTANCE;
    }

    public static String getApplicationId() {
        return applicationLoaderInstance.onGetApplicationId();
    }

    protected String onGetApplicationId() {
        return null;
    }

    public static boolean isHuaweiStoreBuild() {
        return applicationLoaderInstance.isHuaweiBuild();
    }

    public static boolean isStandaloneBuild() {
        return applicationLoaderInstance.isStandalone();
    }

    public static boolean isBetaBuild() {
        return applicationLoaderInstance.isBeta();
    }

    public static boolean isAndroidTestEnvironment() {
        return applicationLoaderInstance.isAndroidTestEnv();
    }

    protected boolean isHuaweiBuild() {
        return false;
    }

    protected boolean isStandalone() {
        return false;
    }

    protected boolean isBeta() {
        return false;
    }

    protected boolean isAndroidTestEnv() {
        return false;
    }

    public static File getFilesDirFixed() {
        for (int a = 0; a < 10; a++) {
            File path = ApplicationLoader.applicationContext.getFilesDir();
            if (path != null) {
                return path;
            }
        }
        try {
            ApplicationInfo info = applicationContext.getApplicationInfo();
            File path = new File(info.dataDir, "files");
            path.mkdirs();
            return path;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new File("/data/data/org.telegram.messenger/files");
    }

    public static File getFilesDirFixed(String child) {
        try {
            File path = getFilesDirFixed();
            File dir = new File(path, child);
            dir.mkdirs();

            return dir;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static void postInitApplication() {
        if (applicationInited || applicationContext == null) {
            return;
        }
        applicationInited = true;
        NativeLoader.initNativeLibs(ApplicationLoader.applicationContext);

        try {
            LocaleController.getInstance(); //TODO improve
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            connectivityManager = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            BroadcastReceiver networkStateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        currentNetworkInfo = connectivityManager.getActiveNetworkInfo();
                    } catch (Throwable ignore) {

                    }

                    boolean isSlow = isConnectionSlow();
                    for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                        ConnectionsManager.getInstance(a).checkConnection();
                        FileLoader.getInstance(a).onNetworkChanged(isSlow);
                    }
                }
            };
            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            ApplicationLoader.applicationContext.registerReceiver(networkStateReceiver, filter);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            final IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            final BroadcastReceiver mReceiver = new ScreenReceiver();
            applicationContext.registerReceiver(mReceiver, filter);
        } catch (Exception e) {
            e.printStackTrace();
        }

        LiteMode.registerBatteryReceiver();

        try {
            PowerManager pm = (PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
            isScreenOn = pm.isScreenOn();
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("screen state = " + isScreenOn);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        SharedConfig.loadConfig();
        SharedPrefsHelper.init(applicationContext);

        // LinkiGram bypass relay startup.
        //
        // This wiring was dropped when the client was moved to 12.10.2, which left
        // ensureStartedSync()/onAppResume() with no caller: the relay only ever came
        // up when a VPN happened to toggle, and never on a plain app start. The
        // controller is the only thing that can arm the local SOCKS listener, so
        // without these calls "Bypass" stays off with no error anywhere.
        try { app.nimarkogram.messenger.wsbypass.NimarkoVpnDetector.start(); } catch (Throwable ignored) {}
        try { app.nimarkogram.messenger.wsbypass.NimarkoWsBypassController.getInstance().ensureStartedSync(); } catch (Throwable ignored) {}

        // Re-arm the sleep timer. AlarmManager does not survive a reboot, so a
        // timer set before one would otherwise never fire and the row would keep
        // claiming it is scheduled. apply() is a no-op when no timer is set.
        try { app.nimarkogram.messenger.utils.NimarkoSleepTimer.apply(applicationContext); } catch (Throwable ignored) {}

        // Warm the relay auth tokens ahead of first use, but only when the matching
        // transport is actually enabled.
        try {
            if (app.nimarkogram.messenger.wsbypass.NimarkoWsBypassConfig.enabled
                    && app.nimarkogram.messenger.wsbypass.voip.VoipBypassConfig.isVoipBypassEnabled()) {
                AndroidUtilities.runOnUIThread(() ->
                        app.nimarkogram.messenger.wsbypass.voip.VoipRelayAuth.prefetchAsync(UserConfig.selectedAccount), 4000);
            }
        } catch (Throwable ignored) {}
        try {
            if (app.nimarkogram.messenger.wsbypass.NimarkoWsBypassConfig.enabled) {
                AndroidUtilities.runOnUIThread(() ->
                        app.nimarkogram.messenger.wsbypass.WsRelayAuth.prefetchAsync(UserConfig.selectedAccount), 4500);
            }
        } catch (Throwable ignored) {}

        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) { //TODO improve account
            UserConfig.getInstance(a).loadConfig();
            MessagesController.getInstance(a);
            if (a == 0) {
                SharedConfig.pushStringStatus = "__FIREBASE_GENERATING_SINCE_" + ConnectionsManager.getInstance(a).getCurrentTime() + "__";
            } else {
                ConnectionsManager.getInstance(a);
            }
            TLRPC.User user = UserConfig.getInstance(a).getCurrentUser();
            if (user != null) {
                MessagesController.getInstance(a).putUser(user, true);
                SendMessagesHelper.getInstance(a).checkUnsentMessages();
            }
        }

        initPluginEngine();

        // A VPN and the bypass relay both bind local listeners; let the VPN win
        // instead of leaving a stale 127.0.0.1 entry that would break the next
        // connection attempt.
        try {
            if (app.nimarkogram.messenger.wsbypass.NimarkoWsBypassController.getInstance().blockedByVpn()) {
                app.nimarkogram.messenger.wsbypass.ProxyApplier.suspendForVpn(
                        app.nimarkogram.messenger.wsbypass.WsBypassCore.LOCAL_PROXY_HOST);
            }
        } catch (Throwable ignored) {}

        ApplicationLoader loader = (ApplicationLoader) ApplicationLoader.applicationContext;
        loader.initPushServices();
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("app initied");
        }

        MediaController.getInstance();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) { //TODO improve account
            ContactsController.getInstance(a).checkAppAccount();
            DownloadController.getInstance(a);
        }
        BillingController.getInstance().startConnection();
        // HTML widgets: the isolated engine's process up front, after the app's own start, so that a widget's first
        // frame does not wait for it.
        AndroidUtilities.runOnUIThread(() -> Utilities.globalQueue.postRunnable(
                () -> org.telegram.utils.twidget.WidgetSandboxSession.prewarmEngine(applicationContext)), 3000);
        app.nimarkogram.messenger.automation.RepeatingMessages.restore();
    }

    private static void initPluginEngine() {
        // The restart helper runs in a separate process; plugins belong only to
        // the main application process. Account state is ready at this point.
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            String process = android.app.Application.getProcessName();
            if (process != null && !applicationContext.getPackageName().equals(process)) {
                return;
            }
        }
        try {
            app.nimarkogram.messenger.plugins.PluginsController.getInstance().init();
        } catch (Throwable failure) {
            android.util.Log.e("LinkiPluginEngine", "Unable to start plugin engine", failure);
        }
    }

    public ApplicationLoader() {
        super();
    }

    @Override
    public void onCreate() {
        applicationLoaderInstance = this;
        try {
            applicationContext = getApplicationContext();
        } catch (Throwable ignore) {

        }

        super.onCreate();

        // The widget engine's isolated process has no files, network or accounts: none of the client starts there.
        if (org.telegram.utils.twidget.WidgetEngineService.isEngineProcess()) {
            return;
        }

        // Collect the previous process exit diagnostics (native crash report
        // and legacy marker) off the main thread, once per process.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                app.nimarkogram.messenger.plugins.utils.NativeCrashHandler
                        .schedulePreviousExitDiagnostics();
            }
        } catch (Throwable t) {
            FileLog.e("nimarko: unable to schedule previous-exit diagnostics", t);
        }

        try {
            app.nimarkogram.messenger.textanim.NimarkoTextAnim.initIfEnabled();
        } catch (Throwable t) {
            FileLog.e("nimarko-textanim: init failed", t);
        }

        // Install the icon pack before any component exists, otherwise the framework builds
        // the first Activity on the stock Resources and it never sees the replacement.
        try {
            if (app.nimarkogram.messenger.NimarkoConfig.iconReplacement != app.nimarkogram.messenger.NimarkoConfig.ICON_REPLACE_NONE) {
                getResources();
            }
        } catch (Throwable t) {
            FileLog.e("nimarko-icons: eager install failed", t);
        }

        // AndroidUtilities must be initialized before FileLog
        final String helloWorld = AndroidUtilities.getHelloWorld();

        if (BuildVars.LOGS_ENABLED) {
            FileLog.d(helloWorld);
            FileLog.d("app start time = " + (startTime = SystemClock.elapsedRealtime()));
            try {
                final PackageInfo info = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                final String abi;
                switch (info.versionCode % 10) {
                    case 1:
                    case 2:
                        abi = "store bundled " + Build.CPU_ABI + " " + Build.CPU_ABI2;
                        break;
                    default:
                    case 9:
                        if (ApplicationLoader.isStandaloneBuild()) {
                            abi = "direct " + Build.CPU_ABI + " " + Build.CPU_ABI2;
                        } else {
                            abi = "universal " + Build.CPU_ABI + " " + Build.CPU_ABI2;
                        }
                        break;
                }
                FileLog.d("buildVersion = " + String.format(Locale.US, "v%s (%d[%d]) %s", info.versionName, info.versionCode / 10, info.versionCode % 10, abi));
            } catch (Exception e) {
                FileLog.e(e);
            }
            FileLog.d("device = manufacturer=" + Build.MANUFACTURER + ", device=" + Build.DEVICE + ", model=" + Build.MODEL + ", product=" + Build.PRODUCT);
        }
        if (applicationContext == null) {
            applicationContext = getApplicationContext();
        }

        NativeLoader.initNativeLibs(ApplicationLoader.applicationContext);

        try {
            ConnectionsManager.native_setJava(false);
        } catch (UnsatisfiedLinkError error) {
            throw new RuntimeException("can't load native libraries " +  Build.CPU_ABI + " lookup folder " + NativeLoader.getAbiFolder());
        }
        new ForegroundDetector(this) {
            @Override
            public void onActivityStarted(Activity activity) {
                boolean wasInBackground = isBackground();
                super.onActivityStarted(activity);
                if (wasInBackground) {
                    ensureCurrentNetworkGet(true);
                }
            }
        };
        if (BuildConfig.DEBUG_VERSION) {
            new ANRDetector(FileLog::dumpANR);
        }

        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("load libs time = " + (SystemClock.elapsedRealtime() - startTime));
        }

        applicationHandler = new Handler(applicationContext.getMainLooper());

        AndroidUtilities.runOnUIThread(ApplicationLoader::startPushService);

        LauncherIconController.tryFixLauncherIconIfNeeded();
        ProxyRotationController.init();

        //if (BuildConfig.DEBUG_PRIVATE_VERSION) {
        //    Choreographer60FpsContent.getInstance().addFrameCallback(debugEverySecondChecks, 1);
        //}
    }

    private final Runnable debugEverySecondChecks = () -> AndroidUtilities.runOnUIThread(() -> {
        NotificationCenter.sanitize();
    });

    public static void startPushService() {
        SharedPreferences preferences = MessagesController.getGlobalNotificationsSettings();
        boolean enabled;
        if (preferences.contains("pushService")) {
            enabled = preferences.getBoolean("pushService", true);
        } else {
            enabled = MessagesController.getMainSettings(UserConfig.selectedAccount).getBoolean("keepAliveService", false);
        }
        if (enabled) {
            try {
                applicationContext.startService(new Intent(applicationContext, NotificationsService.class));
            } catch (Throwable ignore) {

            }
        } else {
            applicationContext.stopService(new Intent(applicationContext, NotificationsService.class));
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        try {
            LocaleController.getInstance().onDeviceConfigurationChange(newConfig);
            AndroidUtilities.checkDisplaySize(applicationContext, newConfig);
            VideoCapturerDevice.checkScreenCapturerSize();
            AndroidUtilities.resetTabletFlag();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initPushServices() {
        AndroidUtilities.runOnUIThread(() -> {
            if (getPushProvider().hasServices()) {
                getPushProvider().onRequestPushToken();
            } else {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("No valid " + getPushProvider().getLogTitle() + " APK found.");
                }
                SharedConfig.pushStringStatus = "__NO_GOOGLE_PLAY_SERVICES__";
                PushListenerController.sendRegistrationToServer(getPushProvider().getPushType(), null);
            }
        }, 1000);
    }

    private boolean checkPlayServices() {
        try {
            int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
            return resultCode == ConnectionResult.SUCCESS;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return true;
    }

    private static long lastNetworkCheck = -1;
    private static void ensureCurrentNetworkGet() {
        final long now = System.currentTimeMillis();
        ensureCurrentNetworkGet(now - lastNetworkCheck > 5000);
        lastNetworkCheck = now;
    }

    private static void ensureCurrentNetworkGet(boolean force) {
        if (force || currentNetworkInfo == null) {
            try {
                if (connectivityManager == null) {
                    connectivityManager = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                }
                currentNetworkInfo = connectivityManager.getActiveNetworkInfo();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if (networkCallback == null) {
                        networkCallback = new ConnectivityManager.NetworkCallback() {
                            @Override
                            public void onAvailable(@NonNull Network network) {
                                lastKnownNetworkType = -1;
                            }

                            @Override
                            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                                lastKnownNetworkType = -1;
                            }
                        };
                        connectivityManager.registerDefaultNetworkCallback(networkCallback);
                    }
                }
            } catch (Throwable ignore) {

            }
        }
    }

    public static boolean isRoaming() {
        try {
            ensureCurrentNetworkGet(false);
            return currentNetworkInfo != null && currentNetworkInfo.isRoaming();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public static boolean isConnectedOrConnectingToWiFi() {
        try {
            ensureCurrentNetworkGet(false);
            if (currentNetworkInfo != null && (currentNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI || currentNetworkInfo.getType() == ConnectivityManager.TYPE_ETHERNET)) {
                NetworkInfo.State state = currentNetworkInfo.getState();
                if (state == NetworkInfo.State.CONNECTED || state == NetworkInfo.State.CONNECTING || state == NetworkInfo.State.SUSPENDED) {
                    return true;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public static boolean isConnectedToWiFi() {
        try {
            ensureCurrentNetworkGet(false);
            if (currentNetworkInfo != null && (currentNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI || currentNetworkInfo.getType() == ConnectivityManager.TYPE_ETHERNET) && currentNetworkInfo.getState() == NetworkInfo.State.CONNECTED) {
                return true;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public static boolean isConnectionSlow() {
        try {
            ensureCurrentNetworkGet(false);
            if (currentNetworkInfo != null && currentNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                switch (currentNetworkInfo.getSubtype()) {
                    case TelephonyManager.NETWORK_TYPE_1xRTT:
                    case TelephonyManager.NETWORK_TYPE_CDMA:
                    case TelephonyManager.NETWORK_TYPE_EDGE:
                    case TelephonyManager.NETWORK_TYPE_GPRS:
                    case TelephonyManager.NETWORK_TYPE_IDEN:
                        return true;
                }
            }
        } catch (Throwable ignore) {

        }
        return false;
    }

    public static int getAutodownloadNetworkType() {
        try {
            ensureCurrentNetworkGet(false);
            if (currentNetworkInfo == null) {
                return StatsController.TYPE_MOBILE;
            }
            if (currentNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI || currentNetworkInfo.getType() == ConnectivityManager.TYPE_ETHERNET) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && (lastKnownNetworkType == StatsController.TYPE_MOBILE || lastKnownNetworkType == StatsController.TYPE_WIFI) && System.currentTimeMillis() - lastNetworkCheckTypeTime < 5000) {
                    return lastKnownNetworkType;
                }
                if (connectivityManager.isActiveNetworkMetered()) {
                    lastKnownNetworkType = StatsController.TYPE_MOBILE;
                } else {
                    lastKnownNetworkType = StatsController.TYPE_WIFI;
                }
                lastNetworkCheckTypeTime = System.currentTimeMillis();
                return lastKnownNetworkType;
            }
            if (currentNetworkInfo.isRoaming()) {
                return StatsController.TYPE_ROAMING;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return StatsController.TYPE_MOBILE;
    }

    public static int getCurrentNetworkType() {
        if (isConnectedOrConnectingToWiFi()) {
            return StatsController.TYPE_WIFI;
        } else if (isRoaming()) {
            return StatsController.TYPE_ROAMING;
        } else {
            return StatsController.TYPE_MOBILE;
        }
    }

    public static boolean isNetworkOnlineFast() {
        try {
            ensureCurrentNetworkGet(false);
            if (currentNetworkInfo == null) {
                return true;
            }
            if (currentNetworkInfo.isConnectedOrConnecting() || currentNetworkInfo.isAvailable()) {
                return true;
            }

            NetworkInfo netInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                return true;
            } else {
                netInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                    return true;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            return true;
        }
        return false;
    }

    public static boolean isNetworkOnlineRealtime() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
            if (netInfo != null && (netInfo.isConnectedOrConnecting() || netInfo.isAvailable())) {
                return true;
            }

            netInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

            if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                return true;
            } else {
                netInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                    return true;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            return true;
        }
        return false;
    }

    public static boolean isNetworkOnline() {
        boolean result = isNetworkOnlineRealtime();
        if (BuildVars.DEBUG_PRIVATE_VERSION) {
            boolean result2 = isNetworkOnlineFast();
            if (result != result2) {
                FileLog.d("network online mismatch");
            }
        }
        return result;
    }

    public static void startAppCenter(Activity context) {
        applicationLoaderInstance.startAppCenterInternal(context);
    }

    public static void checkForUpdates() {
        applicationLoaderInstance.checkForUpdatesInternal();
    }

    public static void appCenterLog(Throwable e) {
        applicationLoaderInstance.appCenterLogInternal(e);
    }

    protected void appCenterLogInternal(Throwable e) {

    }

    protected void checkForUpdatesInternal() {

    }

    protected void startAppCenterInternal(Activity context) {

    }

    public static void logDualCamera(boolean success, boolean vendor) {
        applicationLoaderInstance.logDualCameraInternal(success, vendor);
    }

    protected void logDualCameraInternal(boolean success, boolean vendor) {

    }

    public boolean checkApkInstallPermissions(final Context context) {
        return false;
    }

    public boolean openApkInstall(Activity activity, TLRPC.Document document) {
        return false;
    }

    public boolean showUpdateAppPopup(Context context, TLRPC.TL_help_appUpdate update, int account) {
        return false;
    }

    public boolean showCustomUpdateAppPopup(Context context, BetaUpdate update, int account) {
        return false;
    }

    public IUpdateLayout takeUpdateLayout(Activity activity, ViewGroup sideMenuContainer) {
        return null;
    }

    public TLRPC.Update parseTLUpdate(int constructor) {
        return null;
    }

    public void processUpdate(int currentAccount, TLRPC.Update update) {

    }

    public boolean onSuggestionFill(String suggestion, CharSequence[] output, boolean[] closeable) {
        return false;
    }

    public boolean onSuggestionClick(String suggestion) {
        return false;
    }

    public void addItemOptions(ItemOptions itemOptions) {

    }

    public boolean checkRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        return false;
    }

    public boolean consumePush(int account, JSONObject json) {
        return false;
    }

    public void onResume() {

    }

    public boolean onPause() {
        return false;
    }

    public BaseFragment openSettings(int n) {
        return null;
    }

    public boolean isCustomUpdate() {
        return false;
    }
    public void downloadUpdate() {}
    public void cancelDownloadingUpdate() {}
    public boolean isDownloadingUpdate() {
        return false;
    }
    public float getDownloadingUpdateProgress() {
        return 0.0f;
    }
    public void checkUpdate(boolean force, Runnable whenDone) {}
    public BetaUpdate getUpdate() {
        return null;
    }
    public File getDownloadedUpdateFile() {
        return null;
    }
}
