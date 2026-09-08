package net.droopia.hluweather.ui.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import net.droopia.hluweather.navigation.HluNavHost
import net.droopia.hluweather.ui.theme.HluWeatherTheme

@Composable
fun HluWeatherApp() {
    val darkTheme = isSystemInDarkTheme()

    HluWeatherTheme(darkTheme = darkTheme) {
        HluNavHost()
    }
}
