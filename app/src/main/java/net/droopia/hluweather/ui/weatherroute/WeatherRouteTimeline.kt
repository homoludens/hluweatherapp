package net.droopia.hluweather.ui.weatherroute

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.droopia.hluweather.data.distanceText
import net.droopia.hluweather.data.hourText
import net.droopia.hluweather.data.model.RouteWeatherSample
import net.droopia.hluweather.data.model.WeatherRouteResult
import net.droopia.hluweather.data.model.label
import net.droopia.hluweather.data.percentText
import net.droopia.hluweather.data.temperatureValueText
import net.droopia.hluweather.data.windSpeedText
import net.droopia.hluweather.data.weatherroute.WeatherSeverity
import net.droopia.hluweather.data.weatherroute.weatherSeverity
import net.droopia.hluweather.ui.settings.DistanceUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.settings.WindUnit
import java.time.ZoneId

@Composable
fun WeatherRouteTimeline(
    result: WeatherRouteResult,
    selectedSampleIndex: Int?,
    temperatureUnit: TemperatureUnit,
    windUnit: WindUnit,
    distanceUnit: DistanceUnit,
    onSampleSelected: (Int) -> Unit,
    timeZone: ZoneId = ZoneId.systemDefault(),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = result.end.label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("route_timeline_destination"),
            style = MaterialTheme.typography.titleLarge
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = result.samples,
                key = { index, _ -> index }
            ) { index, sample ->
                WeatherRouteTimelineItem(
                    sample = sample,
                    index = index,
                    isSelected = selectedSampleIndex == index,
                    temperatureUnit = temperatureUnit,
                    windUnit = windUnit,
                    distanceUnit = distanceUnit,
                    timeZone = timeZone,
                    onSelected = onSampleSelected
                )
            }
        }
    }
}

@Composable
private fun WeatherRouteTimelineItem(
    sample: RouteWeatherSample,
    index: Int,
    isSelected: Boolean,
    temperatureUnit: TemperatureUnit,
    windUnit: WindUnit,
    distanceUnit: DistanceUnit,
    timeZone: ZoneId,
    onSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("route_timeline_item_$index")
            .clickable { onSelected(index) },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sample.arrivalTime.hourText(timeZone), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = sample.distanceMeters.div(1_000.0).distanceText(distanceUnit),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (weatherSeverity(sample.condition) == WeatherSeverity.UNAVAILABLE) {
                Text("Weather unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(sample.condition?.label().orEmpty())
            }
            TimelineValue("Temperature", sample.temperatureCelsius.temperatureValueText(temperatureUnit))
            TimelineValue("Wind", sample.windSpeedKmh.windSpeedText(windUnit))
            TimelineValue("Precipitation", sample.precipitationProbability.percentText())
        }
    }
}

@Composable
private fun TimelineValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}
