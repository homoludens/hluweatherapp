# Settings And Continuous Hourly Forecast Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the settings placeholder with the approved in-memory settings experience and render all available hourly days in one synchronized, jumpable table.

**Architecture:** Add an app-level `SettingsViewModel` whose state is shared by `HluWeatherApp`, `HluNavHost`, and `SettingsScreen`; this state is deliberately in-memory so DataStore can become its future source without changing the UI contract. Extract chronological hourly-table preparation into a tested data structure, then use it from the production hourly screen and the standalone hourly component so both paths render the same all-day data.

**Tech Stack:** Kotlin 2.4.20, Jetpack Compose Material 3, Compose `LazyColumn`, lifecycle `ViewModel`/`StateFlow`, kotlinx-datetime, JUnit 4, Robolectric, existing Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-09-09-settings-and-continuous-hourly-design.md`

## Global Constraints

- Preserve application id `net.droopia.hluweather`.
- Preserve SDK floors and targets: min SDK 28, compile SDK 36, target SDK 36.
- Keep settings state in memory only; do not add DataStore or any persistence.
- Do not add real provider networking, GPS permissions, reverse geocoding, map editing, or notification delivery.
- Reuse existing `WeatherProvider`, `ThemeMode`, and `WeatherLocation` models.
- Keep English UI copy and the existing Celsius/mm defaults.
- Use the existing system-local timezone behavior for hourly date grouping.
- Preserve existing loading, error, navigation-bar inset, compact-header, and no-redundant-heading behavior.
- Do not modify the user-provided files under `docs/settings_design/`.

---

### Task 1: Add Shared In-Memory Settings State

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsState.kt`
- Create: `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/app/HluWeatherApp.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/navigation/HluNavHost.kt`
- Test: `app/src/test/java/net/droopia/hluweather/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- `SettingsState.kt` produces `TemperatureUnit`, `WindUnit`, `DistanceUnit`, `PrecipitationUnit`, and `SettingsUiState`.
- `SettingsViewModel` produces `val state: StateFlow<SettingsUiState>` and mutation methods for every settings callback.
- `HluNavHost` consumes the shared `SettingsViewModel` and passes state/callbacks into `SettingsScreen`.
- `HluWeatherApp` consumes the same state to select the active light/dark theme.

- [ ] **Step 1: Write failing state tests**

Add tests for the state transitions, using a real `SettingsViewModel`:

```kotlin
@Test
fun selecting_theme_updates_state() {
    val viewModel = SettingsViewModel()

    viewModel.setTheme(ThemeMode.DARK)

    assertEquals(ThemeMode.DARK, viewModel.state.value.themeMode)
}

@Test
fun selecting_location_disables_track_me_and_selects_location() {
    val viewModel = SettingsViewModel()
    val location = viewModel.state.value.locations[1]
    viewModel.setTrackMe(true)

    viewModel.selectLocation(location)

    assertFalse(viewModel.state.value.trackMeEnabled)
    assertEquals(location.id, viewModel.state.value.selectedLocationId)
}

@Test
fun enabling_track_me_keeps_saved_location_but_marks_track_me_active() {
    val viewModel = SettingsViewModel()
    viewModel.setTrackMe(true)

    assertTrue(viewModel.state.value.trackMeEnabled)
    assertEquals("svilajnac", viewModel.state.value.selectedLocationId)
}
```

- [ ] **Step 2: Run the focused tests and verify the expected failure**

Run:

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.settings.SettingsViewModelTest
```

Expected: compilation or assertion failures because `SettingsViewModel` and its state mutations do not exist yet.

- [ ] **Step 3: Implement the in-memory state contract**

Define the state with the approved defaults:

```kotlin
data class SettingsUiState(
    val provider: WeatherProvider = WeatherProvider.OPEN_METEO,
    val locations: List<WeatherLocation> = defaultSettingsLocations(),
    val selectedLocationId: String? = "svilajnac",
    val trackMeEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windUnit: WindUnit = WindUnit.KMH,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val precipitationUnit: PrecipitationUnit = PrecipitationUnit.MM,
    val weatherAlerts: Boolean = true,
    val dailySummary: Boolean = false,
    val tripAlerts: Boolean = false
)

fun defaultSettingsLocations(): List<WeatherLocation> = listOf(
    Svilajnac,
    WeatherLocation(
        id = "belgrade",
        name = "Belgrade",
        latitude = 44.8176,
        longitude = 20.4633
    ),
    WeatherLocation(
        id = "trieste",
        name = "Trieste",
        latitude = 45.6495,
        longitude = 13.7768
    )
)
```

