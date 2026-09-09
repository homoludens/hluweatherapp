package net.droopia.hluweather.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import net.droopia.hluweather.data.dateTimeText
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.label
import net.droopia.hluweather.data.percentText
import net.droopia.hluweather.data.precipitationText
import net.droopia.hluweather.data.temperatureText
import net.droopia.hluweather.ui.components.MoonPhase
import net.droopia.hluweather.ui.theme.LocalHluColors

@Composable
fun CurrentWeatherCard(
    location: WeatherLocation,
    forecast: WeatherForecast,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = LocalHluColors.current.weatherCard
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = location.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = forecast.fetchedAt.dateTimeText(ZoneId.of(forecast.timezone)),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = forecast.current.temperature.temperatureText(),
                            fontSize = 70.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 70.sp
                        )
                        Spacer(Modifier.width(18.dp))
                        MoonPhase(
                            phase = forecast.moonPhase,
                            modifier = Modifier.size(62.dp)
                        )
                    }
                    Text(
                        text = forecast.current.condition.label(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                VerticalDivider(
                    modifier = Modifier.height(145.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Spacer(Modifier.width(18.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    WeatherMetric(
                        icon = Icons.Default.WaterDrop,
                        title = "Humidity",
                        value = forecast.current.humidity.percentText()
                    )
                    WeatherMetric(
                        icon = Icons.Default.DeviceThermostat,
                        title = "Feels like",
                        value = forecast.current.apparentTemperature.temperatureText()
                    )
                    WeatherMetric(
                        icon = Icons.Default.Eco,
                        title = "Dew point",
                        value = forecast.current.dewPoint.temperatureText()
                    )
                    WeatherMetric(
                        icon = Icons.Default.Umbrella,
                        title = "Precipitation",
                        value = forecast.current.precipitation.precipitationText()
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherMetric(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(21.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontWeight = FontWeight.SemiBold
        )
    }
}
