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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.ui.theme.LocalHluColors

@Composable
fun HluWeatherIcon(
    condition: WeatherCondition,
    isDay: Boolean?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val colors = LocalHluColors.current
    val sunColor = Color(0xFFFFB300)
    val nightColor = colors.moonAccent
    val cloudColor = colors.cloudAccent
    val rainColor = Color(0xFF5367E8)

    val imageVector: ImageVector
    val tint: Color

    when (condition) {
        WeatherCondition.CLEAR,
        WeatherCondition.MOSTLY_CLEAR -> {
            imageVector = if (isDay == false) {
                Icons.Default.DarkMode
            } else {
                Icons.Default.WbSunny
            }
            tint = if (isDay == false) nightColor else sunColor
        }
        WeatherCondition.PARTLY_CLOUDY -> {
            imageVector = Icons.Default.WbCloudy
            tint = cloudColor
        }
        WeatherCondition.CLOUDY -> {
            imageVector = Icons.Default.Cloud
            tint = cloudColor
        }
        WeatherCondition.FOG -> {
            imageVector = Icons.Default.Air
            tint = cloudColor
        }
        WeatherCondition.DRIZZLE,
        WeatherCondition.RAIN -> {
            imageVector = Icons.Default.Shower
            tint = rainColor
        }
        WeatherCondition.SNOW -> {
            imageVector = Icons.Default.AcUnit
            tint = Color(0xFFB9C6FF)
        }
        WeatherCondition.THUNDERSTORM -> {
            imageVector = Icons.Default.Thunderstorm
            tint = Color(0xFFFFB300)
        }
        WeatherCondition.UNKNOWN -> {
            imageVector = Icons.Default.Cloud
            tint = cloudColor
        }
    }

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
    )
}
