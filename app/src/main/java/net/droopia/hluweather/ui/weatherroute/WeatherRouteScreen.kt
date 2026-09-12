package net.droopia.hluweather.ui.weatherroute

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.droopia.hluweather.data.distanceText
import net.droopia.hluweather.data.model.DrivingRoute
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.RouteEndpoint
import net.droopia.hluweather.data.model.RouteWeatherSample
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherRouteResult
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.repository.PlaceSearchResult
import net.droopia.hluweather.data.weatherroute.RouteWeatherForecastHour
import net.droopia.hluweather.data.weatherroute.RouteWeatherSnapshot
import net.droopia.hluweather.data.weatherroute.enrich
import net.droopia.hluweather.data.weatherroute.findTripWeatherWarnings
import net.droopia.hluweather.data.weatherroute.summarizeTripWeather
import net.droopia.hluweather.ui.components.HluWeatherIcon
import net.droopia.hluweather.ui.settings.DistanceUnit
import net.droopia.hluweather.ui.settings.PrecipitationUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.settings.WindUnit
import net.droopia.hluweather.ui.theme.HluWeatherTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherRouteScreen(
    viewModel: WeatherRouteViewModel,
    savedLocations: List<WeatherLocation>,
    darkTheme: Boolean,
    temperatureUnit: TemperatureUnit,
    windUnit: WindUnit,
    distanceUnit: DistanceUnit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    precipitationUnit: PrecipitationUnit,
    mapContent: @Composable (WeatherRouteResult, Int?, Boolean, (Int) -> Unit) -> Unit =
        { result, selectedSampleIndex, isDarkTheme, onSampleSelected ->
            WeatherRouteMap(
                result = result,
                selectedSampleIndex = selectedSampleIndex,
                darkTheme = isDarkTheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                onSampleSelected = onSampleSelected
            )
        }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WeatherRouteScreenContent(
        state = state,
        savedLocations = savedLocations,
        darkTheme = darkTheme,
        temperatureUnit = temperatureUnit,
        windUnit = windUnit,
        distanceUnit = distanceUnit,
        precipitationUnit = precipitationUnit,
        selectedDeparture = viewModel.selectedDeparture(),
        onBackClick = onBackClick,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onSearchResultSelected = viewModel::selectSearchResult,
        onSavedLocationSelected = viewModel::selectSavedLocation,
        onCurrentLocationSelected = viewModel::selectCurrentLocation,
        onMapEndpointSelected = viewModel::selectMapEndpoint,
        onCalculate = viewModel::calculate,
        onDepartureOffsetChanged = viewModel::onDepartureOffsetChanged,
        onSpeedChanged = viewModel::onSpeedChanged,
        onRetry = viewModel::calculate,
        onRetryWeather = viewModel::retryWeather,
        onSampleSelected = viewModel::selectSample,
        mapContent = mapContent,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherRouteScreenContent(
    state: WeatherRouteUiState,
    savedLocations: List<WeatherLocation>,
    darkTheme: Boolean,
    temperatureUnit: TemperatureUnit,
    windUnit: WindUnit,
    distanceUnit: DistanceUnit,
    precipitationUnit: PrecipitationUnit,
    selectedDeparture: Instant,
    onBackClick: () -> Unit,
    onSearchQueryChanged: (RouteEndpointSlot, String) -> Unit,
    onSearchResultSelected: (PlaceSearchResult) -> Unit,
    onSavedLocationSelected: (RouteEndpointSlot, WeatherLocation) -> Unit,
    onCurrentLocationSelected: (RouteEndpointSlot) -> Unit,
    onMapEndpointSelected: (RouteEndpointSlot, RouteEndpoint) -> Unit,
    onCalculate: () -> Unit,
    onDepartureOffsetChanged: (Int) -> Unit,
    onSpeedChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onRetryWeather: () -> Unit,
    onSampleSelected: (Int?) -> Unit,
    mapContent: @Composable (WeatherRouteResult, Int?, Boolean, (Int) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeZone = TimeZone.currentSystemDefault()
    val canCalculate = state.start != null && state.end != null && !state.isCalculating
    val speed = state.speedText.toFloatOrNull()?.coerceIn(40f, 130f) ?: 80f

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("weather_route_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Trip weather") },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("weather_route_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag("weather_route_content"),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                RouteEndpointPicker(
                    state = state,
                    savedLocations = savedLocations,
                    slot = RouteEndpointSlot.START,
                    onSearchQueryChanged = onSearchQueryChanged,
                    onSearchResultSelected = onSearchResultSelected,
                    onSavedLocationSelected = onSavedLocationSelected,
                    onCurrentLocationSelected = onCurrentLocationSelected,
                    onMapEndpointSelected = onMapEndpointSelected,
                    darkTheme = darkTheme,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                RouteEndpointPicker(
                    state = state,
                    savedLocations = savedLocations,
                    slot = RouteEndpointSlot.END,
                    onSearchQueryChanged = onSearchQueryChanged,
                    onSearchResultSelected = onSearchResultSelected,
                    onSavedLocationSelected = onSavedLocationSelected,
                    onCurrentLocationSelected = onCurrentLocationSelected,
                    onMapEndpointSelected = onMapEndpointSelected,
                    darkTheme = darkTheme,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("route_calculate")
                            .semantics { if (!canCalculate) disabled() }
                    ) {
                        Button(
                            onClick = onCalculate,
                            enabled = canCalculate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("trip_show_weather")
                        ) {
                            if (state.isCalculating) {
                                CircularProgressIndicator()
                            } else {
                                Text("Show trip weather")
                            }
                        }
                    }
                    state.routeError?.let { error ->
                        RouteError(
                            error = error,
                            canRetry = state.start != null && state.end != null && !state.isCalculating,
                            onRetry = onRetry
                        )
                    }
                }
            }
            item {
                StartTimeControl(
                    offsetHours = state.departureOffsetHours,
                    selectedDeparture = selectedDeparture,
                    timeZone = timeZone,
                    onOffsetChanged = onDepartureOffsetChanged
                )
            }
            item {
                SpeedControl(
                    speed = speed,
                    onSpeedChanged = { onSpeedChanged(it.roundToInt().toString()) }
                )
            }

            val result = state.result
            if (result != null && state.isResultOutdated) {
                item {
                    Text(
                        text = "Result outdated. Calculate again to use the updated inputs.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .testTag("route_result_outdated")
                    )
                }
            } else if (result != null) {
                item {
                    mapContent(result, state.selectedSampleIndex, darkTheme) { onSampleSelected(it) }
                }
                item {
                    WeatherRouteTable(
                        result = result,
                        selectedSampleIndex = state.selectedSampleIndex,
                        temperatureUnit = temperatureUnit,
                        windUnit = windUnit,
                        distanceUnit = distanceUnit,
                        precipitationUnit = precipitationUnit,
                        onSampleSelected = { onSampleSelected(it) },
                        timeZone = ZoneId.systemDefault(),
                        warnings = state.warnings,
                        modifier = Modifier.fillMaxWidth().testTag("weather_route_table")
                    )
                }
                item { RouteSummary(result, distanceUnit) }
                item {
                    InlineWeatherError(
                        error = state.weatherError,
                        isRetrying = state.isRetryingWeather,
                        onRetry = onRetryWeather
                    )
                }
            }
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915
)
@Composable
private fun WeatherRouteScreenLightPreview() {
    HluWeatherTheme(darkTheme = false) {
        WeatherRoutePreviewContent(darkTheme = false)
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun WeatherRouteScreenDarkPreview() {
    HluWeatherTheme(darkTheme = true) {
        WeatherRoutePreviewContent(darkTheme = true)
    }
}

@Composable
private fun WeatherRoutePreviewContent(darkTheme: Boolean) {
    WeatherRouteScreenContent(
        state = previewWeatherRouteState,
        savedLocations = listOf(previewStartLocation, previewEndLocation),
        darkTheme = darkTheme,
        temperatureUnit = TemperatureUnit.CELSIUS,
        windUnit = WindUnit.KMH,
        distanceUnit = DistanceUnit.KM,
        precipitationUnit = PrecipitationUnit.MM,
        selectedDeparture = previewDeparture,
        onBackClick = {},
        onSearchQueryChanged = { _, _ -> },
        onSearchResultSelected = {},
        onSavedLocationSelected = { _, _ -> },
        onCurrentLocationSelected = {},
        onMapEndpointSelected = { _, _ -> },
        onCalculate = {},
        onDepartureOffsetChanged = {},
        onSpeedChanged = {},
        onRetry = {},
        onRetryWeather = {},
        onSampleSelected = {},
        mapContent = { result, selectedSampleIndex, _, onSampleSelected ->
            PreviewRouteMap(result, selectedSampleIndex, onSampleSelected)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewRouteMap(
    result: WeatherRouteResult,
    selectedSampleIndex: Int?,
    onSampleSelected: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("weather_route_map"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Map preview", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.padding(top = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PreviewRouteMarker("S")
                Text("  ---  ", color = MaterialTheme.colorScheme.primary)
                weatherRouteMarkers(result, selectedSampleIndex).forEach { marker ->
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .size(if (marker.isSelected) 44.dp else 36.dp)
                            .testTag("preview_route_icon_${marker.sampleIndex}"),
                        shape = CircleShape,
                        color = marker.color,
                        onClick = { onSampleSelected(marker.sampleIndex) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            HluWeatherIcon(
                                condition = marker.condition ?: WeatherCondition.UNKNOWN,
                                isDay = marker.isDay,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                Text("  ---  ", color = MaterialTheme.colorScheme.primary)
                PreviewRouteMarker("D")
            }
        }
    }
}

@Composable
private fun PreviewRouteMarker(label: String) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

private val previewStartLocation = WeatherLocation("preview-start", "Ljubljana", 46.0569, 14.5058)
private val previewEndLocation = WeatherLocation("preview-end", "Belgrade", 44.8176, 20.4633)
private val previewStart = RouteEndpoint(previewStartLocation.name, GeoPoint(previewStartLocation.latitude, previewStartLocation.longitude))
private val previewEnd = RouteEndpoint(previewEndLocation.name, GeoPoint(previewEndLocation.latitude, previewEndLocation.longitude))
private val previewNow = Instant.parse("2026-09-12T09:15:00Z")
private val previewDeparture = previewNow + 1.hours
private val previewRoutePoints = listOf(
    previewStart.point,
    GeoPoint(45.6495, 13.7768),
    GeoPoint(44.7866, 17.1910),
    previewEnd.point
)
private val previewRawSamples = previewRoutePoints.mapIndexed { index, point ->
    RouteWeatherSample(
        point = point,
        distanceMeters = index * 80_000.0,
        arrivalTime = previewDeparture + index.hours,
        placeLabel = if (index == previewRoutePoints.lastIndex) null else "Route checkpoint ${index + 1}"
    )
}
private val previewHourly = listOf(
    WeatherCondition.CLEAR to 19.0,
    WeatherCondition.PARTLY_CLOUDY to 21.0,
    WeatherCondition.RAIN to 18.0,
    WeatherCondition.CLEAR to 20.0
).mapIndexed { index, (condition, temperature) ->
    listOf(
        RouteWeatherForecastHour(
            time = previewRawSamples[index].arrivalTime,
            condition = condition,
            temperatureCelsius = temperature,
            windSpeedKmh = 12.0 + index * 8,
            precipitationProbability = if (condition == WeatherCondition.RAIN) 70 else 10,
            precipitationMm = if (condition == WeatherCondition.RAIN) 4.5 else 0.0,
            humidityPercent = 55 + index * 5,
            isDay = true
        )
    )
}
private val previewSnapshot = RouteWeatherSnapshot(
    points = previewRoutePoints,
    hourlyByPoint = previewHourly,
    fetchedAt = previewNow
)
private val previewResult = WeatherRouteResult(
    start = previewStart,
    end = previewEnd,
    route = DrivingRoute(
        providerName = "OSRM",
        polyline = previewRoutePoints,
        distanceMeters = 240_000.0,
        providerDurationSeconds = 10_800.0
    ),
    departure = previewDeparture,
    averageSpeedKmh = 80,
    samples = previewSnapshot.enrich(previewRawSamples)
)
private val previewWeatherRouteState = WeatherRouteUiState(
    start = previewStart,
    end = previewEnd,
    departure = previewNow,
    speedText = "80",
    result = previewResult,
    departureOffsetHours = 1,
    snapshot = previewSnapshot,
    summary = summarizeTripWeather(previewResult.samples),
    warnings = findTripWeatherWarnings(previewResult.samples)
)

@Composable
private fun StartTimeControl(
    offsetHours: Int,
    selectedDeparture: kotlin.time.Instant,
    timeZone: kotlinx.datetime.TimeZone,
    onOffsetChanged: (Int) -> Unit
) {
    val localDeparture = selectedDeparture.toLocalDateTime(timeZone)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("trip_start_time_control")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Start time", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${localDeparture.date} at ${localDeparture.time}",
                modifier = Modifier.testTag("trip_start_time_value")
            )
            Slider(
                value = offsetHours.toFloat(),
                onValueChange = { onOffsetChanged(it.roundToInt()) },
                valueRange = 0f..72f,
                steps = 71,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("trip_start_time_slider")
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Now" to 0, "+24h" to 24, "+48h" to 48, "+72h" to 72).forEach { (label, value) ->
                    Text(
                        text = label,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("trip_start_time_$value"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedControl(speed: Float, onSpeedChanged: (Float) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("trip_speed_control")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Speed", style = MaterialTheme.typography.titleMedium)
            Text("${speed.roundToInt()} km/h", modifier = Modifier.testTag("trip_speed_value"))
            Slider(
                value = speed,
                onValueChange = onSpeedChanged,
                valueRange = 40f..130f,
                steps = 89,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("trip_speed_slider")
            )
        }
    }
}

@Composable
private fun RouteError(error: String, canRetry: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("route_error"))
        if (canRetry) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("route_retry")
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun InlineWeatherError(error: String?, isRetrying: Boolean, onRetry: () -> Unit) {
    if (error == null && !isRetrying) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("route_weather_error")
            )
        }
        Button(
            onClick = onRetry,
            enabled = !isRetrying,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("route_weather_retry")
        ) {
            if (isRetrying) CircularProgressIndicator() else Text("Retry weather")
        }
    }
}

@Composable
private fun RouteSummary(
    result: WeatherRouteResult,
    distanceUnit: DistanceUnit
) {
    val durationSeconds = ((result.samples.lastOrNull()?.arrivalTime ?: result.departure) - result.departure)
        .inWholeSeconds
        .coerceAtLeast(0L)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics(mergeDescendants = true) {}
            .testTag("route_summary")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${result.start.label} to ${result.end.label}", style = MaterialTheme.typography.titleMedium)
            Text((result.route.distanceMeters / 1_000.0).distanceText(distanceUnit))
            Text("Estimated time: ${routeDurationText(durationSeconds.toDouble())}")
            Text("Average speed: ${result.averageSpeedKmh} km/h")
        }
    }
}

private fun routeDurationText(durationSeconds: Double): String {
    val totalMinutes = (durationSeconds / 60.0).roundToInt().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours == 0) "$minutes min" else "${hours}h ${minutes}min"
}
