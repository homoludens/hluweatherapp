package net.droopia.hluweather.ui.weather

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import java.time.ZoneId
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.ui.settings.PrecipitationUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.settings.HourlyTableColumn
import net.droopia.hluweather.ui.settings.defaultHourlyTableColumns
import net.droopia.hluweather.ui.settings.WindUnit
import net.droopia.hluweather.ui.settings.WindDirectionDisplay
import net.droopia.hluweather.ui.map.WeatherMap

private const val WEATHER_HEADER_KEY = "weather_header"
private const val HOURLY_HEADER_KEY = "hourly_header"
private const val HOURLY_DAYS_KEY = "hourly_days"
private val hourlyListPrefixKeys = listOf(
    WEATHER_HEADER_KEY,
    HOURLY_DAYS_KEY,
    HOURLY_HEADER_KEY
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel = viewModel(factory = WeatherViewModel.Factory),
    onSettingsClick: () -> Unit = {},
    onWeatherRouteClick: () -> Unit = {},
    onTrackMeClick: () -> Unit = {},
    trackMeSelected: Boolean = false,
    darkTheme: Boolean = false,
    temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    precipitationUnit: PrecipitationUnit = PrecipitationUnit.MM,
    windUnit: WindUnit = WindUnit.KMH,
    windDirectionDisplay: WindDirectionDisplay = WindDirectionDisplay.ARROW,
    hourlyTableColumns: Set<HourlyTableColumn> = defaultHourlyTableColumns,
    now: Instant? = null,
    modifier: Modifier = Modifier,
    mapContent: @Composable (WeatherLocation, List<WeatherLocation>, String?, Boolean, () -> Unit) -> Unit =
        { mapLocation, locations, activeLocationId, mapDarkTheme, onRecenterClick ->
            WeatherMap(
                locations = locations,
                activeLocationId = activeLocationId,
                center = GeoPoint(mapLocation.latitude, mapLocation.longitude),
                darkTheme = mapDarkTheme,
                fitLocations = true,
                onLocationClick = viewModel::selectLocation,
                onRecenterClick = onRecenterClick
            )
        }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val forecast = state.forecast
    val location = state.activeLocation
    var locationSwitcherVisible by remember { mutableStateOf(false) }
    var permissionRetry by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.onTrackMePermissionResult(
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        )
        permissionRetry++
    }

    LaunchedEffect(lifecycleOwner, trackMeSelected, permissionRetry, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (trackMeSelected) {
                viewModel.trackMeWhileStarted()
            }
        }
    }

    LaunchedEffect(state.trackMeStatus, trackMeSelected) {
        if (trackMeSelected && state.trackMeStatus == TrackMeStatus.PermissionRequired) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
        if (!trackMeSelected) viewModel.clearTrackMeStatus()
    }

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
                    else -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Add your first location",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text("Choose a saved place to see the weather.")
                            Button(onClick = onSettingsClick) {
                                Text("Add location")
                            }
                        }
                    }
                }
            }
        } else {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
                indicator = {}
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (state.forecastMode == ForecastMode.HOURLY) {
                        HourlyWeatherContent(
                            forecast = forecast,
                            location = location,
                            selectedDayIndex = state.selectedDayIndex,
                            temperatureUnit = temperatureUnit,
                            precipitationUnit = precipitationUnit,
                            windUnit = windUnit,
                            windDirectionDisplay = windDirectionDisplay,
                            hourlyTableColumns = hourlyTableColumns,
                            now = now,
                            onDaySelected = viewModel::onDaySelected,
                            onForecastModeSelected = viewModel::onForecastModeSelected,
                            onSettingsClick = onSettingsClick,
                            onLocationClick = { locationSwitcherVisible = true },
                            modifier = Modifier
                                .weight(1f)
                        )
                    } else {
                        WeatherHero(
                            selected = state.forecastMode,
                            onSelected = viewModel::onForecastModeSelected,
                            onSettingsClick = onSettingsClick
                        )

                        if (state.forecastMode != ForecastMode.DAILY) {
                            CurrentWeatherCard(
                                location = location,
                                forecast = forecast,
                                temperatureUnit = temperatureUnit,
                                precipitationUnit = precipitationUnit,
                                onLocationClick = { locationSwitcherVisible = true },
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .offset(y = (-8).dp)
                            )
                        }

                        when (state.forecastMode) {
                            ForecastMode.DAILY -> {
                                DailyForecastList(
                                    forecast = forecast,
                                    temperatureUnit = temperatureUnit,
                                    precipitationUnit = precipitationUnit,
                                    onDaySelected = viewModel::onDaySelected,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            ForecastMode.MAP -> {
                                Box(modifier = Modifier.weight(1f)) {
                                    mapContent(
                                        location,
                                        state.locations,
                                        location.id.takeUnless { it == "current" },
                                        darkTheme
                                    ) {
                                        if (!trackMeSelected) onTrackMeClick()
                                    }
                                }
                            }
                            ForecastMode.HOURLY -> Unit
                        }
                    }
                }
            }
        }

        if (forecast != null && location != null && state.forecastMode == ForecastMode.MAP) {
            Button(
                onClick = onWeatherRouteClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .testTag("weather_route_open")
            ) {
                Text("Weather on route")
            }
        }

        if (forecast != null && location != null && state.isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.TopCenter)
                    .testTag("weather_refresh_indicator")
            )
        }

        trackMeStatusText(state.trackMeStatus, trackMeSelected)?.let { status ->
            Text(
                text = status,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("track_me_status"),
                color = if (state.trackMeStatus == TrackMeStatus.PermissionRequired ||
                    state.trackMeStatus == TrackMeStatus.LocationDisabled
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }

        if (locationSwitcherVisible) {
            ModalBottomSheet(
                onDismissRequest = { locationSwitcherVisible = false }
            ) {
                LocationQuickSwitcher(
                    locations = state.locations,
                    selectedLocationId = location?.id,
                    trackMeSelected = trackMeSelected,
                    onLocationSelected = { selectedLocation ->
                        locationSwitcherVisible = false
                        viewModel.selectLocation(selectedLocation)
                    },
                    onTrackMeClick = {
                        locationSwitcherVisible = false
                        onTrackMeClick()
                    },
                    onAddLocationClick = {
                        locationSwitcherVisible = false
                        onSettingsClick()
                    },
                    onManageLocationsClick = {
                        locationSwitcherVisible = false
                        onSettingsClick()
                    }
                )
            }
        }
    }
}

