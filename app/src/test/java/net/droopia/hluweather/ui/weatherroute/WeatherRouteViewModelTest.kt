package net.droopia.hluweather.ui.weatherroute

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.cancel
import net.droopia.hluweather.data.weatherroute.buildRouteSamples
import net.droopia.hluweather.data.weatherroute.RouteWeatherForecastHour
import net.droopia.hluweather.data.weatherroute.RouteWeatherSnapshot
import net.droopia.hluweather.data.device.DeviceLocationSource
import net.droopia.hluweather.data.model.DrivingRoute
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.GpsResult
import net.droopia.hluweather.data.model.RouteEndpoint
import net.droopia.hluweather.data.model.RouteWeatherSample
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherRouteResult
import net.droopia.hluweather.data.network.OpenMeteoApiException
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.PlaceSearchProvider
import net.droopia.hluweather.data.repository.PlaceSearchResult
import net.droopia.hluweather.data.repository.PlaceSearchSource
import net.droopia.hluweather.data.repository.RouteWeatherSource
import net.droopia.hluweather.data.repository.RoutingSource
import net.droopia.hluweather.data.repository.RoutingException
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
        viewModel.onDepartureOffsetChanged(1)
        viewModel.calculate()
        advanceUntilIdle()

        val expectedSamples = buildRouteSamples(routeData, now + 1.hours, 80)
            .map { it.copy(condition = WeatherCondition.CLEAR) }
        assertEquals(
            WeatherRouteResult(
                start = RouteEndpoint(trieste.label, trieste.point),
                end = RouteEndpoint(end.name, GeoPoint(end.latitude, end.longitude)),
                route = routeData,
                departure = now + 1.hours,
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
        viewModel.onSpeedChanged("39")
        viewModel.calculate()
        advanceUntilIdle()
        assertEquals("Speed must be between 40 and 130 km/h", viewModel.state.value.routeError)
        assertEquals(0, route.calls)

        val incompleteViewModel = viewModel(route = route)
        incompleteViewModel.calculate()
        advanceUntilIdle()
        assertEquals("Choose both route endpoints", incompleteViewModel.state.value.routeError)
        assertEquals(0, route.calls)
    }

    @Test
    fun default_speed_is_80() {
        assertEquals("80", viewModel().state.value.speedText)
    }

    @Test
    fun start_time_uses_stable_reference_now_and_clamps_to_72_hours() = runTest {
        val viewModel = viewModel()

        viewModel.onDepartureOffsetChanged(99)

        assertEquals(72, viewModel.state.value.departureOffsetHours)
        assertEquals(now + 72.hours, viewModel.selectedDeparture())
    }

    @Test
    fun speed_accepts_40_through_130_and_rejects_values_outside_range() = runTest {
        val viewModel = viewModel()
        chooseEndpoints(viewModel)

        viewModel.onSpeedChanged("40")
        viewModel.calculate()
        advanceUntilIdle()
        assertNull(viewModel.state.value.routeError)

        viewModel.onSpeedChanged("131")
        viewModel.calculate()
        assertEquals("Speed must be between 40 and 130 km/h", viewModel.state.value.routeError)
    }

    @Test
    fun speed_accepts_the_direct_upper_bound_130() = runTest {
        val viewModel = viewModel()
        chooseEndpoints(viewModel)

        viewModel.onSpeedChanged("130")
        viewModel.calculate()
        advanceUntilIdle()

        assertNull(viewModel.state.value.routeError)
    }

    @Test
    fun route_sampling_always_uses_average_speed() = runTest {
        val viewModel = viewModel(
            route = FakeRoutingSource(route(distanceMeters = 80_000.0).copy(
                providerDurationSeconds = 7_200.0
            ))
        )
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()

        assertEquals(now + 2.hours, viewModel.state.value.result!!.samples.last().arrivalTime)
    }

    @Test
    fun selected_departure_is_reference_now_plus_offset() = runTest {
        val viewModel = viewModel()

        viewModel.onDepartureOffsetChanged(24)

        assertEquals(now + 24.hours, viewModel.selectedDeparture())
    }

    @Test
    fun departure_and_arrival_forecast_limits_are_rejected() = runTest {
        val route = FakeRoutingSource(route(distanceMeters = 25_000_000.0))
        val viewModel = viewModel(route = route)

        viewModel.selectSavedLocation(RouteEndpointSlot.START, start)
        viewModel.selectSavedLocation(RouteEndpointSlot.END, end)
        viewModel.onDepartureOffsetChanged(72)
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
        assertEquals("Routing failed", viewModel.state.value.routeError)
        assertFalse(viewModel.state.value.isCalculating)
    }

    @Test
    fun known_routing_failure_is_mapped_to_a_stable_message() = runTest {
        val viewModel = viewModel(route = FakeRoutingSource(failure = RoutingException("provider details")))
        chooseEndpoints(viewModel)

        viewModel.calculate()
        advanceUntilIdle()

        assertEquals("No driving route found", viewModel.state.value.routeError)
    }

    @Test
    fun partial_weather_result_is_kept_and_full_weather_failure_retains_route() = runTest {
        val routeData = route(distanceMeters = 80_000.0)
        val weather = FakeRouteWeatherSource()
        val viewModel = viewModel(route = FakeRoutingSource(routeData), weather = weather)
        chooseEndpoints(viewModel)

        weather.result = listOf(
            RouteWeatherSample(
                point = routeData.polyline.first(),
                distanceMeters = 0.0,
                arrivalTime = now + 1.hours,
                condition = WeatherCondition.CLEAR
            )
        )
        viewModel.calculate()
        advanceUntilIdle()
        assertEquals(routeData, viewModel.state.value.result?.route)
        assertEquals(WeatherCondition.CLEAR, viewModel.state.value.result?.samples?.first()?.condition)
        assertNull(viewModel.state.value.weatherError)

        weather.failure = IllegalStateException("weather down")
        viewModel.retryWeather()
        advanceUntilIdle()
        assertEquals(routeData, viewModel.state.value.result?.route)
        assertEquals("Weather unavailable", viewModel.state.value.weatherError)
        assertFalse(viewModel.state.value.isRetryingWeather)
    }

    @Test
    fun initial_weather_failure_preserves_the_route_and_unenriched_samples() = runTest {
        val routeData = route(distanceMeters = 80_000.0)
        val weather = FakeRouteWeatherSource().also {
            it.failure = IllegalStateException("weather down")
        }
        val viewModel = viewModel(route = FakeRoutingSource(routeData), weather = weather)
        chooseEndpoints(viewModel)

        viewModel.calculate()
        advanceUntilIdle()

        assertEquals(routeData, viewModel.state.value.result?.route)
        assertEquals("Weather unavailable", viewModel.state.value.weatherError)
        assertEquals(
            buildRouteSamples(routeData, now + 1.hours, 80),
            viewModel.state.value.result?.samples
        )
        assertFalse(viewModel.state.value.isCalculating)
    }

    @Test
    fun failed_debounced_weather_refresh_keeps_raw_result_visible_and_current() = runTest {
        val weather = FakeRouteWeatherSource().also {
            it.hours = listOf(now + 1.hours, now + 2.hours)
        }
        val viewModel = viewModel(weather = weather)
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()

        weather.failure = IllegalStateException("weather down")
        viewModel.onDepartureOffsetChanged(24)
        advanceUntilIdle()

        assertEquals(route().polyline, viewModel.state.value.result?.route?.polyline)
        assertTrue(viewModel.state.value.result!!.samples.all { sample ->
            sample.condition == null && sample.temperatureCelsius == null && sample.windSpeedKmh == null
        })
        assertNull(viewModel.state.value.routeError)
        assertEquals("Weather unavailable", viewModel.state.value.weatherError)
        assertFalse(viewModel.state.value.isResultOutdated)
    }

    @Test
    fun in_horizon_rebuild_clears_a_stale_route_error_before_weather_refresh() = runTest {
        val weather = FakeRouteWeatherSource().also {
            it.hours = listOf(now + 1.hours, now + 2.hours)
        }
        val viewModel = viewModel(
            search = FakePlaceSearchSource(failure = IllegalStateException("search down")),
            weather = weather
        )
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()

        viewModel.onSearchQueryChanged(RouteEndpointSlot.START, "failed search")
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals("Place search failed", viewModel.state.value.routeError)

        viewModel.onDepartureOffsetChanged(1)

        assertNull(viewModel.state.value.routeError)
    }

    @Test
    fun successful_saved_map_and_gps_endpoint_selection_closes_active_search() = runTest {
        val viewModel = viewModel(
            deviceLocation = FakeDeviceLocationSource(GpsResult.Success(GeoPoint(46.0, 14.0), null))
        )

        viewModel.onSearchQueryChanged(RouteEndpointSlot.START, "search")
        viewModel.selectSavedLocation(RouteEndpointSlot.START, start)
        assertNull(viewModel.state.value.activeSearchSlot)

        viewModel.onSearchQueryChanged(RouteEndpointSlot.END, "search")
        viewModel.selectMapEndpoint(RouteEndpointSlot.END, RouteEndpoint("Map point", endPoint))
        assertNull(viewModel.state.value.activeSearchSlot)

        viewModel.onSearchQueryChanged(RouteEndpointSlot.START, "search")
        viewModel.selectCurrentLocation(RouteEndpointSlot.START)
        advanceUntilIdle()
        assertNull(viewModel.state.value.activeSearchSlot)
    }

    @Test
    fun incomplete_weather_snapshot_keeps_route_but_marks_uncovered_samples_unavailable() = runTest {
        val weather = FakeRouteWeatherSource().also {
            it.hours = listOf(now + 1.hours)
        }
        val viewModel = viewModel(weather = weather)
        chooseEndpoints(viewModel)

        viewModel.calculate()
        advanceUntilIdle()

        assertEquals(route().distanceMeters, viewModel.state.value.result?.route?.distanceMeters)
        assertEquals("Weather unavailable", viewModel.state.value.weatherError)
        assertNull(viewModel.state.value.snapshot)
        assertEquals(WeatherCondition.CLEAR, viewModel.state.value.result!!.samples.first().condition)
        assertTrue(viewModel.state.value.result!!.samples.drop(1).all { sample ->
            sample.condition == null && sample.temperatureCelsius == null && sample.windSpeedKmh == null
        })
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

        viewModel.onDepartureOffsetChanged(24)
        advanceUntilIdle()
        viewModel.retryWeather()
        advanceUntilIdle()
        assertEquals(1, route.calls)
        assertEquals(2, weather.requests)
        assertEquals(now + 24.hours, viewModel.state.value.result?.departure)

        viewModel.onSpeedChanged("90")
        assertTrue(viewModel.state.value.isResultOutdated)
    }

    @Test
    fun slider_reuses_snapshot_without_fetching_weather_again() = runTest {
        val departure = now + 1.hours
        val weather = FakeRouteWeatherSource()
        val viewModel = viewModel(weather = weather)
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()

        viewModel.onDepartureOffsetChanged(24)
        assertEquals(1, weather.requests)
        advanceUntilIdle()

        assertEquals(24, viewModel.state.value.departureOffsetHours)
        assertEquals(1, weather.requests)
        assertEquals(now + 24.hours, viewModel.state.value.result!!.departure)
    }

    @Test
    fun slider_sampling_uses_average_speed() = runTest {
        val route = route().copy(providerDurationSeconds = 0.0)
        val viewModel = viewModel(route = FakeRoutingSource(route))
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()

        viewModel.onDepartureOffsetChanged(24)

        assertNull(viewModel.state.value.routeError)
    }

    @Test
    fun weather_retry_uses_average_speed() = runTest {
        val route = route().copy(providerDurationSeconds = 0.0)
        val viewModel = viewModel(route = FakeRoutingSource(route))
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()

        viewModel.retryWeather()
        advanceUntilIdle()

        assertNull(viewModel.state.value.weatherError)
        assertFalse(viewModel.state.value.isRetryingWeather)
    }

    @Test
    fun weather_retry_preserves_an_already_outdated_result() = runTest {
        val viewModel = viewModel()
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()
        viewModel.onSpeedChanged("90")

        viewModel.retryWeather()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isResultOutdated)
    }

    @Test
    fun departure_offset_is_clamped_to_the_supported_range() = runTest {
        val viewModel = viewModel()

        viewModel.onDepartureOffsetChanged(-1)
        assertEquals(0, viewModel.state.value.departureOffsetHours)
        viewModel.onDepartureOffsetChanged(100)

        assertEquals(72, viewModel.state.value.departureOffsetHours)
    }

    @Test
    fun slider_updates_selected_departure_before_debounced_weather_refresh() = runTest {
        val viewModel = viewModel()
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()

        viewModel.onDepartureOffsetChanged(24)

        assertEquals(now + 24.hours, viewModel.state.value.result!!.departure)
    }

    @Test
    fun slider_offset_at_72_hours_is_rejected_at_the_forecast_horizon_without_refresh() = runTest {
        val weather = FakeRouteWeatherSource()
        val viewModel = viewModel(
            route = FakeRoutingSource(route(distanceMeters = 25_000_000.0)),
            weather = weather
        )
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()
        assertEquals(1, weather.requests)

        viewModel.onDepartureOffsetChanged(72)
        advanceUntilIdle()

        assertEquals("Arrival is outside the forecast range", viewModel.state.value.routeError)
        assertEquals(1, weather.requests)
        assertTrue(viewModel.state.value.result!!.samples.all { sample ->
            sample.condition == null && sample.temperatureCelsius == null && sample.windSpeedKmh == null
        })
    }

    @Test
    fun valid_slider_recalculation_clears_stale_forecast_range_error() = runTest {
        val weather = FakeRouteWeatherSource()
        val viewModel = viewModel(
            route = FakeRoutingSource(route(distanceMeters = 25_000_000.0)),
            weather = weather
        )
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()
        viewModel.onDepartureOffsetChanged(72)
        advanceUntilIdle()
        assertEquals("Arrival is outside the forecast range", viewModel.state.value.routeError)

        viewModel.onDepartureOffsetChanged(24)
        advanceUntilIdle()

        assertNull(viewModel.state.value.routeError)
        assertNull(viewModel.state.value.weatherError)
    }

    @Test
    fun out_of_horizon_weather_retry_does_not_fetch() = runTest {
        val weather = FakeRouteWeatherSource()
        val viewModel = viewModel(
            route = FakeRoutingSource(route(distanceMeters = 25_000_000.0)),
            weather = weather
        )
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()
        viewModel.onDepartureOffsetChanged(72)
        advanceUntilIdle()
        assertEquals("Arrival is outside the forecast range", viewModel.state.value.routeError)
        assertEquals(1, weather.requests)

        viewModel.retryWeather()
        advanceUntilIdle()

        assertEquals(1, weather.requests)
        assertEquals("Arrival is outside the forecast range", viewModel.state.value.routeError)
        assertFalse(viewModel.state.value.isRetryingWeather)
    }

    @Test
    fun missing_snapshot_coverage_causes_one_debounced_fetch() = runTest {
        val weather = FakeRouteWeatherSource().also {
            it.hours = listOf(now + 1.hours, now + 2.hours)
        }
        val viewModel = viewModel(weather = weather)
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()

        viewModel.onDepartureOffsetChanged(24)
        assertTrue(viewModel.state.value.isResultOutdated)
        advanceTimeBy(299)
        assertEquals(1, weather.requests)
        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(2, weather.requests)
        assertFalse(viewModel.state.value.isResultOutdated)
    }

    @Test
    fun rapid_slider_changes_cancel_obsolete_refresh_work() = runTest {
        val weather = CancellingRouteWeatherSource()
        val viewModel = viewModel(weather = weather)
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()

        viewModel.onDepartureOffsetChanged(72)
        advanceTimeBy(300)
        runCurrent()
        viewModel.onDepartureOffsetChanged(48)
        advanceUntilIdle()

        assertTrue(weather.refreshCancellationObserved)
    }

    @Test
    fun calculation_replaces_the_snapshot_and_derived_state() = runTest {
        val first = route(distanceMeters = 80_000.0)
        val second = route(distanceMeters = 90_000.0).copy(polyline = listOf(startPoint, GeoPoint(44.0, 20.0)))
        val viewModel = viewModel(
            route = SequencedRoutingSource(
                listOf(
                    CompletableDeferred<DrivingRoute>().also { it.complete(first) },
                    CompletableDeferred<DrivingRoute>().also { it.complete(second) }
                )
            )
        )
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()
        viewModel.calculate()
        advanceUntilIdle()

        assertEquals(viewModel.state.value.result!!.samples.map { it.point }, viewModel.state.value.snapshot!!.points)
        assertEquals(second, viewModel.state.value.result!!.route)
        assertEquals(viewModel.state.value.result!!.samples.first().temperatureCelsius,
            viewModel.state.value.summary!!.departureTemperatureCelsius)
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
    fun endpoint_speed_and_search_changes_invalidate_route_work() = runTest {
        val pendingRequests = List(3) { CompletableDeferred<DrivingRoute>() }
        val route = SequencedRoutingSource(
            listOf(CompletableDeferred<DrivingRoute>().also { it.complete(route()) }) + pendingRequests
        )
        val viewModel = viewModel(route = route)
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()

        val changes = listOf<(WeatherRouteViewModel) -> Unit>(
            { it.selectSavedLocation(RouteEndpointSlot.START, start.copy(name = "Changed start")) },
            { it.onSpeedChanged("90") },
            { it.onSearchQueryChanged(RouteEndpointSlot.END, "new query") }
        )
        changes.forEachIndexed { index, change ->
            viewModel.calculate()
            assertTrue(viewModel.state.value.isCalculating)

            change(viewModel)

            assertFalse(viewModel.state.value.isCalculating)
            assertTrue(viewModel.state.value.isResultOutdated)
            pendingRequests[index].complete(route(distanceMeters = 90_000.0 + index))
            advanceUntilIdle()
            assertTrue(viewModel.state.value.isResultOutdated)
        }
    }

    @Test
    fun input_change_cancels_in_flight_route_request() = runTest {
        val cancellationObserved = CompletableDeferred<CancellationException>()
        val viewModel = viewModel(route = CancellingRoutingSource(cancellationObserved))
        chooseEndpoints(viewModel)

        viewModel.calculate()
        runCurrent()
        viewModel.onSpeedChanged("90")
        advanceUntilIdle()

        assertTrue(cancellationObserved.isCompleted)
    }

    @Test
    fun input_change_cancels_in_flight_weather_retry() = runTest {
        val weather = CancellingWeatherSource()
        val viewModel = viewModel(route = FakeRoutingSource(route()), weather = weather)
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()

        viewModel.retryWeather()
        runCurrent()
        viewModel.onSpeedChanged("90")
        advanceUntilIdle()

        assertTrue(weather.retryCancellationObserved)
    }

    @Test
    fun search_failure_uses_a_stable_message() = runTest {
        val viewModel = viewModel(
            search = FakePlaceSearchSource(failure = IllegalStateException("provider details"))
        )

        viewModel.onSearchQueryChanged(RouteEndpointSlot.START, "trieste")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals("Place search failed", viewModel.state.value.routeError)
    }

    @Test
    fun known_weather_failure_is_mapped_to_a_stable_message() = runTest {
        val weather = FakeRouteWeatherSource().also {
            it.failure = OpenMeteoApiException("HTTP 503 response body")
        }
        val viewModel = viewModel(route = FakeRoutingSource(route()), weather = weather)
        chooseEndpoints(viewModel)

        viewModel.calculate()
        advanceUntilIdle()

        assertEquals("Weather service unavailable", viewModel.state.value.weatherError)
    }

    @Test
    fun stale_weather_completion_cannot_replace_result_or_clear_outdated_state() = runTest {
        val weatherRequest = CompletableDeferred<List<RouteWeatherSample>>()
        val weather = DeferredRouteWeatherSource(weatherRequest)
        val viewModel = viewModel(route = FakeRoutingSource(route()), weather = weather)
        chooseEndpoints(viewModel)

        viewModel.calculate()
        advanceUntilIdle()
        assertEquals(1, weather.requests)

        viewModel.calculate()
        viewModel.onSpeedChanged("90")
        assertTrue(viewModel.state.value.isResultOutdated)
        assertFalse(viewModel.state.value.isCalculating)
        weatherRequest.complete(listOf(RouteWeatherSample(startPoint, 0.0, now)))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isResultOutdated)
        assertEquals(now + 1.hours, viewModel.state.value.result?.departure)
        assertFalse(viewModel.state.value.isCalculating)
    }

    @Test
    fun superseded_weather_retry_clears_retrying_flag_and_cannot_publish() = runTest {
        val retryRequest = CompletableDeferred<List<RouteWeatherSample>>()
        val weather = DeferredRouteWeatherSource(retryRequest)
        val viewModel = viewModel(route = FakeRoutingSource(route()), weather = weather)
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()

        viewModel.retryWeather()
        assertTrue(viewModel.state.value.isRetryingWeather)
        viewModel.onSpeedChanged("90")
        assertFalse(viewModel.state.value.isRetryingWeather)
        assertTrue(viewModel.state.value.isResultOutdated)

        retryRequest.complete(listOf(RouteWeatherSample(endPoint, 80_000.0, now)))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isRetryingWeather)
        assertTrue(viewModel.state.value.isResultOutdated)
        assertEquals(80, viewModel.state.value.result?.averageSpeedKmh)
    }

    @Test
    fun retrying_weather_replaces_calculation_and_clears_calculating_flag() = runTest {
        val routeRequest = CompletableDeferred<DrivingRoute>()
        val routing = SequencedRoutingSource(
            listOf(CompletableDeferred<DrivingRoute>().also { it.complete(route()) }, routeRequest)
        )
        val retryRequest = CompletableDeferred<List<RouteWeatherSample>>()
        val weather = DeferredRouteWeatherSource(retryRequest)
        val viewModel = viewModel(route = routing, weather = weather)
        chooseEndpoints(viewModel)
        viewModel.calculate()
        advanceUntilIdle()

        viewModel.calculate()
        assertTrue(viewModel.state.value.isCalculating)
        viewModel.retryWeather()
        assertFalse(viewModel.state.value.isCalculating)
        assertTrue(viewModel.state.value.isRetryingWeather)
        routeRequest.complete(route(distanceMeters = 90_000.0))
        retryRequest.complete(listOf(RouteWeatherSample(endPoint, 80_000.0, now)))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isCalculating)
        assertFalse(viewModel.state.value.isRetryingWeather)
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
    fun cancellation_is_propagated_to_the_route_source() = runTest {
        val cancellationObserved = CompletableDeferred<CancellationException>()
        val viewModel = viewModel(route = BlockingRoutingSource(cancellationObserved))
        chooseEndpoints(viewModel)

        viewModel.calculate()
        runCurrent()
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()

        assertTrue(cancellationObserved.isCompleted)
        assertFalse(viewModel.state.value.isCalculating)
        assertNull(viewModel.state.value.routeError)
    }

    @Test
    fun flat_map_latest_cancels_the_previous_search() = runTest {
        val search = CancellingPlaceSearchSource(trieste)
        val viewModel = viewModel(search = search)

        viewModel.onSearchQueryChanged(RouteEndpointSlot.START, "first")
        advanceTimeBy(300)
        runCurrent()
        assertTrue(search.firstStarted)

        viewModel.onSearchQueryChanged(RouteEndpointSlot.START, "second")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertTrue(search.firstCancelled)
        assertEquals(listOf(trieste), viewModel.state.value.searchResults)
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
        viewModel.onDepartureOffsetChanged(1)
    }

    private fun viewModel(
        search: PlaceSearchSource = FakePlaceSearchSource(),
        route: RoutingSource = FakeRoutingSource(route()),
        weather: RouteWeatherSource = FakeRouteWeatherSource(),
        deviceLocation: DeviceLocationSource = FakeDeviceLocationSource(GpsResult.Unavailable)
    ) = WeatherRouteViewModel(
        routingSource = route,
        placeSearchSources = listOf(search),
        placeSearchProvider = PlaceSearchProvider.PHOTON,
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
    private val results: List<PlaceSearchResult> = emptyList(),
    private val failure: Throwable? = null
) : PlaceSearchSource {
    override val provider = PlaceSearchProvider.PHOTON

    override suspend fun search(query: String): List<PlaceSearchResult> {
        failure?.let { throw it }
        return results
    }
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

private class BlockingRoutingSource(
    private val cancellationObserved: CompletableDeferred<CancellationException>
) : RoutingSource {
    override val providerName = "OSRM"

    override suspend fun route(start: GeoPoint, end: GeoPoint): DrivingRoute {
        try {
            awaitCancellation()
            error("Route should have been cancelled")
        } catch (error: CancellationException) {
            cancellationObserved.complete(error)
            throw error
        }
    }
}

private class CancellingRoutingSource(
    private val cancellationObserved: CompletableDeferred<CancellationException>
) : RoutingSource {
    override val providerName = "OSRM"

    override suspend fun route(start: GeoPoint, end: GeoPoint): DrivingRoute {
        try {
            awaitCancellation()
            error("Route should have been cancelled")
        } catch (error: CancellationException) {
            cancellationObserved.complete(error)
            throw error
        }
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
    var hours: List<Instant> = (0..384).map { nowForTests + it.hours }
    var failure: Throwable? = null
    var requests = 0
        private set

    override suspend fun fetchSnapshot(points: List<GeoPoint>): RouteWeatherSnapshot {
        requests++
        failure?.let { throw it }
        val configured = result.orEmpty().groupBy { it.point }
        return RouteWeatherSnapshot(
            points = points,
            hourlyByPoint = points.map { point ->
                configured[point]?.map { it.toForecastHour() } ?: hours.map { time ->
                    RouteWeatherForecastHour(
                        time = time,
                        condition = WeatherCondition.CLEAR,
                        temperatureCelsius = null,
                        windSpeedKmh = null,
                        precipitationProbability = null,
                        precipitationMm = null,
                        humidityPercent = null,
                        isDay = null
                    )
                }
            },
            fetchedAt = nowForTests
        )
    }
}

private class DeferredRouteWeatherSource(
    private val retryResult: CompletableDeferred<List<RouteWeatherSample>>
) : RouteWeatherSource {
    var requests = 0
        private set

    override suspend fun fetchSnapshot(points: List<GeoPoint>): RouteWeatherSnapshot {
        requests++
        if (requests == 1) {
            return RouteWeatherSnapshot(
                points = points,
                hourlyByPoint = points.map { point ->
                    listOf(
                        RouteWeatherForecastHour(
                            time = nowForTests + 1.hours,
                            condition = WeatherCondition.CLEAR,
                            temperatureCelsius = null,
                            windSpeedKmh = null,
                            precipitationProbability = null,
                            precipitationMm = null,
                            humidityPercent = null,
                            isDay = null
                        ),
                        RouteWeatherForecastHour(
                            time = nowForTests + 2.hours,
                            condition = WeatherCondition.CLEAR,
                            temperatureCelsius = null,
                            windSpeedKmh = null,
                            precipitationProbability = null,
                            precipitationMm = null,
                            humidityPercent = null,
                            isDay = null
                        )
                    )
                },
                fetchedAt = nowForTests
            )
        }
        return snapshotFromSamples(points, retryResult.await())
    }
}

private class CancellingWeatherSource : RouteWeatherSource {
    var retryCancellationObserved = false
        private set
    private var requests = 0

    override suspend fun fetchSnapshot(points: List<GeoPoint>): RouteWeatherSnapshot {
        requests++
        if (requests == 1) {
            return RouteWeatherSnapshot(
                points = points,
                hourlyByPoint = points.map { listOf(forecastHour(nowForTests + 1.hours)) },
                fetchedAt = nowForTests
            )
        }
        try {
            awaitCancellation()
            error("Weather retry should have been cancelled")
        } catch (error: CancellationException) {
            retryCancellationObserved = true
            throw error
        }
    }
}

private class CancellingRouteWeatherSource : RouteWeatherSource {
    var refreshCancellationObserved = false
        private set
    private var requests = 0

    override suspend fun fetchSnapshot(points: List<GeoPoint>): RouteWeatherSnapshot {
        requests++
        if (requests == 1) {
            return RouteWeatherSnapshot(
                points = points,
                hourlyByPoint = points.map {
                    listOf(
                        forecastHour(nowForTests + 1.hours),
                        forecastHour(nowForTests + 2.hours)
                    )
                },
                fetchedAt = nowForTests
            )
        }
        try {
            awaitCancellation()
            error("Slider refresh should have been cancelled")
        } catch (error: CancellationException) {
            refreshCancellationObserved = true
            throw error
        }
    }
}

private fun RouteWeatherSample.toForecastHour() = RouteWeatherForecastHour(
    time = arrivalTime,
    condition = condition,
    temperatureCelsius = temperatureCelsius,
    windSpeedKmh = windSpeedKmh,
    precipitationProbability = precipitationProbability,
    precipitationMm = precipitationMm,
    humidityPercent = humidityPercent,
    isDay = isDay
)

private fun forecastHour(time: Instant) = RouteWeatherForecastHour(
    time = time,
    condition = WeatherCondition.CLEAR,
    temperatureCelsius = 20.0,
    windSpeedKmh = 10.0,
    precipitationProbability = 0,
    precipitationMm = 0.0,
    humidityPercent = 50,
    isDay = true
)

private fun snapshotFromSamples(
    points: List<GeoPoint>,
    samples: List<RouteWeatherSample>
) = RouteWeatherSnapshot(
    points = points,
    hourlyByPoint = points.map { point ->
        samples.filter { it.point == point }.map { it.toForecastHour() }
    },
    fetchedAt = nowForTests
)

private val nowForTests = Instant.parse("2026-09-12T09:15:00Z")

private class CancellingPlaceSearchSource(
    private val secondResult: PlaceSearchResult
) : PlaceSearchSource {
    var firstStarted = false
    var firstCancelled = false
    override val provider = PlaceSearchProvider.PHOTON

    override suspend fun search(query: String): List<PlaceSearchResult> = when (query) {
        "first" -> try {
            firstStarted = true
            awaitCancellation()
            emptyList()
        } catch (error: CancellationException) {
            firstCancelled = true
            throw error
        }
        else -> listOf(secondResult)
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
