package net.droopia.hluweather.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.droopia.hluweather.ui.settings.SettingsScreen
import net.droopia.hluweather.ui.weather.WeatherScreen

@Composable
fun HluNavHost() {
    val navController = rememberNavController()

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
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
