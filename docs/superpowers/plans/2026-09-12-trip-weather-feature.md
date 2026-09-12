# Trip Weather Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing Android weather-route feature into a three-screen Trip Weather flow with a cached 72-hour departure slider, route-weather summaries, and detailed timeline/map/table views.

**Architecture:** Keep the existing route models, OSRM source, Open-Meteo route-weather source, MapLibre map, and `WeatherRouteViewModel`. Add pure timing, snapshot matching, summary, and warning calculations underneath the ViewModel. Scope one ViewModel to a nested `trip` navigation graph and let setup, overview, and details collect the same immutable `StateFlow`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation Compose, Ktor, kotlinx.serialization, kotlinx-datetime, MapLibre Compose, JUnit, coroutines-test, Robolectric, and Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-09-12-trip-weather-feature-design.md`

## Global Constraints

- Extend the existing `ui/weatherroute` feature in place.
- Use the existing OSRM routing source and MapLibre/OpenFreeMap integration.
- Use the existing batched Open-Meteo route-weather source for route samples.
- Do not expose MET Norway as a route-weather implementation until a route source for it exists.
- Do not implement saved trips, recent trips, or Save trip persistence in this change.
- Elevation is optional. Do not show an empty graph when routing data has no elevation values.
- The setup slider uses 72 discrete steps and changes the offset in one-hour increments.
- No request is made for ordinary slider movement when the snapshot covers the required forecast range.
- If required data is missing, a 300 ms debounced, cancellable refresh fetches it through the route-weather repository.
- Route sampling retains approximately 30-minute internal samples, including departure and final arrival.
- Timeline and table presentation filter these to hourly checkpoints plus the destination.
- Use `HluWeatherIcon` for all route weather visuals. Do not add emoji glyphs or a second icon mapping.
- Use `MaterialTheme`, `LocalHluColors`, existing card shapes, spacing, buttons, tab patterns, and unit-formatting helpers.
- Keep route weather requests in repository/data classes.
- Add light and dark previews for the three screen surfaces using deterministic preview route data where practical.
- Use only the existing Android module and dependencies; do not add a domain module or dependency-injection framework.
- Preserve `CancellationException`; convert only non-cancellation failures into concise UI state messages.
- Do not commit implementation changes unless the user explicitly requests commits.

---

## File Structure

| Path | Responsibility |
|---|---|
| `app/src/main/java/net/droopia/hluweather/data/model/WeatherRoute.kt` | Existing route models plus forecast-hour, snapshot, timing, and result fields. |
| `app/src/main/java/net/droopia/hluweather/data/weatherroute/RouteSampling.kt` | Existing route geometry sampling extended for average-speed, route-estimate, and 30-minute schedules. |
| `app/src/main/java/net/droopia/hluweather/data/weatherroute/TripWeatherAnalysis.kt` | Pure snapshot matching, display filtering, summary, and warning derivation. |
| `app/src/main/java/net/droopia/hluweather/data/network/OpenMeteoRouteWeatherApi.kt` | Existing Open-Meteo hourly DTOs and request fields, extended for snapshot data. |
| `app/src/main/java/net/droopia/hluweather/data/repository/RouteWeatherSource.kt` | Snapshot-fetching route-weather abstraction and Open-Meteo normalization. |
| `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteViewModel.kt` | Shared trip state, route calculation, departure slider, snapshot refresh, and selected sample. |
| `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteScreen.kt` | Compatibility setup entry point retained for existing callers/tests. |
| `app/src/main/java/net/droopia/hluweather/ui/weatherroute/TripWeatherSetupScreen.kt` | Setup screen with endpoints, departure controls, slider preview, and calculate action. |
| `app/src/main/java/net/droopia/hluweather/ui/weatherroute/TripWeatherOverviewScreen.kt` | Scrollable overview with map, summary, warnings, highlights, and actions. |
| `app/src/main/java/net/droopia/hluweather/ui/weatherroute/TripWeatherDetailsScreen.kt` | Timeline/map/table tabs and destination/weather warning details. |
| `app/src/main/java/net/droopia/hluweather/ui/weatherroute/RouteEndpointPicker.kt` | Existing endpoint search and selection controls, restyled without changing behavior. |
| `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteMap.kt` | Existing route line and markers, updated to use shared weather icons and sample state. |
| `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTimeline.kt` | Existing timeline updated for hourly filtering, shared icons, labels, and warnings. |
| `app/src/main/java/net/droopia/hluweather/navigation/HluNavHost.kt` | Nested `trip` graph and one graph-scoped ViewModel factory. |
| `app/src/main/java/net/droopia/hluweather/HluWeatherApplication.kt` | Existing production source construction reused by the trip graph. |
| `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherScreen.kt` | Existing Map-mode entry action retained and tested. |
| `app/src/test/java/net/droopia/hluweather/data/weatherroute/RouteSamplingTest.kt` | Timing modes, 30-minute samples, and route interpolation tests. |
| `app/src/test/java/net/droopia/hluweather/data/weatherroute/TripWeatherAnalysisTest.kt` | Snapshot matching, display filtering, summary, and warning tests. |
| `app/src/test/java/net/droopia/hluweather/data/network/OpenMeteoRouteWeatherApiTest.kt` | Open-Meteo snapshot request and DTO decoding tests. |
| `app/src/test/java/net/droopia/hluweather/data/repository/OpenMeteoRouteWeatherSourceTest.kt` | Snapshot normalization and refresh error tests. |
| `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteViewModelTest.kt` | Shared state, slider caching, debounce, refresh, and stale-work tests. |
| `app/src/test/java/net/droopia/hluweather/ui/weatherroute/TripWeatherSetupScreenTest.kt` | Setup control and slider semantics tests. |
| `app/src/test/java/net/droopia/hluweather/ui/weatherroute/TripWeatherOverviewScreenTest.kt` | Overview summary, warnings, map, and action tests. |
| `app/src/test/java/net/droopia/hluweather/ui/weatherroute/TripWeatherDetailsScreenTest.kt` | Details tabs, table, destination, and selection tests. |
| `app/src/test/java/net/droopia/hluweather/navigation/HluNavHostTest.kt` | Nested graph entry, shared state, and back-stack tests. |

## Task 1: Route Timing And Trip Analysis

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/data/model/WeatherRoute.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/data/weatherroute/RouteSampling.kt`
- Create: `app/src/main/java/net/droopia/hluweather/data/weatherroute/TripWeatherAnalysis.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/data/weatherroute/RouteSamplingTest.kt`
- Create: `app/src/test/java/net/droopia/hluweather/data/weatherroute/TripWeatherAnalysisTest.kt`

