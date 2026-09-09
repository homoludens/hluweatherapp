package net.droopia.hluweather.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.droopia.hluweather.ui.settings.SettingsScreen
import net.droopia.hluweather.ui.settings.SettingsViewModel
import net.droopia.hluweather.ui.weather.WeatherScreen

@Composable
fun HluNavHost(
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val navController = rememberNavController()
    val settingsState = settingsViewModel.state.collectAsStateWithLifecycle().value

    NavHost(
        navController = navController,
        startDestination = "weather"
    ) {
        composable("weather") {
            WeatherScreen(
                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                state = settingsState,
                onBackClick = {
                    navController.popBackStack()
                },
                onProviderChange = settingsViewModel::setProvider,
                onProviderInfoClick = settingsViewModel::onProviderInfoClick,
                onTrackMeChange = settingsViewModel::setTrackMe,
                onLocationSelect = settingsViewModel::selectLocation,
                onLocationMenuClick = settingsViewModel::onLocationMenuClick,
                onAddLocationClick = settingsViewModel::onAddLocationClick,
                onThemeChange = settingsViewModel::setTheme,
                onTemperatureUnitChange = settingsViewModel::setTemperatureUnit,
                onWindUnitChange = settingsViewModel::setWindUnit,
                onDistanceUnitChange = settingsViewModel::setDistanceUnit,
                onPrecipitationUnitChange = settingsViewModel::setPrecipitationUnit,
                onWeatherAlertsChange = settingsViewModel::setWeatherAlerts,
                onDailySummaryChange = settingsViewModel::setDailySummary,
                onTripAlertsChange = settingsViewModel::setTripAlerts,
                onClearCacheClick = settingsViewModel::clearCache
            )
        }
    }
}
