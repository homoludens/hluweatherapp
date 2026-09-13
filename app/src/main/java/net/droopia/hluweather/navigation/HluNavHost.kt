package net.droopia.hluweather.navigation

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material3.Text
import net.droopia.hluweather.HluWeatherApplication
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherRouteResult
import net.droopia.hluweather.ui.locationpicker.LocationPickerScreen
import net.droopia.hluweather.ui.locationpicker.LocationPickerViewModel
import net.droopia.hluweather.ui.settings.SettingsScreen
import net.droopia.hluweather.ui.settings.SettingsViewModel
import net.droopia.hluweather.ui.weather.WeatherScreen
import net.droopia.hluweather.ui.weather.WeatherViewModel
import net.droopia.hluweather.ui.weatherroute.WeatherRouteMap
import net.droopia.hluweather.ui.weatherroute.WeatherRouteScreen
import net.droopia.hluweather.ui.weatherroute.WeatherRouteViewModel

@Composable
fun HluNavHost(
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    weatherViewModel: WeatherViewModel? = null,
    darkTheme: Boolean = false,
    weatherMapContent: (@Composable (WeatherLocation, List<WeatherLocation>, String?, Boolean, () -> Unit) -> Unit)? = null,
    locationPickerMapContent: (@Composable (GeoPoint, Boolean, (GeoPoint) -> Unit) -> Unit)? = null,
    routeViewModelFactory: ViewModelProvider.Factory? = null,
    routeMapContent: (@Composable (WeatherRouteResult, Int?, Boolean, (Int) -> Unit) -> Unit)? = null,
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
                    onWeatherRouteClick = { navController.navigate(WEATHER_ROUTE) },
                    onTrackMeClick = { settingsViewModel.setTrackMe(true) },
                    trackMeSelected = settingsState.trackMeEnabled,
                    darkTheme = darkTheme,
                    temperatureUnit = settingsState.temperatureUnit,
                    precipitationUnit = settingsState.precipitationUnit,
                    windUnit = settingsState.windUnit,
                    windDirectionDisplay = settingsState.windDirectionDisplay,
                    hourlyTableColumns = settingsState.hourlyTableColumns
                )
            } else {
                WeatherScreen(
                    viewModel = weatherViewModel
                        ?: viewModel(factory = WeatherViewModel.Factory),
                    onSettingsClick = { navController.navigate("settings") },
                    onWeatherRouteClick = { navController.navigate(WEATHER_ROUTE) },
                    onTrackMeClick = { settingsViewModel.setTrackMe(true) },
                    trackMeSelected = settingsState.trackMeEnabled,
                    darkTheme = darkTheme,
                    temperatureUnit = settingsState.temperatureUnit,
                    precipitationUnit = settingsState.precipitationUnit,
                    windUnit = settingsState.windUnit,
                    windDirectionDisplay = settingsState.windDirectionDisplay,
                    hourlyTableColumns = settingsState.hourlyTableColumns,
                    mapContent = weatherMapContent
                )
            }
        }

        composable(WEATHER_ROUTE) {
            if (!settingsState.isInitialized) {
                Box(
                    modifier = Modifier.fillMaxSize().testTag("weather_route_settings_loading"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Loading settings")
                }
            } else {
                val routeFactory = remember(
                    application,
                    routeViewModelFactory,
                    settingsState.placeSearchProvider
                ) {
                    routeViewModelFactory ?: WeatherRouteViewModel.Factory(
                        routingSource = application.routingSource,
                        placeSearchProvider = settingsState.placeSearchProvider,
                        placeSearchSources = application.routePlaceSearchSources.values.toList(),
                        routeWeatherSource = application.routeWeatherSource,
                        locationRepository = application.locationRepository,
                        deviceLocationSource = application.deviceLocationSource
                    )
                }
                val routeViewModel: WeatherRouteViewModel = viewModel(factory = routeFactory)
                WeatherRouteScreen(
                    viewModel = routeViewModel,
                    savedLocations = routeLocations,
                    darkTheme = darkTheme,
                    temperatureUnit = settingsState.temperatureUnit,
                    windUnit = settingsState.windUnit,
                    distanceUnit = settingsState.distanceUnit,
                    precipitationUnit = settingsState.precipitationUnit,
                    onBackClick = navController::popBackStack,
                    mapContent = routeMapContent ?: ::defaultRouteMapContent
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
                onPlaceSearchProviderChange = settingsViewModel::setPlaceSearchProvider,
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
                 onWindDirectionDisplayChange = settingsViewModel::setWindDirectionDisplay,
                 onDistanceUnitChange = settingsViewModel::setDistanceUnit,
                 onPrecipitationUnitChange = settingsViewModel::setPrecipitationUnit,
                 hourlyTableColumns = settingsState.hourlyTableColumns,
                 onHourlyTableColumnChange = settingsViewModel::setHourlyTableColumn,
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
            if (!settingsState.isInitialized) {
                Box(
                    modifier = Modifier.fillMaxSize().testTag("location_picker_settings_loading"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Loading settings")
                }
            } else {
                val locationPickerFactory = remember(
                    application,
                    locationId,
                    settingsState.placeSearchProvider
                ) {
                    LocationPickerViewModel.factory(
                        locationId = locationId,
                        placeSearchProvider = settingsState.placeSearchProvider,
                        placeSearchSources = application.routePlaceSearchSources.values.toList()
                    )
                }
                if (locationPickerMapContent == null) {
                    LocationPickerScreen(
                        viewModel = viewModel(factory = locationPickerFactory),
                        onBackClick = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                        onDeleted = { navController.popBackStack() },
                        darkTheme = darkTheme
                    )
                } else {
                    LocationPickerScreen(
                        viewModel = viewModel(factory = locationPickerFactory),
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
}

private const val LOCATION_ID_ARGUMENT = "locationId"
private const val LOCATION_PICKER_ROUTE = "location_picker?$LOCATION_ID_ARGUMENT={$LOCATION_ID_ARGUMENT}"
private const val WEATHER_ROUTE = "weather_route"

@Composable
private fun defaultRouteMapContent(
    result: WeatherRouteResult,
    selectedSampleIndex: Int?,
    darkTheme: Boolean,
    onSampleSelected: (Int) -> Unit
) {
    WeatherRouteMap(
        result = result,
        selectedSampleIndex = selectedSampleIndex,
        darkTheme = darkTheme,
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        onSampleSelected = onSampleSelected
    )
}

private fun locationPickerRoute(locationId: String): String =
    "location_picker?$LOCATION_ID_ARGUMENT=${Uri.encode(locationId)}"
