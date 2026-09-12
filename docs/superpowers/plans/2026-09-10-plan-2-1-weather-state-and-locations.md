# Plan 2.1: Weather State And Locations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace fixed sample weather state with persisted saved locations and provider-aware forecast loading.

**Architecture:** `LocationRepository` owns persisted places and the selected active location. `WeatherViewModel` combines that active location with the persisted provider and uses `collectLatest` to load the matching forecast. New installations start empty; no sample city is seeded.

**Tech Stack:** Kotlin, Compose Material 3, DataStore Preferences, StateFlow, Coroutines, JUnit, Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-09-plan-2-core-product-completion-design.md`

## Global Constraints

- Do not request location permission or implement Track Me coordinates in this slice.
- `MET_NO` must report an explicit unsupported-provider error until Plan 2.3.
- Locations are persisted; the selected location and location mode have one mutation owner.
- Unit tests are offline and deterministic.

## File Map

- Create `data/model/ActiveLocation.kt` and `data/repository/LocationRepository.kt` for location state and persistence.
- Modify settings files to remove fixed sample ownership and delegate location actions.
- Modify weather repository/view model/application wiring for provider plus active-location requests.
- Create `ui/weather/LocationQuickSwitcher.kt`; modify weather card/screen/navigation for the sheet and empty state.

---

### Task 1: Persist Saved Locations

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/data/model/ActiveLocation.kt`
- Create: `app/src/main/java/net/droopia/hluweather/data/repository/LocationRepository.kt`
- Create: `app/src/test/java/net/droopia/hluweather/data/repository/LocationRepositoryTest.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsRepository.kt`

**Interfaces:**

```kotlin
enum class LocationMode { SAVED_LOCATION, TRACK_ME }

sealed interface ActiveLocation {
    data class Saved(val location: WeatherLocation) : ActiveLocation
}

interface LocationRepository {
    val locations: Flow<List<WeatherLocation>>
    val activeLocation: Flow<ActiveLocation?>
    suspend fun add(location: WeatherLocation)
    suspend fun update(location: WeatherLocation)
    suspend fun delete(id: String)
    suspend fun selectSaved(id: String)
    suspend fun setTrackMe(enabled: Boolean)
}
```

- [ ] **Step 1: Write failing repository tests**

```kotlin
@Test fun new_store_has_no_active_location() = runTest {
    assertNull(repository.activeLocation.first())
}

@Test fun deleting_active_location_selects_first_remaining_location() = runTest {
    repository.add(belgrade)
    repository.add(trieste)
    repository.selectSaved("belgrade")
    repository.delete("belgrade")
    assertEquals("trieste", (repository.activeLocation.first() as ActiveLocation.Saved).location.id)
}
```

Add persistence-after-recreation, update, final-delete, invalid serialized-list, Track Me preserving selected ID, and saved selection disabling Track Me cases.

- [ ] **Step 2: Run the repository tests to verify RED**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.data.repository.LocationRepositoryTest`

Expected: compilation failure because the location repository does not exist.

- [ ] **Step 3: Implement the DataStore-backed repository**

Store a JSON `List<PersistedLocation>` under `locations.saved`, and store `settings.selected_location_id` and `settings.location_mode` in the existing application DataStore. Decode errors as an empty list. Reconcile a missing selected ID to the first list ID or null. Make `delete` atomically update the list and fallback selection in one `edit` call.

- [ ] **Step 4: Run the focused tests to verify GREEN**

Run the Step 2 command. Expected: all location repository tests pass.

- [ ] **Step 5: Commit the persistence slice**

```bash
git add app/src/main/java/net/droopia/hluweather/data/model/ActiveLocation.kt app/src/main/java/net/droopia/hluweather/data/repository/LocationRepository.kt app/src/main/java/net/droopia/hluweather/ui/settings/SettingsRepository.kt app/src/test/java/net/droopia/hluweather/data/repository/LocationRepositoryTest.kt
```

### Task 2: Drive Weather From Provider And Active Location

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/data/repository/WeatherRepository.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/data/repository/OpenMeteoWeatherRepository.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/data/repository/MockWeatherRepository.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/HluWeatherApplication.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherViewModel.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/weather/WeatherViewModelTest.kt`

**Interfaces:**

```kotlin
interface WeatherRepository {
    suspend fun getForecast(provider: WeatherProvider, location: WeatherLocation): WeatherForecast
}
```

