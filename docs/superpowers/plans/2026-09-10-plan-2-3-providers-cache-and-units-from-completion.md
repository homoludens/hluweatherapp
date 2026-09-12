# Plan 2.3: Providers, Cache, And Units Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add MET Norway, provider-aware cached fallback, clear cache, and unit-aware presentation.

**Architecture:** Provider-specific sources return metric `WeatherForecast` values. A single repository routes by provider and writes/reads a DataStore cache keyed by provider plus saved ID or rounded Track Me coordinates; UI formatting converts only at display time.

**Tech Stack:** Ktor, kotlinx.serialization, DataStore, Kotlin datetime, Compose, JUnit.

**Spec:** `docs/superpowers/specs/2026-09-09-plan-2-core-product-completion-design.md`

## Global Constraints

- Requires Plan 2.1 and 2.2 active-location contracts.
- MET timestamps are UTC; group hourly data by the device display zone and retain `TimeZone.currentSystemDefault().id` in the resulting forecast.
- MET requests use `HluWeather/1.0 https://net.droopia.hluweather` as User-Agent.
- Keep at most 20 cache entries and never fall back across providers.

---

### Task 1: Add MET Norway Source

**Files:** Create `data/network/MetNoApi.kt`, `MetNoModels.kt`, `data/repository/MetNoWeatherRepository.kt`, matching API/repository tests; modify `HluWeatherApplication.kt`.

- [ ] **Step 1: Write failing source tests**

```kotlin
assertEquals("/weatherapi/locationforecast/2.0/compact", request.url.encodedPath)
assertEquals("44.8176", request.url.parameters["lat"])
assertEquals("HluWeather/1.0 https://net.droopia.hluweather", request.headers["User-Agent"])
assertEquals(WeatherCondition.THUNDERSTORM, mapped.condition)
```

Cover absent `next_1_hours`, every symbol family/day-night suffix, invalid timeseries, UTC parsing, daily min/max/precipitation aggregation, and local moon calculation.

- [ ] **Step 2: Run source tests to verify RED**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.data.network.MetNoApiTest --tests net.droopia.hluweather.data.repository.MetNoWeatherRepositoryTest`

Expected: compilation failure for MET types.

- [ ] **Step 3: Implement API and mapper**

Request `weatherapi/locationforecast/2.0/compact` with `lat`, `lon`, and non-null `altitude`. Map instant details and `next_1_hours`; unavailable apparent temperature/probability become null. Reject an empty or malformed time series with `WeatherRepositoryException`.

- [ ] **Step 4: Run source tests to verify GREEN**

Run the Step 2 command. Expected: passing offline tests.

- [ ] **Step 5: Commit MET support**

```bash
git add app/src/main/java/net/droopia/hluweather/data/network app/src/main/java/net/droopia/hluweather/data/repository app/src/test/java/net/droopia/hluweather/data
```

### Task 2: Add Provider-Aware Forecast Cache

**Files:** Create `data/cache/ForecastCache.kt`, `data/repository/CachingWeatherRepository.kt`, tests; modify `WeatherRepository.kt`, Open-Meteo source, application, weather/settings view models and screen.

**Interfaces:**

```kotlin
data class ForecastCacheKey(val provider: WeatherProvider, val locationKey: String)
data class ForecastLoad(val forecast: WeatherForecast, val isStale: Boolean)
interface WeatherRepository {
    suspend fun getForecast(provider: WeatherProvider, location: ActiveLocation): ForecastLoad
    suspend fun clearCache()
}
```

- [ ] **Step 1: Write failing cache tests**

```kotlin
assertNull(cache.get(ForecastCacheKey(WeatherProvider.MET_NO, "saved:belgrade")))
assertTrue(repository.getForecast(openMeteo, location).isStale)
assertEquals(20, cache.entries().size)
```

Test successful writes, provider separation, rounded `current:%.3f:%.3f` keys, LRU eviction, clear, matching-cache fallback, and no-cache error.

- [ ] **Step 2: Run cache tests to verify RED**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.data.cache.ForecastCacheTest --tests net.droopia.hluweather.data.repository.CachingWeatherRepositoryTest`

Expected: missing cache types.

- [ ] **Step 3: Implement cache and stale UI**

Serialize a dedicated cache DTO rather than annotating domain models. Route only to the matching provider source; write successful live results; on a controlled live error read the matching key and return `isStale = true`. Render a stale banner with fetched time and Retry; make settings Clear cache call `WeatherRepository.clearCache()`.

- [ ] **Step 4: Run cache, view-model, and screen tests to verify GREEN**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.data.cache --tests net.droopia.hluweather.data.repository.CachingWeatherRepositoryTest --tests net.droopia.hluweather.ui.weather`

Expected: passing stale, retry, and normal error cases.

- [ ] **Step 5: Commit cache fallback**

```bash
git add app/src/main/java/net/droopia/hluweather/data app/src/main/java/net/droopia/hluweather/ui app/src/test/java/net/droopia/hluweather
```

### Task 3: Apply Presentation Units And Verify

**Files:** Modify `data/Format.kt`, `FormatTest.kt`, weather card/hourly/daily components and tests.

- [ ] **Step 1: Write failing formatting tests**

```kotlin
assertEquals("68 degrees F", 20.0.temperatureText(TemperatureUnit.FAHRENHEIT))
assertEquals("0.39 in", 10.0.precipitationText(PrecipitationUnit.INCH))
assertEquals("-", null.temperatureText(TemperatureUnit.CELSIUS))
```

Use the actual app missing-value glyph in the final assertion. Cover C/F, mm/in, km/h/mph, km/mi, rounding, and null.

- [ ] **Step 2: Run format tests to verify RED**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.data.FormatTest`

Expected: missing unit-aware helpers.

- [ ] **Step 3: Implement helpers and pass unit state to UI**

Keep forecast data metric. Add nullable formatting helpers and pass settings units through current, hourly, and daily composables. Do not add unrequested wind/distance forecast fields; format those helpers only where values exist later.

- [ ] **Step 4: Verify full Plan 2**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew testDebugUnitTest assembleDebug lintDebug`

Expected: build success. Manually switch providers, units, locations, and offline mode to confirm cache/provider separation.

- [ ] **Step 5: Commit presentation units**

```bash
git add app/src/main/java/net/droopia/hluweather/data/Format.kt app/src/main/java/net/droopia/hluweather/ui app/src/test/java/net/droopia/hluweather
```
