package net.droopia.hluweather.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.ui.map.MapPlaceholder

@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel = viewModel(factory = WeatherViewModel.Factory),
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val forecast = state.forecast
    val location = state.activeLocation

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (forecast == null || location == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator()
                    }
                    state.error != null -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.error ?: "Weather request failed",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Button(onClick = viewModel::refresh) {
                                Text("Retry")
                            }
                        }
                    }
                    else -> Unit
                }
            }
            return
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            WeatherHero(
                selected = state.forecastMode,
                onSelected = viewModel::onForecastModeSelected,
                onSettingsClick = onSettingsClick
            )

            CurrentWeatherCard(
                location = location,
                forecast = forecast,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .offset(y = (-8).dp)
            )

            when (state.forecastMode) {
                ForecastMode.HOURLY -> {
                    HourlyForecast(
                        forecast = forecast,
                        selectedDayIndex = state.selectedDayIndex,
                        onDaySelected = viewModel::onDaySelected,
                        modifier = Modifier.weight(1f)
                    )
                }
                ForecastMode.DAILY -> {
                    DailyForecastList(
                        forecast = forecast,
                        onDaySelected = viewModel::onDaySelected,
                        modifier = Modifier.weight(1f)
                    )
                }
                ForecastMode.MAP -> {
                    MapPlaceholder(
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
