package net.droopia.hluweather.ui.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.MockWeatherRepository
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.data.repository.buildMockForecast
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.SettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
        val viewModel = WeatherViewModel(MockWeatherRepository())

        assertNotNull(viewModel.state.value.forecast)
        assertEquals(false, viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun changes_forecast_mode() {
        val viewModel = WeatherViewModel(MockWeatherRepository())

        viewModel.onForecastModeSelected(ForecastMode.DAILY)

        assertEquals(ForecastMode.DAILY, viewModel.state.value.forecastMode)
    }

    @Test
    fun day_selection_returns_to_hourly_mode() {
        val viewModel = WeatherViewModel(MockWeatherRepository())

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
                location: WeatherLocation
            ) = forecast
        }
        val viewModel = WeatherViewModel(repository)

        viewModel.onDaySelected(6)

        assertEquals(2, viewModel.state.value.selectedDayIndex)
    }

    @Test
    fun cancelled_forecast_request_does_not_set_an_error() {
        val repository = object : WeatherRepository {
            override suspend fun getForecast(
                provider: WeatherProvider,
                location: WeatherLocation
            ): Nothing =
                throw CancellationException("request cancelled")
        }

        val viewModel = WeatherViewModel(repository)

        assertNull(viewModel.state.value.error)
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

    private class RecordingWeatherRepository : WeatherRepository {
        val requests = mutableListOf<Request>()

        override suspend fun getForecast(
            provider: WeatherProvider,
            location: WeatherLocation
        ) = buildMockForecast(location, Instant.fromEpochSeconds(requests.size.toLong())).also {
            requests += Request(provider, location)
        }
    }

    private class DeferredWeatherRepository(
        private vararg val responses: Pair<WeatherLocation, CompletableDeferred<WeatherForecast>>
    ) : WeatherRepository {

        override suspend fun getForecast(
            provider: WeatherProvider,
            location: WeatherLocation
        ): WeatherForecast {
            return responses.first { it.first == location }.second.await()
        }
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

        suspend fun emitActive(value: ActiveLocation?) {
            active.emit(value)
        }

        override suspend fun add(location: WeatherLocation) = Unit
        override suspend fun update(location: WeatherLocation) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun selectSaved(id: String) = Unit
        override suspend fun setTrackMe(enabled: Boolean) = Unit
    }
}
