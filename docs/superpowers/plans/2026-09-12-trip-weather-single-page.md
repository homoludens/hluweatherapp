# Trip Weather Single-Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize the working Trip Weather feature into one page containing compact endpoints, a Show trip weather action, now-plus-72-hour start-time control, 40..130 km/h speed control, route map, and route weather table.

**Architecture:** Keep the reviewed route/snapshot data layer and one `WeatherRouteViewModel`. Replace the nested `trip` navigation graph and three result pages with one `weather_route` destination whose `WeatherRouteScreen` owns the complete vertical flow. Persist the place-search provider in Settings separately from the weather provider and inject it into the route ViewModel.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation Compose, DataStore Preferences, Ktor, kotlinx.serialization, kotlinx-datetime, MapLibre Compose, JUnit, coroutines-test, Robolectric, and Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-09-12-trip-weather-single-page-design.md`

## Global Constraints

- The Trip Weather destination contains these sections, in this order: Start location, Destination, `Show trip weather`, Start-time slider, Speed slider, Result map with weather icons along the route, Result weather table.
- The whole page may scroll vertically on a phone.
- The start/destination area must not contain its own vertical scroll container.
- The result table may scroll horizontally on narrow screens, but it must not own a nested vertical scroll.
- Start time is an integer slider from `0` through `72` hours from a stable ViewModel reference instant, in one-hour steps.
- Speed is an integer slider from `40` through `130 km/h`, in one km/h steps, defaulting to `80 km/h`.
- The route page does not expose a provider label, provider switch, or provider explanation.
- Photon is the default persisted Location search provider; Open-Meteo is the other value.
- The existing weather-provider setting remains independent and does not select the route-weather source.
- Route weather remains Open-Meteo-only and route geometry remains OSRM-provided.
- Use `HluWeatherIcon`, existing themes, cards, buttons, units, MapLibre, and OpenFreeMap styles.
- Do not add saved trips, recent trips, Save trip persistence, MET.no route weather, a dependency, a module, or a DI framework.
- Preserve `CancellationException`; map only non-cancellation failures to UI messages.
- Do not commit implementation changes unless the user explicitly requests commits.

---

## File Structure

| Path | Responsibility |
|---|---|
| `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsState.kt` | Add persisted place-search provider to UI state. |
| `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsRepository.kt` | Read/write the place-search provider with Photon fallback. |
| `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsViewModel.kt` | Expose the place-search provider mutation. |
| `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsScreen.kt` | Render the separate Location search provider setting. |
| `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteViewModel.kt` | Stable now-relative timing, 40..130 speed, route calculation, cache refresh, and stale-error clearing. |
| `app/src/main/java/net/droopia/hluweather/ui/weatherroute/RouteEndpointPicker.kt` | Compact endpoint fields and capped dropdown without a provider selector or nested vertical scroll. |
| `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteScreen.kt` | Complete single-page input/result composition. |
| `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteMap.kt` | Existing route map and weather markers, reused by the single page. |
| `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTable.kt` | Route table with horizontal-only scrolling and parent-owned vertical content. |
| `app/src/main/java/net/droopia/hluweather/navigation/HluNavHost.kt` | One `weather_route` destination and Settings-to-ViewModel provider wiring. |
| `app/src/main/java/net/droopia/hluweather/ui/weatherroute/TripWeatherSetupScreen.kt` | Delete after its endpoint/control/result behavior is moved into `WeatherRouteScreen.kt`. |
| `app/src/main/java/net/droopia/hluweather/ui/weatherroute/TripWeatherOverviewScreen.kt` | Delete after its map/summary behavior is moved into `WeatherRouteScreen.kt`. |
| `app/src/main/java/net/droopia/hluweather/ui/weatherroute/TripWeatherDetailsScreen.kt` | Delete; its table behavior is already reusable in `WeatherRouteTable.kt`. |
| `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTimeline.kt` | Delete after equivalent table rows/warnings are covered. |
| `app/src/test/java/net/droopia/hluweather/ui/settings/SettingsRepositoryTest.kt` | Provider persistence/default tests. |
| `app/src/test/java/net/droopia/hluweather/ui/settings/SettingsScreenTest.kt` | Provider setting semantics and selection tests. |
| `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteViewModelTest.kt` | Timing, speed, horizon, cache, and stale-error tests. |
| `app/src/test/java/net/droopia/hluweather/ui/weatherroute/RouteEndpointPickerTest.kt` | Compact endpoint/dropdown behavior. |
| `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteScreenTest.kt` | Single-page order, controls, result map/table, and errors. |
| `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteMapTest.kt` | Reused marker/icon selection behavior. |
| `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTableTest.kt` | Seven table columns, horizontal scrolling, and row selection. |
| `app/src/test/java/net/droopia/hluweather/navigation/HluNavHostTest.kt` | One destination and Back behavior. |
| `docs/WEATHER_ON_ROUTE.md` | Single-page Android feature documentation. |

## Task 1: Persisted Location Search Provider

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsState.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsRepository.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsScreen.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/settings/SettingsRepositoryTest.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/settings/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: existing `PlaceSearchProvider` enum with `PHOTON` and `OPEN_METEO`.
- Produces: `SettingsUiState.placeSearchProvider` and `SettingsViewModel.setPlaceSearchProvider(provider: PlaceSearchProvider)`.

- [ ] **Step 1: Write failing persistence and Compose tests**

```kotlin
@Test
fun missing_or_invalid_place_search_provider_defaults_to_photon() = runTest {
    val state = repository.settings.first()

    assertEquals(PlaceSearchProvider.PHOTON, state.placeSearchProvider)
}

