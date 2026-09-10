# Final Fix Wave Report

Date: 2026-09-10
Worktree: `/home/homoludens/projekti/hluweatherapp/.worktrees/weather-refresh-polish`
Branch: `feat/weather-refresh-polish`

## Findings Addressed

1. `WeatherViewModel` no longer advances `handledRefresh` before the network
   call. A superseded request leaves the explicit refresh pending, while a
   current non-cancellation failure marks that refresh handled. Retry still
   increments the refresh request.
2. Every hourly body value and condition label now has an explicit Material 3
   foreground color. Formatting and layout are unchanged.
3. `WeatherHeroTest` now samples the compact 96dp-preserving regular hero at
   the geometry and exact color used by the removed decorative moon, so the
   test fails if that implementation is reintroduced.

## TDD Evidence

The new regression tests were run before production changes. The corrected RED
run compiled and failed for the intended behavior:

```text
HourlyForecastTest > dark_theme_hourly_body_values_use_readable_foreground_colors FAILED
WeatherViewModelTest > superseded_refresh_still_attempts_network_for_the_same_input FAILED
4 tests completed, 2 failed
```

The superseded-refresh assertion observed 2 network calls instead of 3. The
dark-table assertion observed an unreadable inherited foreground. The
failed-refresh/location-change regression passed against the existing code,
confirming that path was already covered; provider-change coverage remains in
`WeatherViewModelTest`.

## Focused Verification

```text
WeatherViewModelTest: BUILD SUCCESSFUL, 29 tests
HourlyForecastTest, WeatherHeroTest, ThemeTest, WeatherScreenTest: BUILD SUCCESSFUL, 36 tests
```

## Full Verification

Command:

```bash
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest :app:lint :app:assembleDebug
```

Result: `BUILD SUCCESSFUL`.

```text
suites=38 tests=290 skipped=0 failures=0 errors=0
```

Lint completed with zero errors. Debug APK was assembled at
`app/build/outputs/apk/debug/app-debug.apk`.

## Diff Check

`git diff --check` passed before this report was created. Final status and diff
were inspected before commit; only the ViewModel, hourly row, and their tests,
the durable hero test, and this report are part of this fix wave.

## Concerns

- Gradle continues to emit the existing warning that AGP 9.1.0 was tested up to
  compile SDK 36.1 while this project uses compile SDK 37.
- Physical-device validation was not performed in this fix wave.
