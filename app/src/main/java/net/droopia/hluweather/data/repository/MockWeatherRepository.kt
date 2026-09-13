package net.droopia.hluweather.data.repository

import kotlin.time.Clock
import kotlin.time.Instant
import net.droopia.hluweather.data.model.ActiveLocation
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
        location: ActiveLocation
    ): ForecastLoad {
        val weatherLocation = location.toWeatherLocation()
        requests += WeatherRequest(provider, weatherLocation)
        return ForecastLoad(
            buildMockForecast(weatherLocation, baseTime).copy(provider = provider),
            isStale = false
        )
    }

    override suspend fun getCachedForecast(
        provider: WeatherProvider,
        location: ActiveLocation
    ): ForecastLoad? = null

    override suspend fun clearCache() = Unit
}

private fun ActiveLocation.toWeatherLocation(): WeatherLocation = when (this) {
    is ActiveLocation.Saved -> location
    is ActiveLocation.Current -> WeatherLocation(
        id = "current",
        name = "Current location",
        latitude = point.latitude,
        longitude = point.longitude,
        altitude = altitude
    )
}