@Test
fun settings_can_select_open_meteo_for_location_search() {
    composeRule.onNodeWithTag("settings_place_search_open_meteo").performClick()
    composeRule.onNodeWithTag("settings_place_search_open_meteo").assertIsSelected()
}
```

Add a reload test proving `OPEN_METEO` survives repository save/read and an invalid stored enum falls back to `PHOTON`. Assert the existing weather-provider rows remain independent.

- [ ] **Step 2: Run the focused settings tests and verify the new assertions fail**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.settings.SettingsRepositoryTest --tests net.droopia.hluweather.ui.settings.SettingsScreenTest`

Expected: compilation or assertion failures because the new setting and setter do not exist.

- [ ] **Step 3: Implement persisted provider state**

Add `placeSearchProvider: PlaceSearchProvider = PlaceSearchProvider.PHOTON` to both `SettingsUiState` and `PersistedSettings`. Add a `settings.place_search_provider` string preference, parse it using the existing enum helper with Photon as the default, and save `provider.name` beside the existing settings. Add:

```kotlin
fun setPlaceSearchProvider(provider: PlaceSearchProvider) {
    updateSettings { it.copy(placeSearchProvider = provider) }
}
```

Render a separate `Location search` section/row in `SettingsScreen` with Photon and Open-Meteo options tagged `settings_place_search_photon` and `settings_place_search_open_meteo`. Wire the row to `onPlaceSearchProviderChange`, without changing the existing weather-provider section.

- [ ] **Step 4: Run settings tests and inspect the diff**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.settings.SettingsRepositoryTest --tests net.droopia.hluweather.ui.settings.SettingsScreenTest`

Expected: PASS.

Run: `git diff --check`

Expected: no output.

## Task 2: Simplify Route Timing And Speed State

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteViewModel.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteViewModelTest.kt`

**Interfaces:**
- Consumes: existing route/snapshot/analysis functions and source interfaces.
- Produces: stable now-relative start-time state, `onDepartureOffsetChanged(offsetHours: Int)`, and speed validation for `40..130`.

- [ ] **Step 1: Write failing ViewModel tests**

