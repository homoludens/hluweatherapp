# Task 5.1 verification — 2026-11-09

## Automated tests and build

All commands used `ANDROID_HOME=/home/homoludens/Android/Sdk`, `ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk`, and `ANDROID_AVD_HOME=/home/homoludens/.config/.android/avd/`.

1. Final `./gradlew --no-daemon :app:testGoogleDebugUnitTest` — **PASS**: 1,046 tests across 105 suites, 0 failures, 0 errors, 0 skipped.
   - The initial full run exposed `HluNavHostTest.privacy_policy_is_accessible_from_weather_settings`, whose hard-coded list scroll did not bring the privacy row into view. The test now scrolls to the tagged privacy row, asserts it is displayed, then clicks it. The isolated test passed after this adjustment.
   - An intermediate full rerun had an intermittent `WeatherWidgetUpdaterTest.selection_refresh_is_coalesced_and_does_not_force_manual_fetch` failure (`List has more than one element` with queued main-looper runnables). Its isolated rerun passed, and the final full-suite rerun passed all tests.
2. `./gradlew --no-daemon :app:assembleGoogleDebug` — **PASS**, `BUILD SUCCESSFUL in 48s` (36 actionable tasks: 5 executed, 31 up-to-date). No release or signed-release variant was built.
3. `./gradlew :app:compileGoogleDebugAndroidTestKotlin` — **PASS** during task 4.2 verification. Connected instrumentation tests were not run separately.
The full unit run covered provider API/repository, cache, settings, forecast UI, widget, and notification tests.

## `small_phone` device checks

- Started with `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ANDROID_AVD_HOME=/home/homoludens/.config/.android/avd/ /home/homoludens/Android/Sdk/emulator/emulator -avd small_phone -no-window -no-audio -no-boot-anim`.
- Device: `emulator-5554`, API 35, `sdk_gphone64_x86_64`, 320×640 at 160 dpi. Installed the built APK with `adb -s emulator-5554 install -r app/build/outputs/apk/google/debug/app-google-debug.apk` — **Success**.
- Online, saved test locations (Berlin/Mariendorf, Oslo, London, and New York) were selected through the app UI. Visual Crossing in Berlin displayed its nearby attribution and hourly precipitation-probability values (0% in the visible 12h–16h rows). Bright Sky in Berlin displayed `Weather data via Bright Sky` / `Source: Deutscher Wetterdienst` and probabilities of 9%, 13%, 14%, 15%, 14%, and 10% in visible 04h–09h rows. MET Norway in Oslo displayed hourly values including 1% at 22h and 0% in nearby rows.
- Limited-field observation: Bright Sky in New York returned core hourly weather, but the visible precipitation-probability cells were unavailable (`—`); the view remained attributed to Bright Sky/DWD. A complete Bright Sky no-weather/out-of-station-range state was **NOT RUN** because the New York query still returned usable core forecast data. This observation does not establish a full provider-coverage failure.
- Offline check: enabled airplane mode and disabled Wi-Fi and mobile data with `adb -s emulator-5554 shell cmd connectivity airplane-mode enable`, `adb -s emulator-5554 shell svc wifi disable`, and `adb -s emulator-5554 shell svc data disable`. With Berlin selected, matching cached forecasts remained visible while cycling Bright Sky, Visual Crossing, and MET Norway; Visual Crossing and Bright Sky attribution remained provider-specific. This exercised the offline cache state without claiming a network refresh succeeded.
- Restored the original connectivity state using `cmd connectivity airplane-mode disable`, `svc wifi enable`, and `svc data enable`; verified airplane mode `0` and Wi-Fi `1`. The installed app was absent before this run, so `adb -s emulator-5554 shell pm clear net.droopia.hluweather` safely removed only test-created locations/cache. Relaunch showed the initial “Add your first location” state. The AVD was stopped afterward.

## Boundaries

- No `.env` file was read or sourced, no secrets were printed, and no API key was supplied; Visual Crossing was exercised through its intended keyless path.
- No production code was edited for task 5.1; the brittle navigation test was updated to scroll its tagged privacy row into view. No signed/release build was attempted.
- The only new artifact is this verification record, following the repository's existing OpenSpec `verification.md` convention.
