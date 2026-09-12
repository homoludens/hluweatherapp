# Weather On Route Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android driving-route planner that displays Open-Meteo weather at expected arrival points along an OSRM route.

**Architecture:** A dedicated `weather_route` Compose destination owns a `WeatherRouteViewModel`. Small source interfaces isolate OSRM routing, Photon/Open-Meteo place search, and Open-Meteo batch route weather. A pure sampling utility projects elapsed travel time onto the actual route polyline; MapLibre renders the route and selectable weather samples.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation Compose, Ktor 3.5.2 with kotlinx.serialization, kotlinx-datetime, MapLibre Compose 0.16.0, JUnit, coroutines-test, Robolectric, Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-09-12-weather-on-route-design.md`

## Global Constraints

- Keep the single Android module and existing `data`/`ui` package layout; do not add a domain module or dependency-injection framework.
- Support Android API 28+ and use only existing Ktor, MapLibre Compose, and Compose dependencies.
- Initial provider implementations are public OSRM, Photon, Open-Meteo Geocoding, and Open-Meteo Forecast. Never embed an ORS API key or silently switch routing providers.
- Use `GeoPoint(latitude, longitude)` internally. Translate to `[longitude, latitude]` only at HTTP and GeoJSON boundaries.
- The planner defaults to Photon search and Open-Meteo route weather. It must not read or change the app-wide Open-Meteo/MET.no weather-provider setting.
- Accept speed only in km/h, default 80, and validate inclusive range 50..240.
- Validate that the selected departure plus calculated trip duration is within the Open-Meteo forecast horizon before fetching weather.
- Request and display metric API values internally, then use existing temperature, wind, distance, and precipitation unit formatters for display.
- Preserve `CancellationException`; convert only non-cancellation failures into concise planner state messages.
- Respect public service usage with a descriptive User-Agent, a 300 ms search debounce, and no automatic routing fallback.
- Do not commit while implementing this plan unless the user explicitly requests a commit. At each task boundary inspect `git diff --check` and the targeted test output instead.

---

## File Structure

| Path | Responsibility |
|---|---|
| `data/model/WeatherRoute.kt` | Provider-neutral endpoint, route, sample, and completed-plan models. |
| `data/weatherroute/RouteSampling.kt` | Pure distance, polyline interpolation, sample scheduling, and weather-severity logic. |
| `data/repository/RoutingSource.kt` | Routing abstraction and OSRM implementation. |
| `data/repository/PlaceSearchSource.kt` | Place-search abstraction and provider selector enum. |
| `data/repository/RouteWeatherSource.kt` | Batch route-weather abstraction and Open-Meteo normalization. |
| `data/network/OsrmApi.kt` | Ktor OSRM route request plus serializable DTOs. |
| `data/network/PhotonApi.kt` | Ktor Photon search request plus GeoJSON DTOs. |
| `data/network/OpenMeteoGeocodingApi.kt` | Ktor Open-Meteo geocoding request plus DTOs. |
| `data/network/OpenMeteoRouteWeatherApi.kt` | Ktor multi-coordinate Open-Meteo forecast request plus DTOs. |
| `ui/weatherroute/WeatherRouteViewModel.kt` | Planner state, endpoint/search actions, validation, and request orchestration. |
| `ui/weatherroute/WeatherRouteScreen.kt` | Full planner screen and form/result state composition. |
| `ui/weatherroute/RouteEndpointPicker.kt` | Search, saved-location, current-location, and map-selection endpoint controls. |
| `ui/weatherroute/WeatherRouteMap.kt` | MapLibre route line, endpoint/weather markers, fit/recenter, and marker selection. |
| `ui/weatherroute/WeatherRouteTimeline.kt` | Selectable, unit-aware arrival timeline. |
| `navigation/HluNavHost.kt` | `weather_route` destination, factories, and Map-mode launch callback. |
| `ui/weather/WeatherScreen.kt` | Visible Map-mode Weather on route action and callback plumbing. |
| `HluWeatherApplication.kt` | Construction of the source implementations from the shared Ktor client. |
| `docs/WEATHER_ON_ROUTE.md` | Android-specific feature overview; replace obsolete web/Next.js status claims. |

### Task 1: Route Models And Sampling Mathematics

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/data/model/WeatherRoute.kt`
- Create: `app/src/main/java/net/droopia/hluweather/data/weatherroute/RouteSampling.kt`
- Test: `app/src/test/java/net/droopia/hluweather/data/weatherroute/RouteSamplingTest.kt`

