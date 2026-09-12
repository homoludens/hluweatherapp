package net.droopia.hluweather.ui.weatherroute

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.hours
import net.droopia.hluweather.data.weatherroute.buildRouteSamples
import net.droopia.hluweather.data.device.DeviceLocationSource
import net.droopia.hluweather.data.model.DrivingRoute
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.GpsResult
import net.droopia.hluweather.data.model.RouteEndpoint
import net.droopia.hluweather.data.model.RouteWeatherSample
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherRouteResult
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.PlaceSearchProvider
import net.droopia.hluweather.data.repository.PlaceSearchResult
import net.droopia.hluweather.data.repository.PlaceSearchSource
import net.droopia.hluweather.data.repository.RouteWeatherSource
import net.droopia.hluweather.data.repository.RoutingSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherRouteViewModelTest {
    private val now = Instant.parse("2026-09-12T09:15:00Z")
    private val start = WeatherLocation("trieste", "Trieste", 45.6495, 13.7768)
    private val end = WeatherLocation("belgrade", "Belgrade", 44.8176, 20.4633)
    private val trieste = PlaceSearchResult("Trieste", GeoPoint(start.latitude, start.longitude))
    private val dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun search_is_debounced_and_selecting_results_and_saved_locations_calculates_weather() = runTest {
        val search = FakePlaceSearchSource(listOf(trieste))
        val routeData = route(distanceMeters = 80_000.0)
        val routing = FakeRoutingSource(routeData)
        val weather = FakeRouteWeatherSource()
        val viewModel = viewModel(search = search, route = routing, weather = weather)

        viewModel.onSearchQueryChanged(RouteEndpointSlot.START, "trieste")
        advanceTimeBy(299)
        assertEquals(emptyList<PlaceSearchResult>(), viewModel.state.value.searchResults)
        advanceTimeBy(1)
        advanceUntilIdle()
        assertEquals(listOf(trieste), viewModel.state.value.searchResults)

        viewModel.selectSearchResult(trieste)
        viewModel.selectSavedLocation(RouteEndpointSlot.END, end)
        viewModel.onSpeedChanged("80")
        viewModel.onDepartureChanged(Instant.parse("2026-09-12T10:00:00Z"))
        viewModel.calculate()
        advanceUntilIdle()

        val expectedSamples = buildRouteSamples(routeData, Instant.parse("2026-09-12T10:00:00Z"), 80)
            .map { it.copy(condition = WeatherCondition.CLEAR) }
        assertEquals(
            WeatherRouteResult(
                start = RouteEndpoint(trieste.label, trieste.point),
                end = RouteEndpoint(end.name, GeoPoint(end.latitude, end.longitude)),
                route = routeData,
                departure = Instant.parse("2026-09-12T10:00:00Z"),
                averageSpeedKmh = 80,
                samples = expectedSamples
            ),
            viewModel.state.value.result
        )
        assertFalse(viewModel.state.value.isCalculating)
        assertEquals(1, routing.calls)
        assertEquals(1, weather.requests)
    }

    @Test
    fun invalid_speed_and_incomplete_endpoints_are_rejected_before_routing() = runTest {
        val route = FakeRoutingSource(route())
        val viewModel = viewModel(route = route)

        viewModel.selectSavedLocation(RouteEndpointSlot.START, start)
        viewModel.selectSavedLocation(RouteEndpointSlot.END, end)
        viewModel.onSpeedChanged("49")
        viewModel.calculate()
        advanceUntilIdle()
        assertEquals("Speed must be between 50 and 240 km/h", viewModel.state.value.routeError)
        assertEquals(0, route.calls)

        val incompleteViewModel = viewModel(route = route)
        incompleteViewModel.onDepartureChanged(now + 1.hours)
        incompleteViewModel.calculate()
        advanceUntilIdle()
        assertEquals("Choose both route endpoints", incompleteViewModel.state.value.routeError)
        assertEquals(0, route.calls)
    }

    @Test
    fun departure_and_arrival_forecast_limits_are_rejected() = runTest {
        val route = FakeRoutingSource(route(distanceMeters = 100_000.0))
        val viewModel = viewModel(route = route)

        viewModel.selectSavedLocation(RouteEndpointSlot.START, start)
        viewModel.selectSavedLocation(RouteEndpointSlot.END, end)
        viewModel.onDepartureChanged(Instant.parse("2026-09-12T09:00:00Z"))
        viewModel.calculate()
        advanceUntilIdle()
        assertEquals("Departure cannot be in the past", viewModel.state.value.routeError)

        viewModel.onDepartureChanged(Instant.parse("2026-09-28T09:00:00Z"))
        viewModel.calculate()
        advanceUntilIdle()
        assertEquals("Arrival is outside the forecast range", viewModel.state.value.routeError)
    }

    @Test
    fun routing_failure_does_not_create_a_result() = runTest {
        val viewModel = viewModel(route = FakeRoutingSource(failure = IllegalStateException("routing down")))
        chooseEndpoints(viewModel)

        viewModel.calculate()
        advanceUntilIdle()

        assertNull(viewModel.state.value.result)
        assertEquals("routing down", viewModel.state.value.routeError)
        assertFalse(viewModel.state.value.isCalculating)
    }

    @Test
    fun partial_weather_result_is_kept_and_full_weather_failure_retains_route() = runTest {
        val routeData = route(distanceMeters = 80_000.0)
        val weather = FakeRouteWeatherSource()
        val viewModel = viewModel(route = FakeRoutingSource(routeData), weather = weather)
        chooseEndpoints(viewModel)

        weather.result = listOf(RouteWeatherSample(routeData.polyline.first(), 0.0, now))
        viewModel.calculate()
        advanceUntilIdle()
        assertEquals(weather.result, viewModel.state.value.result?.samples)
        assertNull(viewModel.state.value.weatherError)

        weather.failure = IllegalStateException("weather down")
        viewModel.retryWeather()
        advanceUntilIdle()
        assertEquals(routeData, viewModel.state.value.result?.route)
        assertEquals("weather down", viewModel.state.value.weatherError)
        assertFalse(viewModel.state.value.isRetryingWeather)
    }

    @Test
    fun retry_weather_reuses_existing_route_and_input_changes_mark_result_outdated() = runTest {
        val route = FakeRoutingSource(route(distanceMeters = 80_000.0))
        val weather = FakeRouteWeatherSource()
        val viewModel = viewModel(route = route, weather = weather)
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()
        assertEquals(1, route.calls)

        viewModel.retryWeather()
        advanceUntilIdle()
        assertEquals(1, route.calls)
        assertEquals(2, weather.requests)

        viewModel.onSpeedChanged("90")
        assertTrue(viewModel.state.value.isResultOutdated)
    }

    @Test
    fun superseded_route_request_cannot_update_state() = runTest {
        val first = CompletableDeferred<DrivingRoute>()
        val second = CompletableDeferred<DrivingRoute>()
        val route = SequencedRoutingSource(listOf(first, second))
        val viewModel = viewModel(route = route)
        chooseEndpoints(viewModel)

        viewModel.calculate()
        viewModel.onSpeedChanged("90")
        viewModel.calculate()
        second.complete(route(distanceMeters = 90_000.0))
        advanceUntilIdle()
        first.complete(route(distanceMeters = 80_000.0))
        advanceUntilIdle()

        assertEquals(90_000.0, viewModel.state.value.result?.route?.distanceMeters)
        assertEquals(90, viewModel.state.value.result?.averageSpeedKmh)
    }

    @Test
    fun current_location_is_requested_only_when_chosen_and_status_is_concise() = runTest {
        val gps = FakeDeviceLocationSource(GpsResult.PermissionRequired)
        val viewModel = viewModel(deviceLocation = gps)

        assertEquals(0, gps.calls)
        viewModel.selectCurrentLocation(RouteEndpointSlot.START)
        advanceUntilIdle()

        assertEquals(1, gps.calls)
        assertEquals("Location permission is required", viewModel.state.value.routeError)
        assertNull(viewModel.state.value.start)
    }

    @Test
    fun cancellation_is_rethrown() = runTest {
        val cancellation = kotlinx.coroutines.CancellationException("cancelled")
        val viewModel = viewModel(route = FakeRoutingSource(failure = cancellation))
        chooseEndpoints(viewModel)

        viewModel.calculate()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isCalculating)
    }

    @Test
    fun next_full_hour_follows_the_device_timezone_at_a_dst_boundary() {
        assertEquals(
            Instant.parse("2026-11-01T10:00:00Z"),
            nextFullHour(
                Instant.parse("2026-11-01T08:30:00Z"),
                TimeZone.of("America/Los_Angeles")
            )
        )
    }

    private fun chooseEndpoints(viewModel: WeatherRouteViewModel) {
        viewModel.selectSavedLocation(RouteEndpointSlot.START, start)
        viewModel.selectSavedLocation(RouteEndpointSlot.END, end)
        viewModel.onDepartureChanged(now + 1.hours)
    }

    private fun viewModel(
        search: PlaceSearchSource = FakePlaceSearchSource(),
        route: RoutingSource = FakeRoutingSource(route()),
        weather: RouteWeatherSource = FakeRouteWeatherSource(),
        deviceLocation: DeviceLocationSource = FakeDeviceLocationSource(GpsResult.Unavailable)
    ) = WeatherRouteViewModel(
        routingSource = route,
        placeSearchSources = listOf(search),
        routeWeatherSource = weather,
        locationRepository = FakeLocationRepository(),
        deviceLocationSource = deviceLocation,
        now = { now }
    )

    private fun route(distanceMeters: Double = 80_000.0) = DrivingRoute(
        providerName = "OSRM",
        polyline = listOf(startPoint, endPoint),
        distanceMeters = distanceMeters,
        providerDurationSeconds = 3_600.0
    )

    private val startPoint = GeoPoint(45.6495, 13.7768)
    private val endPoint = GeoPoint(44.8176, 20.4633)
}

