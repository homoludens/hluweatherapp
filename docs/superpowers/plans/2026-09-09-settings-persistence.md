# Settings Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist the existing settings state with Preferences DataStore while keeping the current `SettingsScreen` contract and fixed sample locations unchanged.

**Architecture:** Add a `SettingsRepository` boundary with a `PersistedSettings` snapshot and `Flow`/`save` API. The production repository uses one application-scoped Preferences DataStore; `SettingsViewModel` maps persisted values to the existing `SettingsUiState`, while tests inject an in-memory fake.

**Tech Stack:** Kotlin, AndroidX Preferences DataStore, Kotlin Coroutines/StateFlow, Jetpack Compose ViewModel factory, JUnit 4, Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-09-settings-persistence-design.md`

## Global Constraints

- Keep the current fixed sample locations in memory; location creation, editing, deletion, and full location persistence belong to Plan 2.4.
- Keep `clearCache()` as an explicit no-op; cache storage and cache invalidation belong to Plan 2.5.
- The existing `SettingsUiState` shape and mutation method names remain stable.
- Use one application-scoped Preferences DataStore instance. Do not create a DataStore instance per screen or per view model.
- Use stable, namespaced string keys. Enum values are stored using stable names rather than ordinal positions.
- DataStore read failures fall back to default settings for the affected load.
- DataStore write failures are contained and do not roll back the already-applied in-memory state.
- `SettingsScreen` requires no DataStore dependency and its public state/callback contract is unchanged.
- Do not implement real provider networking, location CRUD, GPS, reverse geocoding, map integration, notifications, or forecast/cache persistence in this plan.

---

## File Map

- Modify: `gradle/libs.versions.toml` to add the Preferences DataStore version and library alias.
- Modify: `app/build.gradle.kts` to add the production DataStore dependency.
- Create: `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsRepository.kt` containing `PersistedSettings`, `SettingsRepository`, the DataStore implementation, and the application DataStore factory.
- Modify: `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsViewModel.kt` to hydrate and save through the repository and expose an explicit production factory.
- Modify: `app/src/main/java/net/droopia/hluweather/ui/app/HluWeatherApp.kt` to request `SettingsViewModel` with the production factory.
- Modify: `app/src/main/java/net/droopia/hluweather/navigation/HluNavHost.kt` to use the same factory for its default view-model path.
- Create: `app/src/test/java/net/droopia/hluweather/ui/settings/SettingsRepositoryTest.kt` for DataStore serialization, defaults, and invalid stored values.
- Modify: `app/src/test/java/net/droopia/hluweather/ui/settings/SettingsViewModelTest.kt` to inject a fake repository and test hydration, saves, unknown locations, and write failures.
- Modify: `app/src/test/java/net/droopia/hluweather/ui/app/HluWeatherAppTest.kt` to verify the factory-backed app root still reaches the settings destination; do not add DataStore dependencies to Compose screen tests.

## Implementation Tasks

### Task 1: Add The DataStore Repository

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsRepository.kt`
- Create: `app/src/test/java/net/droopia/hluweather/ui/settings/SettingsRepositoryTest.kt`

**Interfaces:**
- Produces `PersistedSettings` with the eleven persisted fields from the spec.
- Produces `SettingsRepository.settings: Flow<PersistedSettings>`.
- Produces `suspend fun save(settings: PersistedSettings)` on `SettingsRepository`.
- Produces `DataStoreSettingsRepository(dataStore: DataStore<Preferences>)` for tests and production wiring.
- Produces `settingsRepository(context: Context): SettingsRepository` backed by one `Context.settingsDataStore` instance.

- [ ] **Step 1: Write the failing repository tests**

Add tests that create one temporary Preferences DataStore file and verify real serialization:

```kotlin
@Test
fun saves_and_reads_the_complete_persisted_settings_snapshot() = runTest {
    val file = temporaryFolder.newFile("settings.preferences_pb")
    val dataStore = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { file }
    )
    val repository = DataStoreSettingsRepository(dataStore)
    val expected = PersistedSettings(
        provider = WeatherProvider.MET_NO,
        selectedLocationId = "trieste",
        trackMeEnabled = true,
        themeMode = ThemeMode.DARK,
        temperatureUnit = TemperatureUnit.FAHRENHEIT,
        windUnit = WindUnit.MPH,
        distanceUnit = DistanceUnit.MILES,
        precipitationUnit = PrecipitationUnit.INCH,
        weatherAlerts = false,
        dailySummary = true,
        tripAlerts = true
    )

    repository.save(expected)

    assertEquals(expected, repository.settings.first())
}

@Test
fun missing_and_invalid_preferences_use_persisted_defaults() = runTest {
    val file = temporaryFolder.newFile("settings.preferences_pb")
    val dataStore = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { file }
    )
    dataStore.edit {
        it[stringPreferencesKey("settings.provider")] = "not-a-provider"
        it[stringPreferencesKey("settings.theme_mode")] = "not-a-theme"
    }

    val settings = DataStoreSettingsRepository(dataStore).settings.first()

    assertEquals(PersistedSettings(), settings)
}
```

