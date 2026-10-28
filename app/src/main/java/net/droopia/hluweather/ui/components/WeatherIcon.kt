package net.droopia.hluweather.ui.components

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.ui.theme.LocalHluColors

@Composable
fun HluWeatherIcon(
    condition: WeatherCondition,
    isDay: Boolean?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val spec = weatherIconSpec(condition, isDay, LocalHluColors.current)

    Icon(
        imageVector = spec.imageVector,
        contentDescription = contentDescription,
        tint = spec.tint,
        modifier = modifier
    )
}
