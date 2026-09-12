package net.droopia.hluweather.data.repository

import kotlin.math.abs
import net.droopia.hluweather.data.model.RouteWeatherSample
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.network.OpenMeteoRouteResponse
import net.droopia.hluweather.data.network.OpenMeteoRouteWeatherApi

interface RouteWeatherSource {
    suspend fun enrich(samples: List<RouteWeatherSample>): List<RouteWeatherSample>
}

class OpenMeteoRouteWeatherSource(
    private val api: OpenMeteoRouteWeatherApi
) : RouteWeatherSource {
    override suspend fun enrich(samples: List<RouteWeatherSample>): List<RouteWeatherSample> {
        if (samples.isEmpty()) return emptyList()

        val responses = api.forecast(samples.map { it.point })
        return samples.mapIndexed { index, sample ->
            responses.getOrNull(index)?.enrich(sample) ?: sample.unavailable()
        }
    }
}

private fun OpenMeteoRouteResponse.enrich(sample: RouteWeatherSample): RouteWeatherSample {
    val hourly = hourly ?: return sample.unavailable()
    val targetNanos = sample.arrivalTime.epochSeconds * NANOS_PER_SECOND +
        sample.arrivalTime.nanosecondsOfSecond
    val entry = hourly.time.indices.mapNotNull { index ->
        val time = hourly.time.getOrNull(index) ?: return@mapNotNull null
        val temperature = hourly.temperature.getOrNull(index) ?: return@mapNotNull null
        val weatherCode = hourly.weatherCode.getOrNull(index) ?: return@mapNotNull null
        val windSpeed = hourly.windSpeed.getOrNull(index) ?: return@mapNotNull null
        val precipitationProbability = hourly.precipitationProbability.getOrNull(index)
            ?: return@mapNotNull null
        if (!temperature.isFinite() || !windSpeed.isFinite()) return@mapNotNull null
        RouteWeatherEntry(
            time = time,
            temperature = temperature,
            condition = weatherCode.toWeatherCondition(),
            windSpeed = windSpeed,
            precipitationProbability = precipitationProbability
        )
    }.minWithOrNull(
        compareBy<RouteWeatherEntry> {
            abs(it.time * NANOS_PER_SECOND - targetNanos)
        }.thenByDescending { it.time }
    ) ?: return sample.unavailable()

    return sample.copy(
        condition = entry.condition,
        temperatureCelsius = entry.temperature,
        windSpeedKmh = entry.windSpeed,
        precipitationProbability = entry.precipitationProbability
    )
}

private fun RouteWeatherSample.unavailable(): RouteWeatherSample = copy(
    condition = null,
    temperatureCelsius = null,
    windSpeedKmh = null,
    precipitationProbability = null
)

private data class RouteWeatherEntry(
    val time: Long,
    val temperature: Double,
    val condition: WeatherCondition,
    val windSpeed: Double,
    val precipitationProbability: Int
)

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

private const val NANOS_PER_SECOND = 1_000_000_000L
