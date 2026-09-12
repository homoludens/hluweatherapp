package net.droopia.hluweather.ui.weatherroute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import net.droopia.hluweather.data.device.DeviceLocationSource
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.GpsResult
import net.droopia.hluweather.data.model.RouteEndpoint
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherRouteResult
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.PlaceSearchProvider
import net.droopia.hluweather.data.repository.PlaceSearchResult
import net.droopia.hluweather.data.repository.PlaceSearchSource
import net.droopia.hluweather.data.repository.RouteWeatherSource
import net.droopia.hluweather.data.repository.RoutingSource
import net.droopia.hluweather.data.weatherroute.buildRouteSamples

enum class RouteEndpointSlot { START, END }

data class WeatherRouteUiState(
    val start: RouteEndpoint? = null,
    val end: RouteEndpoint? = null,
    val activeSearchSlot: RouteEndpointSlot? = null,
    val searchProvider: PlaceSearchProvider = PlaceSearchProvider.PHOTON,
    val searchQuery: String = "",
    val searchResults: List<PlaceSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val departure: Instant = nextFullHour(),
    val speedText: String = "80",
    val result: WeatherRouteResult? = null,
    val isCalculating: Boolean = false,
    val isRetryingWeather: Boolean = false,
    val routeError: String? = null,
    val weatherError: String? = null,
    val isResultOutdated: Boolean = false,
    val selectedSampleIndex: Int? = null
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class WeatherRouteViewModel(
    private val routingSource: RoutingSource,
    private val placeSearchSources: List<PlaceSearchSource>,
    private val routeWeatherSource: RouteWeatherSource,
    private val locationRepository: LocationRepository,
    private val deviceLocationSource: DeviceLocationSource,
    private val now: () -> Instant = { Clock.System.now() }
) : ViewModel() {

    private val _state = MutableStateFlow(
        WeatherRouteUiState(departure = nextFullHour(now()))
    )
    val state = _state.asStateFlow()

    private val searchInput = MutableStateFlow(SearchInput())
    private var calculationGeneration = 0L

    init {
        viewModelScope.launch {
            searchInput
                .debounce(300)
                .flatMapLatest { input ->
                    val source = placeSearchSources.firstOrNull { it.provider == input.provider }
                    if (source == null || input.slot == null || input.query.isBlank()) {
                        flow { emit(SearchOutcome(emptyList(), null)) }
                    } else {
                        flow {
                            try {
                                emit(SearchOutcome(source.search(input.query), null))
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                emit(SearchOutcome(emptyList(), error.message ?: "Search failed"))
                            }
                        }
                    }
                }
                .collect { outcome ->
                    _state.update {
                        it.copy(
                            searchResults = outcome.results,
                            isSearching = false,
                            routeError = outcome.error ?: it.routeError
                        )
                    }
                }
        }
    }

    fun onSearchQueryChanged(slot: RouteEndpointSlot, query: String) {
        invalidateCalculation()
        _state.update {
            it.copy(
                activeSearchSlot = slot,
                searchQuery = query,
                searchResults = emptyList(),
                isSearching = query.isNotBlank(),
                routeError = null
            )
        }
        searchInput.value = SearchInput(slot, query, _state.value.searchProvider)
    }

    fun onSearchProviderChanged(provider: PlaceSearchProvider) {
        invalidateCalculation()
        _state.update {
            it.copy(
                searchProvider = provider,
                searchResults = emptyList(),
                isSearching = it.searchQuery.isNotBlank(),
                routeError = null
            )
        }
        val current = _state.value
        searchInput.value = SearchInput(current.activeSearchSlot, current.searchQuery, provider)
    }

    fun selectSearchResult(result: PlaceSearchResult) {
        val slot = _state.value.activeSearchSlot ?: return
        setEndpoint(slot, RouteEndpoint(result.label, result.point))
        clearSearch()
    }

    fun selectSavedLocation(slot: RouteEndpointSlot, location: WeatherLocation) {
        setEndpoint(
            slot,
            RouteEndpoint(location.name, GeoPoint(location.latitude, location.longitude))
        )
    }

    fun selectMapEndpoint(slot: RouteEndpointSlot, endpoint: RouteEndpoint) {
        setEndpoint(slot, endpoint)
    }

    fun selectCurrentLocation(slot: RouteEndpointSlot) {
        viewModelScope.launch {
            try {
                when (val gps = deviceLocationSource.currentLocation()) {
                    is GpsResult.Success -> {
                        locationRepository.setCurrentLocation(gps.point, gps.altitude)
                        setEndpoint(slot, RouteEndpoint("Current location", gps.point))
                    }
                    GpsResult.PermissionRequired -> showRouteError("Location permission is required")
                    GpsResult.LocationDisabled -> showRouteError("Location is disabled")
                    GpsResult.Unavailable -> showRouteError("Location unavailable")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                showRouteError(error.message ?: "Current location unavailable")
            }
        }
    }

    fun onSpeedChanged(speedText: String) {
        invalidateCalculation()
        _state.update {
            it.copy(
                speedText = speedText,
                isResultOutdated = it.result != null,
                routeError = null,
                weatherError = null
            )
        }
    }

    fun onDepartureChanged(departure: Instant) {
        invalidateCalculation()
        _state.update {
            it.copy(
                departure = departure,
                isResultOutdated = it.result != null,
                routeError = null,
                weatherError = null
            )
        }
    }

    fun selectSample(index: Int?) {
        _state.update { it.copy(selectedSampleIndex = index) }
    }

    fun calculate() {
        val snapshot = _state.value
        val speed = snapshot.speedText.toIntOrNull()
        val validationError = when {
            snapshot.start == null || snapshot.end == null -> "Choose both route endpoints"
            speed == null || speed !in SPEED_RANGE -> "Speed must be between 50 and 240 km/h"
            snapshot.departure < now() -> "Departure cannot be in the past"
            else -> null
        }
        if (validationError != null) {
            showRouteError(validationError)
            return
        }

        val generation = ++calculationGeneration
        val start = snapshot.start!!
        val end = snapshot.end!!
        val departure = snapshot.departure
        val averageSpeed = speed!!
        _state.update {
            it.copy(
                isCalculating = true,
                isRetryingWeather = false,
                isResultOutdated = it.result != null,
                routeError = null,
                weatherError = null,
                selectedSampleIndex = null
            )
        }

        viewModelScope.launch {
            try {
                val route = routingSource.route(start.point, end.point)
                if (generation != calculationGeneration) return@launch

                val durationSeconds = route.distanceMeters /
                    (averageSpeed * METERS_PER_KILOMETER / SECONDS_PER_HOUR)
                if (departure + durationSeconds.seconds > forecastEnd()) {
                    showRouteErrorIfCurrent(generation, "Arrival is outside the forecast range")
                    return@launch
                }

                val samples = buildRouteSamples(route, departure, averageSpeed)
                val result = WeatherRouteResult(
                    start = start,
                    end = end,
                    route = route,
                    departure = departure,
                    averageSpeedKmh = averageSpeed,
                    samples = samples
                )
                enrichAndPublish(generation, result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation == calculationGeneration) {
                    _state.update {
                        it.copy(
                            result = null,
                            routeError = error.message ?: "Routing failed"
                        )
                    }
                }
            } finally {
                if (generation == calculationGeneration) {
                    _state.update { it.copy(isCalculating = false) }
                }
            }
        }
    }

    fun retryWeather() {
        val current = _state.value.result ?: return
        val generation = ++calculationGeneration
        _state.update {
            it.copy(
                isCalculating = false,
                isRetryingWeather = true,
                weatherError = null,
                routeError = null
            )
        }
        viewModelScope.launch {
            try {
                val enriched = routeWeatherSource.enrich(current.samples)
                if (generation == calculationGeneration) {
                    _state.update {
                        it.copy(
                            result = current.copy(samples = enriched),
                            isRetryingWeather = false,
                            weatherError = null,
                            isResultOutdated = false
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation == calculationGeneration) {
                    _state.update {
                        it.copy(
                            isRetryingWeather = false,
                            weatherError = error.message ?: "Weather unavailable"
                        )
                    }
                }
            }
        }
    }

    private suspend fun enrichAndPublish(generation: Long, result: WeatherRouteResult) {
        try {
            val enriched = routeWeatherSource.enrich(result.samples)
            if (generation == calculationGeneration) {
                _state.update {
                    it.copy(
                        result = result.copy(samples = enriched),
                        weatherError = null,
                        isResultOutdated = false
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (generation == calculationGeneration) {
                _state.update {
                    it.copy(
                        result = result,
                        weatherError = error.message ?: "Weather unavailable",
                        isResultOutdated = false
                    )
                }
            }
        }
    }

    private fun setEndpoint(slot: RouteEndpointSlot, endpoint: RouteEndpoint) {
        invalidateCalculation()
        _state.update {
            when (slot) {
                RouteEndpointSlot.START -> it.copy(
                    start = endpoint,
                    isResultOutdated = it.result != null,
                    routeError = null,
                    weatherError = null
                )
                RouteEndpointSlot.END -> it.copy(
                    end = endpoint,
                    isResultOutdated = it.result != null,
                    routeError = null,
                    weatherError = null
                )
            }
        }
    }

    private fun clearSearch() {
        _state.update {
            it.copy(
                activeSearchSlot = null,
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false
            )
        }
        searchInput.value = SearchInput()
    }

    private fun showRouteError(message: String) {
        _state.update {
            it.copy(
                isCalculating = false,
                routeError = message
            )
        }
    }

    private fun showRouteErrorIfCurrent(generation: Long, message: String) {
        if (generation == calculationGeneration) showRouteError(message)
    }

    private fun invalidateCalculation() {
        calculationGeneration++
        _state.update {
            it.copy(
                isCalculating = false,
                isRetryingWeather = false,
                isResultOutdated = it.result != null
            )
        }
    }

    private fun forecastEnd(): Instant = now() + FORECAST_DURATION

    class Factory(
        private val routingSource: RoutingSource,
        private val placeSearchSources: List<PlaceSearchSource>,
        private val routeWeatherSource: RouteWeatherSource,
        private val locationRepository: LocationRepository,
        private val deviceLocationSource: DeviceLocationSource,
        private val now: () -> Instant = { Clock.System.now() }
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WeatherRouteViewModel::class.java))
            return WeatherRouteViewModel(
                routingSource = routingSource,
                placeSearchSources = placeSearchSources,
                routeWeatherSource = routeWeatherSource,
                locationRepository = locationRepository,
                deviceLocationSource = deviceLocationSource,
                now = now
            ) as T
        }
    }
}

fun nextFullHour(
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): Instant {
    val local = now.toLocalDateTime(timeZone)
    val nextHour = (local.hour + 1) % 24
    val nextDate = if (nextHour == 0) {
        local.date.plus(1, DateTimeUnit.DAY)
    } else {
        local.date
    }
    return LocalDateTime(nextDate, LocalTime(nextHour, 0)).toInstant(timeZone)
}

private data class SearchInput(
    val slot: RouteEndpointSlot? = null,
    val query: String = "",
    val provider: PlaceSearchProvider = PlaceSearchProvider.PHOTON
)

private data class SearchOutcome(
    val results: List<PlaceSearchResult>,
    val error: String?
)

private const val METERS_PER_KILOMETER = 1_000.0
private const val SECONDS_PER_HOUR = 3_600.0
private val SPEED_RANGE = 50..240
private val FORECAST_DURATION = 16.days
