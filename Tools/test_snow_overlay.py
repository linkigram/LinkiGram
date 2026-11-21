#!/usr/bin/env python3
"""Source-contract checks for the app-wide snow overlay (no Android SDK)."""

import re
import sys

OVERLAY = "TMessagesProj/src/main/java/app/nimarkogram/messenger/appearance/SnowOverlay.java"
CONFIG = "TMessagesProj/src/main/java/app/nimarkogram/messenger/NimarkoConfig.java"
APPEARANCE = "TMessagesProj/src/main/java/app/nimarkogram/messenger/preferences/AppearancePreferencesActivity.java"
LAUNCH = "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java"

failures = []


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def check(name, condition, detail=""):
    if condition:
        print("PASS", name)
    else:
        print("FAIL", name, detail)
        failures.append(name)


overlay = read(OVERLAY)
config = read(CONFIG)
appearance = read(APPEARANCE)
launch = read(LAUNCH)

check(
    "config key exists with disabled default",
    'getBoolean("drawSnowEverywhere", false)' in config,
)
check(
    "config toggle persists the same key",
    'putBoolean("drawSnowEverywhere", drawSnowEverywhere)' in config,
)
check(
    "overlay registers activity callbacks once",
    overlay.count("registerActivityLifecycleCallbacks(this)") == 1
    and "if (registered) {" in overlay,
)
check(
    "overlay is skipped in power saver and without canvas support",
    "LiteMode.isPowerSaverApplied()" in overlay
    and "LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND)" in overlay,
)
check(
    "overlay is removed when the activity pauses",
    re.search(r"public void onActivityPaused\(Activity activity\) \{.*?detach\(activity\);", overlay, re.S)
    is not None,
)
check(
    "snow view never steals touches",
    "setClickable(false)" in overlay and "setFocusable(false)" in overlay,
)
check(
    "flake count is bounded",
    re.search(r"Math\.max\(26, Math\.min\(MAX_FLAKES", overlay) is not None,
)
check(
    "flakes are recycled instead of leaking",
    "freeParticles" not in overlay
    and re.search(r"if \(flake\.y - flake\.radius > height\) \{\s*reset\(flake, false\);", overlay) is not None,
)
check(
    "redraw is throttled",
    "postInvalidateDelayed(FRAME_DELAY)" in overlay and "FRAME_DELAY = 33" in overlay,
)
check(
    "appearance screen wires the row",
    "snowEverywhereRow" in appearance
    and "NimarkoConfig.toggleDrawSnowEverywhere()" in appearance
    and "SnowOverlay.apply()" in appearance,
)
check(
    "overlay installed at start",
    "SnowOverlay.install()" in launch,
)

if failures:
    print("\n%d check(s) failed" % len(failures))
    sys.exit(1)
print("\nALL SNOW CHECKS PASSED")