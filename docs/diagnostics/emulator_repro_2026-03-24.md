# Emulator repro attempt — 2026-03-24

## Goal
Reproduce crash when tapping the first button on the first screen (entry into recognition mode) and capture full stacktrace from logcat.

## Environment actions
1. Installed Android SDK components via `scripts/setup_android_sdk.sh`.
2. Installed emulator + x86_64 Android 34 Google APIs system image.
3. Created AVD `codexApi34` (Pixel 6 profile).
4. Installed Linux emulator runtime deps (`libx11-xcb1`, `libxcb-*`, `libxkbcommon-x11-0`).
5. Installed JDK 8 and built APK with Maven under Java 8.

## Repro blockers encountered
- Emulator in this container repeatedly flips between `device` and `offline` states when running without hardware acceleration (`-accel off`).
- `adb install` blocks indefinitely in this environment, and Maven deploy reports install failure:

```
Install of /workspace/fluiteblox/target/fluitblox-1.0.0.apk failed.: Unknown failure (at android.os.Binder.execTransact(Binder.java:1275))
```

- Because APK install is unstable here, I could not reliably launch the app and therefore could not capture the app crash stacktrace for the first-button navigation path.

## What is ready now
- Added `scripts/setup_android_emulator.sh` to automate SDK + emulator + AVD provisioning for this repo.
- Once emulator runs with stable acceleration (or on a host with KVM), run:

```bash
export ANDROID_SDK_ROOT=/usr/local/lib/android/sdk
$ANDROID_SDK_ROOT/emulator/emulator -avd codexApi34 -gpu swiftshader_indirect -no-audio
adb logcat -c
adb shell am start -n tatar.eljah.fluitblox/tatar.eljah.MainActivity
# tap the first button manually or with adb input
adb logcat -d > crash.log
```
