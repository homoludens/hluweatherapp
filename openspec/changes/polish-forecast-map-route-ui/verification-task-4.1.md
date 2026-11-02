# Task 4.1 verification

Date: 2026-11-02

## Automated tests

Environment:

```sh
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk
```

Command:

```sh
./gradlew :app:testGoogleDebugUnitTest
```

Outcome: **PASS** (`BUILD SUCCESSFUL`). The test report contains 82 suites and 749 tests, with 0 failures, 0 errors, and 0 skipped.

## Google debug build

Command (with the environment above):

```sh
./gradlew :app:assembleGoogleDebug
```

Outcome: **PASS** (`BUILD SUCCESSFUL`). Gradle reported 36 actionable tasks: 13 executed, 4 from cache, and 19 up-to-date. The build emitted a non-fatal notice that `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`, `libjniMaplibreNativeC.so`, and `libmaplibre-native-c.so` could not be stripped and were packaged as-is.

## Device and AVD availability

Commands (with the environment above):

```sh
"$ANDROID_HOME/platform-tools/adb" devices -l
"$ANDROID_HOME/emulator/emulator" -list-avds
```

Outcome: **No usable device or emulator available.** `adb devices -l` restarted the adb server after reporting a client/server version mismatch (client 41, server 36), then listed no devices. `emulator -list-avds` returned an empty list.

## UI checks

**NOT RUN** — there was no attached device and no configured emulator AVD. Therefore small-screen checks of forecast spacing, map information/credits, and route-result scrolling, in both light and dark themes, were unavailable.
