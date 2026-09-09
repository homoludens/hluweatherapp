# Open-Meteo Networking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the production mock forecast source with a real Open-Meteo repository while keeping the existing weather UI and `WeatherRepository` contract stable.

**Architecture:** A Ktor `HttpClient` is owned by an application-scoped container. `KtorOpenMeteoApi` decodes transport DTOs, and `OpenMeteoWeatherRepository` validates and maps them into the existing domain models. Production `WeatherViewModel.Factory` uses the application repository; Compose tests inject `MockWeatherRepository` so no test performs network I/O.

**Tech Stack:** Kotlin 2.4.20, Ktor 3.5.2, kotlinx.serialization JSON 1.9.0, OkHttp Ktor engine, Kotlin Coroutines, kotlinx.datetime, JUnit 4, Robolectric, Ktor `MockEngine`.

**Spec:** `docs/superpowers/specs/2026-09-09-open-meteo-networking-design.md`

## Global Constraints

- Support Open-Meteo only; `MET_NO` remains a future provider.
- Keep the existing `WeatherRepository.getForecast(location: WeatherLocation): WeatherForecast` contract unchanged.
- Request seven days of current, hourly, and daily data using `timezone=auto`.
- Store stable domain units: Celsius, km/h, and millimeters.
- Map unknown WMO codes to `WeatherCondition.UNKNOWN`.
- Reject missing or mismatched required response arrays instead of producing partial forecasts.
- Keep `MockWeatherRepository` for deterministic tests and previews.
- Do not add retries, cache fallback, location CRUD, GPS, reverse geocoding, maps, notifications, or settings UI changes.
- All tests must remain offline and deterministic.

---

## File Map

- Modify: `gradle/libs.versions.toml` with Ktor, serialization, and plugin aliases.
- Modify: `app/build.gradle.kts` with the serialization plugin and Ktor dependencies.
- Modify: `app/src/main/AndroidManifest.xml` with Android internet permission and the application class.
- Create: `app/src/main/java/net/droopia/hluweather/data/network/OpenMeteoModels.kt` with serializable transport DTOs.
- Create: `app/src/main/java/net/droopia/hluweather/data/network/OpenMeteoApi.kt` with the API boundary and Ktor implementation.
- Create: `app/src/test/java/net/droopia/hluweather/data/network/OpenMeteoApiTest.kt` with `MockEngine` request and failure tests.
- Create: `app/src/main/java/net/droopia/hluweather/data/repository/OpenMeteoWeatherRepository.kt` with validation and domain mapping.
- Create: `app/src/test/java/net/droopia/hluweather/data/repository/OpenMeteoWeatherRepositoryTest.kt` with mapping and invalid-payload tests.
- Create: `app/src/main/java/net/droopia/hluweather/HluWeatherApplication.kt` with the application-scoped client and repository.
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherViewModel.kt` to use the application repository in its factory.
- Modify: `app/src/main/java/net/droopia/hluweather/ui/app/HluWeatherApp.kt` and `app/src/main/java/net/droopia/hluweather/navigation/HluNavHost.kt` to allow offline weather ViewModel injection in tests.
- Create: `app/src/test/java/net/droopia/hluweather/HluWeatherApplicationTest.kt` to verify real repository construction without a request.
- Modify: `app/src/test/java/net/droopia/hluweather/ui/app/HluWeatherAppTest.kt` and `app/src/test/java/net/droopia/hluweather/navigation/HluNavHostTest.kt` to inject `MockWeatherRepository` ViewModels.

---

### Task 1: Add Ktor Open-Meteo API Client

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/net/droopia/hluweather/data/network/OpenMeteoModels.kt`
- Create: `app/src/main/java/net/droopia/hluweather/data/network/OpenMeteoApi.kt`
- Create: `app/src/test/java/net/droopia/hluweather/data/network/OpenMeteoApiTest.kt`