Implement `SettingsViewModel` with `MutableStateFlow`, `asStateFlow`, and methods named `setProvider`, `setTrackMe`, `selectLocation`, `setTheme`, `setTemperatureUnit`, `setWindUnit`, `setDistanceUnit`, `setPrecipitationUnit`, `setWeatherAlerts`, `setDailySummary`, `setTripAlerts`, and `clearCache`. `selectLocation` must set `trackMeEnabled = false`; `setTrackMe(true)` must preserve the saved selected-location id while making Track Me the active source. Use the existing `Svilajnac` plus fixed in-memory Belgrade and Trieste sample locations for the reference screen.

Update `HluWeatherApp` to collect the shared settings state and derive `darkTheme` as follows:

```kotlin
val systemDark = isSystemInDarkTheme()
val darkTheme = when (state.themeMode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
```

Pass the same view model to `HluNavHost` and then to the settings route. Do not create a second settings state inside `SettingsScreen`.

- [ ] **Step 4: Run the focused tests and verify they pass**

Run the same `SettingsViewModelTest` command. Expected: all state transition tests pass.

- [ ] **Step 5: Commit the state layer**

```bash
git add app/src/main/java/net/droopia/hluweather/ui/settings/SettingsState.kt app/src/main/java/net/droopia/hluweather/ui/settings/SettingsViewModel.kt app/src/main/java/net/droopia/hluweather/ui/app/HluWeatherApp.kt app/src/main/java/net/droopia/hluweather/navigation/HluNavHost.kt app/src/test/java/net/droopia/hluweather/ui/settings/SettingsViewModelTest.kt
git commit -m "feat: add in-memory settings state"
```

### Task 2: Build the Settings Screen

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/navigation/HluNavHost.kt`
- Create: `app/src/test/java/net/droopia/hluweather/ui/settings/SettingsScreenTest.kt`

**Interfaces:**
- `SettingsScreen` consumes `state: SettingsUiState`, `onBackClick`, and one callback for each state mutation/action.
- Future-only actions use callbacks: `onLocationMenuClick`, `onAddLocationClick`, and `onClearCacheClick`.
- The route connects those callbacks to `SettingsViewModel` methods and preserves navigation back behavior.

- [ ] **Step 1: Write failing screen tests**

Create a `SettingsScreenTest` with a mutable state holder and callback counters. Cover the reference structure and representative interactions:

```kotlin
@Test
fun renders_reference_sections() {
    composeRule.setContent {
        HluWeatherTheme(darkTheme = false) {
            SettingsScreen(
                state = testSettingsState,
                onBackClick = {},
                onProviderChange = {},
                onTrackMeChange = {},
                onLocationSelect = {},
                onLocationMenuClick = {},
                onAddLocationClick = {},
                onThemeChange = {},
                onTemperatureUnitChange = {},
                onWindUnitChange = {},
                onDistanceUnitChange = {},
                onPrecipitationUnitChange = {},
                onWeatherAlertsChange = {},
                onDailySummaryChange = {},
                onTripAlertsChange = {},
                onClearCacheClick = {}
            )
        }
    }

    composeRule.onNodeWithText("Settings").assertIsDisplayed()
    composeRule.onNodeWithText("Weather Provider").assertIsDisplayed()
    composeRule.onNodeWithText("Locations").assertIsDisplayed()
    composeRule.onNodeWithText("Appearance").assertIsDisplayed()
    composeRule.onNodeWithText("Units").assertIsDisplayed()
    composeRule.onNodeWithText("Notifications").assertIsDisplayed()
    composeRule.onNodeWithText("Data & Cache").assertIsDisplayed()
}

@Test
fun provider_and_appearance_controls_report_changes() {
    var provider: WeatherProvider? = null
    var theme: ThemeMode? = null

    renderSettings(
        onProviderChange = { provider = it },
        onThemeChange = { theme = it }
    )

    composeRule.onNodeWithText("MET.no").performClick()
    composeRule.onNodeWithText("Dark").performClick()

    assertEquals(WeatherProvider.MET_NO, provider)
    assertEquals(ThemeMode.DARK, theme)
}
```

Add tests for Track Me, unit selectors, notification switches, back, and stable semantics for selected controls. Use test tags for controls that have duplicate or scroll-dependent labels.

- [ ] **Step 2: Run the focused screen tests and verify they fail**

Run:

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.settings.SettingsScreenTest
```