**Interfaces:**
- Consumes: existing `GeoPoint`, `WeatherCondition`, `DrivingRoute`, and `WeatherRouteResult`.
- Produces: `RouteTimingMode`, `RouteWeatherForecastHour`, `RouteWeatherSnapshot`, `TripWeatherSummary`, `TripWarningType`, `TripWeatherWarning`, `buildRouteSamples()`, `RouteWeatherSnapshot.enrich()`, `displayRouteSamples()`, `summarizeTripWeather()`, and `findTripWeatherWarnings()`.

- [ ] **Step 1: Write failing pure tests for both timing modes and snapshot matching**

```kotlin
@Test
fun route_estimate_distributes_provider_duration_over_route_distance() {
    val samples = buildRouteSamples(route, departure, 80, RouteTimingMode.ROUTE_ESTIMATE)

    assertEquals(route.providerDurationSeconds, samples.last().arrivalTime - departure)
    assertEquals(route.distanceMeters / 2.0, samples[1].distanceMeters, 0.1)
}

@Test
fun snapshot_matching_uses_nearest_hour_and_later_hour_on_a_tie() {
    val result = snapshot.enrich(listOf(sampleAt("2026-09-12T10:30:00Z"))).single()

    assertEquals(11.0, result.temperatureCelsius)
}
```

