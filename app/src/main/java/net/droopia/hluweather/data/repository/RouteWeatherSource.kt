package net.droopia.hluweather.data.repository

import kotlinx.datetime.Instant
import kotlin.time.Clock
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.network.OpenMeteoRouteResponse
import net.droopia.hluweather.data.network.OpenMeteoRouteWeatherApi
import net.droopia.hluweather.data.weatherroute.RouteWeatherForecastHour
import net.droopia.hluweather.data.weatherroute.RouteWeatherSnapshot

interface RouteWeatherSource {
    suspend fun fetchSnapshot(points: List<GeoPoint>): RouteWeatherSnapshot
}

class OpenMeteoRouteWeatherSource(
    private val api: OpenMeteoRouteWeatherApi
) : RouteWeatherSource {
    override suspend fun fetchSnapshot(points: List<GeoPoint>): RouteWeatherSnapshot {
        if (points.isEmpty()) {
            return RouteWeatherSnapshot(
                points = emptyList(),
                hourlyByPoint = emptyList(),
                fetchedAt = Clock.System.now()
            )
        }

        val responses = api.forecast(points)
        return RouteWeatherSnapshot(
            points = points,
            hourlyByPoint = points.indices.map { index ->
                responses.getOrNull(index)?.toForecastHours().orEmpty()
            },
            fetchedAt = Clock.System.now()
        )
    }
}

private fun OpenMeteoRouteResponse.toForecastHours(): List<RouteWeatherForecastHour> {
    val hourly = hourly ?: return emptyList()
    return hourly.time.indices.mapNotNull { index ->
        val time = hourly.time.getOrNull(index) ?: return@mapNotNull null
        val temperature = hourly.temperature.getOrNull(index) ?: return@mapNotNull null
        val weatherCode = hourly.weatherCode.getOrNull(index) ?: return@mapNotNull null
        val windSpeed = hourly.windSpeed.getOrNull(index) ?: return@mapNotNull null
        val precipitationProbability = hourly.precipitationProbability.getOrNull(index)
            ?: return@mapNotNull null
        if (!temperature.isFinite() || !windSpeed.isFinite()) return@mapNotNull null

        RouteWeatherForecastHour(
            time = Instant.fromEpochSeconds(time),
            condition = weatherCode.toWeatherCondition(),
            temperatureCelsius = temperature,
            windSpeedKmh = windSpeed,
            precipitationProbability = precipitationProbability,
            precipitationMm = hourly.precipitation?.getOrNull(index),
            humidityPercent = hourly.humidity?.getOrNull(index),
            isDay = hourly.isDay?.getOrNull(index)?.let { it == 1 }
        )
    }
}

private fun Int.toWeatherCondition(): WeatherCondition = when (this) {
    0 -> WeatherCondition.CLEAR
    1 -> WeatherCondition.MOSTLY_CLEAR
    2 -> WeatherCondition.PARTLY_CLOUDY
    3 -> WeatherCondition.CLOUDY
    45, 48 -> WeatherCondition.FOG
    in 51..57 -> WeatherCondition.DRIZZLE
    in 61..67, in 80..82 -> WeatherCondition.RAIN
    in 71..77, in 85..86 -> WeatherCondition.SNOW
    95, 96, 99 -> WeatherCondition.THUNDERSTORM
    else -> WeatherCondition.UNKNOWN
}
