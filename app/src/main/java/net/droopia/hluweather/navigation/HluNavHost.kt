package net.droopia.hluweather.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.ui.locationpicker.LocationPickerScreen
import net.droopia.hluweather.ui.locationpicker.LocationPickerViewModel
import net.droopia.hluweather.ui.settings.SettingsScreen
import net.droopia.hluweather.ui.settings.SettingsViewModel
import net.droopia.hluweather.ui.weather.WeatherScreen
import net.droopia.hluweather.ui.weather.WeatherViewModel

@Composable
fun HluNavHost(
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    weatherViewModel: WeatherViewModel? = null,
    darkTheme: Boolean = false,
    weatherMapContent: (@Composable (WeatherLocation, List<WeatherLocation>, String?, Boolean, () -> Unit) -> Unit)? = null,
    locationPickerMapContent: (@Composable (GeoPoint, Boolean, (GeoPoint) -> Unit) -> Unit)? = null
) {
    val navController = rememberNavController()
    val settingsState = settingsViewModel.state.collectAsStateWithLifecycle().value

    NavHost(
        navController = navController,
        startDestination = "weather"
    ) {
        composable("weather") {
            if (weatherMapContent == null) {
                WeatherScreen(
                    viewModel = weatherViewModel
                        ?: viewModel(factory = WeatherViewModel.Factory),
                    onSettingsClick = { navController.navigate("settings") },
                    onTrackMeClick = { settingsViewModel.setTrackMe(true) },
                    trackMeSelected = settingsState.trackMeEnabled,
                    darkTheme = darkTheme
                )
            } else {
                WeatherScreen(
                    viewModel = weatherViewModel
                        ?: viewModel(factory = WeatherViewModel.Factory),
                    onSettingsClick = { navController.navigate("settings") },
                    onTrackMeClick = { settingsViewModel.setTrackMe(true) },
                    trackMeSelected = settingsState.trackMeEnabled,
                    darkTheme = darkTheme,
                    mapContent = weatherMapContent
                )
            }
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
                onLocationMenuClick = { location ->
                    navController.navigate(locationPickerRoute(location.id))
                },
                onAddLocationClick = {
                    navController.navigate("location_picker")
                },
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

        composable(
            route = LOCATION_PICKER_ROUTE,
            arguments = listOf(
                navArgument(LOCATION_ID_ARGUMENT) {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val locationId = backStackEntry.arguments?.getString(LOCATION_ID_ARGUMENT)
            if (locationPickerMapContent == null) {
                LocationPickerScreen(
                    viewModel = viewModel(factory = LocationPickerViewModel.factory(locationId)),
                    onBackClick = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                    darkTheme = darkTheme
                )
            } else {
                LocationPickerScreen(
                    viewModel = viewModel(factory = LocationPickerViewModel.factory(locationId)),
                    onBackClick = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                    darkTheme = darkTheme,
                    mapContent = locationPickerMapContent
                )
            }
        }
    }
}

private const val LOCATION_ID_ARGUMENT = "locationId"
private const val LOCATION_PICKER_ROUTE = "location_picker?$LOCATION_ID_ARGUMENT={$LOCATION_ID_ARGUMENT}"

private fun locationPickerRoute(locationId: String): String =
    "location_picker?$LOCATION_ID_ARGUMENT=${Uri.encode(locationId)}"
