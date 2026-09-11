# Task 2 Report

Status: DONE_WITH_CONCERNS
Branch: `feature/weather-on-route`
Implementation commit: `b67dc2f` (`feat: add OSRM routing source`)

## Files Changed

- `app/src/main/java/net/droopia/hluweather/data/network/OsrmApi.kt`
  - Added the serializable OSRM response models and `OsrmApi` interface.
  - Added injectable-base-URL `KtorOsrmApi` with the required driving,
    GeoJSON, full-overview, and no-steps request parameters.
  - Added `OsrmApiException` for non-2xx HTTP responses.
- `app/src/main/java/net/droopia/hluweather/data/repository/RoutingSource.kt`
  - Added `RoutingSource`, `RoutingException`, and `OsrmRoutingSource`.
  - Maps OSRM longitude/latitude coordinates to `GeoPoint` and preserves
    distance and provider duration.
  - Rejects non-`Ok` responses, missing routes, and malformed geometry.
- `app/src/test/java/net/droopia/hluweather/data/network/OsrmApiTest.kt`
  - Covers request path and parameters, non-2xx responses, and an `Ok`
    response with no routes.
- `app/src/test/java/net/droopia/hluweather/data/repository/OsrmRoutingSourceTest.kt`
  - Covers coordinate conversion, route summary mapping, `NoRoute`, and
    malformed geometry.

## Tests And Commands

The literal command from the brief was attempted with the required SDK
environment:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew test --tests net.droopia.hluweather.data.network.OsrmApiTest
```

Exact result:

```text
FAILURE: Build failed with an exception.
Problem configuring task :app:test from command line.
> Unknown command-line option '--tests'.
```

The valid Android unit-test task was used for the red run before
implementation:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew testDebugUnitTest --tests net.droopia.hluweather.data.network.OsrmApiTest
```

It failed during test compilation with unresolved references to
`KtorOsrmApi`, `OsrmApi`, `OsrmRoute`, `OsrmGeometry`, `OsrmResponse`,
`OsrmRoutingSource`, and `RoutingException`, as expected before production
code existed.

The first post-implementation run exposed a test-harness issue:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew testDebugUnitTest --tests net.droopia.hluweather.data.network.OsrmApiTest --tests net.droopia.hluweather.data.repository.OsrmRoutingSourceTest
```

Exact failure summary:

```text
OsrmApiTest > route_rejects_a_non_success_response FAILED
OsrmRoutingSourceTest > route_rejects_a_route_with_malformed_geometry FAILED
OsrmRoutingSourceTest > route_rejects_an_osrm_no_route_response FAILED
6 tests completed, 3 failed
```

The failures were caused by nested `runTest` calls inside JUnit
`ThrowingRunnable` blocks. Those blocks were changed to use `runBlocking`,
matching the existing Ktor API tests.

The final targeted command was rerun:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew testDebugUnitTest --tests net.droopia.hluweather.data.network.OsrmApiTest --tests net.droopia.hluweather.data.repository.OsrmRoutingSourceTest
```

Final output:

```text
BUILD SUCCESSFUL in 4s
28 actionable tasks: 2 executed, 26 up-to-date
```

The staged whitespace check was:

```text
git diff --cached --check
```

Exact output: no output, exit code 0.

## Self-Review

- Confirmed the implementation uses the shared injected Ktor `HttpClient`.
- Confirmed the default OSRM base URL is `https://router.project-osrm.org`.
- Confirmed the request path uses `longitude,latitude` ordering.
- Confirmed `geometries=geojson`, `overview=full`, and `steps=false` are sent.
- Confirmed non-2xx responses throw the required HTTP status message.
- Confirmed only successful responses with at least two finite, in-range
  coordinate pairs produce a `DrivingRoute`.
- Confirmed route mapping uses provider name `OSRM`, OSRM distance, OSRM
  duration, and latitude/longitude conversion.
- Confirmed the implementation commit contains only the four brief-specified
  implementation/test files.
- Confirmed the staged diff passed `git diff --cached --check`.

## Concerns

- The brief's literal `./gradlew test --tests ...` command is incompatible
  with this Android Gradle setup because the `test` task rejects `--tests`.
  The equivalent `testDebugUnitTest --tests ...` command passes.
- Gradle emits the existing warning that Android Gradle Plugin 9.1.0 was
  tested through compile SDK 36.1 while this project uses compile SDK 37.
- Only the two requested OSRM unit-test classes were run; the full project
  unit-test suite was not run.