private class FakePlaceSearchSource(
    private val results: List<PlaceSearchResult> = emptyList()
) : PlaceSearchSource {
    override val provider = PlaceSearchProvider.PHOTON

    override suspend fun search(query: String): List<PlaceSearchResult> = results
}

private class FakeRoutingSource(
    private val route: DrivingRoute? = null,
    private val failure: Throwable? = null
) : RoutingSource {
    var calls = 0
        private set
    override val providerName = "OSRM"

    override suspend fun route(start: GeoPoint, end: GeoPoint): DrivingRoute {
        calls++
        failure?.let { throw it }
        return route ?: error("route not configured")
    }
}

private class SequencedRoutingSource(
    private val requests: List<CompletableDeferred<DrivingRoute>>
) : RoutingSource {
    private var index = 0
    override val providerName = "OSRM"

    override suspend fun route(start: GeoPoint, end: GeoPoint): DrivingRoute =
        requests[index++].await()
}

private class FakeRouteWeatherSource : RouteWeatherSource {
    var result: List<RouteWeatherSample>? = null
    var failure: Throwable? = null
    var requests = 0
        private set

    override suspend fun enrich(samples: List<RouteWeatherSample>): List<RouteWeatherSample> {
        requests++
        failure?.let { throw it }
        return result ?: samples.map { it.copy(condition = WeatherCondition.CLEAR) }
    }
}

private class FakeDeviceLocationSource(
    private val result: GpsResult
) : DeviceLocationSource {
    var calls = 0
        private set

    override suspend fun currentLocation(): GpsResult {
        calls++
        return result
    }
}

private class FakeLocationRepository : LocationRepository {
    override val locations: Flow<List<WeatherLocation>> = MutableStateFlow(emptyList())
    override val activeLocation: StateFlow<net.droopia.hluweather.data.model.ActiveLocation?> =
        MutableStateFlow(null)
    override val locationMode: StateFlow<net.droopia.hluweather.data.model.LocationMode> =
        MutableStateFlow(net.droopia.hluweather.data.model.LocationMode.SAVED_LOCATION)

    override suspend fun add(location: WeatherLocation) = Unit
    override suspend fun update(location: WeatherLocation) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun selectSaved(id: String) = Unit
    override suspend fun setTrackMe(enabled: Boolean) = Unit
}