```kotlin
@Test
fun start_time_uses_stable_reference_now_and_clamps_to_72_hours() {
    viewModel.onDepartureOffsetChanged(99)

    assertEquals(72, viewModel.state.value.departureOffsetHours)
    assertEquals(referenceNow + 72.hours, viewModel.selectedDeparture())
}

@Test
fun speed_accepts_40_through_130_and_rejects_values_outside_range() {
    viewModel.onSpeedChanged("40")
    viewModel.calculate()
    advanceUntilIdle()
    assertNull(viewModel.state.value.routeError)

    viewModel.onSpeedChanged("131")
    viewModel.calculate()
    assertEquals("Speed must be between 40 and 130 km/h", viewModel.state.value.routeError)
}
```

Add tests for default speed `80`, route sampling always using `AVERAGE_SPEED`, selected departure calculation, forecast-horizon rejection at `+72h`, and the residual regression: after an out-of-horizon offset, a valid in-range offset clears `routeError` after successful snapshot application. Keep existing cancellation, stale-generation, weather-retry, and route-retention tests.

- [ ] **Step 2: Run ViewModel tests and verify the new assertions fail**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteViewModelTest`

Expected: failures for the old `50..240` range, manual date/time base, route-estimate UI state, or stale horizon error.

- [ ] **Step 3: Implement the simplified state contract**

Capture `referenceNow = now()` once in the ViewModel constructor. Initialize the base departure to that instant and derive:

```kotlin
fun selectedDeparture(): Instant = state.value.departure + state.value.departureOffsetHours.hours
```

Change validation to `SPEED_RANGE = 40..130`. Remove date/time mutation from the screen-facing contract and make `calculate()` use `selectedDeparture()` with `RouteTimingMode.AVERAGE_SPEED`. Keep the existing internal route-estimate model only if required by old data tests; no single-page UI event may select it.

When applying a valid in-range slider result, clear `routeError` and `weatherError` after coverage is confirmed. When coverage is missing, enforce the 16-day horizon before any fetch, clear uncovered sample weather fields, and leave a concise forecast-range error. Preserve generation checks and cancellation propagation.

- [ ] **Step 4: Run ViewModel tests and inspect the diff**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteViewModelTest`

Expected: PASS.

Run: `git diff --check`

Expected: no output.

## Task 3: Build The Single-Page Route UI

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/RouteEndpointPicker.kt`
- Rewrite: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteScreen.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTable.kt`
- Keep temporarily: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/TripWeatherSetupScreen.kt` until Task 4 removes its NavHost caller.
- Keep temporarily: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/TripWeatherOverviewScreen.kt` until Task 4 removes its NavHost caller.
- Keep temporarily: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/TripWeatherDetailsScreen.kt` until Task 4 removes its NavHost caller.
- Keep temporarily: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTimeline.kt` until Task 4 removes all runtime callers.
- Modify: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/RouteEndpointPickerTest.kt`
- Rewrite: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteScreenTest.kt`
- Create: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTableTest.kt`

**Interfaces:**
- Consumes: `WeatherRouteUiState`, endpoint callbacks, `WeatherRouteMap`, `WeatherRouteTable`, settings units, and ViewModel actions.
- Produces: one `WeatherRouteScreen` with stable tags `weather_route_screen`, `route_start_search`, `route_end_search`, `trip_show_weather`, `trip_start_time_slider`, `trip_speed_slider`, `weather_route_map`, and `weather_route_table`.

- [ ] **Step 1: Write failing single-page UI tests**

```kotlin
composeRule.onNodeWithTag("weather_route_screen").assertIsDisplayed()
assertOrder(
    "route_start_search",
    "route_end_search",
    "trip_show_weather",
    "trip_start_time_slider",
    "trip_speed_slider",
    "weather_route_map",
    "weather_route_table"
)
composeRule.onNodeWithTag("trip_speed_slider")
    .assert(SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo(80f, 40f..130f, 89)
    ))
```

Test the endpoint block has no vertical scroll semantics, the dropdown renders at most five results, no provider text/selector appears on the page, the result map/table appear after calculation, and route/weather retry stays inline.

- [ ] **Step 2: Run route UI tests and verify the new assertions fail**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weatherroute.RouteEndpointPickerTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteScreenTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteTableTest`

Expected: failures because the current route UI is split across setup/overview/details and still exposes date/time/provider controls.

