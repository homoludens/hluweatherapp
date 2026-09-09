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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.ui.map.MapPlaceholder

private const val WEATHER_HEADER_KEY = "weather_header"
private const val HOURLY_HEADER_KEY = "hourly_header"
private const val HOURLY_DAYS_KEY = "hourly_days"
private val hourlyListPrefixKeys = listOf(
    WEATHER_HEADER_KEY,
    HOURLY_HEADER_KEY,
    HOURLY_DAYS_KEY
)

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
    val scope = rememberCoroutineScope()
    val isCollapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    val tableData = remember(forecast) {
        forecast.toHourlyTableData()
    }
    val tableItemStartIndex = hourlyListPrefixKeys.size
    val firstListItemIndexByDay = remember(tableData) {
        tableData.days.mapNotNull { day ->
            tableData.firstHourIndexForDay(day.dayIndex)?.let { firstHourIndex ->
                day.dayIndex to tableItemStartIndex + firstHourIndex
            }
        }.toMap()
    }

    fun visibleDayIndex(): Int? = listState.layoutInfo.visibleItemsInfo
        .asSequence()
        .map { it.index - tableItemStartIndex }
        .filter { it in tableData.items.indices }
        .mapNotNull { tableData.items[it].dayIndex.takeIf { dayIndex -> dayIndex >= 0 } }
        .firstOrNull()

    LaunchedEffect(tableData) {
        val firstVisibleDay = snapshotFlow { visibleDayIndex() }
            .filterNotNull()
            .first()
        firstListItemIndexByDay[selectedDayIndex]?.let { target ->
            if (firstVisibleDay != selectedDayIndex) {
                listState.animateScrollToItem(target)
            }
        }
    }

    LaunchedEffect(tableData) {
        var previousDayIndex: Int? = null
        snapshotFlow { visibleDayIndex() }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { dayIndex ->
                if (previousDayIndex != null && dayIndex != previousDayIndex) {
                    onDaySelected(dayIndex)
                }
                previousDayIndex = dayIndex
            }
    }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("weather_scroll"),
            state = listState
        ) {
            item(key = WEATHER_HEADER_KEY) {
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

            stickyHeader(key = HOURLY_HEADER_KEY) {
                ForecastColumnHeader(
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            stickyHeader(key = HOURLY_DAYS_KEY) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(top = if (isCollapsed) 96.dp else 0.dp)
                ) {
                    HourlyDaySelector(
                        forecast = forecast,
                        selectedDayIndex = selectedDayIndex,
                        onDaySelected = { dayIndex ->
                            onDaySelected(dayIndex)
                            scope.launch {
                                firstListItemIndexByDay[dayIndex]?.let { target ->
                                    listState.animateScrollToItem(target)
                                }
                            }
                        }
                    )
                }
            }

            itemsIndexed(
                items = tableData.items,
                key = { _, item -> item.key }
            ) { itemIndex, item ->
                when (item) {
                    is HourlyTableBoundary -> HourlyDateBoundary(
                        item = item,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    is HourlyTableHour -> ForecastRow(
                        weather = item.hour,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .then(
                                if (tableData.firstHourIndexForDay(item.dayIndex) ==
                                    itemIndex && selectedDayIndex == item.dayIndex
                                ) {
                                    Modifier.testTag("hourly_selected_day_${item.dayIndex}")
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isCollapsed,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .testTag("compact_weather_hero")
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
