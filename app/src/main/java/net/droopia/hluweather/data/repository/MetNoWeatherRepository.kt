package net.droopia.hluweather.data.repository

import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.DateTimeUnit
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import net.droopia.hluweather.data.MoonPhaseCalculator
import net.droopia.hluweather.data.model.CurrentWeather
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.network.MetNoApi
import net.droopia.hluweather.data.network.MetNoResponse
import net.droopia.hluweather.data.network.MetNoTimeSeries

class MetNoWeatherRepository(
    private val api: MetNoApi,
    private val clock: Clock = Clock.System
) : WeatherSource {

    override val provider: WeatherProvider = WeatherProvider.MET_NO

    override suspend fun getForecast(location: WeatherLocation): WeatherForecast {
        val response = try {
            api.forecast(location)
        } catch (error: CancellationException) {
            throw error
        } catch (error: WeatherRepositoryException) {
            throw error
        } catch (error: Throwable) {
            throw WeatherRepositoryException("Unable to load weather data", error)
        }

        return try {
            response.toWeatherForecast(location, clock.now())
        } catch (error: CancellationException) {
            throw error
        } catch (error: WeatherRepositoryException) {
            throw error
        } catch (error: Throwable) {
            throw WeatherRepositoryException("Unable to map weather data", error)
        }
    }

}

private fun MetNoResponse.toWeatherForecast(
    location: WeatherLocation,
    fetchedAt: Instant
): WeatherForecast {
    val displayTimeZone = TimeZone.of("UTC")
    val today = fetchedAt.toLocalDateTime(displayTimeZone).date
    val lastDate = today.plus(7, DateTimeUnit.DAY)
    val normalizedHours = properties.timeseries
        .mapIndexed { index, timeSeries -> timeSeries.toNormalizedHour(index) }
    // MET's one-hour probability ends at timestamp + 1h. Key by that instant so
    // sparse series cannot shift it to a later row.
    val probabilityByHourEnding = normalizedHours.mapNotNull { hour ->
        hour.oneHourProbability?.let { probability ->
            (hour.forecast.time + 1.hours) to probability
        }
    }.toMap()
    val hourly = normalizedHours
        .map { hour ->
            hour.copy(
                forecast = hour.forecast.copy(
                    precipitationProbability = probabilityByHourEnding[hour.forecast.time]
                )
            )
        }
        .filter { hour ->
            val date = hour.forecast.time.toLocalDateTime(displayTimeZone).date
            date >= today && date < lastDate
        }

    if (hourly.isEmpty()) {
        throw WeatherRepositoryException("Missing usable MET Norway timeseries data")
    }

    val current = hourly.first().toCurrentWeather()
    return WeatherForecast(
        location = location,
        provider = WeatherProvider.MET_NO,
        fetchedAt = fetchedAt,
        current = current,
        hourly = hourly.map { it.forecast },
        daily = emptyList(),
        moonPhase = MoonPhaseCalculator.phase(fetchedAt),
        timezone = displayTimeZone.id,
        timezoneIsUtcFallback = true
    )
}

private data class NormalizedHour(
    val forecast: HourForecast,
    val precipitation: Double?,
    val oneHourProbability: Int?
)

private fun MetNoTimeSeries.toNormalizedHour(index: Int): NormalizedHour {
    val timestamp = try {
        Instant.parse(time.required("timeseries[$index].time"))
    } catch (error: Throwable) {
        if (error is WeatherRepositoryException) throw error
        throw WeatherRepositoryException("Invalid timestamp: $time", error)
    }
    val details = (data?.instant?.details).required("timeseries[$index].data.instant.details")
    val temperature = details.airTemperature.required(
        "timeseries[$index].data.instant.details.air_temperature"
    )
    val period = data?.next1Hours ?: data?.next6Hours
    val symbol = period?.summary?.symbolCode.toWeatherSymbol()
    val precipitation = period?.details?.precipitationAmount
    val oneHourProbability = data?.next1Hours?.details?.probabilityOfPrecipitation
        ?.takeIf { it.isFinite() && it in 0.0..100.0 }
        ?.roundToInt()
    return NormalizedHour(
        forecast = HourForecast(
            time = timestamp,
            temperature = temperature,
            apparentTemperature = null,
            humidity = details.relativeHumidity?.roundToInt(),
            dewPoint = details.dewPoint,
            precipitation = precipitation,
            precipitationProbability = null,
            condition = symbol.condition,
            isDay = symbol.isDay,
            windSpeedKmh = details.windSpeed?.times(3.6),
            windDirectionDegrees = details.windDirection
        ),
        precipitation = precipitation,
        oneHourProbability = oneHourProbability
    )
}

private fun NormalizedHour.toCurrentWeather(): CurrentWeather = CurrentWeather(
    temperature = forecast.temperature,
    apparentTemperature = forecast.apparentTemperature,
    humidity = forecast.humidity,
    dewPoint = forecast.dewPoint,
    precipitation = precipitation,
    condition = forecast.condition,
    isDay = forecast.isDay
)

/* The normalized wrapper preserves the provider's distinction between zero
 * precipitation and an unavailable amount for current conditions. */

private data class WeatherSymbol(
    val condition: WeatherCondition,
    val isDay: Boolean?
)

private fun String?.toWeatherSymbol(): WeatherSymbol {
    if (this == null) return WeatherSymbol(WeatherCondition.UNKNOWN, null)
    val normalized = lowercase()
    val isDay = when {
        normalized.endsWith("_day") -> true
        normalized.endsWith("_night") -> false
        normalized.endsWith("_polartwilight") -> null
        else -> null
    }
    val family = normalized.removeSuffix("_day")
        .removeSuffix("_night")
        .removeSuffix("_polartwilight")
    val condition = when {
        family == "clearsky" -> WeatherCondition.CLEAR
        family.startsWith("fair") -> WeatherCondition.MOSTLY_CLEAR
        family.startsWith("partlycloudy") -> WeatherCondition.PARTLY_CLOUDY
        family == "cloudy" -> WeatherCondition.CLOUDY
        family.startsWith("fog") -> WeatherCondition.FOG
        family.contains("thunder") -> WeatherCondition.THUNDERSTORM
        family.contains("snow") || family.contains("sleet") -> WeatherCondition.SNOW
        family.contains("rain") -> WeatherCondition.RAIN
        else -> WeatherCondition.UNKNOWN
    }
    return WeatherSymbol(condition, isDay)
}

private fun <T> T?.required(field: String): T = this
    ?: throw WeatherRepositoryException("Missing required value: $field")
