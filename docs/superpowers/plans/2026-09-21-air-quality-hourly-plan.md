# Air Quality In Hourly Weather Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Open-Meteo European AQI, PM2.5, and PM10 to the configurable hourly table and use the highest-priority enabled metric in the current-weather card.

**Architecture:** Add a dedicated `OpenMeteoAirQualityApi` for the separate Open-Meteo air-quality endpoint. Merge its nullable current and timestamp-keyed hourly values into the existing weather models in `OpenMeteoWeatherRepository`; keep failures non-fatal. Reuse the existing settings-column persistence and table/card rendering paths.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Ktor, Kotlin serialization, DataStore Preferences, JUnit, Robolectric Compose tests, Gradle Google debug variant.

**Spec:** `docs/superpowers/specs/2026-09-21-air-quality-hourly-design.md`

## Global Constraints

- Air-quality columns are disabled by default.
- Card selection priority is `EUROPEAN_AQI`, then `PM2_5`, then `PM10`.
- If the selected card value is unavailable, show its label with `—` rather than falling back to `Feels like`.
- Open-Meteo air-quality request failures are non-fatal.
- MET Norway forecasts keep null air-quality values.
- Existing cached forecasts without new fields remain readable.
- Do not add a new dependency; use the existing Ktor client and Kotlin serialization setup.
- Preserve existing weather fetching behavior, provider selection, navigation, location handling, and default hourly columns.
- Build and test the Google debug variant only.

---

### Task 1: Add Air-Quality API Contract

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/data/network/OpenMeteoAirQualityApi.kt`
- Create: `app/src/main/java/net/droopia/hluweather/data/network/OpenMeteoAirQualityModels.kt`
- Create: `app/src/test/java/net/droopia/hluweather/data/network/OpenMeteoAirQualityApiTest.kt`

**Interfaces:**
- Produces `OpenMeteoAirQualityApi`, `KtorOpenMeteoAirQualityApi`, `OpenMeteoAirQualityResponse`, `AirQualityCurrentDto`, and `AirQualityHourlyDto` for the repository task.
- `suspend fun OpenMeteoAirQualityApi.forecast(location: WeatherLocation): OpenMeteoAirQualityResponse`.

- [ ] **Step 1: Write failing API tests**

Add tests named `forecast_sends_current_and_hourly_air_quality_parameters`, `forecast_rejects_non_success_responses`, and `forecast_decodes_optional_blocks`. The request test must assert:

```kotlin
assertEquals("air-quality-api.open-meteo.test", request.url.host)
assertEquals("/v1/air-quality", request.url.encodedPath)
assertEquals("auto", request.url.parameters["timezone"])
assertEquals("7", request.url.parameters["forecast_days"])
assertEquals("european_aqi,pm10,pm2_5", request.url.parameters["current"])
assertEquals("european_aqi,pm10,pm2_5", request.url.parameters["hourly"])
```

Use the same Ktor `MockEngine`, `ContentNegotiation`, and JSON setup as `OpenMeteoApiTest`.

- [ ] **Step 2: Run the API tests and verify they fail**

Run:

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk
./gradlew :app:testGoogleDebugUnitTest --tests net.droopia.hluweather.data.network.OpenMeteoAirQualityApiTest
```

Expected: compilation fails because the new API and DTOs do not exist.

- [ ] **Step 3: Implement the API and serializable DTOs**

Define serializable DTOs with nullable response blocks and nullable arrays:

```kotlin
@Serializable
data class OpenMeteoAirQualityResponse(
    val timezone: String? = null,
    val current: AirQualityCurrentDto? = null,
    val hourly: AirQualityHourlyDto? = null
)

@Serializable
data class AirQualityCurrentDto(
    val time: String? = null,
    @SerialName("european_aqi") val europeanAqi: Double? = null,
    val pm10: Double? = null,
    @SerialName("pm2_5") val pm2_5: Double? = null
)
```

Implement `KtorOpenMeteoAirQualityApi` with default base URL `https://air-quality-api.open-meteo.com`, a GET to `/v1/air-quality`, the exact query parameters above, success-status validation, and `OpenMeteoApiException` for non-2xx responses.

- [ ] **Step 4: Run the API tests and verify they pass**

