# Task 7 Report

## Status

Implemented Task 7 on branch `feature/weather-on-route` in worktree
`/home/homoludens/projekti/hluweatherapp/.worktrees/weather-on-route`.

## Files

Added:

- `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteMap.kt`
- `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTimeline.kt`
- `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteMapTest.kt`
- `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTimelineTest.kt`

Added map behavior includes a MapLibre GeoJSON `LineString`, rounded primary route
stroke, fitted initial bounds, selected-sample camera updates, start/end markers,
severity-colored sample markers, selected marker emphasis, 48 dp sample targets, and
the `Map tiles unavailable` failure message.

Added timeline behavior includes stable sample-index keys and tags, destination
label, arrival time, configured temperature/wind/distance conversions, precipitation
percentage, distance travelled, selection callbacks, and unavailable-weather text
with dash placeholders.

## TDD Commands and Output

Environment used for all Gradle commands:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk
ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk
```

RED command from the brief:

```text
./gradlew test --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteMapTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteTimelineTest
```

Output:

```text
FAILURE: Build failed with an exception.
Problem configuring task :app:test from command line.
> Unknown command-line option '--tests'.
BUILD FAILED in 1s
```

Corrected Android unit-test RED command:

```text
./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteMapTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteTimelineTest
```

Output before implementation:

```text
> Task :app:compileDebugUnitTestKotlin FAILED
Unresolved reference 'weatherRouteMarkers'.
Unresolved reference 'WeatherRouteMapMarkers'.
Unresolved reference 'WeatherRouteTimeline'.
BUILD FAILED
```

Final targeted test command:

```text
./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteMapTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteTimelineTest
```

Output:

```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 18s
28 actionable tasks: 4 executed, 24 up-to-date
```

The targeted suite completed 6 tests with 0 failures.

Diff check command:

```text
git diff --check
```

Output: no output.

## Self-Review

- Map route geometry is emitted as a GeoJSON `LineString` with longitude-first coordinates.
- MapLibre `LineLayer` uses `LineCap.Round`, `LineJoin.Round`, a 5 dp primary stroke, and `rememberGeoJsonSource(GeoJsonData.JsonString(...))`.
- Initial camera fitting is keyed to the map state and route geometry; selected marker camera movement is keyed only to the selected sample index.
- Sample markers use `weatherSeverity`, distinct severity colors, a neutral unavailable color, stable sample tags, callback propagation, and at least 48 dp touch targets.
- Timeline items use sample indices as stable keys and expose the required destination and item tags.
- Timeline values use `hourText`, `temperatureValueText`, `windSpeedText`, `percentText`, and `distanceText`.
- Missing weather is rendered as `Weather unavailable`; missing measures use the existing dash formatting.
- `git diff --check` produced no output.

## Concerns

- Gradle emits the existing unsupported compile SDK warning because AGP 9.1.0 is tested through SDK 36.1 while this project compiles with SDK 37.
- Kotlin emits one MapLibre composable-target warning at the `MaplibreMap` call; the existing `WeatherMap` uses the same MapLibre API pattern.
- Tests cover the marker layer and timeline directly; full native MapLibre rendering and camera animation are not exercised by the Robolectric targeted suite.

## Review Follow-up

Addressed reviewer finding P2 by adding an actual `WeatherRouteMap` composition test
using a minimal route result. The test uses Compose inspection mode because Robolectric
cannot load MapLibre's native library; it verifies the public composable preserves its
full-size map container contract without native rendering.

Additional focused coverage now verifies the route `LineString` source payload,
full-route bounds supplied to initial fitting, selected sample point supplied to
centering, and the tile-error overlay composable. Existing `WeatherRouteMapMarkers`
tests were retained unchanged in behavior.

Exact follow-up command:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteMapTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteTimelineTest
```

Exact follow-up output:

```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 18s
28 actionable tasks: 4 executed, 24 up-to-date
```

The follow-up targeted suite completed 9 tests with 0 failures.

Follow-up self-review:

- `WeatherRouteMapTest` now renders `WeatherRouteMap` directly with a minimal result.
- `routeLineGeoJson` is asserted as a longitude-first GeoJSON `LineString`, matching the source passed to `rememberGeoJsonSource(GeoJsonData.JsonString(...))`.
- `routeMapBounds` and `routeMapSelectedPoint` are the exact helper inputs used by the map camera effects and are asserted against deterministic fixture values.
- `WeatherRouteMapError(showError = true)` is rendered directly and asserts the existing tile-failure message and tag.
- The original severity, unavailable marker, callback, and touch-target tests remain present.
- `git diff --check` remains clean after the follow-up changes.

Follow-up concerns:

- Robolectric cannot exercise the normal MapLibre source/layer installation or native camera calls because MapLibre Compose fails with `UnsatisfiedLinkError`; inspection mode intentionally bypasses that unavailable native boundary.
- Gradle still emits the existing SDK 37/AGP compatibility warning and the MapLibre composable-target warning.

## Final Verification

Exact command:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteMapTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteTimelineTest
```

Exact output:

```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 18s
28 actionable tasks: 4 executed, 24 up-to-date
```

Final self-review: 9 focused tests pass; the direct `WeatherRouteMap` test is
deterministic in inspection mode, and source payload, camera inputs, and error
overlay are covered by the exact helpers and branch used by the production
composable. Native MapLibre rendering remains unavailable under Robolectric.
