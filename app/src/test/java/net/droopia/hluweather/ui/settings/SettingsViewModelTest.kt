package net.droopia.hluweather.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.WeatherLocation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.ForecastLoad
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selecting_theme_updates_state() {
        val viewModel = SettingsViewModel(
            InMemorySettingsRepository(),
            InMemoryLocationRepository()
        )

        viewModel.setTheme(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, viewModel.state.value.themeMode)
    }

    @Test
    fun selecting_location_disables_track_me_and_selects_location() {
        val viewModel = SettingsViewModel(
            InMemorySettingsRepository(),
            InMemoryLocationRepository()
        )
        val location = viewModel.state.value.locations[1]
        viewModel.setTrackMe(true)

        viewModel.selectLocation(location)

        assertFalse(viewModel.state.value.trackMeEnabled)
        assertEquals(location.id, viewModel.state.value.selectedLocationId)
    }

    @Test
    fun enabling_track_me_keeps_saved_location_but_marks_track_me_active() {
        val viewModel = SettingsViewModel(
            InMemorySettingsRepository(),
            InMemoryLocationRepository()
        )

        viewModel.setTrackMe(true)

        assertTrue(viewModel.state.value.trackMeEnabled)
        assertEquals("svilajnac", viewModel.state.value.selectedLocationId)
    }

    @Test
    fun future_settings_actions_are_explicit_no_ops() {
        val viewModel = SettingsViewModel(
            InMemorySettingsRepository(),
            InMemoryLocationRepository()
        )
        val initialState = viewModel.state.value

        viewModel.onLocationMenuClick(initialState.locations.first())
        viewModel.onAddLocationClick()
        viewModel.onProviderInfoClick(WeatherProvider.OPEN_METEO)
        viewModel.clearCache()

        assertEquals(initialState, viewModel.state.value)
    }

    @Test
    fun exposes_a_view_model_factory() {
        assertNotNull(SettingsViewModel.Factory)
    }

    @Test
    fun hydrates_state_from_repository() {
        val repository = InMemorySettingsRepository(
            PersistedSettings(provider = WeatherProvider.MET_NO, themeMode = ThemeMode.DARK)
        )

        val viewModel = SettingsViewModel(repository, InMemoryLocationRepository())

        assertEquals(WeatherProvider.MET_NO, viewModel.state.value.provider)
        assertEquals(ThemeMode.DARK, viewModel.state.value.themeMode)
    }

    @Test
    fun mutations_save_the_updated_snapshot() {
        val initial = PersistedSettings(
            provider = WeatherProvider.MET_NO,
            selectedLocationId = "trieste",
            trackMeEnabled = true,
            themeMode = ThemeMode.DARK,
            temperatureUnit = TemperatureUnit.FAHRENHEIT,
            windUnit = WindUnit.MPH,
            distanceUnit = DistanceUnit.MILES,
            precipitationUnit = PrecipitationUnit.INCH,
            weatherAlerts = false,
            dailySummary = false,
            tripAlerts = true
        )
        val repository = InMemorySettingsRepository(initial)
        val viewModel = SettingsViewModel(
            repository,
            InMemoryLocationRepository(initialActiveLocation = null)
        )

        viewModel.setDailySummary(true)

        assertTrue(viewModel.state.value.dailySummary)
        assertEquals(initial.copy(trackMeEnabled = false, dailySummary = true), repository.saved)
    }

    @Test
    fun unknown_stored_location_uses_the_default_location() {
        val repository = InMemorySettingsRepository(
            PersistedSettings(selectedLocationId = "missing-location")
        )

        val viewModel = SettingsViewModel(repository, InMemoryLocationRepository())

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
        val viewModel = SettingsViewModel(repository, InMemoryLocationRepository())

        viewModel.setTheme(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, viewModel.state.value.themeMode)
    }

    @Test
    fun failed_read_keeps_default_state() {
        val repository = object : SettingsRepository {
            override val settings = flow<PersistedSettings> {
                error("read failed")
            }

            override suspend fun save(settings: PersistedSettings) = Unit
        }

        val viewModel = SettingsViewModel(repository, InMemoryLocationRepository(emptyList()))

        assertEquals(SettingsUiState(), viewModel.state.value)
    }

    @Test
    fun initial_hydration_does_not_overwrite_a_mutation_made_while_loading() {
        val repository = DeferredSettingsRepository()
        val viewModel = SettingsViewModel(repository, InMemoryLocationRepository())

        viewModel.setTheme(ThemeMode.LIGHT)
        repository.snapshot.complete(PersistedSettings(themeMode = ThemeMode.DARK))

        assertEquals(ThemeMode.LIGHT, viewModel.state.value.themeMode)
    }

    @Test
    fun selecting_location_delegates_to_location_repository() {
        val locationRepository = InMemoryLocationRepository()
        val viewModel = SettingsViewModel(InMemorySettingsRepository(), locationRepository)
        val location = viewModel.state.value.locations[1]

        viewModel.selectLocation(location)

        assertEquals(listOf(location.id), locationRepository.selectedIds)
    }

    @Test
    fun track_me_changes_delegate_to_location_repository() {
        val locationRepository = InMemoryLocationRepository()
        val viewModel = SettingsViewModel(InMemorySettingsRepository(), locationRepository)

        viewModel.setTrackMe(true)

        assertEquals(listOf(true), locationRepository.trackMeValues)
    }

    @Test
    fun clear_cache_delegates_to_weather_repository() {
        var clearCalls = 0
        val weatherRepository = object : WeatherRepository {
            override suspend fun getForecast(
                provider: WeatherProvider,
                location: ActiveLocation
            ) = error("not used")

            override suspend fun clearCache() {
                clearCalls++
            }
        }
        val viewModel = SettingsViewModel(
            InMemorySettingsRepository(),
            InMemoryLocationRepository(),
            weatherRepository
        )

        viewModel.clearCache()

        assertEquals(1, clearCalls)
    }

    @Test
    fun reflects_a_saved_location_selected_through_the_shared_repository() {
        val locationRepository = InMemoryLocationRepository()
        val viewModel = SettingsViewModel(InMemorySettingsRepository(), locationRepository)
        val selectedLocation = locationRepository.locations.value[1]

        locationRepository.activeLocation.value = ActiveLocation.Saved(selectedLocation)

        assertEquals(selectedLocation.id, viewModel.state.value.selectedLocationId)
    }

    @Test
    fun returning_from_track_me_to_the_previous_saved_location_disables_track_me() {
        val locationRepository = InMemoryLocationRepository()
        val viewModel = SettingsViewModel(InMemorySettingsRepository(), locationRepository)
        val previousLocation = locationRepository.locations.value.first()

        viewModel.setTrackMe(true)
        locationRepository.activeLocation.value = ActiveLocation.Saved(previousLocation)

        assertEquals(previousLocation.id, viewModel.state.value.selectedLocationId)
        assertFalse(viewModel.state.value.trackMeEnabled)
    }

    @Test
    fun hydration_uses_canonical_location_mode_for_track_me() {
        val locationRepository = InMemoryLocationRepository(initialActiveLocation = null)
        locationRepository.mode.value = LocationMode.TRACK_ME

        val viewModel = SettingsViewModel(
            InMemorySettingsRepository(PersistedSettings(trackMeEnabled = false)),
            locationRepository
        )

        assertTrue(viewModel.state.value.trackMeEnabled)
    }

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

    private class DeferredSettingsRepository : SettingsRepository {
        val snapshot = CompletableDeferred<PersistedSettings>()

        override val settings = flow {
            emit(snapshot.await())
        }

        override suspend fun save(settings: PersistedSettings) = Unit
    }

    private class InMemoryLocationRepository(
        initial: List<WeatherLocation> = testLocations,
        initialActiveLocation: WeatherLocation? = initial.firstOrNull()
    ) : LocationRepository {
        override val locations = MutableStateFlow(initial)
        override val activeLocation = MutableStateFlow<ActiveLocation?>(
            initialActiveLocation?.let(ActiveLocation::Saved)
        )
        val mode = MutableStateFlow(LocationMode.SAVED_LOCATION)
        override val locationMode: StateFlow<LocationMode> = mode
        val selectedIds = mutableListOf<String>()
        val trackMeValues = mutableListOf<Boolean>()

        override suspend fun add(location: WeatherLocation) = Unit

        override suspend fun update(location: WeatherLocation) = Unit

        override suspend fun delete(id: String) = Unit

        override suspend fun selectSaved(id: String) {
            selectedIds += id
            activeLocation.value = locations.value
                .firstOrNull { it.id == id }
                ?.let(ActiveLocation::Saved)
        }

        override suspend fun setTrackMe(enabled: Boolean) {
            trackMeValues += enabled
            mode.value = if (enabled) LocationMode.TRACK_ME else LocationMode.SAVED_LOCATION
            if (!enabled && activeLocation.value == null) {
                activeLocation.value = locations.value.firstOrNull()?.let(ActiveLocation::Saved)
            } else if (enabled) {
                activeLocation.value = null
            }
        }
    }

    private companion object {
        val testLocations = listOf(
            WeatherLocation("svilajnac", "Svilajnac", 44.2380, 21.1970),
            WeatherLocation("belgrade", "Belgrade", 44.8176, 20.4633),
            WeatherLocation("trieste", "Trieste", 45.6495, 13.7768)
        )
    }
}
