package net.droopia.hluweather.ui.weather

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.droopia.hluweather.HluWeatherApplication
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.GpsResult
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.device.DeviceLocationSource
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.ui.map.shouldRefresh
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.SettingsRepository
import kotlinx.datetime.Instant
import kotlin.time.Clock

data class WeatherUiState(
    val activeLocation: WeatherLocation? = null,
    val locations: List<WeatherLocation> = emptyList(),
    val forecast: WeatherForecast? = null,
    val isStale: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val forecastMode: ForecastMode = ForecastMode.HOURLY,
    val selectedDayIndex: Int = 0,
    val trackMeStatus: TrackMeStatus = TrackMeStatus.Idle
)

sealed interface TrackMeStatus {
    data object Idle : TrackMeStatus
    data object Locating : TrackMeStatus
    data object Active : TrackMeStatus
    data object PermissionRequired : TrackMeStatus
    data object LocationDisabled : TrackMeStatus
    data object Unavailable : TrackMeStatus
}

class WeatherViewModel(
    private val repository: WeatherRepository,
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
    private val deviceLocationSource: DeviceLocationSource? = null,
    private val now: () -> Instant = { Clock.System.now() }
) : ViewModel() {

    constructor(repository: WeatherRepository, location: WeatherLocation) : this(
        repository = repository,
        settingsRepository = FixedSettingsRepository,
        locationRepository = FixedLocationRepository(location)
    )

    private val refreshes = MutableStateFlow(0)
    private val _state = MutableStateFlow(WeatherUiState(isLoading = true))
    private var requestGeneration = 0L
    private var lastCurrentFetchPoint: GeoPoint? = null
    private var lastCurrentFetchAt: Instant? = null
    private var lastLoadedLocationKey: String? = null
    private var lastLoadedProvider: WeatherProvider? = null
    private var handledRefresh = 0
    private var observedLocationMode: LocationMode? = null

    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.settings,
                locationRepository.activeLocation,
                locationRepository.locations,
                locationRepository.locationMode,
                refreshes
            ) { settings, activeLocation, locations, locationMode, _ ->
                if (locationMode != observedLocationMode) {
                    lastCurrentFetchPoint = null
                    lastCurrentFetchAt = null
                    observedLocationMode = locationMode
                }
                WeatherLoadRequest(
                    generation = ++requestGeneration,
                    provider = settings.provider,
                    activeLocation = activeLocation,
                    locations = locations,
                    locationMode = locationMode,
                    refresh = refreshes.value
                )
            }.collectLatest { request ->
                load(request)
            }
        }
    }

    fun refresh() {
        refreshes.update { it + 1 }
    }

    suspend fun trackMeWhileStarted() {
        val source = deviceLocationSource ?: return
        _state.update { it.copy(trackMeStatus = TrackMeStatus.Locating) }
        source.foregroundLocations().collect { result ->
            when (result) {
                is GpsResult.Success -> {
                    locationRepository.setCurrentLocation(result.point, result.altitude)
                    _state.update { it.copy(trackMeStatus = TrackMeStatus.Active) }
                }
                GpsResult.PermissionRequired ->
                    _state.update { it.copy(trackMeStatus = TrackMeStatus.PermissionRequired) }
                GpsResult.LocationDisabled ->
                    _state.update { it.copy(trackMeStatus = TrackMeStatus.LocationDisabled) }
                GpsResult.Unavailable ->
                    _state.update { it.copy(trackMeStatus = TrackMeStatus.Unavailable) }
            }
        }
    }

    fun onTrackMePermissionResult(granted: Boolean) {
        _state.update {
            it.copy(trackMeStatus = if (granted) TrackMeStatus.Idle else TrackMeStatus.PermissionRequired)
        }
    }

    fun clearTrackMeStatus() {
        _state.update { it.copy(trackMeStatus = TrackMeStatus.Idle) }
    }

    fun selectLocation(location: WeatherLocation) {
        lastCurrentFetchPoint = null
        lastCurrentFetchAt = null
        viewModelScope.launch {
            locationRepository.selectSaved(location.id)
        }
    }

    fun onForecastModeSelected(mode: ForecastMode) {
        _state.update { it.copy(forecastMode = mode) }
    }

    fun onDaySelected(index: Int) {
        val maxDayIndex = state.value.forecast?.daily?.lastIndex ?: 0
        _state.update {
            it.copy(
                forecastMode = ForecastMode.HOURLY,
                selectedDayIndex = index.coerceIn(0, maxDayIndex)
            )
        }
    }

    private suspend fun load(request: WeatherLoadRequest) {
        val currentLocation = request.activeLocation.toWeatherLocation()
        if (currentLocation == null) {
            if (requestGeneration != request.generation) return
            _state.update {
                it.copy(
                    activeLocation = null,
                    locations = request.locations,
                    forecast = null,
                    isStale = false,
                    isLoading = false,
                    error = null,
                    selectedDayIndex = 0,
                    trackMeStatus = it.trackMeStatus
                )
            }
            return
        }

        val currentPoint = (request.activeLocation as? ActiveLocation.Current)?.point
        val locationKey = currentPoint?.let { "current" } ?: currentLocation.id
        val refreshRequested = request.refresh != handledRefresh
        val providerChanged = request.provider != lastLoadedProvider
        val locationChanged = currentPoint == null && locationKey != lastLoadedLocationKey
        val currentNeedsRefresh = currentPoint != null && shouldRefresh(
            lastCurrentFetchPoint,
            currentPoint,
            lastCurrentFetchAt,
            now()
        )
        if (!refreshRequested && !providerChanged && !locationChanged && !currentNeedsRefresh) {
            _state.update {
                it.copy(
                    activeLocation = currentLocation,
                    locations = request.locations,
                    isLoading = false,
                    error = null
                )
            }
            return
        }

        _state.update {
            it.copy(
                isLoading = true,
                activeLocation = currentLocation,
                locations = request.locations,
                forecast = null,
                isStale = false,
                error = null,
                selectedDayIndex = 0
            )
        }
        try {
            val forecast = repository.getForecast(request.provider, request.activeLocation!!)
            if (requestGeneration == request.generation &&
                _state.value.activeLocation == currentLocation
            ) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        forecast = forecast.forecast,
                        isStale = forecast.isStale,
                        error = null
                    )
                }
                handledRefresh = request.refresh
                lastLoadedLocationKey = locationKey
                lastLoadedProvider = request.provider
                if (currentPoint != null) {
                    lastCurrentFetchPoint = currentPoint
                    lastCurrentFetchAt = now()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (requestGeneration != request.generation) return
            Log.e("WeatherViewModel", "Weather request failed", error)
            _state.update {
                it.copy(
                    isLoading = false,
                    isStale = false,
                    error = error.message ?: "Weather request failed"
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[
                    ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
                ] as HluWeatherApplication
                WeatherViewModel(
                    application.weatherRepository,
                    application.settingsRepository,
                    application.locationRepository,
                    application.deviceLocationSource
                )
            }
        }
    }
}

