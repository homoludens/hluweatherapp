package net.droopia.hluweather.ui.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.repository.MockWeatherRepository
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.data.repository.buildMockForecast
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
            override suspend fun getForecast(location: WeatherLocation) = forecast
        }
        val viewModel = WeatherViewModel(repository)

        viewModel.onDaySelected(6)

        assertEquals(2, viewModel.state.value.selectedDayIndex)
    }

    @Test
    fun cancelled_forecast_request_does_not_set_an_error() {
        val repository = object : WeatherRepository {
            override suspend fun getForecast(location: WeatherLocation): Nothing =
                throw CancellationException("request cancelled")
        }

        val viewModel = WeatherViewModel(repository)

        assertNull(viewModel.state.value.error)
    }
}