Expected: failures because the current settings destination has no cards or controls.

- [ ] **Step 3: Implement the approved settings layout**

Replace the placeholder `Scaffold` content with the supplied visual structure:

- Use a `LazyColumn` with 16.dp horizontal content padding, 12.dp top padding, 32.dp bottom padding, and 14.dp section spacing.
- Keep a Material 3 top/header treatment with back button, `Settings`, and `Customize your weather experience`.
- Implement focused private composables in `SettingsScreen.kt`: `SettingsCard`, `SectionHeader`, provider row, Track Me row, location row, add-location row, theme selector, unit row/selector, toggle row, and cache row.
- Use `selectable` with `Role.Tab` for provider/theme/unit/location choices so selected state is exposed to accessibility services.
- Use `Switch` for Track Me and notification settings; use `RadioButton` or selectable rows for mutually exclusive choices.
- Keep Add Location, location menu, provider info, and Clear cache as callback actions with no networking, GPS, map, or persistence.
- Apply system/navigation-bar insets to the scroll content consistently with the weather screen.

Use the existing Material icons dependency and preserve the visual language from the supplied image: rounded surface cards, primary blue selected controls, dividers inset after the leading icon, and readable dark/light color-scheme contrast.

- [ ] **Step 4: Connect the route and theme behavior**

In `HluNavHost`, pass the shared state and callbacks into `SettingsScreen`. Verify selecting Light or Dark causes `HluWeatherApp` to recompose with the matching theme while navigating back to weather. Keep System mapped to `isSystemInDarkTheme()`.

- [ ] **Step 5: Run settings tests and the existing navigation tests**

Run:

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.settings.SettingsScreenTest --tests net.droopia.hluweather.navigation.HluNavHostTest --tests net.droopia.hluweather.ui.app.HluWeatherAppTest
```

Expected: all selected-state, callback, theme, and back-navigation tests pass.

- [ ] **Step 6: Commit the settings UI**

```bash
git add app/src/main/java/net/droopia/hluweather/ui/settings/SettingsScreen.kt app/src/main/java/net/droopia/hluweather/navigation/HluNavHost.kt app/src/test/java/net/droopia/hluweather/ui/settings/SettingsScreenTest.kt
git commit -m "feat: add state-driven settings screen"
```

### Task 3: Prepare Chronological All-Day Hourly Data

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/ui/weather/HourlyTableData.kt`
- Test: `app/src/test/java/net/droopia/hluweather/ui/weather/HourlyTableDataTest.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weather/HourlyForecast.kt`

**Interfaces:**
- `HourlyTableData` contains ordered `items`, `days`, and a lookup from day index to first list item index.
- Each hour item contains its source `HourForecast`, day index, `LocalDate`, and stable key.
- Each date-boundary item contains its day index, date, and stable key.
- Date-boundary items expose `hourly_day_boundary_<dayIndex>` test tags, and day-strip chips expose `hourly_day_chip_<dayIndex>` test tags.
- `WeatherForecast.toHourlyTableData()` consumes the existing hourly and daily lists and returns the complete table model.

Use an explicit sealed item model so the list index and the hour-only index cannot be confused:

```kotlin
data class HourlyTableData(
    val items: List<HourlyTableItem>,
    val days: List<HourlyTableDay>
) {
    val hourItems: List<HourlyTableHour>
        get() = items.filterIsInstance<HourlyTableHour>()
}

sealed interface HourlyTableItem {
    val dayIndex: Int
    val key: String
}

data class HourlyTableDay(
    val dayIndex: Int,
    val date: LocalDate,
    val firstItemIndex: Int
)

data class HourlyTableBoundary(
    override val dayIndex: Int,
    val date: LocalDate,
    override val key: String
) : HourlyTableItem

data class HourlyTableHour(
    override val dayIndex: Int,
    val date: LocalDate,
    val hour: HourForecast,
    override val key: String
) : HourlyTableItem
```

- [ ] **Step 1: Write failing data-preparation tests**

Use `buildMockForecast(location = Svilajnac, baseTime = Instant.fromEpochSeconds(0L))` and assert:

```kotlin
@Test
fun includes_all_days_in_source_order() {
    val table = forecast.toHourlyTableData()

    assertEquals(7 * 24, table.hourItems.size)
    assertEquals(7, table.days.size)
    assertEquals(0, table.hourItems.first().dayIndex)
    assertEquals(6, table.hourItems.last().dayIndex)
    assertTrue(table.hourItems.zipWithNext().all { (a, b) -> a.hour.time <= b.hour.time })
}

@Test
fun exposes_first_item_index_for_each_day_jump() {
    val table = forecast.toHourlyTableData()

    assertEquals(0, table.firstItemIndexForDay(0))
    assertEquals(26, table.firstHourIndexForDay(1))
    assertEquals(4, table.dayIndexForDate(table.days[4].date))
}
```

Also test an incomplete forecast where one day has no hourly entries: that day must not appear in `days` or the jump lookup, and the remaining rows must stay ordered.

- [ ] **Step 2: Run the focused data tests and verify failure**

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.weather.HourlyTableDataTest
```

Expected: compilation failure because `HourlyTableData` and `toHourlyTableData()` do not exist.

- [ ] **Step 3: Implement the chronological model**

Group each hourly entry by `toAppLocalDate()`, retain source order, and create day metadata only for dates represented by hourly entries. Use the corresponding `forecast.daily` index when available; otherwise use a stable date-derived entry without indexing outside the daily list. Define stable keys from the date/time, for example `date-header-2026-09-09` and `hour-2026-09-09T13:00:00Z`.

Expose helpers for:

```kotlin
fun HourlyTableData.firstItemIndexForDay(dayIndex: Int): Int?
fun HourlyTableData.firstHourIndexForDay(dayIndex: Int): Int?
fun HourlyTableData.dayIndexForDate(date: LocalDate): Int?
```

Keep conversion and date grouping outside composables so it can be tested without Compose.

- [ ] **Step 4: Run the data tests and verify they pass**

Run the focused command from Step 2. Expected: all ordering, missing-day, and lookup tests pass.

- [ ] **Step 5: Update the standalone hourly component to use the model**

Change `HourlyForecast` to consume `toHourlyTableData()` rather than `hoursForDay(selectedDayIndex)`. Render all date-boundary and hourly items in the same `LazyColumn`, keeping the existing reusable day-strip, column-header, and row composables. Preserve the `hourly_table` and `hourly_day_strip` test tags.

- [ ] **Step 6: Run existing hourly tests**

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.weather.HourlyForecastTest
```

Expected: the heading-removal and table-header/row tests pass with all-day data.

- [ ] **Step 7: Commit the hourly data model**

```bash
git add app/src/main/java/net/droopia/hluweather/ui/weather/HourlyTableData.kt app/src/main/java/net/droopia/hluweather/ui/weather/HourlyForecast.kt app/src/test/java/net/droopia/hluweather/ui/weather/HourlyTableDataTest.kt
git commit -m "feat: prepare continuous hourly forecast data"
```

### Task 4: Integrate Sticky Day Jumping And Visible-Day Synchronization

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherScreen.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weather/HourlyForecast.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherViewModel.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/weather/WeatherScreenTest.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/weather/WeatherViewModelTest.kt`

**Interfaces:**
- `HourlyWeatherContent` consumes `HourlyTableData`, the existing selected-day index, and the existing mode/settings callbacks.
- `WeatherViewModel.onDaySelected(index)` remains the state update used by daily selection and the day strip; it must not itself scroll the list.
- The composable owns `LazyListState` and a coroutine scope for explicit chip-click jumps.

- [ ] **Step 1: Add failing production-path tests**

Extend `WeatherScreenTest` with deterministic mock data and tests for:

```kotlin
@Test
fun hourly_screen_renders_rows_from_the_next_day_without_switching_tables() {
    renderWeather(baseTime = Instant.fromEpochSeconds(0L))

    composeRule.onNodeWithTag("weather_scroll").performScrollToIndex(28)

    composeRule.onNodeWithTag("hourly_day_boundary_1").assertIsDisplayed()
    composeRule.onNodeWithText("00h").assertIsDisplayed()
}

@Test
fun selecting_a_day_chip_jumps_to_that_day() {
    renderWeather(baseTime = Instant.fromEpochSeconds(0L))

    composeRule.onNodeWithTag("hourly_day_chip_1").performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("hourly_selected_day_1").assertIsDisplayed()
}
```

Use semantics/test tags tied to the date index rather than ambiguous repeated hour text. Add a view-model test confirming selecting a day still sets hourly mode and selected index without owning scroll behavior.

- [ ] **Step 2: Run the production-path tests and verify failure**

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.weather.WeatherScreenTest --tests net.droopia.hluweather.ui.weather.WeatherViewModelTest
```