Run the focused command from Step 2. Expected: all `OpenMeteoAirQualityApiTest` tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/droopia/hluweather/data/network/OpenMeteoAirQualityApi.kt app/src/main/java/net/droopia/hluweather/data/network/OpenMeteoAirQualityModels.kt app/src/test/java/net/droopia/hluweather/data/network/OpenMeteoAirQualityApiTest.kt
git commit -m "feat: add Open-Meteo air quality API"
```

### Task 2: Merge Air Quality Into Weather Forecasts

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/data/model/WeatherForecast.kt:7-30`
- Modify: `app/src/main/java/net/droopia/hluweather/data/repository/OpenMeteoWeatherRepository.kt:23-128`
- Modify: `app/src/main/java/net/droopia/hluweather/HluWeatherApplication.kt:90-105`
- Modify: `app/src/test/java/net/droopia/hluweather/data/repository/OpenMeteoWeatherRepositoryTest.kt`

**Interfaces:**
- Consumes `OpenMeteoAirQualityApi.forecast(location)` from Task 1.
- Produces nullable `CurrentWeather.europeanAqi`, `CurrentWeather.pm10`, `CurrentWeather.pm2_5` and corresponding `HourForecast` fields.
- `OpenMeteoWeatherRepository` constructor accepts both `OpenMeteoApi` and `OpenMeteoAirQualityApi` plus the existing clock.

- [ ] **Step 1: Extend model and repository tests first**

Append nullable fields with null defaults to `CurrentWeather` and `HourForecast`. In `OpenMeteoWeatherRepositoryTest`, add a fake air-quality API and tests named:

- `getForecast_merges_current_and_timestamp_matched_air_quality`
- `getForecast_keeps_weather_when_air_quality_request_fails`
- `getForecast_rethrows_air_quality_cancellation`
- `getForecast_leaves_air_quality_null_when_optional_blocks_are_missing`

Use air-quality timestamps that are deliberately ordered differently from the weather fixture’s list to prove the merge uses `Instant` keys rather than array positions.

- [ ] **Step 2: Run the repository tests and verify they fail**

Run:

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk
./gradlew :app:testGoogleDebugUnitTest --tests net.droopia.hluweather.data.repository.OpenMeteoWeatherRepositoryTest
```

Expected: compilation or assertion failures because air-quality fields and merge logic are not implemented.

- [ ] **Step 3: Add air-quality fields and timestamp merge logic**

In `OpenMeteoWeatherRepository`, keep normal weather mapping as the primary result. Add a helper that:

1. Requests air quality after the weather response is available.
2. Rethrows `CancellationException`.
3. Returns null on all other air-quality failures.
4. Parses the air-quality timezone and timestamps with the same timestamp parser used for weather.
5. Maps current fields directly from the air-quality current block.
6. Builds `Map<Instant, AirQualityHour>` from the air-quality hourly response.
7. Copies each weather hour with matching nullable air-quality values.

When air-quality data is null, return the original weather forecast unchanged except for its default null fields. Validate air-quality hourly array lengths and throw a repository exception inside the non-fatal air-quality boundary when they mismatch.

Construct `KtorOpenMeteoAirQualityApi(httpClient)` in `HluWeatherApplication` and pass it into the Open-Meteo repository.

- [ ] **Step 4: Run the repository tests and verify they pass**

Run the command from Step 2. Expected: all repository tests pass, including non-fatal failures and cancellation behavior.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/droopia/hluweather/data/model/WeatherForecast.kt app/src/main/java/net/droopia/hluweather/data/repository/OpenMeteoWeatherRepository.kt app/src/main/java/net/droopia/hluweather/HluWeatherApplication.kt app/src/test/java/net/droopia/hluweather/data/repository/OpenMeteoWeatherRepositoryTest.kt
git commit -m "feat: merge air quality into weather forecasts"
```

### Task 3: Preserve Air Quality In Forecast Cache

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/data/cache/ForecastCache.kt:106-263`
- Modify: `app/src/test/java/net/droopia/hluweather/data/cache/ForecastCacheTest.kt`

**Interfaces:**
- Consumes the new nullable model fields from Task 2.
- Produces cache round-tripping for current and hourly air-quality values while accepting old serialized entries.

- [ ] **Step 1: Add failing cache tests**

Add a round-trip test with non-null AQI/PM values and a compatibility test that writes JSON without those keys, then asserts `toWeatherForecastOrNull()` succeeds and all new fields are null.

- [ ] **Step 2: Run the cache tests and verify they fail**

Run:

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk
./gradlew :app:testGoogleDebugUnitTest --tests net.droopia.hluweather.data.cache.ForecastCacheTest
```

Expected: compilation or assertion failures because cache DTOs do not carry the new fields.

- [ ] **Step 3: Add nullable cache DTO fields and mappings**

