package net.droopia.hluweather.data.repository

import kotlin.time.Clock
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider

data class WeatherRequest(
    val provider: WeatherProvider,
    val location: WeatherLocation
)

class MockWeatherRepository(
    private val baseTime: Instant = Clock.System.now()
) : WeatherRepository {

    val requests = mutableListOf<WeatherRequest>()

    override suspend fun getForecast(
        provider: WeatherProvider,
        location: WeatherLocation
    ): WeatherForecast {
        requests += WeatherRequest(provider, location)
        return buildMockForecast(location, baseTime)
    }
}
