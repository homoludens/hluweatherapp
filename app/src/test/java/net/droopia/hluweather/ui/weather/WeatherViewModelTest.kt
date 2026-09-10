package net.droopia.hluweather.ui.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.GpsResult
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.MockWeatherRepository
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.data.repository.ForecastLoad
import net.droopia.hluweather.data.device.DeviceLocationSource
import net.droopia.hluweather.data.repository.buildMockForecast
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.SettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherViewModelTest {

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loads_mock_forecast_for_default_location() {
        val viewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)

        assertNotNull(viewModel.state.value.forecast)
        assertEquals(false, viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun changes_forecast_mode() {
        val viewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)

        viewModel.onForecastModeSelected(ForecastMode.DAILY)

        assertEquals(ForecastMode.DAILY, viewModel.state.value.forecastMode)
    }

    @Test
    fun day_selection_returns_to_hourly_mode() {
        val viewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)

        viewModel.onForecastModeSelected(ForecastMode.DAILY)
        viewModel.onDaySelected(2)

        assertEquals(ForecastMode.HOURLY, viewModel.state.value.forecastMode)
        assertEquals(2, viewModel.state.value.selectedDayIndex)
    }

    @Test
    fun day_selection_is_limited_by_available_daily_forecast() {
        val forecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        ).let { it.copy(daily = it.daily.take(3)) }
        val repository = object : WeatherRepository {
            override suspend fun getForecast(
                provider: WeatherProvider,
                location: ActiveLocation
            ) = ForecastLoad(forecast)

            override suspend fun clearCache() = Unit
        }
        val viewModel = WeatherViewModel(repository, Svilajnac)

        viewModel.onDaySelected(6)

        assertEquals(2, viewModel.state.value.selectedDayIndex)
    }

    @Test
    fun cancelled_forecast_request_does_not_set_an_error() {
        val repository = object : WeatherRepository {
            override suspend fun getForecast(
                provider: WeatherProvider,
                location: ActiveLocation
            ): Nothing =
                throw CancellationException("request cancelled")

            override suspend fun clearCache() = Unit
        }

        val viewModel = WeatherViewModel(repository, Svilajnac)

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun stale_forecast_load_sets_stale_state_without_an_error() {
        val repository = object : WeatherRepository {
            override suspend fun getForecast(
                provider: WeatherProvider,
                location: ActiveLocation
            ) = ForecastLoad(buildMockForecast(Svilajnac), isStale = true)

            override suspend fun clearCache() = Unit
        }

        val viewModel = WeatherViewModel(repository, Svilajnac)

        assertTrue(viewModel.state.value.isStale)
        assertNotNull(viewModel.state.value.forecast)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun retry_starts_a_new_forecast_request() = runTest {
        val repository = RecordingWeatherRepository()
        val viewModel = WeatherViewModel(repository, Svilajnac)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2, repository.requests.size)
    }

    @Test
    fun provider_change_refetches_for_the_same_location() = runTest {
        val settings = TestSettingsRepository()
        val locations = TestLocationRepository(ActiveLocation.Saved(Svilajnac))
        val repository = RecordingWeatherRepository()
        WeatherViewModel(repository, settings, locations)

        settings.emit(provider = WeatherProvider.MET_NO)
        advanceUntilIdle()

        assertEquals(WeatherProvider.MET_NO, repository.requests.last().provider)
        assertEquals(Svilajnac, repository.requests.last().location)
    }

    @Test
    fun edited_saved_location_with_the_same_id_refetches_weather() = runTest {
        val repository = RecordingWeatherRepository()
        val locations = TestLocationRepository(ActiveLocation.Saved(Svilajnac))
        val viewModel = WeatherViewModel(repository, TestSettingsRepository(), locations)
        advanceUntilIdle()

        val editedLocation = Svilajnac.copy(
            latitude = Svilajnac.latitude + 0.1,
            longitude = Svilajnac.longitude + 0.1,
            altitude = Svilajnac.altitude!! + 1
        )
        locations.emitActive(ActiveLocation.Saved(editedLocation))
        advanceUntilIdle()

        assertEquals(2, repository.requests.size)
        assertEquals(editedLocation, repository.requests.last().location)
        assertEquals(editedLocation, viewModel.state.value.activeLocation)
    }

    @Test
    fun no_active_location_does_not_request_weather() = runTest {
        val repository = RecordingWeatherRepository()
        val viewModel = WeatherViewModel(
            repository,
            TestSettingsRepository(),
            TestLocationRepository(null)
        )

        advanceUntilIdle()

        assertTrue(repository.requests.isEmpty())
        assertNull(viewModel.state.value.forecast)
    }

    @Test
    fun obsolete_location_result_cannot_replace_newer_location_result() = runTest {
        val firstLocation = Svilajnac
        val secondLocation = WeatherLocation("belgrade", "Belgrade", 44.81, 20.46)
        val firstResult = CompletableDeferred<WeatherForecast>()
        val secondResult = CompletableDeferred<WeatherForecast>()
        val repository = DeferredWeatherRepository(
            firstLocation to firstResult,
            secondLocation to secondResult
        )
        val locations = TestLocationRepository(ActiveLocation.Saved(firstLocation))
        val viewModel = WeatherViewModel(repository, TestSettingsRepository(), locations)
        advanceUntilIdle()

        locations.emitActive(ActiveLocation.Saved(secondLocation))
        advanceUntilIdle()
        firstResult.complete(buildMockForecast(firstLocation, Instant.fromEpochSeconds(1L)))
        secondResult.complete(buildMockForecast(secondLocation, Instant.fromEpochSeconds(2L)))
        advanceUntilIdle()

        assertEquals(secondLocation, viewModel.state.value.forecast?.location)
    }

    @Test
    fun obsolete_same_location_provider_result_cannot_update_state() = runTest {
        val oldForecast = buildMockForecast(Svilajnac, Instant.fromEpochSeconds(1L))
        val newForecast = buildMockForecast(Svilajnac, Instant.fromEpochSeconds(2L))
        val firstRequest = PendingRequest(
            onCancellation = Result.success(oldForecast)
        )
        val secondRequest = PendingRequest()
        val repository = CancellationAwareWeatherRepository(firstRequest, secondRequest)
        val settings = TestSettingsRepository()
        val viewModel = WeatherViewModel(
            repository,
            settings,
            TestLocationRepository(ActiveLocation.Saved(Svilajnac))
        )
        val observed = mutableListOf<WeatherUiState>()
        val observer = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect(observed::add) }
        advanceUntilIdle()
        observed.clear()

        settings.emit(provider = WeatherProvider.MET_NO)
        advanceUntilIdle()

        assertFalse(observed.any { it.forecast == oldForecast })
        secondRequest.result.complete(newForecast)
        advanceUntilIdle()
        observer.cancel()
        assertEquals(newForecast, viewModel.state.value.forecast)
    }

    @Test
    fun obsolete_refresh_result_cannot_update_state() = runTest {
        val oldForecast = buildMockForecast(Svilajnac, Instant.fromEpochSeconds(1L))
        val newForecast = buildMockForecast(Svilajnac, Instant.fromEpochSeconds(2L))
        val firstRequest = PendingRequest(
            onCancellation = Result.success(oldForecast)
        )
        val secondRequest = PendingRequest()
        val repository = CancellationAwareWeatherRepository(firstRequest, secondRequest)
        val viewModel = WeatherViewModel(
            repository,
            TestSettingsRepository(),
            TestLocationRepository(ActiveLocation.Saved(Svilajnac))
        )
        val observed = mutableListOf<WeatherUiState>()
        val observer = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect(observed::add)
        }
        advanceUntilIdle()
        observed.clear()

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(observed.any { it.forecast == oldForecast })
        secondRequest.result.complete(newForecast)
        advanceUntilIdle()
        observer.cancel()
        assertEquals(newForecast, viewModel.state.value.forecast)
    }

    @Test
    fun obsolete_same_location_failure_cannot_update_state() = runTest {
        val secondRequest = PendingRequest()
        val repository = CancellationAwareWeatherRepository(
            PendingRequest(
                onCancellation = Result.failure(IllegalStateException("stale failure"))
            ),
            secondRequest
        )
        val settings = TestSettingsRepository()
        val viewModel = WeatherViewModel(
            repository,
            settings,
            TestLocationRepository(ActiveLocation.Saved(Svilajnac))
        )
        val observed = mutableListOf<WeatherUiState>()
        val observer = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect(observed::add)
        }
        advanceUntilIdle()
        observed.clear()

        settings.emit(provider = WeatherProvider.MET_NO)
        advanceUntilIdle()

        assertFalse(observed.any { it.error == "stale failure" })
        secondRequest.result.complete(buildMockForecast(Svilajnac, Instant.fromEpochSeconds(2L)))
        advanceUntilIdle()
        observer.cancel()
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun cancellation_swallowing_stale_request_cannot_restore_weather_after_active_location_is_cleared() = runTest {
        val staleForecast = buildMockForecast(Svilajnac, Instant.fromEpochSeconds(1L))
        val firstRequest = PendingRequest(onCancellation = Result.success(staleForecast))
        val repository = CancellationAwareWeatherRepository(firstRequest)
        val locations = TestLocationRepository(ActiveLocation.Saved(Svilajnac))
        val viewModel = WeatherViewModel(repository, TestSettingsRepository(), locations)
        advanceUntilIdle()

        locations.emitActive(null)
        advanceUntilIdle()

        assertNull(viewModel.state.value.activeLocation)
        assertNull(viewModel.state.value.forecast)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun track_me_updates_the_display_but_refreshes_only_after_the_gate() = runTest {
        val source = TestDeviceLocationSource()
        val locations = TestLocationRepository(null)
        locations.mode.value = LocationMode.TRACK_ME
        val repository = RecordingWeatherRepository()
        val viewModel = WeatherViewModel(
            repository,
            TestSettingsRepository(),
            locations,
            source,
            now = { Instant.fromEpochSeconds(1_000L) }
        )
        val tracking = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.trackMeWhileStarted()
        }
        val first = GeoPoint(44.8176, 20.4633)
        val nearby = GeoPoint(44.8266, 20.4633)
        val farAway = GeoPoint(44.8650, 20.4633)

        source.emit(GpsResult.Success(first, null))
        advanceUntilIdle()
        source.emit(GpsResult.Success(nearby, null))
        advanceUntilIdle()

        assertEquals(nearby.latitude, viewModel.state.value.activeLocation?.latitude)
        assertEquals(1, repository.requests.size)

        source.emit(GpsResult.Success(farAway, null))
        advanceUntilIdle()

        assertEquals(farAway.latitude, viewModel.state.value.activeLocation?.latitude)
        assertEquals(2, repository.requests.size)
        tracking.cancel()
    }

    @Test
    fun stale_track_me_result_does_not_advance_live_refresh_gate() = runTest {
        val source = TestDeviceLocationSource()
        val locations = TestLocationRepository(null)
        locations.mode.value = LocationMode.TRACK_ME
        val repository = RecordingWeatherRepository(stale = true)
        val viewModel = WeatherViewModel(
            repository,
            TestSettingsRepository(),
            locations,
            source,
            now = { Instant.fromEpochSeconds(1_000L) }
        )
        val tracking = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.trackMeWhileStarted()
        }
        val first = GeoPoint(44.8176, 20.4633)
        val nearby = GeoPoint(44.8266, 20.4633)

        source.emit(GpsResult.Success(first, null))
        advanceUntilIdle()
        source.emit(GpsResult.Success(nearby, null))
        advanceUntilIdle()

        assertEquals(2, repository.requests.size)
        tracking.cancel()
    }

    @Test
    fun track_me_routes_permission_and_disabled_statuses() = runTest {
        val source = TestDeviceLocationSource()
        val viewModel = WeatherViewModel(
            RecordingWeatherRepository(),
            TestSettingsRepository(),
            TestLocationRepository(null),
            source
        )
        val tracking = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.trackMeWhileStarted()
        }

        source.emit(GpsResult.PermissionRequired)
        advanceUntilIdle()
        assertEquals(TrackMeStatus.PermissionRequired, viewModel.state.value.trackMeStatus)
        source.emit(GpsResult.LocationDisabled)
        advanceUntilIdle()
        assertEquals(TrackMeStatus.LocationDisabled, viewModel.state.value.trackMeStatus)
        tracking.cancel()
    }

    @Test
    fun reentering_track_me_requires_a_fresh_fix() = runTest {
        val currentPoint = GeoPoint(44.8176, 20.4633)
        val savedLocation = WeatherLocation("saved", "Saved", 45.0, 22.0)
        val locations = TestLocationRepository(null).also { it.mode.value = LocationMode.TRACK_ME }
        val repository = RecordingWeatherRepository()
        val viewModel = WeatherViewModel(
            repository,
            TestSettingsRepository(),
            locations,
            now = { Instant.fromEpochSeconds(1_000L) }
        )

        locations.emitActive(ActiveLocation.Current(currentPoint))
        advanceUntilIdle()
        repository.requests.clear()
        locations.mode.emit(LocationMode.SAVED_LOCATION)
        locations.emitActive(ActiveLocation.Saved(savedLocation))
        advanceUntilIdle()
        repository.requests.clear()
        locations.mode.emit(LocationMode.TRACK_ME)
        locations.emitActive(null)
        advanceUntilIdle()
        locations.emitActive(ActiveLocation.Current(currentPoint))
        advanceUntilIdle()

        assertEquals(1, repository.requests.size)
        assertEquals(currentPoint.latitude, repository.requests.last().location.latitude, 0.0001)
        assertEquals(currentPoint.longitude, repository.requests.last().location.longitude, 0.0001)
        viewModel.clearTrackMeStatus()
    }

    private class RecordingWeatherRepository(
        private val stale: Boolean = false
    ) : WeatherRepository {
        val requests = mutableListOf<Request>()

        override suspend fun getForecast(
            provider: WeatherProvider,
            location: ActiveLocation
        ): ForecastLoad {
            val weatherLocation = location.toTestWeatherLocation()
            return ForecastLoad(
                buildMockForecast(weatherLocation, Instant.fromEpochSeconds(requests.size.toLong()))
                    .copy(provider = provider),
                isStale = stale
            ).also {
                requests += Request(provider, weatherLocation)
            }
        }

        override suspend fun clearCache() = Unit
    }

    private class DeferredWeatherRepository(
        private vararg val responses: Pair<WeatherLocation, CompletableDeferred<WeatherForecast>>
    ) : WeatherRepository {

        override suspend fun getForecast(
            provider: WeatherProvider,
            location: ActiveLocation
        ): ForecastLoad {
            return ForecastLoad(
                responses.first { it.first == location.toTestWeatherLocation() }.second.await()
            )
        }

        override suspend fun clearCache() = Unit
    }

    private class CancellationAwareWeatherRepository(
        private vararg val requests: PendingRequest
    ) : WeatherRepository {
        private var requestIndex = 0

        override suspend fun getForecast(
            provider: WeatherProvider,
            location: ActiveLocation
        ): ForecastLoad {
            val request = requests[requestIndex++]
            val forecast = try {
                request.result.await()
            } catch (error: CancellationException) {
                request.onCancellation?.let { outcome -> return ForecastLoad(outcome.getOrThrow()) }
                throw error
            }
            return ForecastLoad(forecast)
        }

        override suspend fun clearCache() = Unit
    }

    private class PendingRequest(
        val onCancellation: Result<WeatherForecast>? = null
    ) {
        val result = CompletableDeferred<WeatherForecast>()
    }

    private data class Request(
        val provider: WeatherProvider,
        val location: WeatherLocation
    )

    private class TestSettingsRepository(
        initial: PersistedSettings = PersistedSettings()
    ) : SettingsRepository {
        private val state = MutableStateFlow(initial)
        override val settings: StateFlow<PersistedSettings> = state

        suspend fun emit(provider: WeatherProvider) {
            state.emit(state.value.copy(provider = provider))
        }

        override suspend fun save(settings: PersistedSettings) {
            state.emit(settings)
        }
    }

    private class TestLocationRepository(
        initialActive: ActiveLocation?
    ) : LocationRepository {
        private val active = MutableStateFlow(initialActive)
        override val locations = MutableStateFlow(
            (initialActive as? ActiveLocation.Saved)?.let { listOf(it.location) } ?: emptyList()
        )
        override val activeLocation: StateFlow<ActiveLocation?> = active
        val mode = MutableStateFlow(LocationMode.SAVED_LOCATION)
        override val locationMode: StateFlow<LocationMode> = mode

        suspend fun emitActive(value: ActiveLocation?) {
            active.emit(value)
        }

        override suspend fun add(location: WeatherLocation) = Unit
        override suspend fun update(location: WeatherLocation) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun selectSaved(id: String) = Unit
        override suspend fun setTrackMe(enabled: Boolean) = Unit

        override suspend fun setCurrentLocation(point: GeoPoint, altitude: Int?) {
            active.value = ActiveLocation.Current(point, altitude)
        }
    }

    private class TestDeviceLocationSource : DeviceLocationSource {
        val updates = MutableSharedFlow<GpsResult>(extraBufferCapacity = 1)

        override suspend fun currentLocation(): GpsResult = GpsResult.Unavailable

        override fun foregroundLocations() = updates

        suspend fun emit(result: GpsResult) {
            updates.emit(result)
        }
    }

}

private fun ActiveLocation.toTestWeatherLocation(): WeatherLocation = when (this) {
    is ActiveLocation.Saved -> location
    is ActiveLocation.Current -> WeatherLocation(
        id = "current",
        name = "Current location",
        latitude = point.latitude,
        longitude = point.longitude,
        altitude = altitude
    )
}
