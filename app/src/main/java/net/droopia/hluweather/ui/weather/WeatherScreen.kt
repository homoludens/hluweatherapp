package net.droopia.hluweather.ui.weather

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.ui.map.MapPlaceholder

@OptIn(ExperimentalFoundationApi::class)
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
            .navigationBarsPadding()
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
            if (state.forecastMode == ForecastMode.HOURLY) {
                HourlyWeatherContent(
                    forecast = forecast,
                    location = location,
                    selectedDayIndex = state.selectedDayIndex,
                    onDaySelected = viewModel::onDaySelected,
                    onForecastModeSelected = viewModel::onForecastModeSelected,
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier
                        .weight(1f)
                )
            } else {
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
                    ForecastMode.DAILY -> {
                        DailyForecastList(
                            forecast = forecast,
                            onDaySelected = viewModel::onDaySelected,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    ForecastMode.MAP -> {
                        MapPlaceholder(modifier = Modifier.weight(1f))
                    }
                    ForecastMode.HOURLY -> Unit
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HourlyWeatherContent(
    forecast: WeatherForecast,
    location: WeatherLocation,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit,
    onForecastModeSelected: (ForecastMode) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val isCollapsed by remember {
        derivedStateOf { listState.canScrollBackward }
    }
    val dayHours = remember(forecast, selectedDayIndex) {
        forecast.hoursForDay(selectedDayIndex)
    }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("weather_scroll"),
            state = listState
        ) {
            item(key = "weather_header") {
                WeatherHero(
                    selected = ForecastMode.HOURLY,
                    onSelected = onForecastModeSelected,
                    onSettingsClick = onSettingsClick
                )
                CurrentWeatherCard(
                    location = location,
                    forecast = forecast,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .offset(y = (-8).dp)
                )
            }

            stickyHeader(key = "hourly_days") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(top = if (isCollapsed) 72.dp else 0.dp)
                ) {
                    HourlyDaySelector(
                        forecast = forecast,
                        selectedDayIndex = selectedDayIndex,
                        onDaySelected = onDaySelected
                    )
                }
            }

            item(key = "hourly_header") {
                ForecastColumnHeader(
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            items(dayHours) { hour ->
                ForecastRow(
                    weather = hour,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isCollapsed,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            WeatherHero(
                selected = ForecastMode.HOURLY,
                onSelected = onForecastModeSelected,
                onSettingsClick = onSettingsClick,
                compact = true
            )
        }
    }
}
