package net.droopia.hluweather.data.repository

import kotlin.time.Clock
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation

class MockWeatherRepository(
    private val baseTime: Instant = Clock.System.now()
) : WeatherRepository {

    override suspend fun getForecast(location: WeatherLocation): WeatherForecast =
        buildMockForecast(location, baseTime)
}
