package net.droopia.hluweather.ui.weatherroute

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import net.droopia.hluweather.data.hourText
import net.droopia.hluweather.data.model.RouteWeatherSample
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherRouteResult
import net.droopia.hluweather.data.model.label
import net.droopia.hluweather.data.precipitationText
import net.droopia.hluweather.data.temperatureValueText
import net.droopia.hluweather.data.weatherroute.WeatherSeverity
import net.droopia.hluweather.data.weatherroute.displayRouteSampleIndices
import net.droopia.hluweather.data.weatherroute.weatherSeverity
import net.droopia.hluweather.data.weatherroute.TripWeatherWarning
import net.droopia.hluweather.data.weatherroute.TripWarningType
import net.droopia.hluweather.data.windSpeedText
import net.droopia.hluweather.ui.components.HluWeatherIcon
import net.droopia.hluweather.ui.settings.DistanceUnit
import net.droopia.hluweather.ui.settings.PrecipitationUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.settings.WindUnit
import net.droopia.hluweather.ui.theme.LocalHluColors
import java.time.ZoneId

@Composable
fun WeatherRouteTable(
    result: WeatherRouteResult,
    selectedSampleIndex: Int?,
    temperatureUnit: TemperatureUnit,
    windUnit: WindUnit,
    distanceUnit: DistanceUnit,
    precipitationUnit: PrecipitationUnit = PrecipitationUnit.MM,
    onSampleSelected: (Int) -> Unit,
    timeZone: ZoneId = ZoneId.systemDefault(),
    warnings: List<TripWeatherWarning> = emptyList(),
    modifier: Modifier = Modifier
) {
    val displayedSampleIndices = displayRouteSampleIndices(result.samples, result.departure)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("route_weather_table")
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("route_weather_table_scroll"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                WeatherRouteTableHeader()
                displayedSampleIndices.forEach { sampleIndex ->
                    WeatherRouteTableRow(
                        sample = result.samples[sampleIndex],
                        index = sampleIndex,
                        selected = selectedSampleIndex == sampleIndex,
                        temperatureUnit = temperatureUnit,
                        windUnit = windUnit,
                        precipitationUnit = precipitationUnit,
                        timeZone = timeZone,
                        onSelected = onSampleSelected
                    )
                }
            }
        }
        if (warnings.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .testTag("route_weather_table_warnings"),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                Text("Route warnings", style = MaterialTheme.typography.titleMedium)
                warnings.forEachIndexed { warningIndex, warning ->
                    WeatherRouteTableWarning(warning, result, timeZone, warningIndex)
                }
            }
        }
    }
}

@Composable
private fun WeatherRouteTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalHluColors.current.tableHeader)
            .semantics(mergeDescendants = true) {}
            .testTag("route_weather_table_header")
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        TableCell("Time", 0.14f, header = true)
        TableCell("Weather", 0.32f, header = true)
        TableCell("Temp.", 0.15f, header = true)
        TableCell("Precip.", 0.22f, header = true)
        TableCell("Wind", 0.17f, header = true)
    }
}

@Composable
private fun WeatherRouteTableRow(
    sample: RouteWeatherSample,
    index: Int,
    selected: Boolean,
    temperatureUnit: TemperatureUnit,
    windUnit: WindUnit,
    precipitationUnit: PrecipitationUnit,
    timeZone: ZoneId,
    onSelected: (Int) -> Unit
) {
    val condition = routeWeatherConditionText(sample)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else LocalHluColors.current.tableRow
            )
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .clickable(
                onClickLabel = "Show weather sample ${index + 1}",
                onClick = { onSelected(index) }
            )
            .semantics(mergeDescendants = true) {}
            .testTag("route_weather_table_row_$index")
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        TableCell(sample.arrivalTime.hourText(timeZone), 0.14f)
        Row(modifier = Modifier.weight(0.32f)) {
            HluWeatherIcon(
                condition = sample.condition ?: WeatherCondition.UNKNOWN,
                isDay = sample.isDay,
                modifier = Modifier.width(24.dp)
            )
            Text(
                condition,
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        TableCell(sample.temperatureCelsius.temperatureValueText(temperatureUnit), 0.15f)
        TableCell(sample.precipitationMm.precipitationText(precipitationUnit), 0.22f)
        TableCell(sample.windSpeedKmh.windSpeedText(windUnit), 0.17f)
    }
}

@Composable
private fun WeatherRouteTableWarning(
    warning: TripWeatherWarning,
    result: WeatherRouteResult,
    timeZone: ZoneId,
    warningIndex: Int
) {
    val sampleIndex = result.samples.indexOfFirst { it.point == warning.point }
    val place = warning.placeLabel
        ?: "Route checkpoint ${sampleIndex.takeIf { it >= 0 }?.plus(1) ?: ""}".trim()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {}
            .testTag("route_weather_table_warning_${warning.type.name.lowercase()}_$warningIndex"),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
    ) {
        Text(warning.type.tableTitle(), style = MaterialTheme.typography.titleSmall)
        Text("${warning.startTime.hourText(timeZone)} - ${warning.endTime.hourText(timeZone)}")
        Text(place, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowScope.TableCell(
    text: String,
    weight: Float,
    header: Boolean = false,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier.weight(weight),
        color = if (header) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

private fun routeWeatherConditionText(sample: RouteWeatherSample): String =
    if (weatherSeverity(sample.condition) == WeatherSeverity.UNAVAILABLE) {
        "Weather unavailable"
    } else {
        sample.condition?.label().orEmpty()
    }

private fun TripWarningType.tableTitle(): String = when (this) {
    TripWarningType.RAIN -> "Rain"
    TripWarningType.SNOW -> "Snow"
    TripWarningType.THUNDERSTORM -> "Thunderstorm"
    TripWarningType.FOG -> "Fog"
    TripWarningType.STRONG_WIND -> "Strong wind"
}