Use `TemporaryFolder`, `runTest`, `first`, and the existing JUnit 4 setup. The tests must reference the intended production API before that API exists.

- [ ] **Step 2: Run the repository tests and verify the RED state**

Run:

```bash
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.settings.SettingsRepositoryTest
```

Expected: compilation failures for the missing DataStore dependency, `PersistedSettings`, and repository types.

- [ ] **Step 3: Add the DataStore dependency**

Add `dataStore = "1.1.7"` and the `datastore-preferences` library alias to `gradle/libs.versions.toml`, then add `implementation(libs.datastore.preferences)` to `app/build.gradle.kts`. Do not add Proto DataStore or a second persistence library.

- [ ] **Step 4: Implement the repository contract and mapping**

Create `SettingsRepository.kt` with this contract:

```kotlin
data class PersistedSettings(
    val provider: WeatherProvider = WeatherProvider.OPEN_METEO,
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

interface SettingsRepository {
    val settings: Flow<PersistedSettings>
    suspend fun save(settings: PersistedSettings)
}
```

Use the stable keys `settings.provider`, `settings.track_me_enabled`, `settings.selected_location_id`, `settings.theme_mode`, `settings.temperature_unit`, `settings.wind_unit`, `settings.distance_unit`, `settings.precipitation_unit`, `settings.weather_alerts`, `settings.daily_summary`, and `settings.trip_alerts`.

Store enum values by `name`, not ordinal. Store the nullable selected-location ID only when non-null. Map missing or invalid enum strings to the corresponding `PersistedSettings` default. Read booleans with their typed Preferences defaults. Catch `IOException` while reading and emit `PersistedSettings()`; let `save` failures reach the view model's contained write path. Define the DataStore once with `preferencesDataStore(name = "settings")` and pass that instance to `DataStoreSettingsRepository`.

- [ ] **Step 5: Run the repository tests and verify GREEN**

Run the same `SettingsRepositoryTest` command from Step 2.

Expected: all repository tests pass with no new errors. Existing deprecation warnings are acceptable if they already exist in the project.

- [ ] **Step 6: Commit the repository slice**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/net/droopia/hluweather/ui/settings/SettingsRepository.kt app/src/test/java/net/droopia/hluweather/ui/settings/SettingsRepositoryTest.kt
git commit -m "feat: add settings DataStore repository"
```

### Task 2: Persist SettingsViewModel State

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes `SettingsRepository` from Task 1.
- `SettingsViewModel(repository: SettingsRepository)` remains the testable constructor.
- Keeps all existing mutation method names and `state: StateFlow<SettingsUiState>`.

- [ ] **Step 1: Add failing hydration and persistence tests**

Define an in-memory fake in `SettingsViewModelTest`:

```kotlin
private class InMemorySettingsRepository(
    initial: PersistedSettings = PersistedSettings()
) : SettingsRepository {
    private val stored = MutableStateFlow(initial)
    var saved: PersistedSettings? = null

    override val settings: StateFlow<PersistedSettings> = stored

    override suspend fun save(settings: PersistedSettings) {
        saved = settings
        stored.value = settings
    }
}
```

Add tests that set the main dispatcher to `UnconfinedTestDispatcher` and cover:

```kotlin
@Test
fun hydrates_state_from_repository() {
    val repository = InMemorySettingsRepository(
        PersistedSettings(provider = WeatherProvider.MET_NO, themeMode = ThemeMode.DARK)
    )

    val viewModel = SettingsViewModel(repository)

    assertEquals(WeatherProvider.MET_NO, viewModel.state.value.provider)
    assertEquals(ThemeMode.DARK, viewModel.state.value.themeMode)
}

@Test
fun mutations_save_the_updated_snapshot() {
    val repository = InMemorySettingsRepository()
    val viewModel = SettingsViewModel(repository)

    viewModel.setDailySummary(true)

    assertTrue(viewModel.state.value.dailySummary)
    assertEquals(true, repository.saved?.dailySummary)
}

@Test
fun unknown_stored_location_uses_the_default_location() {
    val repository = InMemorySettingsRepository(
        PersistedSettings(selectedLocationId = "missing-location")
    )

    val viewModel = SettingsViewModel(repository)

    assertEquals("svilajnac", viewModel.state.value.selectedLocationId)
}

