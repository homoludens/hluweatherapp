package net.droopia.hluweather.ui.weather

import android.Manifest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableFloatStateOf
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
import kotlinx.coroutines.awaitCancellation
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarTimelineNeighborhoodPlanner
import net.droopia.hluweather.data.repository.RadarMetadataState
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.ui.settings.PrecipitationUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.settings.HourlyTableColumn
import net.droopia.hluweather.ui.settings.defaultHourlyTableColumns
import net.droopia.hluweather.ui.settings.WindUnit
import net.droopia.hluweather.ui.settings.WindDirectionDisplay
import net.droopia.hluweather.ui.map.WeatherMap
import net.droopia.hluweather.ui.map.RadarMapOverlay
import net.droopia.hluweather.ui.map.RadarPlaybackViewModel
import net.droopia.hluweather.ui.map.RadarPlaybackState
import net.droopia.hluweather.ui.map.WeatherMapRadarConfig
import net.droopia.hluweather.ui.map.WeatherRadarResources
import net.droopia.hluweather.HluWeatherApplication

private const val WEATHER_HEADER_KEY = "weather_header"
private const val HOURLY_HEADER_KEY = "hourly_header"
private val hourlyListPrefixKeys = listOf(
    WEATHER_HEADER_KEY,
    HOURLY_HEADER_KEY
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel = viewModel(factory = WeatherViewModel.Factory),
    onSettingsClick: () -> Unit = {},
    onAddLocationClick: () -> Unit = {},
    onTrackMeClick: () -> Unit = {},
    trackMeSelected: Boolean = false,
    darkTheme: Boolean = false,
    temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    precipitationUnit: PrecipitationUnit = PrecipitationUnit.MM,
    windUnit: WindUnit = WindUnit.KMH,
    windDirectionDisplay: WindDirectionDisplay = WindDirectionDisplay.ARROW,
    hourlyTableColumns: Set<HourlyTableColumn> = defaultHourlyTableColumns,
    radarEnabled: Boolean = true,
    onRadarEnabledChange: (Boolean) -> Unit = {},
    radarProvider: RadarProvider = RadarProvider.LIBREWXR,
    onRadarProviderChange: (RadarProvider) -> Unit = {},
    mainMapDestinationVisible: Boolean = true,
    radarPlaybackViewModel: RadarPlaybackViewModel = viewModel(factory = RadarPlaybackViewModel.Factory),
    now: Instant? = null,
    modifier: Modifier = Modifier,
    mapContent: (@Composable (WeatherLocation, List<WeatherLocation>, String?, Boolean, () -> Unit) -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val radarState by radarPlaybackViewModel.state.collectAsStateWithLifecycle()
    val forecast = state.forecast
    val location = state.activeLocation
    var locationSwitcherVisible by remember { mutableStateOf(false) }
    var permissionRetry by remember { mutableIntStateOf(0) }
    var radarOpacity by rememberSaveable { mutableFloatStateOf(0.75f) }
    val hourlyListState = rememberLazyListState()
    val dailyListState = rememberLazyListState()
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

    val radarShouldBeVisible = mainMapDestinationVisible && state.forecastMode == ForecastMode.MAP &&
        radarEnabled &&
        forecast != null &&
        location != null
    // This effect is declared before visibility so persisted Settings is applied first on entry.
    LaunchedEffect(radarProvider, radarPlaybackViewModel, radarShouldBeVisible, lifecycleOwner) {
        if (!radarShouldBeVisible ||
            !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            radarPlaybackViewModel.setVisible(false)
        }
        radarPlaybackViewModel.selectProvider(radarProvider)
    }
    LaunchedEffect(lifecycleOwner, radarShouldBeVisible, radarPlaybackViewModel) {
        if (radarShouldBeVisible) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                radarPlaybackViewModel.setVisible(true)
                try {
                    awaitCancellation()
                } finally {
                    radarPlaybackViewModel.setVisible(false)
                }
            }
        } else {
            radarPlaybackViewModel.setVisible(false)
        }
    }
    DisposableEffect(radarPlaybackViewModel) {
        onDispose { radarPlaybackViewModel.setVisible(false) }
    }

    val radarStateForProvider = if (radarState.selectedProvider == radarProvider) {
        radarState
    } else {
        // Do not briefly show the outgoing provider's timeline while Settings is being applied.
        RadarPlaybackState(
            selectedProvider = radarProvider,
            metadataState = RadarMetadataState(isLoading = radarShouldBeVisible)
        )
    }
    val selectedRadar = if (
        radarShouldBeVisible && radarStateForProvider.isVisible
    ) {
        val metadata = radarStateForProvider.metadata
        val frame = radarStateForProvider.requestedFrame?.takeIf { requested ->
            requested.provider == radarProvider && metadata?.let { current ->
                current.provider == radarProvider &&
                    requested.identity(current.host) == radarStateForProvider.requestedFrameIdentity
            } == true
        }
        if (frame != null && metadata != null) {
            WeatherMapRadarConfig(host = metadata.host, frame = frame, opacity = radarOpacity)
        } else {
            null
        }
    } else {
        null
    }
    // Renderer-only preparation hint: playback retains selection ownership until its accepted
    // display interval ends.
    val radarNextPlaybackFrame = if (radarStateForProvider.isPlaying && selectedRadar != null) {
        radarStateForProvider.metadata?.let { metadata ->
            radarStateForProvider.requestedFrameIdentity?.let { requested ->
                RadarTimelineNeighborhoodPlanner.nextFrame(metadata, requested)
            }
        }
    } else {
        null
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
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Choose a saved place to see the weather.",
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Button(onClick = onAddLocationClick) {
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
                            listState = hourlyListState,
                            now = now,
                            onDaySelected = viewModel::onDaySelected,
                            onLocationClick = { locationSwitcherVisible = true },
                            modifier = Modifier
                                .weight(1f)
                        )
                    } else {
                        when (state.forecastMode) {
                            ForecastMode.DAILY -> {
                                DailyForecastList(
                                    forecast = forecast,
                                    temperatureUnit = temperatureUnit,
                                    precipitationUnit = precipitationUnit,
                                    onDaySelected = viewModel::onDaySelected,
                                    listState = dailyListState,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            ForecastMode.MAP -> {
                                Box(modifier = Modifier.weight(1f)) {
                                    val onRecenterClick = {
                                        if (!trackMeSelected) onTrackMeClick()
                                    }
                                    if (mapContent != null) {
                                        mapContent(
                                            location,
                                            state.locations,
                                            location.id.takeUnless { it == "current" },
                                            darkTheme,
                                            onRecenterClick
                                        )
                                    } else {
                                        val application =
                                            LocalContext.current.applicationContext as? HluWeatherApplication
                                        val radarResources = remember(application) {
                                            application?.let {
                                                WeatherRadarResources(
                                                    it.radarTileRegistry,
                                                    it.radarTileLoader,
                                                    it.radarMapResourceProvider
                                                )
                                            }
                                        }
                                        WeatherMap(
                                            locations = state.locations,
                                            activeLocationId = location.id.takeUnless { it == "current" },
                                            center = GeoPoint(location.latitude, location.longitude),
                                            darkTheme = darkTheme,
                                            fitLocations = true,
                                            radar = selectedRadar,
                                            radarResources = radarResources,
                                            radarProvider = radarProvider,
                                            radarMetadata = radarStateForProvider.metadata,
                                            radarPlaybackPreparationEnabled = true,
                                            radarPlaybackIsPlaying = radarStateForProvider.isPlaying,
                                            radarNextPlaybackFrame = radarNextPlaybackFrame,
                                            radarRetryToken = radarStateForProvider.displayRetryToken,
                                            onRadarDisplayChanged = { display ->
                                                if (radarShouldBeVisible && radarStateForProvider.isVisible &&
                                                    radarStateForProvider.selectedProvider == radarProvider &&
                                                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                                                ) {
                                                    radarPlaybackViewModel.onDisplayChanged(display)
                                                }
                                            },
                                            collapsedAttributionInitially = true,
                                            recenterTopPadding = 72.dp,
                                            onLocationClick = viewModel::selectLocation,
                                            onRecenterClick = onRecenterClick
                                        )
                                    }
                                    RadarMapOverlay(
                                        enabled = radarEnabled,
                                        onEnabledChange = { enabled ->
                                            if (!enabled) radarPlaybackViewModel.setVisible(false)
                                            onRadarEnabledChange(enabled)
                                        },
                                        selectedProvider = radarProvider,
                                        onProviderChange = { provider ->
                                            radarPlaybackViewModel.selectProvider(provider)
                                            onRadarProviderChange(provider)
                                        },
                                        playbackState = radarStateForProvider,
                                        metadataState = radarStateForProvider.metadataState,
                                        opacity = radarOpacity,
                                        onOpacityChange = { radarOpacity = it },
                                        onFrameSelected = radarPlaybackViewModel::selectFrame,
                                        onPlayPause = {
                                            if (radarStateForProvider.isPlaying) {
                                                radarPlaybackViewModel.pause()
                                            } else {
                                                radarPlaybackViewModel.play()
                                            }
                                        }
                                    )
                                }
                            }
                            ForecastMode.HOURLY -> Unit
                        }
                    }
                }
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
    onLocationClick: () -> Unit,
    now: Instant?,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val displayZone = ZoneId.of(forecast.timezone)
    val tableNow = rememberWeatherNow(forecast.timezone, now)
    val scope = rememberCoroutineScope()
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
                CurrentWeatherCard(
                    location = location,
                    forecast = forecast,
                    temperatureUnit = temperatureUnit,
                    precipitationUnit = precipitationUnit,
                    hourlyTableColumns = hourlyTableColumns,
                    onLocationClick = onLocationClick,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(vertical = 16.dp)
                        .testTag("current_weather_card")
                )
            }

            stickyHeader(key = HOURLY_HEADER_KEY) {
                HourlyTableStickyHeader(
                    tableData = tableData,
                    selectedDayDate = selectedTableDay?.date,
                    displayZone = displayZone,
                    onDaySelected = ::onDayChipSelected,
                    columns = hourlyTableColumns,
                    precipitationUnit = precipitationUnit,
                    dayStripModifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp
                    ),
                    columnHeaderModifier = Modifier.padding(horizontal = 16.dp)
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

    }
}
