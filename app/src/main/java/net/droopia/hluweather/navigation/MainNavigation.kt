package net.droopia.hluweather.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.ui.weather.WeatherViewModel

enum class MainDestination(val forecastMode: ForecastMode?) {
    HOURLY(ForecastMode.HOURLY),
    DAILY(ForecastMode.DAILY),
    MAP(ForecastMode.MAP),
    ROUTE(null)
}

@Composable
fun rememberMainNavigation(weatherViewModel: WeatherViewModel? = null): MainNavigation {
    val navController = rememberNavController()
    return remember(navController, weatherViewModel) {
        MainNavigation(navController, weatherViewModel)
    }
}

/** Coordinates the existing weather entry and saved route entry; secondary screens use the NavController. */
class MainNavigation internal constructor(
    internal val navController: NavHostController,
    private val suppliedWeatherViewModel: WeatherViewModel?
) {
    /** Null while a secondary screen is visible; weather selection comes from its existing owner. */
    @Composable
    fun currentDestination(): MainDestination? {
        val entry by navController.currentBackStackEntryAsState()
        return when (entry?.destination?.route) {
            WEATHER -> {
                val state by weatherViewModel().state.collectAsStateWithLifecycle()
                MainDestination.entries.first { it.forecastMode == state.forecastMode }
            }
            WEATHER_ROUTE -> MainDestination.ROUTE
            else -> null
        }
    }

    /** Select from a main screen without adding another weather or route entry. */
    fun select(destination: MainDestination) {
        val current = navController.currentDestination?.route
        if (current != WEATHER && current != WEATHER_ROUTE) return

        if (destination == MainDestination.ROUTE) {
            if (current != WEATHER_ROUTE) {
                navController.navigate(WEATHER_ROUTE) {
                    launchSingleTop = true
                    restoreState = true
                }
            }
        } else {
            if (current == WEATHER_ROUTE && !returnFromRoute()) return
            weatherViewModel().onForecastModeSelected(requireNotNull(destination.forecastMode))
        }
    }

    fun openSettings() {
        if (navController.currentDestination?.route == WEATHER ||
            navController.currentDestination?.route == WEATHER_ROUTE
        ) {
            navController.navigate(SETTINGS) { launchSingleTop = true }
        }
    }

    internal fun returnFromRoute(): Boolean =
        navController.currentDestination?.route == WEATHER_ROUTE &&
            navController.popBackStack(WEATHER, inclusive = false, saveState = true)

    internal fun usesWeatherViewModel(viewModel: WeatherViewModel): Boolean =
        suppliedWeatherViewModel === viewModel

    internal fun weatherViewModel(): WeatherViewModel = suppliedWeatherViewModel
        ?: ViewModelProvider(navController.getBackStackEntry(WEATHER), WeatherViewModel.Factory)
            .get(WeatherViewModel::class.java)
}

internal const val WEATHER = "weather"
internal const val WEATHER_ROUTE = "weather_route"
internal const val SETTINGS = "settings"
