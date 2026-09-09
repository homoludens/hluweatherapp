package net.droopia.hluweather.ui.settings

import net.droopia.hluweather.data.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {

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
}
