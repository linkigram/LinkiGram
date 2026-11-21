/**
 * This file is part of LinkiGram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * LinkiGram modifications:
 * Copyright Ettacent, 2026.
 */

package app.nimarkogram.messenger.camera;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.SizeF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lists the separate rear cameras the stock (Camera1) backend can open, so the
 * attach camera can offer a 0.6x / 1x / 2x module switcher.
 * <p>
 * The CameraX backend swaps physical lenses through a zoom ratio on one logical
 * camera, but Camera1 opens physical cameras one by one. The focal lengths still
 * come from Camera2 characteristics, which are readable next to an open Camera1
 * device, and they decide both the order (widest first) and the label.
 */
public final class CameraModuleSelector {

    /** One openable rear camera plus the way a camera app would label it. */
    public static final class Module {
        public final int cameraId;
        public final float focalLength;
        public final float factor;

        Module(int cameraId, float focalLength, float factor) {
            this.cameraId = cameraId;
            this.focalLength = focalLength;
            this.factor = factor;
        }
    }

    private static final float MIN_FACTOR = 0.45f;
    private static final float MAX_FACTOR = 3.2f;
    private static final float MIN_FACTOR_GAP = 0.12f;

    private CameraModuleSelector() {
    }

    /**
     * Rear cameras of this device, widest first.
     *
     * @return an empty list when the device exposes a single rear camera or the
     * camera service refuses to describe itself, so callers hide the switcher.
     */
    @NonNull
    public static List<Module> rearModules(@Nullable Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return Collections.emptyList();
        }
        CameraManager manager;
        try {
            manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
        if (manager == null) {
            return Collections.emptyList();
        }

        final ArrayList<int[]> ids = new ArrayList<>();
        final ArrayList<Float> focals = new ArrayList<>();
        final ArrayList<Float> sensorAreas = new ArrayList<>();
        try {
            String[] cameraIds = manager.getCameraIdList();
            if (cameraIds == null) {
                return Collections.emptyList();
            }
            for (String id : cameraIds) {
                CameraCharacteristics characteristics;
                try {
                    characteristics = manager.getCameraCharacteristics(id);
                } catch (Throwable t) {
                    continue;
                }
                if (characteristics == null) continue;
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;
                float focal = primaryFocal(characteristics);
                if (focal <= 0f) continue;
                float area = sensorArea(characteristics);
                int cameraId;
                try {
                    cameraId = Integer.parseInt(id);
                } catch (NumberFormatException e) {
                    continue;
                }
                ids.add(new int[]{cameraId});
                focals.add(focal);
                sensorAreas.add(area);
            }
        } catch (Throwable t) {
            FileLog.e(t);
            return Collections.emptyList();
        }

        if (ids.size() <= 1) {
            return Collections.emptyList();
        }

        // The 1x anchor is the biggest sensor: that is the main camera on every
        // phone this runs on, and it keeps an ultra-wide at a factor below one.
        int anchor = 0;
        float biggest = -1f;
        for (int a = 0; a < ids.size(); a++) {
            if (sensorAreas.get(a) > biggest) {
                biggest = sensorAreas.get(a);
                anchor = a;
            }
        }
        float anchorFocal = focals.get(anchor);
        if (anchorFocal <= 0f) {
            return Collections.emptyList();
        }

        ArrayList<Module> modules = new ArrayList<>(ids.size());
        for (int a = 0; a < ids.size(); a++) {
            float focal = focals.get(a);
            float factor = focal / anchorFocal;
            if (factor < MIN_FACTOR || factor > MAX_FACTOR) continue;
            int cameraId = ids.get(a)[0];
            boolean duplicate = false;
            for (int b = 0; b < modules.size(); b++) {
                if (Math.abs(modules.get(b).factor - factor) < MIN_FACTOR_GAP) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) continue;
            modules.add(new Module(cameraId, focal, factor));
        }

        if (modules.size() <= 1) {
            return Collections.emptyList();
        }
        Collections.sort(modules, (first, second) -> Float.compare(first.factor, second.factor));
        return modules;
    }

    /** The module at the given camera id, or the closest one to 1x. */
    @NonNull
    public static Module current(@NonNull List<Module> modules, int cameraId) {
        for (Module module : modules) {
            if (module.cameraId == cameraId) {
                return module;
            }
        }
        Module best = modules.get(0);
        for (Module module : modules) {
            if (Math.abs(module.factor - 1f) < Math.abs(best.factor - 1f)) {
                best = module;
            }
        }
        return best;
    }

    /** The module after the one in use, wrapping around, for a tap-to-cycle button. */
    @NonNull
    public static Module next(@NonNull List<Module> modules, int cameraId) {
        int index = -1;
        for (int a = 0; a < modules.size(); a++) {
            if (modules.get(a).cameraId == cameraId) {
                index = a;
                break;
            }
        }
        if (index < 0) {
            // No explicit choice yet: the stock preview sits on the main lens, so
            // cycling starts after the module closest to 1x, not at the widest.
            index = modules.indexOf(current(modules, cameraId));
        }
        return modules.get((index + 1) % modules.size());
    }

    /** Formats a factor the way the camera UI labels it, e.g. 0.6x / 1x / 2x. */
    @NonNull
    public static String labelFor(float factor) {
        return CameraLensSelector.labelFor(factor);
    }

    private static float primaryFocal(@NonNull CameraCharacteristics characteristics) {
        float[] focals = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        if (focals == null || focals.length == 0) {
            return 0f;
        }
        float smallest = focals[0];
        for (float focal : focals) {
            if (focal < smallest) {
                smallest = focal;
            }
        }
        return smallest;
    }

    private static float sensorArea(@NonNull CameraCharacteristics characteristics) {
        SizeF physical;
        try {
            physical = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        } catch (Throwable t) {
            return 0f;
        }
        if (physical == null) {
            return 0f;
        }
        return physical.getWidth() * physical.getHeight();
    }
}