package net.droopia.hluweather.data.repository

import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation

interface WeatherRepository {
    suspend fun getForecast(location: WeatherLocation): WeatherForecast
}
