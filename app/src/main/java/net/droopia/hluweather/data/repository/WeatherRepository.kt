package net.droopia.hluweather.data.repository

import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider

interface WeatherRepository {
    suspend fun getForecast(provider: WeatherProvider, location: WeatherLocation): WeatherForecast
}