Add tests for 30-minute internal samples, departure/final samples, exact-hour arrival without a duplicate destination, hourly display filtering, unavailable weather, summary maximum temperature/rain duration/strongest wind, each warning category, and optional place/elevation values.

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `./gradlew test --tests net.droopia.hluweather.data.weatherroute.RouteSamplingTest --tests net.droopia.hluweather.data.weatherroute.TripWeatherAnalysisTest`

Expected: compilation failures for the new timing, snapshot, and analysis interfaces.

- [ ] **Step 3: Add the models and pure calculations**

Extend `RouteWeatherSample` with nullable `precipitationMm`, `humidityPercent`, `isDay`, `placeLabel`, and `elevationMeters`. Add:

```kotlin
enum class RouteTimingMode { AVERAGE_SPEED, ROUTE_ESTIMATE }

data class RouteWeatherForecastHour(
    val time: Instant,
    val condition: WeatherCondition?,
    val temperatureCelsius: Double?,
    val windSpeedKmh: Double?,
    val precipitationProbability: Int?,
    val precipitationMm: Double?,
    val humidityPercent: Int?,
    val isDay: Boolean?
)

data class RouteWeatherSnapshot(
    val points: List<GeoPoint>,
    val hourlyByPoint: List<List<RouteWeatherForecastHour>>,
    val fetchedAt: Instant
)
```

Update `buildRouteSamples()` to accept `timingMode`, generate 30-minute offsets strictly before arrival, interpolate each offset on cumulative geometry distance, and always append the final arrival. Average-speed duration uses route distance and entered speed; route-estimate duration uses `providerDurationSeconds`. Require a positive finite provider duration for route-estimate mode.

Implement these exact pure functions:

```kotlin
fun RouteWeatherSnapshot.covers(samples: List<RouteWeatherSample>): Boolean
fun RouteWeatherSnapshot.enrich(samples: List<RouteWeatherSample>): List<RouteWeatherSample>
fun displayRouteSamples(samples: List<RouteWeatherSample>, departure: Instant): List<RouteWeatherSample>
fun summarizeTripWeather(samples: List<RouteWeatherSample>): TripWeatherSummary
fun findTripWeatherWarnings(
    samples: List<RouteWeatherSample>,
    strongWindThresholdKmh: Double = 50.0
): List<TripWeatherWarning>
```

`covers()` is true only when every sample has a corresponding non-empty hourly
list with a forecast hour at or before and after the requested arrival, unless
the requested arrival is exactly on an available forecast hour. `enrich()`
matches each arrival to its coordinate's nearest hourly entry; equal distances
choose the later hour. `displayRouteSamples()` retains departure, each hourly
elapsed checkpoint, and the final sample exactly once.

Define `TripWeatherSummary` with nullable departure temperature, maximum
temperature, rainy duration in minutes, and strongest wind. Define warning
types `RAIN`, `SNOW`, `THUNDERSTORM`, `FOG`, and `STRONG_WIND`. Rain warnings
use `RAIN` or `DRIZZLE` conditions or positive precipitation; strong-wind
warnings use `windSpeedKmh >= strongWindThresholdKmh`. Group adjacent internal
samples with the same warning type into ranges and preserve the first available
route point/time for each range.

- [ ] **Step 4: Run the pure tests and inspect the diff**

Run: `./gradlew test --tests net.droopia.hluweather.data.weatherroute.RouteSamplingTest --tests net.droopia.hluweather.data.weatherroute.TripWeatherAnalysisTest`

Expected: PASS.

Run: `git diff --check`

Expected: no output.

