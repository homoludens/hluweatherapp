# Task 3 Report

Status: DONE_WITH_CONCERNS
Branch: `feature/weather-on-route`
Implementation commit: `19e8397` (`feat: add swappable place search sources`)

## Files Changed

- `app/src/main/java/net/droopia/hluweather/data/network/PhotonApi.kt`
  - Added Photon response DTOs, `PhotonApi`, `KtorPhotonApi`, and
    `PhotonApiException`.
  - Sends the required `/api/` query, limit, language, and configured
    `HluWeather/3.2 route planner` User-Agent.
- `app/src/main/java/net/droopia/hluweather/data/network/OpenMeteoGeocodingApi.kt`
  - Added Open-Meteo geocoding response DTOs, `OpenMeteoGeocodingApi`,
    `KtorOpenMeteoGeocodingApi`, and `OpenMeteoGeocodingApiException`.
  - Sends the required `/v1/search` query parameters.
- `app/src/main/java/net/droopia/hluweather/data/repository/PlaceSearchSource.kt`
  - Added `PlaceSearchProvider`, `PlaceSearchResult`, `PlaceSearchSource`,
    `PhotonPlaceSearchSource`, and `OpenMeteoPlaceSearchSource`.
  - Trims queries, short-circuits blank input, normalizes labels, rejects
    invalid coordinates, and de-duplicates coordinate matches.
- `app/src/test/java/net/droopia/hluweather/data/network/PhotonApiTest.kt`
  - Covers request construction, User-Agent, decoding, HTTP failure, and
    missing/empty feature arrays.
- `app/src/test/java/net/droopia/hluweather/data/network/OpenMeteoGeocodingApiTest.kt`
  - Covers request construction, decoding, HTTP failure, and missing/empty
    result arrays.
- `app/src/test/java/net/droopia/hluweather/data/repository/PlaceSearchSourceTest.kt`
  - Covers providers, blank queries, trimming, labels, invalid coordinates,
    and duplicate coordinates for both sources.

## Tests And Commands

SDK environment used for all Gradle commands:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk
```

### TDD Red Run

Command:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew testDebugUnitTest --tests net.droopia.hluweather.data.network.PhotonApiTest --tests net.droopia.hluweather.data.network.OpenMeteoGeocodingApiTest --tests net.droopia.hluweather.data.repository.PlaceSearchSourceTest
```

Output:

```text
> Task :app:compileDebugUnitTestKotlin FAILED
e: .../OpenMeteoGeocodingApiTest.kt:34:24 Unresolved reference 'KtorOpenMeteoGeocodingApi'.
e: .../PhotonApiTest.kt:35:24 Unresolved reference 'KtorPhotonApi'.
e: .../PlaceSearchSourceTest.kt:24:23 Unresolved reference 'PhotonPlaceSearchSource'.
Execution failed for task ':app:compileDebugUnitTestKotlin'.
BUILD FAILED in 2s
```

The omitted compiler lines were the corresponding unresolved references for
the remaining requested APIs, DTOs, and source types. The failure occurred
before any production implementation existed.

### Brief Literal Command

Command:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew test --tests net.droopia.hluweather.data.network.PhotonApiTest --tests net.droopia.hluweather.data.network.OpenMeteoGeocodingApiTest
```

Output:

```text
Problem configuring task :app:test from command line.
> Unknown command-line option '--tests'.
BUILD FAILED in 1s
```

### Targeted Green Run

Command:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew testDebugUnitTest --tests net.droopia.hluweather.data.network.PhotonApiTest --tests net.droopia.hluweather.data.network.OpenMeteoGeocodingApiTest --tests net.droopia.hluweather.data.repository.PlaceSearchSourceTest --console=plain
```

Output:

```text
BUILD SUCCESSFUL in 1s
28 actionable tasks: 28 up-to-date
```

The test XML reports show 11 tests completed, 0 skipped, 0 failures, and 0
errors: 3 Photon API tests, 3 Open-Meteo geocoding API tests, and 5 source
tests.

### Diff Check

Commands:

```text
git diff --check
```

Output: no output; both exited with status 0.

## Self-Review

- Confirmed the implementation uses the shared injected Ktor `HttpClient`.
- Confirmed Photon uses `/api/`, `q`, `limit=8`, `lang=en`, and the exact
  required User-Agent.
- Confirmed Open-Meteo uses `/v1/search`, `name`, `count=8`, `language=en`,
  and `format=json`.
- Confirmed both HTTP APIs throw provider-specific `IOException` subclasses
  for non-2xx responses.
- Confirmed missing and empty response arrays decode to empty lists.
- Confirmed source queries are trimmed and blank queries make no API call.
- Confirmed labels trim components, omit blanks, and remove duplicate
  components.
- Confirmed source coordinates use longitude/latitude input ordering and
  reject non-finite or out-of-range values.
- Confirmed same-coordinate results are de-duplicated while preserving the
  first result.
- Confirmed the six implementation/test files are the only files in the
  implementation commit.
- Confirmed staged whitespace validation passed.

## Concerns

- The brief's literal `./gradlew test --tests ...` command is incompatible
  with this Android Gradle setup because the `test` task rejects `--tests`.
  The equivalent `testDebugUnitTest --tests ...` command passes.
- Gradle emits the existing warning that Android Gradle Plugin 9.1.0 was
  tested through compile SDK 36.1 while this project uses compile SDK 37.
- Ktor's test runtime emits an SLF4J no-provider warning.
- Only the three requested unit-test classes were run; the full project unit
  test suite was not run.
