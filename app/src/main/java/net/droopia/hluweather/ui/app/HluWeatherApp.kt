package net.droopia.hluweather.ui.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.droopia.hluweather.ui.theme.HluWeatherTheme

@Composable
fun HluWeatherApp() {
    HluWeatherTheme(darkTheme = false) {
        Text(
            text = "HluWeatherApp",
            modifier = Modifier.fillMaxSize()
        )
    }
}
