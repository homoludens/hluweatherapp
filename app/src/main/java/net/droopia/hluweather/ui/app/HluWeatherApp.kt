package net.droopia.hluweather.ui.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.navigation.HluNavHost
import net.droopia.hluweather.ui.settings.SettingsViewModel
import net.droopia.hluweather.ui.theme.HluWeatherTheme

@Composable
fun HluWeatherApp() {
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val state = settingsViewModel.state.collectAsStateWithLifecycle().value
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (state.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    HluWeatherTheme(darkTheme = darkTheme) {
        HluNavHost(settingsViewModel)
    }
}
