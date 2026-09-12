package net.droopia.hluweather.ui.weatherroute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
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
import kotlin.time.Duration.Companion.hours
import net.droopia.hluweather.data.device.DeviceLocationSource
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.GpsResult
import net.droopia.hluweather.data.model.RouteEndpoint
import net.droopia.hluweather.data.model.RouteWeatherSample
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherRouteResult
import net.droopia.hluweather.data.network.OpenMeteoApiException
import net.droopia.hluweather.data.network.OpenMeteoGeocodingApiException
import net.droopia.hluweather.data.network.OsrmApiException
import net.droopia.hluweather.data.network.PhotonApiException
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.PlaceSearchProvider
import net.droopia.hluweather.data.repository.PlaceSearchResult
import net.droopia.hluweather.data.repository.PlaceSearchSource
import net.droopia.hluweather.data.repository.RouteWeatherSource
import net.droopia.hluweather.data.repository.RoutingException
import net.droopia.hluweather.data.repository.RoutingSource
import net.droopia.hluweather.data.weatherroute.buildRouteSamples
import net.droopia.hluweather.data.weatherroute.RouteTimingMode
import net.droopia.hluweather.data.weatherroute.RouteWeatherSnapshot
import net.droopia.hluweather.data.weatherroute.TripWeatherSummary
import net.droopia.hluweather.data.weatherroute.TripWeatherWarning
import net.droopia.hluweather.data.weatherroute.covers
import net.droopia.hluweather.data.weatherroute.enrich
import net.droopia.hluweather.data.weatherroute.findTripWeatherWarnings
import net.droopia.hluweather.data.weatherroute.summarizeTripWeather

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
    val selectedSampleIndex: Int? = null,
    val departureOffsetHours: Int = 0,
    val snapshot: RouteWeatherSnapshot? = null,
    val summary: TripWeatherSummary? = null,
    val warnings: List<TripWeatherWarning> = emptyList()
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class WeatherRouteViewModel(
    private val routingSource: RoutingSource,
    private val placeSearchSources: List<PlaceSearchSource>,
    private val placeSearchProvider: PlaceSearchProvider,
    private val routeWeatherSource: RouteWeatherSource,
    private val locationRepository: LocationRepository,
    private val deviceLocationSource: DeviceLocationSource,
    private val now: () -> Instant = { Clock.System.now() }
) : ViewModel() {

    private val referenceNow = now()
    private val _state = MutableStateFlow(
        WeatherRouteUiState(
            departure = referenceNow,
            searchProvider = placeSearchProvider
        )
    )
    val state = _state.asStateFlow()

    private val searchInput = MutableStateFlow(SearchInput())
    private val departureRefreshInput = MutableSharedFlow<DepartureRefresh>(extraBufferCapacity = 1)
    private val departureRefreshCancellation = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var calculationGeneration = 0L
    private var activeWorkJob: Job? = null

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
                                emit(SearchOutcome(emptyList(), searchErrorMessage(error)))
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
        viewModelScope.launch {
            merge(
                departureRefreshInput
                    .debounce(300)
                    .map { DepartureRefreshEvent.Refresh(it) },
                departureRefreshCancellation.map { DepartureRefreshEvent.Cancel }
            ).flatMapLatest { event ->
                when (event) {
                    DepartureRefreshEvent.Cancel -> emptyFlow<Unit>()
                    is DepartureRefreshEvent.Refresh -> flow<Unit> {
                        refreshDeparture(event.request)
                        emit(Unit)
                    }
                }
            }.collect { }
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
                showRouteError("Current location unavailable")
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

    fun onDepartureOffsetChanged(offsetHours: Int) {
        val offset = offsetHours.coerceIn(DEPARTURE_OFFSET_RANGE)
        val generation = ++calculationGeneration
        cancelActiveWork()
        cancelDepartureRefresh()
        val current = _state.value
        val result = current.result
        val selectedDeparture = selectedDeparture(offset)
        val samples = try {
            result?.let {
                buildRouteSamples(
                    route = it.route,
                    departure = selectedDeparture,
                    averageSpeedKmh = it.averageSpeedKmh,
                    timingMode = RouteTimingMode.AVERAGE_SPEED
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            _state.update {
                it.copy(
                    departureOffsetHours = offset,
                    isCalculating = false,
                    isRetryingWeather = false,
                    routeError = routeErrorMessage(error),
                    weatherError = null
                )
            }
            return
        }
        if (result != null && samples != null && samples.last().arrivalTime > forecastEnd()) {
            val updatedResult = result.copy(
                departure = selectedDeparture,
                samples = samples.map { it.withoutWeather() }
            )
            _state.update {
                it.copy(
                    departureOffsetHours = offset,
                    isCalculating = false,
                    isRetryingWeather = false,
                    result = updatedResult,
                    snapshot = null,
                    summary = summarizeTripWeather(updatedResult.samples),
                    warnings = findTripWeatherWarnings(updatedResult.samples),
                    routeError = "Arrival is outside the forecast range",
                    weatherError = null,
                    isResultOutdated = true
                )
            }
            return
        }
        val coveredSnapshot = current.snapshot?.takeIf { snapshot ->
            samples != null && snapshot.covers(samples)
        }
        val updatedResult = result?.copy(
            departure = selectedDeparture,
            samples = coveredSnapshot?.enrich(samples!!) ?: samples ?: result.samples
        )
        _state.update {
            it.copy(
                departureOffsetHours = offset,
                isCalculating = false,
                isRetryingWeather = false,
                result = updatedResult,
                snapshot = coveredSnapshot,
                summary = updatedResult?.let { value -> summarizeTripWeather(value.samples) },
                warnings = updatedResult?.let { value -> findTripWeatherWarnings(value.samples) }.orEmpty(),
                routeError = null,
                isResultOutdated = if (result != null && coveredSnapshot == null) {
                    true
                } else {
                    it.isResultOutdated
                },
                weatherError = null
            )
        }
        if (generation == calculationGeneration && result != null && samples != null && coveredSnapshot == null) {
            departureRefreshInput.tryEmit(DepartureRefresh(generation, offset))
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
            speed == null || speed !in SPEED_RANGE -> "Speed must be between 40 and 130 km/h"
            selectedDeparture() < referenceNow -> "Departure cannot be in the past"
            else -> null
        }
        if (validationError != null) {
            showRouteError(validationError)
            return
        }

        val generation = ++calculationGeneration
        cancelActiveWork()
        cancelDepartureRefresh()
        val start = snapshot.start!!
        val end = snapshot.end!!
        val departure = selectedDeparture()
        val averageSpeed = speed!!
        _state.update {
            it.copy(
                isCalculating = true,
                isRetryingWeather = false,
                isResultOutdated = it.result != null,
                routeError = null,
                weatherError = null,
                selectedSampleIndex = null,
                snapshot = null,
                summary = null,
                warnings = emptyList()
            )
        }

        activeWorkJob = viewModelScope.launch {
            try {
                val route = routingSource.route(start.point, end.point)
                if (generation != calculationGeneration) return@launch

                val samples = buildRouteSamples(
                    route = route,
                    departure = departure,
                    averageSpeedKmh = averageSpeed,
                    timingMode = RouteTimingMode.AVERAGE_SPEED
                )
                if (samples.last().arrivalTime > forecastEnd()) {
                    showRouteErrorIfCurrent(generation, "Arrival is outside the forecast range")
                    return@launch
                }
                val result = WeatherRouteResult(
                    start = start,
                    end = end,
                    route = route,
                    departure = departure,
                    averageSpeedKmh = averageSpeed,
                    samples = samples
                )
                fetchAndPublish(generation, result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation == calculationGeneration) {
                    _state.update {
                        it.copy(
                            result = null,
                            snapshot = null,
                            summary = null,
                            warnings = emptyList(),
                            routeError = routeErrorMessage(error)
                        )
                    }
                }
            } finally {
                if (generation == calculationGeneration) {
                    _state.update { it.copy(isCalculating = false) }
                    activeWorkJob = null
                }
            }
        }
    }

    fun retryWeather() {
        val current = _state.value.result ?: return
        val wasResultOutdated = _state.value.isResultOutdated
        val generation = ++calculationGeneration
        cancelActiveWork()
        cancelDepartureRefresh()
        val selectedDeparture = selectedDeparture()
        _state.update {
            it.copy(
                isCalculating = false,
                isRetryingWeather = true,
                weatherError = null,
                routeError = null
            )
        }
        activeWorkJob = viewModelScope.launch {
            try {
                val samples = buildRouteSamples(
                    route = current.route,
                    departure = selectedDeparture,
                    averageSpeedKmh = current.averageSpeedKmh,
                    timingMode = RouteTimingMode.AVERAGE_SPEED
                )
                if (samples.last().arrivalTime > forecastEnd()) {
                    val updatedResult = current.copy(
                        departure = selectedDeparture,
                        samples = samples.map { it.withoutWeather() }
                    )
                    if (generation == calculationGeneration) {
                        _state.update {
                            it.copy(
                                result = updatedResult,
                                snapshot = null,
                                summary = summarizeTripWeather(updatedResult.samples),
                                warnings = findTripWeatherWarnings(updatedResult.samples),
                                routeError = "Arrival is outside the forecast range",
                                weatherError = null,
                                isRetryingWeather = false,
                                isResultOutdated = wasResultOutdated
                            )
                        }
                    }
                    return@launch
                }
                val snapshot = routeWeatherSource.fetchSnapshot(samples.map { it.point })
                if (generation == calculationGeneration) {
                    publishSnapshot(
                        generation = generation,
                        result = current.copy(departure = selectedDeparture, samples = samples),
                        snapshot = snapshot,
                        isResultOutdated = wasResultOutdated
                    )
                    _state.update { it.copy(isRetryingWeather = false) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation == calculationGeneration) {
                    _state.update {
                        it.copy(
                            isRetryingWeather = false,
                            weatherError = weatherErrorMessage(error),
                            snapshot = null
                        )
                    }
                }
            } finally {
                if (generation == calculationGeneration) activeWorkJob = null
            }
        }
    }

    private suspend fun fetchAndPublish(generation: Long, result: WeatherRouteResult) {
        try {
            val snapshot = routeWeatherSource.fetchSnapshot(result.samples.map { it.point })
            if (generation == calculationGeneration) {
                publishSnapshot(generation, result, snapshot, isResultOutdated = false)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (generation == calculationGeneration) {
                _state.update {
                    it.copy(
                        result = result,
                        snapshot = null,
                        summary = summarizeTripWeather(result.samples),
                        warnings = findTripWeatherWarnings(result.samples),
                        weatherError = weatherErrorMessage(error),
                        isResultOutdated = false
                    )
                }
            }
        }
    }

    private suspend fun refreshDeparture(request: DepartureRefresh) {
        if (request.generation != calculationGeneration) return
        try {
            val current = _state.value
            val result = current.result ?: return
            val selectedDeparture = selectedDeparture(request.offsetHours)
            val samples = buildRouteSamples(
                route = result.route,
                departure = selectedDeparture,
                averageSpeedKmh = result.averageSpeedKmh,
                timingMode = RouteTimingMode.AVERAGE_SPEED
            )
            if (samples.last().arrivalTime > forecastEnd()) {
                val updatedResult = result.copy(
                    departure = selectedDeparture,
                    samples = samples.map { it.withoutWeather() }
                )
                if (request.generation == calculationGeneration) {
                    _state.update {
                        it.copy(
                            result = updatedResult,
                            snapshot = null,
                            summary = summarizeTripWeather(updatedResult.samples),
                            warnings = findTripWeatherWarnings(updatedResult.samples),
                            routeError = "Arrival is outside the forecast range",
                            weatherError = null
                        )
                    }
                }
                return
            }
            val snapshot = current.snapshot
            if (snapshot?.covers(samples) == true) {
                publishSnapshot(
                    generation = request.generation,
                    result = result.copy(departure = selectedDeparture, samples = samples),
                    snapshot = snapshot,
                    isResultOutdated = false
                )
                return
            }
            val refreshed = routeWeatherSource.fetchSnapshot(samples.map { it.point })
            if (request.generation == calculationGeneration) {
                publishSnapshot(
                    generation = request.generation,
                    result = result.copy(departure = selectedDeparture, samples = samples),
                    snapshot = refreshed,
                    isResultOutdated = false
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (request.generation == calculationGeneration) {
                _state.update {
                        it.copy(
                            routeError = null,
                            isResultOutdated = false,
                            weatherError = weatherErrorMessage(error),
                            snapshot = null
                    )
                }
            }
        }
    }

    private fun publishSnapshot(
        generation: Long,
        result: WeatherRouteResult,
        snapshot: RouteWeatherSnapshot,
        isResultOutdated: Boolean
    ) {
        if (generation != calculationGeneration) return
        val covered = snapshot.covers(result.samples)
        val enriched = snapshot.enrich(result.samples)
        _state.update {
            it.copy(
                result = result.copy(samples = enriched),
                snapshot = snapshot.takeIf { covered },
                summary = summarizeTripWeather(enriched),
                warnings = findTripWeatherWarnings(enriched),
                routeError = null,
                weatherError = if (covered) null else "Weather unavailable",
                isResultOutdated = isResultOutdated
            )
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
        clearSearch()
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
        cancelActiveWork()
        cancelDepartureRefresh()
        _state.update {
            it.copy(
                isCalculating = false,
                isRetryingWeather = false,
                isResultOutdated = it.result != null
            )
        }
    }

    private fun cancelActiveWork() {
        activeWorkJob?.cancel()
        activeWorkJob = null
    }

    private fun cancelDepartureRefresh() {
        departureRefreshCancellation.tryEmit(Unit)
    }

    private fun searchErrorMessage(error: Throwable): String = when (error) {
        is PhotonApiException, is OpenMeteoGeocodingApiException, is IOException ->
            "Place search unavailable"
        else -> "Place search failed"
    }

    private fun routeErrorMessage(error: Throwable): String = when (error) {
        is RoutingException -> "No driving route found"
        is OsrmApiException, is IOException -> "Routing service unavailable"
        else -> "Routing failed"
    }

    private fun weatherErrorMessage(error: Throwable): String = when (error) {
        is OpenMeteoApiException, is IOException -> "Weather service unavailable"
        else -> "Weather unavailable"
    }

    private fun forecastEnd(): Instant = referenceNow + FORECAST_DURATION

    fun selectedDeparture(): Instant = selectedDeparture(_state.value.departureOffsetHours)

    private fun selectedDeparture(offsetHours: Int): Instant = referenceNow + offsetHours.hours

    class Factory(
        private val routingSource: RoutingSource,
        private val placeSearchProvider: PlaceSearchProvider,
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
                placeSearchProvider = placeSearchProvider,
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

private data class DepartureRefresh(
    val generation: Long,
    val offsetHours: Int
)

private sealed interface DepartureRefreshEvent {
    data object Cancel : DepartureRefreshEvent
    data class Refresh(val request: DepartureRefresh) : DepartureRefreshEvent
}

private val SPEED_RANGE = 40..130
private val DEPARTURE_OFFSET_RANGE = 0..72
private val FORECAST_DURATION = 16.days

private fun RouteWeatherSample.withoutWeather(): RouteWeatherSample = copy(
    condition = null,
    temperatureCelsius = null,
    windSpeedKmh = null,
    precipitationProbability = null,
    precipitationMm = null,
    humidityPercent = null,
    isDay = null
)