**Interfaces:**
- Consumes: `GeoPoint` and `WeatherCondition` from `data/model`.
- Produces: `RouteEndpoint`, `DrivingRoute`, `RouteWeatherSample`, `WeatherRouteResult`, `WeatherSeverity`, `buildRouteSamples()`, and `weatherSeverity()` for repositories and UI.

- [ ] **Step 1: Write failing sampling tests**

```kotlin
@Test
fun buildRouteSamples_interpolates_each_hour_on_the_route_geometry() {
    val route = DrivingRoute("OSRM", listOf(
        GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0), GeoPoint(1.0, 1.0)
    ), distanceMeters = 222_390.0, providerDurationSeconds = 10_000.0)

    val samples = buildRouteSamples(
        route = route,
        departure = Instant.parse("2026-09-12T10:00:00Z"),
        averageSpeedKmh = 80
    )

    assertEquals(4, samples.size)
    assertEquals(0.0, samples[0].distanceMeters, 0.1)
    assertEquals(80_000.0, samples[1].distanceMeters, 0.1)
    assertEquals(222_390.0, samples.last().distanceMeters, 0.1)
    assertEquals(Instant.parse("2026-09-12T12:46:47.550Z"), samples.last().arrivalTime)
}

@Test
fun buildRouteSamples_keeps_distinct_start_and_destination_for_short_route() {
    val route = DrivingRoute("OSRM", listOf(GeoPoint(44.8, 20.4), GeoPoint(44.81, 20.41)), 1_000.0, 100.0)
    val samples = buildRouteSamples(route, Instant.parse("2026-09-12T10:00:00Z"), 80)
    assertEquals(2, samples.size)
    assertEquals(0.0, samples.first().distanceMeters, 0.1)
    assertEquals(1_000.0, samples.last().distanceMeters, 0.1)
}
```

- [ ] **Step 2: Run the sampling test to verify it fails**

Run: `./gradlew test --tests net.droopia.hluweather.data.weatherroute.RouteSamplingTest`

Expected: compilation fails because `DrivingRoute` and `buildRouteSamples` do not exist.

- [ ] **Step 3: Define provider-neutral models and pure calculations**

Create models with nullable weather values so a valid route can render when weather is wholly or partially unavailable:

```kotlin
data class RouteEndpoint(val label: String, val point: GeoPoint)

data class DrivingRoute(
    val providerName: String,
    val polyline: List<GeoPoint>,
    val distanceMeters: Double,
    val providerDurationSeconds: Double
)

data class RouteWeatherSample(
    val point: GeoPoint,
    val distanceMeters: Double,
    val arrivalTime: Instant,
    val condition: WeatherCondition? = null,
    val temperatureCelsius: Double? = null,
    val windSpeedKmh: Double? = null,
    val precipitationProbability: Int? = null
)

data class WeatherRouteResult(
    val start: RouteEndpoint,
    val end: RouteEndpoint,
    val route: DrivingRoute,
    val departure: Instant,
    val averageSpeedKmh: Int,
    val samples: List<RouteWeatherSample>
)
```

Implement Haversine segment lengths. `buildRouteSamples()` must compute the chosen-speed duration from `route.distanceMeters`, create offsets `0`, every full elapsed hour strictly before arrival, then arrival, and interpolate each offset over cumulative segment distance. Reject routes with fewer than two points or non-positive distance using `IllegalArgumentException`.

Add:

```kotlin
enum class WeatherSeverity { FAVORABLE, CAUTION, ADVERSE, SEVERE, UNAVAILABLE }

fun weatherSeverity(condition: WeatherCondition?): WeatherSeverity = when (condition) {
    WeatherCondition.CLEAR, WeatherCondition.MOSTLY_CLEAR, WeatherCondition.PARTLY_CLOUDY -> WeatherSeverity.FAVORABLE
    WeatherCondition.CLOUDY, WeatherCondition.FOG, WeatherCondition.DRIZZLE -> WeatherSeverity.CAUTION
    WeatherCondition.RAIN, WeatherCondition.SNOW -> WeatherSeverity.ADVERSE
    WeatherCondition.THUNDERSTORM -> WeatherSeverity.SEVERE
    null, WeatherCondition.UNKNOWN -> WeatherSeverity.UNAVAILABLE
}
```