**Interfaces:**
- `interface OpenMeteoApi { suspend fun forecast(location: WeatherLocation): OpenMeteoResponse }`
- `class KtorOpenMeteoApi(private val client: HttpClient, private val baseUrl: String = "https://api.open-meteo.com") : OpenMeteoApi`
- `class OpenMeteoApiException(message: String) : IOException(message)`
- `data class OpenMeteoResponse(...)` and nested `@Serializable` DTOs expose the Open-Meteo response only; they do not use domain model types.

- [ ] **Step 1: Add dependency and plugin aliases**

Add these entries to `gradle/libs.versions.toml`:

```toml
ktor = "3.5.2"
serialization = "1.9.0"

[libraries]
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

Add `alias(libs.plugins.kotlin.serialization)` to the app plugins and add:

```kotlin
implementation(libs.ktor.client.core)
implementation(libs.ktor.client.okhttp)
implementation(libs.ktor.client.content.negotiation)
implementation(libs.ktor.serialization.kotlinx.json)
implementation(libs.kotlinx.serialization.json)
testImplementation(libs.ktor.client.mock)
```

- [ ] **Step 2: Add the internet permission**

Add this direct child of `<manifest>` in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

- [ ] **Step 3: Write the failing API request test**

Create a `MockEngine` test that captures the request and returns this compact
valid response:

```json
{
  "timezone": "Europe/Belgrade",
  "current": {
    "time": "2026-09-09T12:00",
    "temperature_2m": 21.0,
    "relative_humidity_2m": 51,
    "apparent_temperature": 21.0,
    "dew_point_2m": 10.0,
    "precipitation": 0.0,
    "weather_code": 0,
    "is_day": 1
  },
  "hourly": {
    "time": ["2026-09-09T12:00"],
    "temperature_2m": [21.0],
    "relative_humidity_2m": [51],
    "dew_point_2m": [10.0],
    "apparent_temperature": [21.0],
    "precipitation": [0.0],
    "precipitation_probability": [0],
    "weather_code": [0],
    "is_day": [1]
  },
  "daily": {
    "time": ["2026-09-09"],
    "weather_code": [0],
    "temperature_2m_max": [30.0],
    "temperature_2m_min": [16.0],
    "precipitation_sum": [0.0],
    "sunrise": ["2026-09-09T06:10"],
    "sunset": ["2026-09-09T19:00"],
    "moon_phase": [0.5]
  }
}
```

Assert the method is `GET`, the URL path is `/v1/forecast`, and the query
contains the location coordinates, `timezone=auto`, `forecast_days=7`, all
requested current/hourly/daily variables, and the three unit parameters. The
test must reference `KtorOpenMeteoApi` before it exists.

- [ ] **Step 4: Run the API test and verify RED**

Run:

```bash
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.data.network.OpenMeteoApiTest
```

Expected: compilation failures for the missing Ktor API and DTO types.

- [ ] **Step 5: Implement serializable DTOs and the Ktor API**

Use DTO fields matching the JSON names with `@SerialName`, nullable scalar
values where Open-Meteo may omit a value, and lists for each hourly/daily
series. Configure the production client at the application layer, not in this
API class. In `KtorOpenMeteoApi.forecast`, build the request with
`url(baseUrl) { appendPathSegments("v1", "forecast") }`, add the exact query
parameters from the spec, reject non-2xx responses with
`OpenMeteoApiException("Weather service returned HTTP ${response.status.value}")`,
and decode the body with `response.body<OpenMeteoResponse>()`.

- [ ] **Step 6: Add API failure tests and verify GREEN**

Add tests for a 503 response and invalid JSON. Assert both throw
`OpenMeteoApiException` or a decoding exception at the API boundary, without
making a real request. Run the focused API test command again; expected result
is BUILD SUCCESSFUL.

- [ ] **Step 7: Commit the API slice**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/net/droopia/hluweather/data/network app/src/test/java/net/droopia/hluweather/data/network
git commit -m "feat: add Open-Meteo API client"
```