private fun trackMeStatusText(status: TrackMeStatus, enabled: Boolean): String? {
    if (!enabled) return null
    return when (status) {
        TrackMeStatus.Idle -> null
        TrackMeStatus.Locating -> "Finding your location..."
        TrackMeStatus.Active -> null
        TrackMeStatus.PermissionRequired -> "Location permission is required"
        TrackMeStatus.LocationDisabled -> "Location is disabled"
        TrackMeStatus.Unavailable -> "Location unavailable"
    }
}

@Composable
private fun rememberWeatherNow(timezone: String, fixedNow: Instant?): Instant {
    var liveNow by remember(timezone) { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(timezone, fixedNow) {
        if (fixedNow != null) return@LaunchedEffect
        while (true) {
            val current = Clock.System.now()
            liveNow = current
            val local = current.toLocalDateTime(TimeZone.of(timezone))
            val nextHour = LocalDateTime(
                year = local.year,
                month = local.month,
                day = local.day,
                hour = local.hour,
                minute = 0,
                second = 0,
                nanosecond = 0
            ).toInstant(TimeZone.of(timezone)) + 1.hours
            delay((nextHour - current).inWholeMilliseconds.coerceAtLeast(1L))
        }
    }
    return fixedNow ?: liveNow
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HourlyWeatherContent(
    forecast: WeatherForecast,
    location: WeatherLocation,
    selectedDayIndex: Int,
    temperatureUnit: TemperatureUnit,
    precipitationUnit: PrecipitationUnit,
    windUnit: WindUnit,
    windDirectionDisplay: WindDirectionDisplay,
    hourlyTableColumns: Set<HourlyTableColumn>,
    onDaySelected: (Int) -> Unit,
    onForecastModeSelected: (ForecastMode) -> Unit,
    onSettingsClick: () -> Unit,
    onLocationClick: () -> Unit,
    now: Instant?,
    modifier: Modifier = Modifier
) {
    val displayZone = ZoneId.of(forecast.timezone)
    val tableNow = rememberWeatherNow(forecast.timezone, now)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isCollapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    val tableData = remember(forecast, tableNow) {
        forecast.toHourlyTableData(now = tableNow)
    }
    val tableItemStartIndex = hourlyListPrefixKeys.size
    val firstListItemIndexByDay = remember(tableData) {
        tableData.days.mapNotNull { day ->
            tableData.firstHourIndexForDay(day.dayIndex)?.let { firstHourIndex ->
                day.dayIndex to tableItemStartIndex + firstHourIndex
            }
        }.toMap()
    }
    var selectedDateOverride by remember(tableData) { mutableStateOf<LocalDate?>(null) }
    val selectedTableDay = selectedDateOverride?.let { date ->
        tableData.days.firstOrNull { it.date == date }
    } ?: tableData.nearestDayForIndex(selectedDayIndex)

    LaunchedEffect(selectedDayIndex) {
        selectedDateOverride = null
    }

    fun visibleDayIndex(): Int? = listState.layoutInfo.visibleItemsInfo
        .asSequence()
        .map { it.index - tableItemStartIndex }
        .filter { it in tableData.items.indices }
        .map { tableData.items[it].dayIndex }
        .firstOrNull()

    fun onDayChipSelected(dayIndex: Int) {
        if (dayIndex >= 0) {
            selectedDateOverride = null
            onDaySelected(dayIndex)
        } else {
            selectedDateOverride = tableData.days.firstOrNull { it.dayIndex == dayIndex }?.date
        }
        scope.launch {
            firstListItemIndexByDay[dayIndex]?.let { target ->
                listState.animateScrollToItem(target)
            }
        }
    }

    LaunchedEffect(tableData) {
        val firstVisibleDay = snapshotFlow { visibleDayIndex() }
            .filterNotNull()
            .first()
        selectedTableDay?.let { day ->
            firstListItemIndexByDay[day.dayIndex]?.let { target ->
                if (firstVisibleDay != day.dayIndex) {
                    listState.animateScrollToItem(target)
                }
            }
        }
    }

    LaunchedEffect(tableData) {
        var previousDayIndex: Int? = null
        snapshotFlow { visibleDayIndex() }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { dayIndex ->
                if (dayIndex < 0) {
                    selectedDateOverride = tableData.days
                        .firstOrNull { it.dayIndex == dayIndex }
                        ?.date
                } else if (previousDayIndex != null && dayIndex != previousDayIndex) {
                    selectedDateOverride = null
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
                    temperatureUnit = temperatureUnit,
                    precipitationUnit = precipitationUnit,
                    onLocationClick = onLocationClick,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .offset(y = (-8).dp)
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
                        tableData = tableData,
                        selectedDayDate = selectedTableDay?.date,
                        displayZone = displayZone,
                        onDaySelected = ::onDayChipSelected
                    )
                }
            }

            stickyHeader(key = HOURLY_HEADER_KEY) {
                ForecastColumnHeader(
                    columns = hourlyTableColumns,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            itemsIndexed(
                items = tableData.items,
                key = { _, item -> item.key }
            ) { itemIndex, item ->
                when (item) {
                    is HourlyTableBoundary -> HourlyDateBoundary(
                        item = item,
                        displayZone = displayZone,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    is HourlyTableHour -> {
                        val isFirstHour = tableData.firstHourIndexForDay(item.dayIndex) == itemIndex
                        ForecastRow(
                            weather = item.hour,
                            displayZone = displayZone,
                            temperatureUnit = temperatureUnit,
                            precipitationUnit = precipitationUnit,
                            windUnit = windUnit,
                            windDirectionDisplay = windDirectionDisplay,
                            columns = hourlyTableColumns,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .then(
                                    if (isFirstHour) {
                                        Modifier.testTag("hourly_day_start_${item.dayIndex}")
                                    } else {
                                        Modifier
                                    }
                                ),
                            timeTestTag = if (isFirstHour && selectedTableDay?.dayIndex == item.dayIndex) {
                                "hourly_selected_day_${item.dayIndex}"
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isCollapsed,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = 96.dp)
            ) {
                HourlyDaySelector(
                    tableData = tableData,
                    selectedDayDate = selectedTableDay?.date,
                    displayZone = displayZone,
                    onDaySelected = ::onDayChipSelected
                )
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