- [ ] **Step 3: Implement the one-page composition**

Make `WeatherRouteScreen` the only runtime route composable. Its outer `LazyColumn` owns all vertical scrolling and renders, in order:

```text
RouteEndpointPicker(start)
RouteEndpointPicker(destination)
Show trip weather
Start time Slider(valueRange = 0f..72f, steps = 71)
Speed Slider(valueRange = 40f..130f, steps = 89)
WeatherRouteMap
WeatherRouteTable
```

Use two compact endpoint fields backed by the existing slot callbacks. Remove provider controls from `RouteEndpointPicker`; its search source is selected in ViewModel state from Settings. Cap dropdown results at five without a nested scroll container. Remove date/time dialogs and the route-estimate switch. Put the actual clickable action tag `trip_show_weather` on the button and retain `route_calculate` only as a non-clickable compatibility tag if existing tests require it.

Change `WeatherRouteTable` to render its rows in a normal `Column` inside a horizontal scroll container. Do not use a vertical `LazyColumn` or vertical scroll inside the table. Keep seven columns, destination row, warnings, unavailable values, existing table styling, `HluWeatherIcon`, unit formatters, and selected-row callback. Reuse `WeatherRouteMap` unchanged except for its existing reviewed marker/icon behavior.

Move the useful summary/error/retry content into the same page around the map/table result. Delete the four obsolete page composables only after all their runtime callers and tests are migrated.

- [ ] **Step 4: Run route UI tests and inspect the diff**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weatherroute.RouteEndpointPickerTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteScreenTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteTableTest`

Expected: PASS.

Run: `git diff --check`

Expected: no output.

## Task 4: Simplify Navigation And Wire Settings Provider

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/navigation/HluNavHost.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteViewModel.kt` only for the factory provider input
- Delete: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/TripWeatherSetupScreen.kt`
- Delete: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/TripWeatherOverviewScreen.kt`
- Delete: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/TripWeatherDetailsScreen.kt`
- Delete: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTimeline.kt`
- Delete: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/TripWeatherSetupScreenTest.kt`
- Delete: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/TripWeatherOverviewScreenTest.kt`
- Delete: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/TripWeatherDetailsScreenTest.kt`
- Delete: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteTimelineTest.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/navigation/HluNavHostTest.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteScreenTest.kt` only for factory construction

**Interfaces:**
- Consumes: `settingsState.placeSearchProvider`, application route sources, and the single `WeatherRouteScreen`.
- Produces: one `composable("weather_route")` destination and a `WeatherRouteViewModel.Factory` accepting `placeSearchProvider: PlaceSearchProvider`.

- [ ] **Step 1: Write failing navigation tests**

```kotlin
composeRule.onNodeWithTag("weather_route_open").performClick()
composeRule.onNodeWithTag("weather_route_screen").assertIsDisplayed()
composeRule.onNodeWithTag("trip_setup_screen").assertDoesNotExist()
composeRule.onNodeWithTag("trip_overview_screen").assertDoesNotExist()
composeRule.onNodeWithTag("trip_details_screen").assertDoesNotExist()
composeRule.onNodeWithTag("weather_route_back").performClick()
composeRule.onNodeWithTag("weather_screen").assertIsDisplayed()
```

Add a factory test proving Settings’ Open-Meteo place-search choice reaches the ViewModel without changing the route-weather source.

