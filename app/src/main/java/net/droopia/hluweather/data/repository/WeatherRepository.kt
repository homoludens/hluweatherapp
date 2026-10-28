package net.droopia.hluweather.data.repository

import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider

data class ForecastLoad(
    val forecast: WeatherForecast,
    val isStale: Boolean = false
)

interface WeatherSource {
    val provider: WeatherProvider
    suspend fun getForecast(location: WeatherLocation): WeatherForecast
}

interface WeatherRepository {
    suspend fun getForecast(provider: WeatherProvider, location: ActiveLocation): ForecastLoad
    suspend fun getCachedForecast(
        provider: WeatherProvider,
        location: ActiveLocation
    ): ForecastLoad? = null
    /** Unlike the normal startup cache read, also permits old, matching forecasts. */
    suspend fun getLastCachedForecast(provider: WeatherProvider, location: ActiveLocation): ForecastLoad? =
        getCachedForecast(provider, location)
    suspend fun clearCache()
}