## Task 2: Open-Meteo Forecast Snapshot Source

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/data/network/OpenMeteoRouteWeatherApi.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/data/repository/RouteWeatherSource.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/data/network/OpenMeteoRouteWeatherApiTest.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/data/repository/OpenMeteoRouteWeatherSourceTest.kt`

**Interfaces:**
- Consumes: `RouteWeatherForecastHour`, `RouteWeatherSnapshot`, and existing Ktor `HttpClient`.
- Produces: `RouteWeatherSource.fetchSnapshot(points: List<GeoPoint>): RouteWeatherSnapshot` and `KtorOpenMeteoRouteWeatherApi.forecast(points: List<GeoPoint>): List<OpenMeteoRouteResponse>`.

- [ ] **Step 1: Write failing request and normalization tests**

Assert the existing batched latitude/longitude request also requests:

```kotlin
assertEquals(
    "temperature_2m,weather_code,wind_speed_10m,precipitation_probability," +
        "precipitation,relative_humidity_2m,is_day",
    request.url.parameters["hourly"]
)
```

Add fixture assertions for humidity, precipitation amount, day/night, exact-hour matching, later-hour tie matching, malformed per-coordinate data becoming unavailable, and HTTP failure propagating as a source error.

- [ ] **Step 2: Run the source tests and verify they fail**

Run: `./gradlew test --tests net.droopia.hluweather.data.network.OpenMeteoRouteWeatherApiTest --tests net.droopia.hluweather.data.repository.OpenMeteoRouteWeatherSourceTest`

Expected: compilation failures because the expanded DTO fields and snapshot contract do not exist.

- [ ] **Step 3: Implement the expanded DTO and source contract**

Add nullable DTO arrays for `precipitation`, `relative_humidity_2m`, and `is_day`. Keep `forecast_days=16`, metric units, `timezone=GMT`, and the existing comma-separated multi-coordinate request. Convert each response into a `RouteWeatherForecastHour` list, preserving response order and treating incomplete individual rows as unavailable. Return `RouteWeatherSnapshot(points, hourlyByPoint, fetchedAt = Clock.System.now())`.

Replace the old repository-only `enrich()` contract with `fetchSnapshot()`. Keep nearest-hour selection in `TripWeatherAnalysis.kt`, so the repository only performs network normalization. A request-level error throws; an invalid coordinate response produces an empty hourly list for that coordinate.

- [ ] **Step 4: Run source tests and inspect the diff**

Run: `./gradlew test --tests net.droopia.hluweather.data.network.OpenMeteoRouteWeatherApiTest --tests net.droopia.hluweather.data.repository.OpenMeteoRouteWeatherSourceTest`

Expected: PASS.

Run: `git diff --check`

Expected: no output.

## Task 3: Shared ViewModel State, Slider, And Refresh

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteViewModel.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteViewModelTest.kt`

**Interfaces:**
- Consumes: `RoutingSource`, `RouteWeatherSource`, `buildRouteSamples()`, `RouteWeatherSnapshot.enrich()`, `summarizeTripWeather()`, and `findTripWeatherWarnings()`.
- Produces: `WeatherRouteUiState` fields `departureOffsetHours`, `routeTimingMode`, `snapshot`, `summary`, and `warnings`, plus `onDepartureOffsetChanged(offsetHours: Int)` and `onRouteTimingModeChanged(mode: RouteTimingMode)`.

- [ ] **Step 1: Write failing ViewModel tests**

```kotlin
@Test
fun slider_reuses_snapshot_without_fetching_weather_again() = runTest {
    chooseEndpoints(viewModel)
    viewModel.calculate()
    advanceUntilIdle()

    viewModel.onDepartureOffsetChanged(24)
    advanceUntilIdle()

    assertEquals(24, viewModel.state.value.departureOffsetHours)
    assertEquals(1, weatherSource.fetchCount)
    assertEquals(departure.plus(24.hours), viewModel.state.value.result!!.departure)
}
```

Also test clamping to `0..72`, immediate selected-departure display, route-estimate timing, missing snapshot coverage causing one debounced fetch, rapid slider changes cancelling obsolete refresh work, calculation replacing the snapshot, weather failure retaining the route, and input changes marking the result outdated.

- [ ] **Step 2: Run the ViewModel tests and verify they fail**

Run: `./gradlew test --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteViewModelTest`

Expected: compilation failures for missing state fields and slider actions, followed by behavioral failures until implementation is complete.

