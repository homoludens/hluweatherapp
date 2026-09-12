# Final Fix Report

Status: COMPLETE
Branch: `feature/weather-on-route`
Worktree: `/home/homoludens/projekti/hluweatherapp/.worktrees/weather-on-route`

## Findings

1. Stale results are now hidden from the result section when planner inputs
   change. The screen shows `Result outdated. Calculate again to use the
   updated inputs.` instead. Added coverage in
   `WeatherRouteScreenTest.outdated_result_is_not_rendered_as_current`.

2. `WeatherRouteViewModel` now owns the active calculation/retry `Job` and
   cancels it on invalidation and replacement. Existing generation checks remain
   as a publication guard. Added route-source and weather-retry cancellation
   tests that observe `CancellationException` at the source boundary.

3. Planner errors no longer expose `Throwable.message`. Known routing,
   weather, search, and IO failures map to stable concise messages; unknown
   failures use category fallbacks. Added unknown and known error mapping tests.

4. Open-Meteo geocoding now sends `User-Agent: HluWeather/3.2 route planner`.
   The Ktor request test asserts the header.

5. Photon coordinates and Open-Meteo latitude/longitude fields are nullable.
   Sources discard malformed coordinate pairs with `mapNotNull` while keeping
   valid neighboring results. Added API decoding and source regression tests.

6. Saved-location and map-picker overlays now use full-window Compose dialogs,
   preserving their test tags and preventing the old 440dp parent from limiting
   overlay coverage. Added overlay-bound regression assertions.

## Changed Files

- `app/src/main/java/net/droopia/hluweather/data/network/OpenMeteoGeocodingApi.kt`
- `app/src/main/java/net/droopia/hluweather/data/network/PhotonApi.kt`
- `app/src/main/java/net/droopia/hluweather/data/repository/PlaceSearchSource.kt`
- `app/src/main/java/net/droopia/hluweather/ui/weatherroute/RouteEndpointPicker.kt`
- `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteScreen.kt`
- `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteViewModel.kt`
- `app/src/test/java/net/droopia/hluweather/data/network/OpenMeteoGeocodingApiTest.kt`
- `app/src/test/java/net/droopia/hluweather/data/network/PhotonApiTest.kt`
- `app/src/test/java/net/droopia/hluweather/data/repository/PlaceSearchSourceTest.kt`
- `app/src/test/java/net/droopia/hluweather/ui/weatherroute/RouteEndpointPickerTest.kt`
- `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteScreenTest.kt`
- `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteViewModelTest.kt`
- `.superpowers/sdd/2026-09-12-weather-on-route/final-fix-report.md`

## Verification

SDK environment for every Gradle command:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk
ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk
```

Targeted affected tests:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteViewModelTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteScreenTest --tests net.droopia.hluweather.ui.weatherroute.RouteEndpointPickerTest --tests net.droopia.hluweather.data.network.OpenMeteoGeocodingApiTest --tests net.droopia.hluweather.data.network.PhotonApiTest --tests net.droopia.hluweather.data.repository.PlaceSearchSourceTest --console=plain
```

Exact final result:

```text
BUILD SUCCESSFUL in 23s
28 actionable tasks: 1 executed, 27 up-to-date
```

Generated report: 44 tests, 0 failures, 0 skipped, 100% successful.

Full tests:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew test --console=plain
```

Exact result:

```text
BUILD SUCCESSFUL in 51s
28 actionable tasks: 1 executed, 27 up-to-date
```

Generated report: 369 tests, 0 failures, 0 skipped, 100% successful.

Lint:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew lint --console=plain
```

Exact result:

```text
BUILD SUCCESSFUL in 58s
27 actionable tasks: 8 executed, 19 up-to-date
```

The generated lint report contains 30 warnings and no errors.

Debug assembly:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew assembleDebug --console=plain
```

Exact result:

```text
BUILD SUCCESSFUL in 15s
36 actionable tasks: 4 executed, 32 up-to-date
```

Additional checks:

```text
git diff --check
```

Exact result: no output, exit code 0.

## Remaining Concerns

- Gradle reports the existing AGP/compile-SDK 37 compatibility warning.
- The project retains existing Kotlin `kotlinx.datetime.Instant` deprecation
  warnings and 30 lint warnings.
- No emulator or physical-device manual verification was run; native MapLibre
  rendering remains outside the Robolectric coverage boundary.