### Task 2: Map and Validate Open-Meteo Forecasts

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/data/repository/OpenMeteoWeatherRepository.kt`
- Create: `app/src/test/java/net/droopia/hluweather/data/repository/OpenMeteoWeatherRepositoryTest.kt`

**Interfaces:**
- `class OpenMeteoWeatherRepository(private val api: OpenMeteoApi, private val clock: Clock = Clock.System) : WeatherRepository`
- `override suspend fun getForecast(location: WeatherLocation): WeatherForecast`
- Internal mapper helpers accept `OpenMeteoResponse` and return existing domain models.

- [ ] **Step 1: Write failing mapping tests**

Create a fake `OpenMeteoApi` that returns the one-hour/one-day response from
Task 1. Assert `WeatherForecast.location`, provider `OPEN_METEO`, current
values, hourly values, daily values, timezone-aware `Instant` values, the
first daily moon phase, and `fetchedAt` from an injected fixed `Clock`.

Add a weather-code test covering `0`, `1`, `2`, `3`, `45`, `51`, `61`, `71`,
`95`, and an unrecognized value. Assert the corresponding
`WeatherCondition` values from the spec.

Add invalid-response tests for a missing current block, mismatched hourly
array lengths, mismatched daily array lengths, invalid timezone, and invalid
timestamp. Each test must assert a controlled `WeatherRepositoryException`
with a non-empty message.

- [ ] **Step 2: Run mapping tests and verify RED**

Run:

```bash
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.data.repository.OpenMeteoWeatherRepositoryTest
```

Expected: compilation failures for the missing repository and exception types.

- [ ] **Step 3: Implement validation and mapping**

Implement `OpenMeteoWeatherRepository` with these rules:

```kotlin
class WeatherRepositoryException(message: String, cause: Throwable? = null) : IOException(message, cause)

class OpenMeteoWeatherRepository(
    private val api: OpenMeteoApi,
    private val clock: Clock = Clock.System
) : WeatherRepository {
    override suspend fun getForecast(location: WeatherLocation): WeatherForecast {
        val response = try {
            api.forecast(location)
        } catch (error: WeatherRepositoryException) {
            throw error
        } catch (error: Throwable) {
            throw WeatherRepositoryException("Unable to load weather data", error)
        }

        return response.toWeatherForecast(location, clock.now())
    }
}
```

Validate all required blocks and that every hourly/daily list has the same
length as its time list. Parse `timezone` with `TimeZone.of`; parse a local
ISO date-time with `LocalDateTime.parse(value).toInstant(timezone)`, while
accepting an already-offset `Instant.parse(value)` first. Parse daily dates
with `LocalDate.parse`. Map nullable API fields to nullable domain fields and
throw `WeatherRepositoryException` for required nulls or invalid values.

Use the exact WMO ranges from the spec. Set `fetchedAt` to `clock.now()` and
`moonPhase` to the first daily moon-phase value.

- [ ] **Step 4: Run mapping tests and verify GREEN**

Run the focused repository test command again. Expected: all mapping,
validation, timestamp, and weather-code tests pass.

- [ ] **Step 5: Commit the repository slice**

```bash
git add app/src/main/java/net/droopia/hluweather/data/repository/OpenMeteoWeatherRepository.kt app/src/test/java/net/droopia/hluweather/data/repository/OpenMeteoWeatherRepositoryTest.kt
git commit -m "feat: map Open-Meteo forecasts"
```

### Task 3: Wire the Real Repository and Preserve Offline UI Tests

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/HluWeatherApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherViewModel.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/app/HluWeatherApp.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/navigation/HluNavHost.kt`
- Create: `app/src/test/java/net/droopia/hluweather/HluWeatherApplicationTest.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/app/HluWeatherAppTest.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/navigation/HluNavHostTest.kt`

**Interfaces:**
- `class HluWeatherApplication : Application() { val weatherRepository: WeatherRepository }`
- `HluWeatherApp(weatherViewModel: WeatherViewModel? = null)` keeps the existing no-argument call valid and uses the optional ViewModel only for tests.
- `HluNavHost(settingsViewModel: SettingsViewModel = ..., weatherViewModel: WeatherViewModel? = null)` keeps production defaults and passes an injected weather ViewModel to `WeatherScreen` when present.

- [ ] **Step 1: Write the failing application wiring test**

Create `HluWeatherApplicationTest` using `ApplicationProvider`:

```kotlin
@Test
fun application_exposes_the_open_meteo_repository() {
    val application = ApplicationProvider
        .getApplicationContext<HluWeatherApplication>()

    assertTrue(application.weatherRepository is OpenMeteoWeatherRepository)
}
```

The test must not call `getForecast` or access the network.

- [ ] **Step 2: Run the wiring test and verify RED**

Run:

```bash
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.HluWeatherApplicationTest
```

Expected: compilation failures for the missing application class and real
repository wiring.

- [ ] **Step 3: Implement the application-scoped client**

Register `HluWeatherApplication` in the manifest. Construct one lazy
`HttpClient(OkHttp)` with `ContentNegotiation { json(Json { ignoreUnknownKeys = true }) }`,
`expectSuccess = false`, and `HttpTimeout { requestTimeoutMillis = 15_000 }`.
Expose one lazy `OpenMeteoWeatherRepository(KtorOpenMeteoApi(client))`.

Update `WeatherViewModel.Factory` to cast the application key to
`HluWeatherApplication` and pass `application.weatherRepository`. Do not
instantiate `MockWeatherRepository` from the production factory.

- [ ] **Step 4: Add the test injection seam**

Change `HluWeatherApp` and `HluNavHost` to accept an optional
`WeatherViewModel?`. In `HluNavHost`, use the supplied ViewModel for
`WeatherScreen`; otherwise obtain the real one with
`viewModel(factory = WeatherViewModel.Factory)`.

Update the app and navigation Compose tests to pass
`WeatherViewModel(MockWeatherRepository())`. Keep the existing assertions and
the settings persistence recreation test; only replace the weather source so
the tests remain offline.

- [ ] **Step 5: Run wiring and UI tests and verify GREEN**

Run:

```bash
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.HluWeatherApplicationTest --tests net.droopia.hluweather.ui.app.HluWeatherAppTest --tests net.droopia.hluweather.navigation.HluNavHostTest
```

Expected: all selected tests pass without a network request.

- [ ] **Step 6: Commit production wiring**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/net/droopia/hluweather/HluWeatherApplication.kt app/src/main/java/net/droopia/hluweather/ui/weather/WeatherViewModel.kt app/src/main/java/net/droopia/hluweather/ui/app/HluWeatherApp.kt app/src/main/java/net/droopia/hluweather/navigation/HluNavHost.kt app/src/test/java/net/droopia/hluweather/HluWeatherApplicationTest.kt app/src/test/java/net/droopia/hluweather/ui/app/HluWeatherAppTest.kt app/src/test/java/net/droopia/hluweather/navigation/HluNavHostTest.kt
git commit -m "feat: wire Open-Meteo weather repository"
```

### Task 4: Full Verification and Review

**Files:**
- No production file changes expected unless a verification failure identifies a concrete issue.
- Update: `.superpowers/sdd/2026-09-09-open-meteo-networking/progress.md`
- Create: `.superpowers/sdd/2026-09-09-open-meteo-networking/task-4-report.md`

- [ ] **Step 1: Run the complete test suite**

```bash
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew testDebugUnitTest
```

Expected: all tests pass; no test may access the live Open-Meteo endpoint.

- [ ] **Step 2: Run build, lint, and whitespace checks**

```bash
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew assembleDebug lintDebug
```

Expected: `BUILD SUCCESSFUL` for both Gradle tasks and no whitespace errors.

- [ ] **Step 3: Self-review the complete diff**

Check that the diff contains only the Ktor API, Open-Meteo mapping, app
wiring, deterministic tests, and required dependency/manifest changes. Verify
that no API key, real test network call, cache, second provider, or unrelated
UI refactor was added.

- [ ] **Step 4: Record verification and commit the report**

Record exact test counts, build outputs, warnings, and any deferred minor
items in `task-4-report.md`, append the final status to the SDD progress
ledger, then commit only those SDD records:

```bash
git add .superpowers/sdd/2026-09-09-open-meteo-networking/progress.md .superpowers/sdd/2026-09-09-open-meteo-networking/task-4-report.md
git commit -m "test: verify Open-Meteo networking"
```