- [ ] **Step 3: Implement graph-shared trip state**

Add to `WeatherRouteUiState`:

```kotlin
val departureOffsetHours: Int = 0
val routeTimingMode: RouteTimingMode = RouteTimingMode.AVERAGE_SPEED
val snapshot: RouteWeatherSnapshot? = null
val summary: TripWeatherSummary? = null
val warnings: List<TripWeatherWarning> = emptyList()
```

Keep the existing endpoint/search/error state and production source constructor. Treat `departure` as the base departure selected by date/time controls; derive the selected departure as `departure + departureOffsetHours.hours`. `calculate()` routes and samples using the selected timing mode, fetches one snapshot, enriches the internal samples, and publishes summaries and warnings without discarding the route on weather failure.

Implement `onDepartureOffsetChanged()` as an immediate state update followed by a 300 ms debounced `flatMapLatest` refresh flow. When the current snapshot contains every requested arrival, enrich from memory and publish immediately. When it does not, fetch one new snapshot for the existing sample points; cancel it when a newer offset or route calculation supersedes it. Preserve `CancellationException` and use the existing concise error-message mapping for other failures.

`retryWeather()` fetches a fresh snapshot for the current route points and reapplies the current selected departure. No saved trip or recent-trip state is added.

- [ ] **Step 4: Run ViewModel tests and inspect the diff**

Run: `./gradlew test --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteViewModelTest`

Expected: PASS.

Run: `git diff --check`

Expected: no output.

## Task 4: Trip Setup Screen And Departure Controls

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/RouteEndpointPicker.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteScreen.kt`
- Create: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/TripWeatherSetupScreen.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/RouteEndpointPickerTest.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteScreenTest.kt`
- Create: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/TripWeatherSetupScreenTest.kt`

**Interfaces:**
- Consumes: `WeatherRouteUiState`, existing endpoint callbacks, unit settings, and `WeatherRouteViewModel` actions.
- Produces: `TripWeatherSetupScreen` and a retained `WeatherRouteScreen` wrapper that delegates to setup for existing callers/tests.

- [ ] **Step 1: Write failing Compose tests**

```kotlin
composeRule.onNodeWithTag("trip_departure_slider")
    .assert(SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo(0f, 72f, 0)
    ))
