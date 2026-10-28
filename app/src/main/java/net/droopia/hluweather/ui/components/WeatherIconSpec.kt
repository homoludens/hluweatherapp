package net.droopia.hluweather.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Shower
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.ui.theme.HluColors

data class WeatherIconSpec(
    val imageVector: ImageVector,
    val tint: Color
)

/** Selects the existing weather icon and tint without depending on Compose state. */
fun weatherIconSpec(
    condition: WeatherCondition,
    isDay: Boolean?,
    colors: HluColors
): WeatherIconSpec {
    val sunColor = Color(0xFFFFB300)
    val rainColor = Color(0xFF5367E8)

    return when (condition) {
        WeatherCondition.CLEAR,
        WeatherCondition.MOSTLY_CLEAR -> if (isDay == false) {
            WeatherIconSpec(Icons.Default.DarkMode, colors.moonAccent)
        } else {
            WeatherIconSpec(Icons.Default.WbSunny, sunColor)
        }

        WeatherCondition.PARTLY_CLOUDY ->
            WeatherIconSpec(Icons.Default.WbCloudy, colors.cloudAccent)

        WeatherCondition.CLOUDY ->
            WeatherIconSpec(Icons.Default.Cloud, colors.cloudAccent)

        WeatherCondition.FOG ->
            WeatherIconSpec(Icons.Default.Air, colors.cloudAccent)

        WeatherCondition.DRIZZLE,
        WeatherCondition.RAIN ->
            WeatherIconSpec(Icons.Default.Shower, rainColor)

        WeatherCondition.SNOW ->
            WeatherIconSpec(Icons.Default.AcUnit, Color(0xFFB9C6FF))

        WeatherCondition.THUNDERSTORM ->
            WeatherIconSpec(Icons.Default.Thunderstorm, sunColor)

        WeatherCondition.UNKNOWN ->
            WeatherIconSpec(Icons.Default.Cloud, colors.cloudAccent)
    }
}
