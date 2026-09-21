package net.droopia.hluweather.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import net.droopia.hluweather.data.airQualityIndexText
import net.droopia.hluweather.data.dateTimeText
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.label
import net.droopia.hluweather.data.particulateMatterText
import net.droopia.hluweather.data.percentText
import net.droopia.hluweather.data.precipitationText
import net.droopia.hluweather.data.temperatureValueText
import net.droopia.hluweather.ui.components.HluWeatherIcon
import net.droopia.hluweather.ui.settings.HourlyTableColumn
import net.droopia.hluweather.ui.settings.PrecipitationUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.settings.defaultHourlyTableColumns
import net.droopia.hluweather.ui.theme.LocalHluColors

private data class AirQualityMetric(
    val title: String,
    val value: String
)

@Composable
fun CurrentWeatherCard(
    location: WeatherLocation,
    forecast: WeatherForecast,
    temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    precipitationUnit: PrecipitationUnit = PrecipitationUnit.MM,
    hourlyTableColumns: Set<HourlyTableColumn> = defaultHourlyTableColumns,
    onLocationClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val current = forecast.current
    val selectedMetric = when {
        HourlyTableColumn.EUROPEAN_AQI in hourlyTableColumns -> AirQualityMetric(
            "AQI",
            current.europeanAqi.airQualityIndexText()
        )
        HourlyTableColumn.PM2_5 in hourlyTableColumns -> AirQualityMetric(
            "PM2.5",
            current.pm2_5.particulateMatterText()
        )
        HourlyTableColumn.PM10 in hourlyTableColumns -> AirQualityMetric(
            "PM10",
            current.pm10.particulateMatterText()
        )
        else -> AirQualityMetric(
            "Feels like",
            current.apparentTemperature.temperatureValueText(temperatureUnit)
        )
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = LocalHluColors.current.weatherCard,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            WeatherHeader(
                location = location,
                timestamp = forecast.fetchedAt.dateTimeText(ZoneId.of(forecast.timezone)),
                onLocationClick = onLocationClick
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(0.58f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = current.temperature.temperatureValueText(temperatureUnit),
                        modifier = Modifier
                            .testTag("current_temperature")
                            .fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 60.sp,
                        lineHeight = 56.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 40.sp,
                            maxFontSize = 60.sp
                        )
                    )
                    Text(
                        text = current.condition.label(),
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(0.42f)
                        .height(88.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HluWeatherIcon(
                        condition = current.condition,
                        isDay = current.isDay,
                        modifier = Modifier.size(88.dp),
                        contentDescription = null
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            WeatherMetrics(
                humidity = current.humidity.percentText(),
                dewPoint = current.dewPoint.temperatureValueText(temperatureUnit),
                thirdMetricTitle = selectedMetric.title,
                thirdMetricValue = selectedMetric.value,
                precipitation = current.precipitation.precipitationText(precipitationUnit)
            )
        }
    }
}

@Composable
private fun WeatherHeader(
    location: WeatherLocation,
    timestamp: String,
    onLocationClick: () -> Unit
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
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = location.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = timestamp,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WeatherMetrics(
    humidity: String,
    dewPoint: String,
    thirdMetricTitle: String,
    thirdMetricValue: String,
    precipitation: String
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("current_metrics"),
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherMetric(
                modifier = Modifier
                    .weight(1f)
                    .testTag("current_metric_humidity"),
                icon = Icons.Default.WaterDrop,
                title = "Humidity",
                value = humidity,
                iconTint = colors.primary
            )
            MetricDivider()
            WeatherMetric(
                modifier = Modifier
                    .weight(1f)
                    .testTag("current_metric_dew_point"),
                icon = Icons.Default.Eco,
                title = "Dew point",
                value = dewPoint,
                iconTint = colors.secondary
            )
            MetricDivider()
            WeatherMetric(
                modifier = Modifier
                    .weight(1f)
                    .testTag("current_metric_feels_like"),
                icon = Icons.Default.DeviceThermostat,
                title = thirdMetricTitle,
                value = thirdMetricValue,
                iconTint = colors.error
            )
            MetricDivider()
            WeatherMetric(
                modifier = Modifier
                    .weight(1f)
                    .testTag("current_metric_precipitation"),
                icon = Icons.Default.Umbrella,
                title = "Precipitation",
                value = precipitation,
                iconTint = colors.tertiary
            )
        }
    }
}

@Composable
private fun MetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(68.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    )
}

@Composable
private fun WeatherMetric(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    iconTint: Color
) {
    Column(
        modifier = modifier
            .height(68.dp)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconTint.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 16.sp)
        )
    }
}