composeRule.onNodeWithTag("trip_departure_offset_24").performClick()
composeRule.onNodeWithText("+24h").assertIsDisplayed()
composeRule.onNodeWithTag("trip_route_estimates").performClick()
composeRule.onNodeWithTag("trip_show_weather").assertIsEnabled()
```

Verify endpoint test tags and search behavior from the current picker remain intact, incomplete endpoints disable calculation, and the setup screen shows loading, route error, retry, weather-only retry, and outdated-result states without replacing existing visual hierarchy.

- [ ] **Step 2: Run setup and picker tests and verify the new assertions fail**

Run: `./gradlew test --tests net.droopia.hluweather.ui.weatherroute.RouteEndpointPickerTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteScreenTest --tests net.droopia.hluweather.ui.weatherroute.TripWeatherSetupScreenTest`

Expected: failures for the new setup screen, slider, and route-estimate controls.

- [ ] **Step 3: Implement setup UI using existing visual primitives**

Move the current route form composition into `TripWeatherSetupScreen` while retaining the endpoint picker callbacks and stable tags. Add native date/time controls, average speed default `80 km/h`, the `Use route estimates` switch with supporting text, and a discrete `Slider` with `steps = 71`, `valueRange = 0f..72f`, and test tag `trip_departure_slider`. Add visible `Now`, `+24h`, `+48h`, and `+72h` labels and make those labels invoke the same ViewModel offset event.

Use `HluWeatherIcon` only for weather visuals. Use existing cards, theme colors, typography, spacing, unit formatters, and rounded button treatments. Keep `WeatherRouteScreen` as a thin setup-compatible entry point rather than duplicating the form. Do not add Save trip or recent-trip UI.

- [ ] **Step 4: Run setup and picker tests and inspect the diff**

Run: `./gradlew test --tests net.droopia.hluweather.ui.weatherroute.RouteEndpointPickerTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteScreenTest --tests net.droopia.hluweather.ui.weatherroute.TripWeatherSetupScreenTest`

Expected: PASS.

Run: `git diff --check`

Expected: no output.

## Task 5: Overview Screen And Shared Route Map

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/TripWeatherOverviewScreen.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteMap.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTimeline.kt`
- Create: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/TripWeatherOverviewScreenTest.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteMapTest.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTimelineTest.kt`

**Interfaces:**
- Consumes: `WeatherRouteResult`, `TripWeatherSummary`, `TripWeatherWarning`, `WeatherRouteUiState`, and existing map/timeline callbacks.
- Produces: `TripWeatherOverviewScreen` with `onDetailsClick`, `onShareClick`, and `onBackClick`; updated `WeatherRouteMap` and timeline components reusable by details.

- [ ] **Step 1: Write failing overview, map, and timeline tests**

```kotlin
composeRule.onNodeWithTag("trip_overview_summary").assertIsDisplayed()
composeRule.onNodeWithTag("trip_overview_warning_rain").assertIsDisplayed()
composeRule.onNodeWithTag("trip_overview_details").performClick()
composeRule.onNodeWithTag("route_weather_marker_1").performClick()
assertEquals(1, selectedIndex)
```

Add tests for departure/max/rain/wind summary values, missing-weather dashes, warning ranges, optional elevation visibility, light/dark marker rendering, shared `HluWeatherIcon` use, route polyline and endpoint markers, and selected marker camera callback. Verify timeline presents hourly checkpoints plus destination rather than every 30-minute internal sample.

- [ ] **Step 2: Run focused UI tests and verify the new assertions fail**

Run: `./gradlew test --tests net.droopia.hluweather.ui.weatherroute.TripWeatherOverviewScreenTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteMapTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteTimelineTest`

Expected: failures for the overview screen and new summary/warning/icon behavior.

- [ ] **Step 3: Implement the overview and update reusable route components**

Build a vertically scrollable overview with the existing top-bar treatment, `From -> To`, selected departure, speed, rounded map surface, route summary, weather-along-route summary, warning cards, optional elevation card, highlights, Share callback, and Details action. Keep the existing map style URLs, route GeoJSON, endpoint markers, fit-to-route behavior, tile error state, and selected sample state.

Replace number-only weather marker visuals with `HluWeatherIcon(condition, isDay)` inside the existing marker touch target, while preserving marker tags and severity colors. Update timeline rows to use `HluWeatherIcon`, `displayRouteSamples()`, place-label fallback, destination labeling, precipitation amount/probability, humidity where available, unit formatters, and warning sections. Keep unavailable values as `Weather unavailable` and dashes.

- [ ] **Step 4: Run focused UI tests and inspect the diff**

Run: `./gradlew test --tests net.droopia.hluweather.ui.weatherroute.TripWeatherOverviewScreenTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteMapTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteTimelineTest`

Expected: PASS.

Run: `git diff --check`

Expected: no output.

## Task 6: Details Timeline, Map, And Table Tabs

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/TripWeatherDetailsScreen.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTimeline.kt`
- Create: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTable.kt`
- Create: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/TripWeatherDetailsScreenTest.kt`

**Interfaces:**
- Consumes: shared `WeatherRouteUiState`, `WeatherRouteMap`, `WeatherRouteTimeline`, `RouteWeatherSample`, destination data, and unit settings.
- Produces: `TripWeatherDetailsScreen` with tabs `Timeline`, `Map`, and `Table`, plus `WeatherRouteTable` using the same selected sample callback.

- [ ] **Step 1: Write failing details and table tests**

```kotlin
composeRule.onNodeWithText("Table").performClick()
composeRule.onNodeWithTag("route_weather_table").assertIsDisplayed()
composeRule.onNodeWithTag("route_weather_table_row_1").assertTextContains("km")
composeRule.onNodeWithText("Map").performClick()
composeRule.onNodeWithTag("weather_route_map").assertIsDisplayed()
```

