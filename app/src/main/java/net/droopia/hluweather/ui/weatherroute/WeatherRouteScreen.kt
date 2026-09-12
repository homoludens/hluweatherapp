package net.droopia.hluweather.ui.weatherroute

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import net.droopia.hluweather.data.distanceText
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherRouteResult
import net.droopia.hluweather.ui.settings.DistanceUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.settings.WindUnit

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
    val context = LocalContext.current
    val timeZone = TimeZone.currentSystemDefault()
    val localDeparture = state.departure.toLocalDateTime(timeZone)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("weather_route_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Weather on route") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RouteEndpointPicker(
                state = state,
                savedLocations = savedLocations,
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                onSearchProviderChanged = viewModel::onSearchProviderChanged,
                onSearchResultSelected = viewModel::selectSearchResult,
                onSavedLocationSelected = viewModel::selectSavedLocation,
                onCurrentLocationSelected = viewModel::selectCurrentLocation,
                onMapEndpointSelected = viewModel::selectMapEndpoint,
                darkTheme = darkTheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                viewModel.onDepartureChanged(
                                    kotlinx.datetime.LocalDateTime(
                                        LocalDate(year, month + 1, day),
                                        localDeparture.time
                                    ).toInstant(timeZone)
                                )
                            },
                            localDeparture.year,
                            localDeparture.month.ordinal,
                            localDeparture.day
                        ).show()
                    },
                    modifier = Modifier.testTag("route_departure_date")
                ) {
                    Text("Date: ${localDeparture.date}")
                }
                TextButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                viewModel.onDepartureChanged(
                                    kotlinx.datetime.LocalDateTime(
                                        localDeparture.date,
                                        LocalTime(hour, minute)
                                    ).toInstant(timeZone)
                                )
                            },
                            localDeparture.hour,
                            localDeparture.minute,
                            true
                        ).show()
                    },
                    modifier = Modifier.testTag("route_departure_time")
                ) {
                    Text("Time: ${localDeparture.time}")
                }
            }

            OutlinedTextField(
                value = state.speedText,
                onValueChange = viewModel::onSpeedChanged,
                label = { Text("Average speed (km/h)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("route_speed")
            )

            Button(
                onClick = viewModel::calculate,
                enabled = state.start != null && state.end != null && !state.isCalculating,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("route_calculate")
            ) {
                if (state.isCalculating) {
                    CircularProgressIndicator()
                } else {
                    Text("Calculate")
                }
            }

            state.routeError?.let { error ->
                Text(
                    text = if (error == "Speed must be between 50 and 240 km/h") {
                        "Enter a speed from 50 to 240 km/h"
                    } else {
                        error
                    },
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .testTag("route_error")
                )
                if (state.start != null && state.end != null && !state.isCalculating) {
                    Button(
                        onClick = viewModel::calculate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("route_retry")
                    ) {
                        Text("Retry")
                    }
                }
            }

            state.result?.let { result ->
                RouteSummary(result, distanceUnit)
                if (state.weatherError != null) {
                    Text(
                        text = state.weatherError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Button(
                        onClick = viewModel::retryWeather,
                        enabled = !state.isRetryingWeather,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("route_weather_retry")
                    ) {
                        Text("Retry weather")
                    }
                }
                mapContent(result, state.selectedSampleIndex, darkTheme, viewModel::selectSample)
                WeatherRouteTimeline(
                    result = result,
                    selectedSampleIndex = state.selectedSampleIndex,
                    temperatureUnit = temperatureUnit,
                    windUnit = windUnit,
                    distanceUnit = distanceUnit,
                    onSampleSelected = viewModel::selectSample,
                    timeZone = ZoneId.systemDefault(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                )
            }

            if (state.isRetryingWeather) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RouteSummary(result: WeatherRouteResult, distanceUnit: DistanceUnit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("route_summary")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${result.start.label} to ${result.end.label}", style = MaterialTheme.typography.titleMedium)
            Text((result.route.distanceMeters / 1_000.0).distanceText(distanceUnit))
            Text("Average speed: ${result.averageSpeedKmh} km/h")
        }
    }
}
