package net.droopia.hluweather.ui.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import net.droopia.hluweather.data.dateTimeText
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.label
import net.droopia.hluweather.data.percentText
import net.droopia.hluweather.data.precipitationText
import net.droopia.hluweather.data.temperatureValueText
import net.droopia.hluweather.ui.components.MoonPhase
import net.droopia.hluweather.ui.settings.PrecipitationUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.theme.LocalHluColors

@Composable
fun CurrentWeatherCard(
    location: WeatherLocation,
    forecast: WeatherForecast,
    temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    precipitationUnit: PrecipitationUnit = PrecipitationUnit.MM,
    onLocationClick: () -> Unit = {},
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = "Change location",
                        role = Role.Button,
                        onClick = onLocationClick
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Change location, ${location.name}"
                    }
                    .testTag("current_location"),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = forecast.current.temperature.temperatureValueText(temperatureUnit),
                    modifier = Modifier.testTag("current_temperature"),
                    fontWeight = FontWeight.Medium,
                    autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 70.sp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoonPhase(
                        phase = forecast.moonPhase,
                        modifier = Modifier.size(62.dp)
                    )
                    Spacer(Modifier.width(18.dp))
                    Text(
                        text = forecast.current.condition.label(),
                        modifier = Modifier.weight(1f),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row {
                    Column(modifier = Modifier.weight(1f)) {
                        WeatherMetric(
                            icon = Icons.Default.WaterDrop,
                            title = "Humidity",
                            value = forecast.current.humidity.percentText()
                        )
                        WeatherMetric(
                            icon = Icons.Default.DeviceThermostat,
                            title = "Feels like",
                            value = forecast.current.apparentTemperature.temperatureValueText(temperatureUnit)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        WeatherMetric(
                            icon = Icons.Default.Eco,
                            title = "Dew point",
                            value = forecast.current.dewPoint.temperatureValueText(temperatureUnit)
                        )
                        WeatherMetric(
                            icon = Icons.Default.Umbrella,
                            title = "Precipitation",
                            value = forecast.current.precipitation.precipitationText(precipitationUnit)
                        )
                    }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(21.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