Add `europeanAqi`, `pm10`, and `pm2_5` with `= null` defaults to both cache DTOs. Include them in `toCurrentWeather`, `toHourForecast`, `WeatherForecast.toDto`, and the corresponding constructors without changing existing serialized field names.

- [ ] **Step 4: Run the cache tests and verify they pass**

Run the command from Step 2. Expected: round-trip and old-entry compatibility tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/droopia/hluweather/data/cache/ForecastCache.kt app/src/test/java/net/droopia/hluweather/data/cache/ForecastCacheTest.kt
git commit -m "feat: cache air quality forecast values"
```

### Task 4: Add Air-Quality Settings

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsState.kt:35-54`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsScreen.kt:257-281,667-693`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/settings/SettingsScreenTest.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/settings/SettingsRepositoryTest.kt`

**Interfaces:**
- Consumes the existing generic `HourlyTableColumn` settings flow.
- Produces stable settings tags `settings_hourly_column_european_aqi`, `settings_hourly_column_pm2_5`, and `settings_hourly_column_pm10`.

- [ ] **Step 1: Add failing settings tests**

Add assertions that the three new rows are present, disabled in `defaultHourlyTableColumns`, and invoke `onHourlyTableColumnChange` with the expected enum when clicked. Add a repository test proving the names survive DataStore serialization and old column sets still decode.

- [ ] **Step 2: Run the settings tests and verify they fail**

Run:

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk
./gradlew :app:testGoogleDebugUnitTest --tests net.droopia.hluweather.ui.settings.SettingsScreenTest --tests net.droopia.hluweather.ui.settings.SettingsRepositoryTest
```

Expected: compilation or assertion failures because the enum entries and labels do not exist.

- [ ] **Step 3: Add enum entries and settings copy**

Add the three enum values without adding a new preference key. Add titles/subtitles in the existing `when` expressions. The existing `SettingsViewModel.setHourlyTableColumn` and `DataStoreSettingsRepository` should work unchanged because they operate on enum names.

- [ ] **Step 4: Run the settings tests and verify they pass**

Run the command from Step 2. Expected: all settings tests pass and the default set remains unchanged.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/droopia/hluweather/ui/settings/SettingsState.kt app/src/main/java/net/droopia/hluweather/ui/settings/SettingsScreen.kt app/src/test/java/net/droopia/hluweather/ui/settings/SettingsScreenTest.kt app/src/test/java/net/droopia/hluweather/ui/settings/SettingsRepositoryTest.kt
git commit -m "feat: add air quality table settings"
```

### Task 5: Format and Render Air-Quality Table Columns

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/data/Format.kt:35-48`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weather/HourlyForecast.kt:312-441`
- Modify: `app/src/test/java/net/droopia/hluweather/data/FormatTest.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/weather/HourlyForecastTest.kt`

**Interfaces:**
- Consumes nullable air-quality fields and the new settings enum values.
- Produces `Double?.airQualityIndexText()` and `Double?.particulateMatterText()` formatting helpers and table rendering branches.

- [ ] **Step 1: Write failing formatting and table tests**

Add formatter assertions:

```kotlin
assertEquals("42", 42.4.airQualityIndexText())
assertEquals("12.5 µg/m³", 12.5.particulateMatterText())
assertEquals("—", null.particulateMatterText())
```

Add a table test that enables each new column and asserts headers `AQI`, `PM2.5`, and `PM10` plus values from a populated `HourForecast`. Add a null-value test asserting table cells expose `-`.

- [ ] **Step 2: Run the focused table tests and verify they fail**

Run:

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk
./gradlew :app:testGoogleDebugUnitTest --tests net.droopia.hluweather.data.FormatTest --tests net.droopia.hluweather.ui.weather.HourlyForecastTest
```

Expected: compilation or assertion failures because formatting and rendering branches are missing.

- [ ] **Step 3: Implement formatting and table branches**

Add an AQI formatter that rounds to an integer and a particulate-matter formatter that uses compact decimal formatting plus `µg/m³`. In `HourlyForecast`, add header labels and `when` branches using `ForecastValue`, with AQI as a normal value and PM values as normal values. Assign weights consistent with adjacent numeric columns.

- [ ] **Step 4: Run the focused table tests and verify they pass**

Run the command from Step 2. Expected: formatter and table tests pass with nulls represented as `-`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/droopia/hluweather/data/Format.kt app/src/main/java/net/droopia/hluweather/ui/weather/HourlyForecast.kt app/src/test/java/net/droopia/hluweather/data/FormatTest.kt app/src/test/java/net/droopia/hluweather/ui/weather/HourlyForecastTest.kt
git commit -m "feat: render air quality in hourly table"
```

