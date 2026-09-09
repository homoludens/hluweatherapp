package net.droopia.hluweather.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val viewModel = SettingsViewModel(InMemorySettingsRepository())

        viewModel.setTheme(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, viewModel.state.value.themeMode)
    }

    @Test
    fun selecting_location_disables_track_me_and_selects_location() {
        val viewModel = SettingsViewModel(InMemorySettingsRepository())
        val location = viewModel.state.value.locations[1]
        viewModel.setTrackMe(true)

        viewModel.selectLocation(location)

        assertFalse(viewModel.state.value.trackMeEnabled)
        assertEquals(location.id, viewModel.state.value.selectedLocationId)
    }

    @Test
    fun enabling_track_me_keeps_saved_location_but_marks_track_me_active() {
        val viewModel = SettingsViewModel(InMemorySettingsRepository())

        viewModel.setTrackMe(true)

        assertTrue(viewModel.state.value.trackMeEnabled)
        assertEquals("svilajnac", viewModel.state.value.selectedLocationId)
    }

    @Test
    fun future_settings_actions_are_explicit_no_ops() {
        val viewModel = SettingsViewModel(InMemorySettingsRepository())
        val initialState = viewModel.state.value

        viewModel.onLocationMenuClick(initialState.locations.first())
        viewModel.onAddLocationClick()
        viewModel.onProviderInfoClick(WeatherProvider.OPEN_METEO)

        assertEquals(initialState, viewModel.state.value)
    }

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
}