Expected: failures because the production hourly screen still filters one day and the day strip has no jump target synchronization.

- [ ] **Step 3: Render one continuous production list**

Replace `dayHours = forecast.hoursForDay(selectedDayIndex)` in `HourlyWeatherContent` with `tableData = forecast.toHourlyTableData()`. Render date boundary items and all hour items in one `LazyColumn` with stable keys.

Keep the existing top header item and compact overlay. Make the day strip and forecast column header separate `stickyHeader` blocks. When collapsed, reserve `96.dp` above the day strip, matching the compact hero height already used by the current implementation.

Give each date boundary a `testTag("hourly_day_boundary_$dayIndex")` and each day-strip chip a `testTag("hourly_day_chip_$dayIndex")`. The selected chip must continue to expose selected semantics.

- [ ] **Step 4: Implement day-chip jumps without scroll feedback loops**

Build a `dayIndex -> first list item index` map from `HourlyTableData`. On chip click:

```kotlin
onDaySelected = { dayIndex ->
    viewModelOnDaySelected(dayIndex)
    scope.launch {
        tableData.firstItemIndexForDay(dayIndex)?.let { target ->
            listState.animateScrollToItem(target)
        }
    }
}
```

Do not put scrolling inside `WeatherViewModel`; the view model remains state-only.

- [ ] **Step 5: Synchronize the strip during manual scroll**

Use `snapshotFlow` over `listState.layoutInfo.visibleItemsInfo` and resolve the first visible date-boundary/hour item to a day index. Apply `distinctUntilChanged()` and call `onDaySelected` only when the resolved day changes. The observer must not call `animateScrollToItem`, preventing a manual-scroll feedback loop.

On initial entry, perform one jump to the current `selectedDayIndex` if it is a valid available day and the list is not already positioned there. This preserves daily-to-hourly day selection.

Update `WeatherViewModel.onDaySelected` so its upper bound comes from `state.value.forecast?.daily?.lastIndex ?: 0` instead of the hard-coded value `6`; retain the existing lower bound of zero.

- [ ] **Step 6: Verify production hourly behavior**

Run the `WeatherScreenTest` and `WeatherViewModelTest` command from Step 2. Expected: continuous rows, day jump, manual selected-day updates, compact collapse, sticky spacing, insets, and navigation all pass.

- [ ] **Step 7: Commit hourly screen integration**

```bash
git add app/src/main/java/net/droopia/hluweather/ui/weather/WeatherScreen.kt app/src/main/java/net/droopia/hluweather/ui/weather/HourlyForecast.kt app/src/main/java/net/droopia/hluweather/ui/weather/WeatherViewModel.kt app/src/test/java/net/droopia/hluweather/ui/weather/WeatherScreenTest.kt app/src/test/java/net/droopia/hluweather/ui/weather/WeatherViewModelTest.kt
git commit -m "feat: make hourly forecast continuous across days"
```

### Task 5: Full Regression Verification And Review

**Files:**
- Modify: `app/src/test/java/net/droopia/hluweather/ui/app/HluWeatherAppTest.kt` if shared-theme assertions need extension.
- Modify: `app/src/test/java/net/droopia/hluweather/navigation/HluNavHostTest.kt` if settings route assertions need extension.
- No production files should be changed in this task unless a test exposes a defect from Tasks 1-4.

**Interfaces:**
- Consumes the completed settings and hourly UI contracts from Tasks 1-4.
- Produces a verified `main`-compatible feature branch with no persistence/provider networking changes.

- [ ] **Step 1: Run the full unit suite**

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon
```

Expected: all existing and new unit/UI tests pass.

- [ ] **Step 2: Run debug assembly and lint**

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:assembleDebug :app:lintDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL` for both tasks with no new lint errors.

- [ ] **Step 3: Inspect the final diff**

```bash
git diff --check
git status --short --branch
git diff main..HEAD --stat
```

Confirm that only the approved settings/hourly files and tests changed, and that `docs/settings_design/` remains untouched.

- [ ] **Step 4: Request a code review**

Review the complete settings state flow, theme propagation, hourly item indexing, sticky-header geometry, day-chip jump behavior, and accessibility semantics before integration.

- [ ] **Step 5: Commit any review fixes and verify again**

For each review fix, add a regression test first, run the focused test, then rerun the full suite, assembly, and lint commands before merging.
