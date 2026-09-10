package net.droopia.hluweather.ui.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.navigation.HluNavHost
import net.droopia.hluweather.ui.settings.SettingsViewModel
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import net.droopia.hluweather.ui.weather.WeatherViewModel

@Composable
fun HluWeatherApp(
    weatherViewModel: WeatherViewModel? = null,
    weatherMapContent: (@Composable (WeatherLocation, List<WeatherLocation>, String?, () -> Unit) -> Unit)? = null,
    locationPickerMapContent: (@Composable (GeoPoint, (GeoPoint) -> Unit) -> Unit)? = null
) {
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val state = settingsViewModel.state.collectAsStateWithLifecycle().value
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (state.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    HluWeatherTheme(darkTheme = darkTheme) {
        HluNavHost(
            settingsViewModel = settingsViewModel,
            weatherViewModel = weatherViewModel,
            weatherMapContent = weatherMapContent,
            locationPickerMapContent = locationPickerMapContent
        )
    }
}
