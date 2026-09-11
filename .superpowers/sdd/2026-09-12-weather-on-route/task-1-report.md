# Task 1 Report

Status: DONE_WITH_CONCERNS
Branch: `feature/weather-on-route`
Implementation commit: `ddbc8cc` (`feat: add weather route sampling models`)

## Files Changed

- `app/src/main/java/net/droopia/hluweather/data/model/WeatherRoute.kt`
  - Added `RouteEndpoint`, `DrivingRoute`, `RouteWeatherSample`, and `WeatherRouteResult`.
  - Weather values are nullable as required.
- `app/src/main/java/net/droopia/hluweather/data/weatherroute/RouteSampling.kt`
  - Added speed-based route sampling with start, full-hour offsets, and arrival.
  - Added Haversine geometry distances and cumulative polyline interpolation.
  - Added `WeatherSeverity` and `weatherSeverity()`.
- `app/src/test/java/net/droopia/hluweather/data/weatherroute/RouteSamplingTest.kt`
  - Added the required four-sample route test and short-route test.
  - Added exact-hour, invalid speed, invalid geometry/distance, and severity tests.

## Tests And Commands

The literal command from the brief was attempted:

```text
./gradlew test --tests net.droopia.hluweather.data.weatherroute.RouteSamplingTest
```

Exact result:

```text
FAILURE: Build failed with an exception.
Problem configuring task :app:test from command line.
> Unknown command-line option '--tests'.
```

The Android unit-test task was used for the targeted test filter. Without the
SDK environment it reported:

```text
./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.data.weatherroute.RouteSamplingTest
Could not determine the dependencies of task ':app:testDebugUnitTest'.
> SDK location not found.
```

The required red test run, with the SDK environment, was:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.data.weatherroute.RouteSamplingTest
```

Before implementation it failed during test compilation with unresolved
references to `DrivingRoute`, `buildRouteSamples`, `WeatherSeverity`, and
`weatherSeverity`, as expected.

After the first implementation, the same command reported:

```text
6 tests completed, 2 failed
```

Those failures exposed incorrect geometry normalization. After fixing the
interpolation calculation, the final targeted command was rerun exactly:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.data.weatherroute.RouteSamplingTest
```

Final output:

```text
BUILD SUCCESSFUL in 2s
28 actionable tasks: 28 up-to-date
```

The suite contained 6 tests and completed successfully. The final staged
whitespace check was:

```text
git diff --cached --check
```

Exact output: no output, exit code 0.

## Self-Review

- Confirmed only the three brief-specified implementation/test files were in the implementation commit.
- Confirmed the sample count is four for the provided 2h46m route.
- Confirmed exact-hour arrivals do not duplicate the destination.
- Confirmed arrival time uses the chosen average speed, not `providerDurationSeconds`.
- Confirmed fewer than two geometry points, non-positive route distance, and non-positive speed throw `IllegalArgumentException`.
- Confirmed the severity mapping matches the brief exactly.
- Confirmed the final staged diff passed `git diff --cached --check`.

## Concerns

- The brief's literal `./gradlew test --tests ...` command is incompatible with
  this project's configured Android Gradle task setup because `test` rejects
  `--tests`. The equivalent `:app:testDebugUnitTest --tests ...` command
  passes.
- Gradle emits existing compile-SDK compatibility and Kotlin deprecation
  warnings. The required route model API uses `kotlinx.datetime.Instant`,
  which is also reported as deprecated by the current dependency/toolchain.