private data class WeatherLoadRequest(
    val generation: Long,
    val provider: WeatherProvider,
    val activeLocation: ActiveLocation?,
    val locations: List<WeatherLocation>,
    val locationMode: LocationMode,
    val refresh: Int
)

private fun ActiveLocation?.toWeatherLocation(): WeatherLocation? = when (this) {
    is ActiveLocation.Saved -> location
    is ActiveLocation.Current -> WeatherLocation(
        id = "current",
        name = "Current location",
        latitude = point.latitude,
        longitude = point.longitude,
        altitude = altitude
    )
    null -> null
}

private object FixedSettingsRepository : SettingsRepository {
    override val settings = flowOf(PersistedSettings())

    override suspend fun save(settings: PersistedSettings) = Unit
}

private class FixedLocationRepository(
    location: WeatherLocation
) : LocationRepository {
    override val locations = flowOf(listOf(location))
    override val activeLocation = flowOf<ActiveLocation?>(ActiveLocation.Saved(location))
    override val locationMode = flowOf(LocationMode.SAVED_LOCATION)

    override suspend fun add(location: WeatherLocation) = Unit
    override suspend fun update(location: WeatherLocation) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun selectSaved(id: String) = Unit
    override suspend fun setTrackMe(enabled: Boolean) = Unit
    override suspend fun setCurrentLocation(point: GeoPoint, altitude: Int?) = Unit
}