Verify the Timeline tab includes hourly rows, destination weather, and warnings; the Map tab reuses the map and marker selection; and the Table tab is horizontally scrollable with time, weather, location, temperature, precipitation, wind, and distance columns.

- [ ] **Step 2: Run the details test and verify it fails**

Run: `./gradlew test --tests net.droopia.hluweather.ui.weatherroute.TripWeatherDetailsScreenTest`

Expected: compilation failures because the details screen and table do not exist.

- [ ] **Step 3: Implement the three details tabs**

Use the existing tab visual language and stable selected-tab semantics. `Timeline` delegates to the reusable timeline and renders destination/warning sections. `Map` delegates to the same `WeatherRouteMap` with the shared selected sample. `Table` uses a `Row` inside `horizontalScroll(rememberScrollState())`, fixed readable column widths, existing table colors/typography/dividers, and one stable test tag per row.

Display destination temperature, condition, precipitation, wind, and humidity. For each warning display type, time range, and route location when present. Keep all display values unit-aware through `temperatureValueText`, `precipitationText`, `windSpeedText`, and `distanceText`.

- [ ] **Step 4: Run the details tests and inspect the diff**

Run: `./gradlew test --tests net.droopia.hluweather.ui.weatherroute.TripWeatherDetailsScreenTest`

Expected: PASS.

Run: `git diff --check`

Expected: no output.

## Task 7: Nested Trip Navigation And Production Wiring

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/navigation/HluNavHost.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherScreen.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/HluWeatherApplication.kt` only if constructor exposure needs adjustment
- Modify: `app/src/test/java/net/droopia/hluweather/navigation/HluNavHostTest.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/weather/WeatherScreenTest.kt`

**Interfaces:**
- Consumes: existing application-level `routingSource`, `routePlaceSearchSources`, `routeWeatherSource`, `locationRepository`, `deviceLocationSource`, and the three trip screens.
- Produces: routes `trip/setup`, `trip/overview`, and `trip/details` under graph route `trip`, with one graph-scoped `WeatherRouteViewModel`.

- [ ] **Step 1: Write failing navigation tests**

```kotlin
composeRule.onNodeWithTag("weather_route_open").performClick()
composeRule.onNodeWithTag("trip_setup_screen").assertIsDisplayed()
composeRule.onNodeWithTag("trip_show_weather").performClick()
composeRule.onNodeWithTag("trip_overview_screen").assertIsDisplayed()
composeRule.onNodeWithTag("trip_overview_details").performClick()
composeRule.onNodeWithTag("trip_details_screen").assertIsDisplayed()
composeRule.onNodeWithTag("trip_details_back").performClick()
composeRule.onNodeWithTag("trip_overview_screen").assertIsDisplayed()
```

Assert that setup and overview observe the same endpoint/result state, and that Back from setup returns to Weather. Keep the existing Map-mode entry tag `weather_route_open`.

- [ ] **Step 2: Run navigation tests and verify the new graph assertions fail**

Run: `./gradlew test --tests net.droopia.hluweather.navigation.HluNavHostTest --tests net.droopia.hluweather.ui.weather.WeatherScreenTest`

Expected: failures because the current single `weather_route` destination is not the nested trip graph.

- [ ] **Step 3: Implement the graph-scoped ViewModel and destinations**

Replace navigation to `weather_route` with navigation to `trip`. Add a `navigation(startDestination = "trip/setup", route = "trip")` graph. In each destination, obtain the parent entry with `navController.getBackStackEntry("trip")` and call `viewModel(parentEntry, factory = tripViewModelFactory(application))` so all three screens share one instance.

Pass current saved locations and settings units into setup/overview/details. Wire overview Details to `trip/details`, each screen's Back to the appropriate `popBackStack()`, and Share to the existing callback boundary without persistence. Reuse application lazy sources; do not add a second HTTP client or provider selector for route weather.

- [ ] **Step 4: Run navigation tests and inspect the diff**

Run: `./gradlew test --tests net.droopia.hluweather.navigation.HluNavHostTest --tests net.droopia.hluweather.ui.weather.WeatherScreenTest`

Expected: PASS.

Run: `git diff --check`

Expected: no output.

## Task 8: Previews, Documentation, And Full Verification

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/TripWeatherSetupScreen.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/TripWeatherOverviewScreen.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/TripWeatherDetailsScreen.kt`
- Modify: `docs/WEATHER_ON_ROUTE.md` if it still describes the old single-screen behavior
- Test: all route and navigation tests from Tasks 1-7