- [ ] **Step 1: Write failing view-model tests**

```kotlin
@Test fun provider_change_refetches_for_the_same_location() = runTest {
    settings.emit(provider = WeatherProvider.MET_NO)
    advanceUntilIdle()
    assertEquals(WeatherProvider.MET_NO, repository.requests.last().provider)
}

@Test fun no_active_location_does_not_request_weather() = runTest {
    locations.emitActive(null)
    advanceUntilIdle()
    assertTrue(repository.requests.isEmpty())
}
```

Use a deferred fake request to verify an obsolete location result cannot replace the newer location result.

- [ ] **Step 2: Run the focused tests to verify RED**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weather.WeatherViewModelTest`

Expected: compilation failures for the provider-aware contract and repository dependencies.

- [ ] **Step 3: Implement provider-aware loading**

Make Open-Meteo reject any provider except `OPEN_METEO`; make the mock retain its deterministic forecast while recording the provider. Inject settings and location repositories into `WeatherViewModel`; combine provider with `ActiveLocation.Saved`, use `collectLatest`, set an explicit empty state for null, and rethrow `CancellationException`.

- [ ] **Step 4: Run focused weather and repository tests to verify GREEN**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weather.WeatherViewModelTest --tests net.droopia.hluweather.data.repository.OpenMeteoWeatherRepositoryTest`

Expected: passing tests with no network access.

- [ ] **Step 5: Commit the state integration**

```bash
git add app/src/main/java/net/droopia/hluweather/data/repository app/src/main/java/net/droopia/hluweather/HluWeatherApplication.kt app/src/main/java/net/droopia/hluweather/ui/weather/WeatherViewModel.kt app/src/test/java/net/droopia/hluweather
```

### Task 3: Replace Settings Location Stubs And Add Quick Switching

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/ui/weather/LocationQuickSwitcher.kt`
- Create: `app/src/test/java/net/droopia/hluweather/ui/weather/LocationQuickSwitcherTest.kt`
- Modify: `ui/settings/SettingsState.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`
- Modify: `ui/weather/CurrentWeatherCard.kt`, `WeatherScreen.kt`
- Modify: `navigation/HluNavHost.kt`, `ui/app/HluWeatherApp.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/settings/SettingsViewModelTest.kt`, `SettingsScreenTest.kt`, `ui/weather/WeatherScreenTest.kt`, `navigation/HluNavHostTest.kt`

- [ ] **Step 1: Write failing UI tests**

```kotlin
composeRule.onNodeWithText("Svilajnac").performClick()
composeRule.onNodeWithText("Add location").assertIsDisplayed()
composeRule.onNodeWithText("Belgrade").performClick()
assertEquals("belgrade", selectedLocationId)
```

Add tests for empty setup content, Track Me callback boundary, and settings selection delegating to `LocationRepository`.

- [ ] **Step 2: Run the focused Compose tests to verify RED**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weather.LocationQuickSwitcherTest --tests net.droopia.hluweather.ui.weather.WeatherScreenTest`

Expected: failing assertions because the sheet and empty state do not exist.

- [ ] **Step 3: Implement the UI wiring**

Remove `defaultSettingsLocations()` from `SettingsUiState`; expose repository-backed locations in the settings view model. Make the current-location area clickable, render a `ModalBottomSheet`, and route Add/Manage to settings as temporary slice boundaries. Render a location setup action when `activeLocation` is null.

- [ ] **Step 4: Run settings, weather, and navigation tests to verify GREEN**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.settings --tests net.droopia.hluweather.ui.weather --tests net.droopia.hluweather.navigation.HluNavHostTest`

Expected: all selected tests pass.

- [ ] **Step 5: Commit the UI slice**

```bash
git add app/src/main/java/net/droopia/hluweather/ui app/src/main/java/net/droopia/hluweather/navigation app/src/test/java/net/droopia/hluweather/ui app/src/test/java/net/droopia/hluweather/navigation
```

### Task 4: Verify Slice 2.1

- [ ] **Step 1: Run the complete unit suite**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew testDebugUnitTest`

Expected: all tests pass offline.

- [ ] **Step 2: Build and lint**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew assembleDebug lintDebug`

Expected: `BUILD SUCCESSFUL` for both tasks.

- [ ] **Step 3: Inspect the verified worktree**

Run: `git diff --check && git status --short`

Expected: no whitespace errors; only location-flow files changed by this plan are present.