### Task 6: Use Selected Air Quality In Weather Card

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weather/CurrentWeatherCard.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherScreen.kt:203-238,378-496`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/weather/CurrentWeatherCardTest.kt`

**Interfaces:**
- Consumes `Set<HourlyTableColumn>` from the existing settings state and new current-weather fields from Task 2.
- Produces the card’s selected metric label/value while preserving the existing public defaults and location semantics.

- [ ] **Step 1: Add failing card and wiring tests**

Add card tests for these sets:

```kotlin
emptySet<HourlyTableColumn>()
setOf(HourlyTableColumn.PM10)
setOf(HourlyTableColumn.PM10, HourlyTableColumn.PM2_5, HourlyTableColumn.EUROPEAN_AQI)
setOf(HourlyTableColumn.EUROPEAN_AQI) // current value null
```

Assert respectively: `Feels like` remains, `PM10` is shown, `European AQI` wins by priority, and `European AQI` shows `—`. Keep existing tests for values, units, compactness, location semantics, dark contrast, and grouped metrics.

- [ ] **Step 2: Run the card tests and verify they fail**

Run:

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk
./gradlew :app:testGoogleDebugUnitTest --tests net.droopia.hluweather.ui.weather.CurrentWeatherCardTest
```

Expected: new precedence assertions fail because the card always renders “Feels like” and the weather screen does not pass column selection.

- [ ] **Step 3: Implement card selection and pass settings through both paths**

Add an optional `hourlyTableColumns: Set<HourlyTableColumn> = defaultHourlyTableColumns` parameter to `CurrentWeatherCard`. Resolve one metric in this exact order:

```kotlin
when {
    EUROPEAN_AQI in columns -> AirQualityMetric("European AQI", current.europeanAqi.airQualityIndexText())
    PM2_5 in columns -> AirQualityMetric("PM2.5", current.pm2_5.particulateMatterText())
    PM10 in columns -> AirQualityMetric("PM10", current.pm10.particulateMatterText())
    else -> AirQualityMetric("Feels like", current.apparentTemperature.temperatureValueText(temperatureUnit))
}
```

Use the selected label/value in the existing third metric slot. Pass `hourlyTableColumns` to the card in the normal non-daily branch and through `HourlyWeatherContent` to its card call. Keep previews and direct callers on the default set.

- [ ] **Step 4: Run the card tests and verify they pass**

Run the command from Step 2. Expected: precedence, fallback, unavailable-value, and existing card tests all pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/droopia/hluweather/ui/weather/CurrentWeatherCard.kt app/src/main/java/net/droopia/hluweather/ui/weather/WeatherScreen.kt app/src/test/java/net/droopia/hluweather/ui/weather/CurrentWeatherCardTest.kt
git commit -m "feat: show selected air quality in weather card"
```

### Task 7: Integration Verification and Cleanup

**Files:**
- Modify: none planned; only verified fixes to files listed in Tasks 1-6 if integration verification exposes a defect.
- Test: the focused feature tests and full `testGoogleDebugUnitTest` suite.

**Interfaces:**
- Consumes all completed feature tasks.
- Produces a verified Google debug build and a clean, focused diff.

- [ ] **Step 1: Run all focused feature tests together**

Run:

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk
./gradlew :app:testGoogleDebugUnitTest --tests net.droopia.hluweather.data.network.OpenMeteoAirQualityApiTest --tests net.droopia.hluweather.data.repository.OpenMeteoWeatherRepositoryTest --tests net.droopia.hluweather.data.cache.ForecastCacheTest --tests net.droopia.hluweather.ui.settings.SettingsScreenTest --tests net.droopia.hluweather.ui.settings.SettingsRepositoryTest --tests net.droopia.hluweather.ui.weather.HourlyForecastTest --tests net.droopia.hluweather.ui.weather.CurrentWeatherCardTest
```

Expected: all focused feature and regression tests pass.

- [ ] **Step 2: Run the full Google debug unit-test suite**

Run:

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk
./gradlew :app:testGoogleDebugUnitTest
```

Expected: zero failures. If an unrelated existing failure remains, record its exact test name and assertion rather than changing unrelated behavior.

- [ ] **Step 3: Build the Google debug APK**

Run:

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk
./gradlew :app:assembleGoogleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Check formatting and scope**

Run:

```bash
git diff --check
git status --short
```

Review that only air-quality implementation, tests, and required docs changed. Do not modify unrelated worktree changes.