**Interfaces:**
- Consumes: completed trip flow and approved design specification.
- Produces: deterministic light/dark previews, accurate Android documentation, and verified build artifacts.

- [ ] **Step 1: Add deterministic light and dark previews**

Create preview-only setup, overview, and details states with fixed endpoints, route samples, cached hourly forecasts, summary, warnings, and no network calls. Wrap each in the existing app theme and verify `HluWeatherIcon` day/night rendering and optional elevation behavior in both themes.

- [ ] **Step 2: Update feature documentation**

Document the Android Map-mode entry, three-screen flow, OSRM routing, Open-Meteo-only route weather, 80 km/h default, 0..72 hour slider, cached snapshot behavior, timeline/map/table tabs, warnings, error/retry states, and explicit exclusion of saved/recent trips and MET.no route weather. Remove obsolete web or single-screen claims.

- [ ] **Step 3: Run the complete quality suite**

Run: `./gradlew test lint assembleDebug --console=plain`

Expected: `BUILD SUCCESSFUL`, tests pass, lint reports no new errors, and a debug APK is produced under `app/build/outputs/apk/debug/`.

- [ ] **Step 4: Perform manual emulator/device verification**

Open Weather Map mode, choose Weather on route, select two endpoints, calculate a route, move the slider to `+24h` and `+72h`, switch route-estimate mode, open Details, switch Timeline/Map/Table, tap a map marker, verify destination/warnings, rotate or use light/dark themes, and trigger route/weather retry states. Confirm no slider request is made when cached hourly coverage is sufficient.

- [ ] **Step 5: Inspect final worktree**

Run: `git status --short`

Expected: only intended Trip Weather changes plus pre-existing unrelated changes; do not modify or revert unrelated work.

## Plan Self-Review

### Spec Coverage

- Extend in place and reuse existing theme, icons, map, route source, units, and endpoint controls: Tasks 4-6.
- Nested three-screen navigation with one shared ViewModel: Task 7.
- Average-speed and route-estimate timing: Task 1.
- 30-minute internal samples and hourly/destination filtering: Tasks 1 and 5.
- Cached full hourly snapshot, 0..72 slider, 300 ms debounce, and missing-coverage refresh: Tasks 2 and 3.
- Summary metrics, warnings, optional elevation, destination weather, and highlights: Tasks 1 and 5-6.
- Timeline, Map, and horizontally scrollable Table tabs: Task 6.
- Open-Meteo-only route weather and no saved/recent trips: Global Constraints and Tasks 2, 3, and 7.
- Light/dark previews, unit-aware display, tests, build, and manual verification: Task 8.

### Placeholder Scan

The plan contains no `TODO`, `TBD`, `FIXME`, or unresolved implementation step. Every task identifies exact files, public interfaces, test commands, and expected outcomes.

### Type Consistency

`RouteWeatherSnapshot` is produced by `RouteWeatherSource.fetchSnapshot()`, consumed by `RouteWeatherSnapshot.enrich()`, and stored in `WeatherRouteUiState`. `RouteTimingMode` flows from setup controls into `buildRouteSamples()`. `WeatherRouteResult` and derived `TripWeatherSummary`/`TripWeatherWarning` flow from the ViewModel into overview, timeline, map, and table screens. `selectedSampleIndex` remains the shared selection callback across all route visualizations.