- [ ] **Step 4: Add boundary tests and run them**

Add tests for an arrival exactly on an hour (no duplicated destination), invalid speed, invalid geometry, and each `WeatherSeverity`. Run: `./gradlew test --tests net.droopia.hluweather.data.weatherroute.RouteSamplingTest`

Expected: PASS.

- [ ] **Step 5: Inspect task output**

Run: `git diff --check`

Expected: no output.

### Task 2: OSRM Routing Source

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/data/network/OsrmApi.kt`
- Create: `app/src/main/java/net/droopia/hluweather/data/repository/RoutingSource.kt`
- Test: `app/src/test/java/net/droopia/hluweather/data/network/OsrmApiTest.kt`
- Test: `app/src/test/java/net/droopia/hluweather/data/repository/OsrmRoutingSourceTest.kt`

**Interfaces:**
- Consumes: `GeoPoint` and `DrivingRoute` from Task 1 plus the shared Ktor `HttpClient`.
- Produces: `interface RoutingSource { val providerName: String; suspend fun route(start: GeoPoint, end: GeoPoint): DrivingRoute }` and `OsrmRoutingSource`.

- [ ] **Step 1: Write failing Ktor API tests**

```kotlin
@Test
fun route_sends_driving_geojson_full_overview_request() = runTest {
    val route = KtorOsrmApi(mockClient { request ->
        captured = request
        respond(okRouteJson, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    }, "https://osrm.test").route(GeoPoint(44.8, 20.4), GeoPoint(45.6, 13.7))

    assertEquals("/route/v1/driving/20.4,44.8;13.7,45.6", captured!!.url.encodedPath)
    assertEquals("geojson", captured!!.url.parameters["geometries"])
    assertEquals("full", captured!!.url.parameters["overview"])
    assertEquals("Ok", route.code)
}
```

Add a non-2xx test and an `Ok` response with an empty `routes` list test.

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew test --tests net.droopia.hluweather.data.network.OsrmApiTest`

Expected: compilation fails because `KtorOsrmApi` does not exist.

- [ ] **Step 3: Implement OSRM API and route adaptation**

Request `GET /route/v1/driving/{start.lon},{start.lat};{end.lon},{end.lat}` with `geometries=geojson`, `overview=full`, and `steps=false`. Default `KtorOsrmApi` to `https://router.project-osrm.org` and keep its base URL constructor-injectable for tests and future self-hosted/alternative sources. Decode only `code`, optional `message`, and the first route's `distance`, `duration`, and `geometry.coordinates`.

`KtorOsrmApi` throws `OsrmApiException("Routing service returned HTTP <status>")` for HTTP failures. `OsrmRoutingSource` throws `RoutingException("No driving route found")` if `code != "Ok"`, the first route is absent, or the geometry has fewer than two valid longitude/latitude pairs. On success it returns:

```kotlin
DrivingRoute(
    providerName = "OSRM",
    polyline = dto.geometry.coordinates.map { (longitude, latitude) -> GeoPoint(latitude, longitude) },
    distanceMeters = dto.distance,
    providerDurationSeconds = dto.duration
)
```

- [ ] **Step 4: Test mapping and errors**

Add `OsrmRoutingSourceTest` assertions for longitude/latitude conversion, route summary fields, `NoRoute`, and malformed geometry. Run:

`./gradlew test --tests net.droopia.hluweather.data.network.OsrmApiTest --tests net.droopia.hluweather.data.repository.OsrmRoutingSourceTest`

Expected: PASS.

- [ ] **Step 5: Inspect task output**

Run: `git diff --check`

Expected: no output.

### Task 3: Swappable Photon And Open-Meteo Place Search

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/data/network/PhotonApi.kt`
- Create: `app/src/main/java/net/droopia/hluweather/data/network/OpenMeteoGeocodingApi.kt`
- Create: `app/src/main/java/net/droopia/hluweather/data/repository/PlaceSearchSource.kt`
- Test: `app/src/test/java/net/droopia/hluweather/data/network/PhotonApiTest.kt`
- Test: `app/src/test/java/net/droopia/hluweather/data/network/OpenMeteoGeocodingApiTest.kt`
- Test: `app/src/test/java/net/droopia/hluweather/data/repository/PlaceSearchSourceTest.kt`

**Interfaces:**
- Consumes: `GeoPoint` from existing models and Ktor from Task 2.
- Produces: `PlaceSearchProvider`, `PlaceSearchResult`, `PlaceSearchSource`, `PhotonPlaceSearchSource`, and `OpenMeteoPlaceSearchSource` for the ViewModel.

- [ ] **Step 1: Write failing API/request tests**

Test Photon request `GET /api/?q=trieste&limit=8&lang=en` and assert that the configured `User-Agent` is present. Decode a `FeatureCollection` whose `features[].geometry.coordinates` are longitude/latitude pairs and whose label is composed from `name`, `city`, `state`, and `country`, omitting blanks and duplicates.

Test Open-Meteo request `GET /v1/search?name=trieste&count=8&language=en&format=json`, mapping each result to a label such as `Trieste, Friuli Venezia Giulia, Italy`. Test non-2xx responses and missing/empty result arrays for both APIs.

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew test --tests net.droopia.hluweather.data.network.PhotonApiTest --tests net.droopia.hluweather.data.network.OpenMeteoGeocodingApiTest`

Expected: compilation fails because the search APIs and source types do not exist.

- [ ] **Step 3: Implement normalized place sources**

Define:

```kotlin
enum class PlaceSearchProvider { PHOTON, OPEN_METEO }

data class PlaceSearchResult(val label: String, val point: GeoPoint)

interface PlaceSearchSource {
    val provider: PlaceSearchProvider
    suspend fun search(query: String): List<PlaceSearchResult>
}
```

Make each network API throw a provider-specific `IOException` on HTTP failure. Sources must trim the query, return `emptyList()` for blank input without an HTTP request, discard invalid latitude/longitude pairs, and de-duplicate same-coordinate results. Use `HluWeather/3.2 route planner` as the Photon User-Agent. Do not use the existing reverse-geocoder abstraction because forward search has different results and service selection.

- [ ] **Step 4: Verify source behavior**

Add source tests for blank queries, normalized labels, invalid coordinates, and duplicate results. Run:

`./gradlew test --tests net.droopia.hluweather.data.network.PhotonApiTest --tests net.droopia.hluweather.data.network.OpenMeteoGeocodingApiTest --tests net.droopia.hluweather.data.repository.PlaceSearchSourceTest`

Expected: PASS.

- [ ] **Step 5: Inspect task output**

Run: `git diff --check`

Expected: no output.

### Task 4: Batched Open-Meteo Route Weather

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/data/network/OpenMeteoRouteWeatherApi.kt`
- Create: `app/src/main/java/net/droopia/hluweather/data/repository/RouteWeatherSource.kt`
- Test: `app/src/test/java/net/droopia/hluweather/data/network/OpenMeteoRouteWeatherApiTest.kt`
- Test: `app/src/test/java/net/droopia/hluweather/data/repository/OpenMeteoRouteWeatherSourceTest.kt`

**Interfaces:**
- Consumes: `RouteWeatherSample` from Task 1 and the existing `WeatherCondition` mapping semantics.
- Produces: `interface RouteWeatherSource { suspend fun enrich(samples: List<RouteWeatherSample>): List<RouteWeatherSample> }` and `OpenMeteoRouteWeatherSource`.

- [ ] **Step 1: Write failing batch API tests**

```kotlin
@Test
fun forecast_batches_coordinates_and_requests_driving_fields() = runTest {
    KtorOpenMeteoRouteWeatherApi(client, "https://open-meteo.test").forecast(points)

    assertEquals("44.8,45.6", captured!!.url.parameters["latitude"])
    assertEquals("20.4,13.7", captured!!.url.parameters["longitude"])
    assertEquals("temperature_2m,weather_code,wind_speed_10m,precipitation_probability", captured!!.url.parameters["hourly"])
    assertEquals("unixtime", captured!!.url.parameters["timeformat"])
    assertEquals("GMT", captured!!.url.parameters["timezone"])
    assertEquals("16", captured!!.url.parameters["forecast_days"])
}
```

Use fixture responses in the multi-location array shape. Include a location whose requested arrival instant lies halfway between forecast hours.

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew test --tests net.droopia.hluweather.data.network.OpenMeteoRouteWeatherApiTest --tests net.droopia.hluweather.data.repository.OpenMeteoRouteWeatherSourceTest`

Expected: compilation fails because the route-weather API and source do not exist.

- [ ] **Step 3: Implement request, normalization, and unavailable samples**

Open-Meteo accepts comma-separated coordinate lists. Request the four hourly fields above with `timeformat=unixtime`, `timezone=GMT`, `forecast_days=16`, metric units, and no current/daily fields. Decode both a single-object and array response defensively, preserving response order.

For every requested sample, choose the hourly entry nearest to `sample.arrivalTime`; when equally distant choose the later forecast hour. Map WMO codes with the same rules as `OpenMeteoWeatherRepository`. A malformed or missing hourly entry makes only that sample unavailable; it must not discard the route or other samples. A request-level failure still throws so the ViewModel can show the weather-retry state.

- [ ] **Step 4: Verify source results**

Test exact-hour selection, half-hour tie selecting the later hour, WMO rain/thunderstorm mapping, one invalid location response producing one unavailable sample, and request-level HTTP failure. Run:

`./gradlew test --tests net.droopia.hluweather.data.network.OpenMeteoRouteWeatherApiTest --tests net.droopia.hluweather.data.repository.OpenMeteoRouteWeatherSourceTest`

Expected: PASS.

- [ ] **Step 5: Inspect task output**

Run: `git diff --check`

Expected: no output.

### Task 5: Weather Route ViewModel And Planner State

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteViewModel.kt`
- Test: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteViewModelTest.kt`

**Interfaces:**
- Consumes: source interfaces from Tasks 2-4, `LocationRepository`, `DeviceLocationSource`, and existing unit settings read by navigation.
- Produces: `WeatherRouteUiState`, endpoint/search actions, `calculate()`, `retryWeather()`, and `Factory` for the screen.

- [ ] **Step 1: Write failing ViewModel state tests**

Cover these public events and observable outcomes:

```kotlin
viewModel.onSearchQueryChanged(RouteEndpointSlot.START, "trieste")
advanceTimeBy(300)
assertEquals(listOf(trieste), viewModel.state.value.searchResults)

viewModel.selectSearchResult(trieste)
viewModel.selectSavedLocation(RouteEndpointSlot.END, belgrade)
viewModel.onSpeedChanged("80")
viewModel.onDepartureChanged(Instant.parse("2026-09-12T10:00:00Z"))
viewModel.calculate()

assertEquals(expectedResult, viewModel.state.value.result)
assertFalse(viewModel.state.value.isCalculating)
```

Also write tests for invalid speed, incomplete endpoints, arrival outside a supplied forecast limit, routing failure, partial weather result, full weather failure retaining the route, weather retry, input change marking a result outdated, and a superseded route request never updating state.

- [ ] **Step 2: Run ViewModel tests to verify failure**

Run: `./gradlew test --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteViewModelTest`

Expected: compilation fails because `WeatherRouteViewModel` does not exist.

- [ ] **Step 3: Implement state and orchestration**

Use these state shapes so the composables remain stateless:

```kotlin
enum class RouteEndpointSlot { START, END }

data class WeatherRouteUiState(
    val start: RouteEndpoint? = null,
    val end: RouteEndpoint? = null,
    val activeSearchSlot: RouteEndpointSlot? = null,
    val searchProvider: PlaceSearchProvider = PlaceSearchProvider.PHOTON,
    val searchQuery: String = "",
    val searchResults: List<PlaceSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val departure: Instant = nextFullHour(),
    val speedText: String = "80",
    val result: WeatherRouteResult? = null,
    val isCalculating: Boolean = false,
    val isRetryingWeather: Boolean = false,
    val routeError: String? = null,
    val weatherError: String? = null,
    val isResultOutdated: Boolean = false,
    val selectedSampleIndex: Int? = null
)
```

Keep a monotonically increasing calculation generation. `calculate()` validates resolved endpoints, integer speed `50..240`, departure not before `now()`, and `departure + route distance / speed` before `forecastEnd()`. It then calls `RoutingSource.route`, `buildRouteSamples`, and `RouteWeatherSource.enrich` in that order. A weather exception creates a `WeatherRouteResult` with the un-enriched samples and sets `weatherError`; a routing exception leaves no result and sets `routeError`. Re-throw `CancellationException`.

Define `forecastEnd()` as `now() + 16.days` and define `nextFullHour()` by converting `now()` to the device timezone, truncating to the local hour, adding one hour, and converting back to an `Instant`. Search with `debounce(300)` and `flatMapLatest` over current query, selected provider, and active slot. Invoke the existing `DeviceLocationSource.currentLocation()` only when the user chooses current location; propagate its `GpsResult` state into a concise planner message.

- [ ] **Step 4: Run ViewModel tests**

Run: `./gradlew test --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteViewModelTest`

Expected: PASS.

- [ ] **Step 5: Inspect task output**

Run: `git diff --check`

Expected: no output.

### Task 6: Endpoint Selection Controls

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/RouteEndpointPicker.kt`
- Test: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/RouteEndpointPickerTest.kt`

**Interfaces:**
- Consumes: `WeatherRouteUiState`, `RouteEndpointSlot`, `PlaceSearchResult`, `WeatherLocation`, and ViewModel actions from Task 5.
- Produces: `RouteEndpointPicker` and a map-picker dialog callback returning a `RouteEndpoint`.

- [ ] **Step 1: Write failing Compose tests**

Render the picker with fake state and verify the following test tags/actions:

```kotlin
composeRule.onNodeWithTag("route_start_search").performTextInput("Trieste")
composeRule.onNodeWithText("Trieste, Friuli Venezia Giulia, Italy").performClick()
composeRule.onNodeWithTag("route_start_label").assertTextContains("Trieste")
composeRule.onNodeWithTag("route_search_provider_open_meteo").performClick()
composeRule.onNodeWithTag("route_end_saved_locations").performClick()
```

Test that an endpoint result cannot be replaced merely by changing unfinished search text and that the current-location action is absent for the destination slot.

- [ ] **Step 2: Run the picker test to verify failure**

Run: `./gradlew test --tests net.droopia.hluweather.ui.weatherroute.RouteEndpointPickerTest`

Expected: compilation fails because `RouteEndpointPicker` does not exist.

- [ ] **Step 3: Implement search, saved, current, and map choices**

Build a Material 3 endpoint card with an `OutlinedTextField`, Photon/Open-Meteo selector, result list, and actions. Both slots accept search, saved locations, and map selection. Only the start slot has `Use my location`.

Reuse the existing `WeatherMap` camera-idle callback in a full-screen map picker with a fixed center marker, following `LocationPickerScreen` behavior. On confirmation, create `RouteEndpoint("Selected map point", selectedPoint)`; never call `LocationRepository.add` or `update`. The selected place search result must be used exactly as returned, not re-geocoded.

- [ ] **Step 4: Run picker tests**

Run: `./gradlew test --tests net.droopia.hluweather.ui.weatherroute.RouteEndpointPickerTest`

Expected: PASS.

- [ ] **Step 5: Inspect task output**

Run: `git diff --check`

Expected: no output.

### Task 7: Route Map And Weather Timeline

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteMap.kt`
- Create: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTimeline.kt`
- Test: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteMapTest.kt`
- Test: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTimelineTest.kt`

**Interfaces:**
- Consumes: `WeatherRouteResult`, `RouteWeatherSample`, `weatherSeverity()`, and settings units.
- Produces: selectable `WeatherRouteMap` and `WeatherRouteTimeline` with a shared selected sample index.

- [ ] **Step 1: Write failing pure/UI tests**

Test marker color/category from each severity, a neutral unavailable marker, and selection callback propagation. Test timeline content for expected arrival time, converted unit values, travelled distance, destination label, and unavailable weather text.

```kotlin
composeRule.onNodeWithTag("route_timeline_item_1").performClick()
assertEquals(1, selectedIndex)
composeRule.onNodeWithTag("route_timeline_destination").assertIsDisplayed()
composeRule.onNodeWithText("Weather unavailable").assertIsDisplayed()
```

- [ ] **Step 2: Run map/timeline tests to verify failure**

Run: `./gradlew test --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteMapTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteTimelineTest`

Expected: compilation fails because the route map and timeline do not exist.

- [ ] **Step 3: Implement map line, markers, and timeline**

Build a route `LineString` GeoJSON source with `rememberGeoJsonSource(GeoJsonData.JsonString(...))`, then render it with MapLibre Compose `LineLayer`, `LineCap.Round`, `LineJoin.Round`, and a 4-5 dp primary-colored stroke. This is separate from the saved-location `WeatherMap` markers.

Render start/end and sample markers with `placedAt`, as the existing `WeatherMap` does. Give samples 48 dp touch targets, color them from `WeatherSeverity`, and visibly enlarge/outline the selected marker. Fit the initial camera to route bounds; update only when a selected marker changes. Retain MapLibre's tile-failure message.

Render a `LazyColumn` timeline. Each stable key is its sample index; include tags `route_timeline_item_<index>` and `route_timeline_destination`. Use `hourText`, `temperatureValueText`, `windSpeedText`, `percentText`, and `distanceText` from `data/Format.kt`. An unavailable sample displays `Weather unavailable` and dashes for missing measures.

- [ ] **Step 4: Run map/timeline tests**

Run: `./gradlew test --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteMapTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteTimelineTest`

Expected: PASS.

- [ ] **Step 5: Inspect task output**

Run: `git diff --check`

Expected: no output.

### Task 8: Planner Screen, Application Wiring, And Navigation

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteScreen.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/HluWeatherApplication.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/navigation/HluNavHost.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherScreen.kt`
- Test: `app/src/test/java/net/droopia/hluweather/navigation/HluNavHostTest.kt`
- Test: `app/src/test/java/net/droopia/hluweather/ui/weather/WeatherScreenTest.kt`
- Test: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteScreenTest.kt`

**Interfaces:**
- Consumes: all source implementations, `WeatherRouteViewModel.Factory`, picker from Task 6, and result components from Task 7.
- Produces: an app-accessible `weather_route` flow from Map mode with real production dependencies.

- [ ] **Step 1: Write failing navigation and screen tests**

Add a `WeatherScreen` test asserting Map mode displays `Weather on route`. Extend `HluNavHostTest` to enter Map mode, tap the action, assert planner title `Weather on route`, and use Back to return to the weather screen. Screen tests must verify calculate is disabled for incomplete endpoints, invalid speed shows `Enter a speed from 50 to 240 km/h`, route failures expose Retry, and complete weather failure retains route summary plus `Retry weather`.

- [ ] **Step 2: Run navigation/screen tests to verify failure**

Run: `./gradlew test --tests net.droopia.hluweather.navigation.HluNavHostTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteScreenTest`

Expected: test failures because there is no route action or destination.

- [ ] **Step 3: Wire production services and Compose navigation**

In `HluWeatherApplication`, expose lazy instances built with the existing `httpClient`:

```kotlin
val routingSource: RoutingSource by lazy { OsrmRoutingSource(KtorOsrmApi(httpClient)) }
val routePlaceSearchSources: Map<PlaceSearchProvider, PlaceSearchSource> by lazy {
    mapOf(
        PlaceSearchProvider.PHOTON to PhotonPlaceSearchSource(KtorPhotonApi(httpClient)),
        PlaceSearchProvider.OPEN_METEO to OpenMeteoPlaceSearchSource(KtorOpenMeteoGeocodingApi(httpClient))
    )
}
val routeWeatherSource: RouteWeatherSource by lazy {
    OpenMeteoRouteWeatherSource(KtorOpenMeteoRouteWeatherApi(httpClient))
}
```

Extend `WeatherRouteViewModel.Factory` to consume these plus `locationRepository` and `deviceLocationSource`. Add `composable("weather_route")` to `HluNavHost`, pass `onBackClick = navController::popBackStack`, and pass the current theme and unit settings from `settingsState`.

Add an `onWeatherRouteClick` callback through `WeatherScreen`. In `ForecastMode.MAP`, place a labelled Material 3 button over the map with test tag `weather_route_open`; it calls `navController.navigate("weather_route")`.

`WeatherRouteScreen` uses `Scaffold` with a back top app bar, endpoint controls, native date/time dialogs, speed field, error text, calculate/retry buttons, summary, map, and timeline. Show a progress indicator during calculate without clearing valid results. The route result and its weather error must remain visible for a weather-only retry.

- [ ] **Step 4: Run targeted integration tests**

Run: `./gradlew test --tests net.droopia.hluweather.navigation.HluNavHostTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteScreenTest`

Expected: PASS.

- [ ] **Step 5: Inspect task output**

Run: `git diff --check`

Expected: no output.

### Task 9: Android Documentation And Full Verification

**Files:**
- Modify: `docs/WEATHER_ON_ROUTE.md`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteScreen.kt` only to render the already-approved route summary fields and destination marker omitted by Task 8.
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTimeline.kt` only to label the final sample as the destination.
- Test: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteScreenTest.kt`
- Test: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTimelineTest.kt`
- Modify: `docs/superpowers/specs/2026-09-12-weather-on-route-design.md` only if implementation reveals a factual API constraint that changes an approved requirement.

**Interfaces:**
- Consumes: the completed behavior from Tasks 1-8 and the approved specification.
- Produces: accurate Android-facing documentation without obsolete web architecture claims.

- [ ] **Step 1: Complete the approved summary and destination UI gap**

Before rewriting the documentation, add the missing approved UI behavior identified during review. Render OSRM provider name, OSRM duration, departure, and expected arrival in `RouteSummary`, and add a visible destination label/tag to the final timeline item. Extend the existing screen/timeline tests to assert these values. Run the targeted screen/timeline tests and expect them to pass.

- [ ] **Step 2: Rewrite the feature documentation**

Replace the Next.js, Leaflet, URL-sharing, VisualCrossing, and claimed-complete phase sections. Document the Android planner's Map-mode entry, endpoint choices, Photon/Open-Meteo selector, OSRM initial provider and limitations, 80 km/h default/range, forecast-horizon restriction, Open-Meteo-only route weather, timeline/map selection, error states, and explicit v1 exclusions. Link to the design spec for architecture details.

- [ ] **Step 3: Verify documentation matches implementation**

Check each documented provider name, default, speed range, forecast restriction, and excluded feature against the source and spec. Run: `git diff --check`

Expected: no output.

- [ ] **Step 4: Run the complete quality suite**

Run: `./gradlew test`

Expected: PASS.

Run: `./gradlew lint`

Expected: PASS or only pre-existing accepted warnings.

Run: `./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL` and a debug APK under `app/build/outputs/apk/debug/`.

- [ ] **Step 5: Perform manual emulator/device verification**

Use Map mode -> Weather on route. Search Zajecar with Photon for start and Trieste with Open-Meteo search for destination. Set 80 km/h and a next-full-hour departure. Confirm route summary, OSRM label, route line, hourly markers, timeline/map two-way selection, light and dark themes, speed validation, no-results search, route retry, and offline weather retry behavior.

- [ ] **Step 6: Inspect final worktree**

Run: `git status --short`

Expected: only the intended route feature files plus any pre-existing unrelated changes; do not alter unrelated work.

## Plan Self-Review

### Spec Coverage

- Dedicated Map-mode destination: Task 8.
- Photon-default/Open-Meteo-switchable search with saved, GPS, and map endpoints: Tasks 3, 5, 6, and 8.
- OSRM-first extensible routing: Task 2, with `RoutingSource` used by Task 5.
- Constant-speed interpolation and hourly/destination sampling: Task 1.
- Batched Open-Meteo route weather and partial failure: Task 4.
- Summary, map markers, timeline, unit-aware display, and bidirectional selection: Tasks 7 and 8.
- Cancellation, validation, forecast horizon, retries, and stale input: Task 5 and Task 8.
- Tests, build verification, and Android documentation rewrite: Task 9.

### Placeholder Scan

The plan contains no unresolved markers or deferred implementation instructions. Each task identifies concrete files, interfaces, tests, expected commands, and behavior.

### Type Consistency

`GeoPoint` is the shared coordinate type. `DrivingRoute` flows from `RoutingSource` into `buildRouteSamples`; `RouteWeatherSample` flows into `RouteWeatherSource.enrich`; `WeatherRouteResult` flows from `WeatherRouteViewModel` to the map and timeline. `PlaceSearchResult` flows from `PlaceSearchSource` into `RouteEndpoint` selection.