- [ ] **Step 2: Run navigation tests and verify the graph assertions fail**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.navigation.HluNavHostTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteScreenTest`

Expected: failures because the current NavHost still declares the nested `trip` graph and uses setup/overview/details destinations.

- [ ] **Step 3: Implement one-destination navigation**

Remove the `navigation(route = "trip")` block and its three composables. Add one `composable("weather_route")` that constructs one ViewModel and renders `WeatherRouteScreen`. Pass `settingsState.placeSearchProvider`, saved locations, current units, and `onBackClick = navController::popBackStack`. Keep the existing Weather Map-mode callback navigating to `weather_route` and do not add an automatic result navigation effect. Delete the four obsolete page-specific production files and their corresponding setup/overview/details/timeline tests after the single-page tests cover their retained behavior; keep reusable map, screen, table, ViewModel, and endpoint-picker tests.

Extend `WeatherRouteViewModel.Factory` with the selected place-search provider and initialize `WeatherRouteUiState.searchProvider` from it. Remove `onDetailsClick`, `onShareClick`, overview navigation, details navigation, and graph-scoping seams that are no longer needed. Keep the application’s existing place-search source map and fixed Open-Meteo route-weather source.

- [ ] **Step 4: Run navigation tests and inspect the diff**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.navigation.HluNavHostTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteScreenTest`

Expected: PASS.

Run: `git diff --check`

Expected: no output.

## Task 5: Documentation, Previews, And Final Verification

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteScreen.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsScreen.kt`
- Modify: `docs/WEATHER_ON_ROUTE.md`
- Modify: relevant route/settings/navigation tests from Tasks 1-4

**Interfaces:**
- Consumes: completed single-page route flow and approved specification.
- Produces: deterministic previews, accurate docs, and a green Android quality suite.

- [ ] **Step 1: Add deterministic previews**

Add light and dark previews for the single `WeatherRouteScreen` and updated Settings section using fixed endpoints, route samples, cached hourly data, and no-network fake sources. Previews must show the compact endpoint area, both sliders, map placeholder/route icons, and table result without initializing MapLibre or opening a nested vertical scroller.

- [ ] **Step 2: Update Android documentation**

Document the single `weather_route` page and exact section order, now-plus-72-hour start time, 40..130 km/h speed slider with 80 default, Settings-owned location search provider, Open-Meteo-only route weather, OSRM route map, horizontal-only table overflow, inline retries, and explicit saved/recent/MET.no exclusions. Remove all claims about trip setup/overview/details pages and route-page provider selection.

- [ ] **Step 3: Run the complete quality suite**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew test lint assembleDebug --console=plain`

Expected: `BUILD SUCCESSFUL`, all tests pass, lint has no new errors, and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 4: Perform manual device verification when available**

Open Weather Map mode and enter Weather on route. Confirm the single-page order, compact endpoint dropdown, no provider text, now-plus-72 slider, 40..130 speed slider, map icons, table rows, horizontal table movement, Back behavior, retry states, and Settings provider persistence. If no device/AVD is attached, record that limitation without changing the implementation.

- [ ] **Step 5: Inspect the final worktree**

Run: `git status --short`

Expected: only intended single-page reorganization files plus pre-existing unrelated changes; do not alter unrelated work.

## Plan Self-Review

### Spec Coverage

- Single-page order, outer vertical scroll, compact endpoint dropdown, and horizontal-only table overflow: Task 3.
- One `weather_route` destination and removal of trip pages: Task 4.
- Now-plus-72 start slider and stale forecast-error clearing: Task 2.
- 40..130 speed slider and removal of route-estimate UI: Tasks 2 and 3.
- Open-Meteo-only route weather with no provider text on route page: Global Constraints and Tasks 3-4.
- Persisted independent location-search provider: Task 1 and Task 4.
- Existing map icons, route geometry, table columns, units, unavailable values, retries, and selection: Tasks 2-3.
- Tests, previews, documentation, build, lint, and manual verification: Task 5.

### Placeholder Scan

The plan contains no unresolved placeholders or implementation steps. Each task has exact files, interfaces, test commands, expected outcomes, and concrete behavior.

### Type Consistency

`PlaceSearchProvider` flows from persisted `SettingsUiState.placeSearchProvider` through `HluNavHost` into `WeatherRouteViewModel.Factory`. `WeatherRouteUiState` exposes `departureOffsetHours`, `speedText`, `result`, `snapshot`, and errors to the single `WeatherRouteScreen`. The screen passes `selectedSampleIndex` to `WeatherRouteMap` and `WeatherRouteTable`, while `RouteWeatherSnapshot` and analysis functions remain unchanged data boundaries.