@Test
fun failed_save_does_not_change_the_applied_state() {
    val repository = object : SettingsRepository {
        override val settings = MutableStateFlow(PersistedSettings())
        override suspend fun save(settings: PersistedSettings) {
            error("write failed")
        }
    }
    val viewModel = SettingsViewModel(repository)

    viewModel.setTheme(ThemeMode.DARK)

    assertEquals(ThemeMode.DARK, viewModel.state.value.themeMode)
}
```

Update existing tests to construct `SettingsViewModel(InMemorySettingsRepository())`; preserve all current mutation assertions.

- [ ] **Step 2: Run the view-model tests and verify RED**

Run:

```bash
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.settings.SettingsViewModelTest
```

Expected: compilation failures because the view model still has no repository constructor or hydration/save behavior.

- [ ] **Step 3: Implement hydration and mapping**

Change `SettingsViewModel` to accept `SettingsRepository`. Initialize state with `SettingsUiState()`, then in `viewModelScope` read the first repository snapshot. Map every persisted field into the current state, validate `selectedLocationId` against `state.locations`, and preserve the existing fixed location list. If reading throws, keep defaults.

Use one private mutation helper for all setters:

```kotlin
private fun updateSettings(transform: (SettingsUiState) -> SettingsUiState) {
    val next = transform(_state.value)
    _state.value = next
    viewModelScope.launch {
        runCatching { repository.save(next.toPersistedSettings()) }
    }
}
```

Map the complete state snapshot before each save. Keep `selectLocation` setting `trackMeEnabled = false`, keep `setTrackMe(true)` from changing `selectedLocationId`, and leave future actions plus `clearCache()` as no-ops.

- [ ] **Step 4: Run the view-model tests and verify GREEN**

Run the same `SettingsViewModelTest` command from Step 2.

Expected: all settings view-model tests pass.

- [ ] **Step 5: Commit the view-model slice**

```bash
git add app/src/main/java/net/droopia/hluweather/ui/settings/SettingsViewModel.kt app/src/test/java/net/droopia/hluweather/ui/settings/SettingsViewModelTest.kt
git commit -m "feat: persist settings view-model state"
```

### Task 3: Wire The Production Factory Without Duplicating State

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/ui/app/HluWeatherApp.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/navigation/HluNavHost.kt`
- Modify: `app/src/test/java/net/droopia/hluweather/ui/app/HluWeatherAppTest.kt` only if a factory startup regression test is needed.

**Interfaces:**
- Consumes `SettingsViewModel.Factory` from Task 2.
- Produces one application-scoped settings view model shared by the app root and `HluNavHost`.

- [ ] **Step 1: Add the factory wiring regression test**

Add this test to `HluWeatherAppTest`:

```kotlin
@Test
fun app_root_uses_the_factory_backed_settings_view_model() {
    composeRule.setContent {
        HluWeatherApp()
    }

    composeRule.onNodeWithContentDescription("Settings").performClick()
    composeRule.onNodeWithText("Settings").assertIsDisplayed()
}
```

Keep `HluWeatherApp` and `HluNavHost` UI tests state-driven. The existing title, theme, settings navigation, and back-navigation tests remain the behavioral coverage.

- [ ] **Step 2: Run the app and navigation tests to establish the expected failure**

Run:

```bash
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.app.HluWeatherAppTest --tests net.droopia.hluweather.navigation.HluNavHostTest
```

Expected: the new factory references do not compile until the app root and navigation host are updated.

- [ ] **Step 3: Wire both production entry points**

Use `viewModel(factory = SettingsViewModel.Factory)` in `HluWeatherApp`. Use the same factory for the default `settingsViewModel` parameter in `HluNavHost`. Preserve the existing explicit parameter path so `HluWeatherApp` passes its single instance to `HluNavHost`; do not create another settings state in the settings route.

- [ ] **Step 4: Run the app and navigation tests**

Run the same command from Step 2.

Expected: the isolated app/navigation tests pass, and settings theme/navigation behavior remains unchanged.

- [ ] **Step 5: Commit the wiring slice**

```bash
git add app/src/main/java/net/droopia/hluweather/ui/app/HluWeatherApp.kt app/src/main/java/net/droopia/hluweather/navigation/HluNavHost.kt app/src/test/java/net/droopia/hluweather/ui/app/HluWeatherAppTest.kt
git commit -m "feat: wire persistent settings factory"
```

### Task 4: Run Full Verification

**Files:**
- No source changes unless verification exposes a regression.

- [ ] **Step 1: Run the complete unit-test suite**

```bash
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew testDebugUnitTest
```

Expected: all tests pass. If the known order-dependent app/navigation failures remain, record their exact test names and failure messages; do not hide them.

- [ ] **Step 2: Build and lint**

```bash
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew assembleDebug lintDebug
```

Expected: `BUILD SUCCESSFUL` for both tasks.

- [ ] **Step 3: Inspect the final diff and status**

```bash
git diff --check
git status --short --branch
git log --oneline -5
```

Confirm that only the intended persistence files are changed and that `docs/settings_design/` remains excluded if still untracked.

- [ ] **Step 4: Commit any final verification-only fix**

If verification required a source correction, run the affected focused test again and commit it with a message matching the correction. Otherwise leave the task commits intact and report the verification results.
