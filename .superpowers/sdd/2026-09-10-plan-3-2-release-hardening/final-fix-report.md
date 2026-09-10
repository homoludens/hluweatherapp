# Plan 3.2 Final Whole-Branch Review Fix Report

## Status

All final whole-branch review fixes that can be verified locally are applied.
Plan, specification, ledger, and generated files were not modified.

## Changes

- Redesigned `CurrentWeatherCard` so the temperature has full card width and
  auto-sizes to fit, while metrics wrap within their columns. Removed all
  single-line/non-wrapping value constraints.
- Removed the hourly condition `maxLines = 1` limit so condition labels wrap.
- Added constrained 1.5x/2x weather-screen, current-card, and hourly-row
  coverage. The card test uses the text-layout result's width-overflow signal.
- Added merged Button semantics, label, and action label to the current-location
  row; quick-switcher rows now expose merged labeled RadioButton semantics; the
  daily-summary-time row now exposes one merged labeled Button action.
- Added focused accessibility coverage for current weather, quick switching,
  settings summary time, map (existing focused test retained), and location
  picker actions.
- Added `AndroidJUnit4` to `MainActivityLaunchTest`, controlled settings-entry
  navigation coverage, safe notification `PendingIntent.send()` launch checks,
  and WorkManager daily-summary schedule identity coverage. Device tests retain
  explicit notification-permission prerequisites and cleanup.
- Replaced the alert notification hash ID with the deterministic event key as
  notification tag plus a fixed alert ID. Android's `(tag, id)` identity avoids
  distinct alert collisions without changing worker deduplication keys.
- Replaced the committed `changeit` value in the signing matrix with a per-run
  32-byte random hexadecimal password. The password is never printed.
- Added explicit release-checklist sections for privacy/no background location
  and network-free unit tests.

## TDD Evidence

Production behavior changes were introduced after these focused red results:

- `./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weather.CurrentWeatherCardTest --tests net.droopia.hluweather.ui.weather.HourlyForecastTest`
  failed with the expected current-card visual overflow, missing current-location
  semantics, and hourly condition overflow assertions.
- `./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weather.CurrentWeatherCardTest --tests net.droopia.hluweather.ui.weather.HourlyForecastTest --tests net.droopia.hluweather.ui.weather.WeatherScreenTest --tests net.droopia.hluweather.ui.weather.LocationQuickSwitcherTest --tests net.droopia.hluweather.ui.settings.SettingsScreenTest --tests net.droopia.hluweather.notifications.AndroidWeatherNotificationPublisherTest`
  failed with the expected missing current-location, quick-switcher, and
  summary-time semantics, and with the old hash-ID alert assertion.
- `rg --fixed-strings --line-number 'changeit' scripts/verify-release-signing-matrix.sh`
  returned `8:password=changeit` before the random per-run password change.

The corresponding green focused suite passed:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weather.CurrentWeatherCardTest --tests net.droopia.hluweather.ui.weather.HourlyForecastTest --tests net.droopia.hluweather.ui.weather.WeatherScreenTest --tests net.droopia.hluweather.ui.weather.LocationQuickSwitcherTest --tests net.droopia.hluweather.ui.map.WeatherMapTest --tests net.droopia.hluweather.ui.locationpicker.LocationPickerScreenTest --tests net.droopia.hluweather.ui.settings.SettingsScreenTest --tests net.droopia.hluweather.notifications.AndroidWeatherNotificationPublisherTest --tests net.droopia.hluweather.notifications.WeatherAlertWorkerTest --tests net.droopia.hluweather.notifications.DailySummaryWorkerTest --tests net.droopia.hluweather.notifications.NotificationSchedulerTest
```

Result: `BUILD SUCCESSFUL`.

## Verification

- `./gradlew test :app:compileDebugAndroidTestKotlin`: `BUILD SUCCESSFUL`.
- `./gradlew lintDebug lintRelease assembleDebug assembleRelease`: `BUILD
  SUCCESSFUL` in 1m 45s. An earlier run reached the harness 120-second timeout
  while lint was still running; the 300-second rerun completed successfully.
- `./scripts/verify-release-signing-matrix.sh`: all four cases passed, including
  signed APK generation from the non-logged random password.
- `apksigner verify --verbose app/build/outputs/apk/release/app-release.apk`:
  verifies with one signer and v2 signature enabled.
- `aapt dump badging app/build/outputs/apk/release/app-release.apk`: confirms
  `versionCode='32'`, `versionName='3.2.0'`, and only foreground coarse/fine
  location permissions; no background-location permission is present.
- `bash -n scripts/verify-release-signing-matrix.sh && ! rg --fixed-strings
  'changeit' scripts/verify-release-signing-matrix.sh`: passed.

## Device Results

- `adb devices -l` detected `M2012K11AG`, API 33.
- `./gradlew connectedDebugAndroidTest` built both APKs, installed far enough to
  report `Starting 7 tests on M2012K11AG - 13`, then remained at `0/7`
  completed until the five-minute harness timeout. No device assertion result
  was obtained.
- Device-only checks, including TalkBack, real settings navigation, notification
  tap, scheduled delivery, map rendering, and signed-release launch, remain
  unchecked. The new tests compile but require an unrestricted responsive device
  to execute.

## Deferred Source Checks

The existing release resource source checks remain intentionally unchanged.
Extending them would add more brittle source-string assertions rather than
consumer-visible behavior coverage. The signing matrix now exercises all
credential combinations and verifies the resulting signed APK, which is the
bounded behavioral validation available for this release script.
