package net.droopia.hluweather.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.HluWeatherApplication
import net.droopia.hluweather.ui.locationpicker.LocationPickerScreen
import net.droopia.hluweather.ui.locationpicker.LocationPickerViewModel
import net.droopia.hluweather.ui.settings.SettingsScreen
import net.droopia.hluweather.ui.settings.SettingsViewModel
import net.droopia.hluweather.ui.weather.WeatherScreen
import net.droopia.hluweather.ui.weather.WeatherViewModel
import net.droopia.hluweather.ui.weatherroute.WeatherRouteScreen
import net.droopia.hluweather.ui.weatherroute.WeatherRouteViewModel

@Composable
fun HluNavHost(
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    weatherViewModel: WeatherViewModel? = null,
    darkTheme: Boolean = false,
    weatherMapContent: (@Composable (WeatherLocation, List<WeatherLocation>, String?, Boolean, () -> Unit) -> Unit)? = null,
    locationPickerMapContent: (@Composable (GeoPoint, Boolean, (GeoPoint) -> Unit) -> Unit)? = null,
    notificationsPermissionGranted: Boolean = true,
    onNotificationPermissionRequest: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {}
) {
    val navController = rememberNavController()
    val settingsState = settingsViewModel.state.collectAsStateWithLifecycle().value
    val application = LocalContext.current.applicationContext as HluWeatherApplication
    val routeLocations = application.locationRepository.locations
        .collectAsStateWithLifecycle(initialValue = emptyList())
        .value

    NavHost(
        navController = navController,
        startDestination = "weather",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("weather") {
            if (weatherMapContent == null) {
                WeatherScreen(
                    viewModel = weatherViewModel
                        ?: viewModel(factory = WeatherViewModel.Factory),
                    onSettingsClick = { navController.navigate("settings") },
                    onWeatherRouteClick = { navController.navigate("weather_route") },
                    onTrackMeClick = { settingsViewModel.setTrackMe(true) },
                    trackMeSelected = settingsState.trackMeEnabled,
                    darkTheme = darkTheme,
                    temperatureUnit = settingsState.temperatureUnit,
                    precipitationUnit = settingsState.precipitationUnit
                )
            } else {
                WeatherScreen(
                    viewModel = weatherViewModel
                        ?: viewModel(factory = WeatherViewModel.Factory),
                    onSettingsClick = { navController.navigate("settings") },
                    onWeatherRouteClick = { navController.navigate("weather_route") },
                    onTrackMeClick = { settingsViewModel.setTrackMe(true) },
                    trackMeSelected = settingsState.trackMeEnabled,
                    darkTheme = darkTheme,
                    temperatureUnit = settingsState.temperatureUnit,
                    precipitationUnit = settingsState.precipitationUnit,
                    mapContent = weatherMapContent
                )
            }
        }

        composable("weather_route") {
            WeatherRouteScreen(
                viewModel = viewModel(
                    factory = WeatherRouteViewModel.Factory(
                        routingSource = application.routingSource,
                        placeSearchSources = application.routePlaceSearchSources.values.toList(),
                        routeWeatherSource = application.routeWeatherSource,
                        locationRepository = application.locationRepository,
                        deviceLocationSource = application.deviceLocationSource
                    )
                ),
                savedLocations = routeLocations,
                darkTheme = darkTheme,
                temperatureUnit = settingsState.temperatureUnit,
                windUnit = settingsState.windUnit,
                distanceUnit = settingsState.distanceUnit,
                onBackClick = { navController.popBackStack() }
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
                onWeatherAlertsChange = { enabled ->
                    settingsViewModel.setWeatherAlerts(enabled)
                    if (enabled) onNotificationPermissionRequest()
                },
                onDailySummaryChange = { enabled ->
                    settingsViewModel.setDailySummary(enabled)
                    if (enabled) onNotificationPermissionRequest()
                },
                onDailySummaryTimeChange = settingsViewModel::setDailySummaryTime,
                notificationsPermissionGranted = notificationsPermissionGranted,
                onOpenNotificationSettings = onOpenNotificationSettings,
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
