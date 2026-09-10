package net.droopia.hluweather.data.repository

import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import net.droopia.hluweather.data.MoonPhaseCalculator
import net.droopia.hluweather.data.model.CurrentWeather
import net.droopia.hluweather.data.model.DayForecast
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
    private val clock: Clock = Clock.System,
    private val displayTimeZone: TimeZone = TimeZone.currentSystemDefault()
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
            response.toWeatherForecast(location, clock.now(), displayTimeZone)
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
    fetchedAt: Instant,
    displayTimeZone: TimeZone
): WeatherForecast {
    val today = fetchedAt.toLocalDateTime(displayTimeZone).date
    val lastDate = today.plus(7, DateTimeUnit.DAY)
    val hourly = properties.timeseries
        .mapIndexed { index, timeSeries -> timeSeries.toNormalizedHour(index) }
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
        daily = hourly.toDailyForecasts(displayTimeZone),
        moonPhase = MoonPhaseCalculator.phase(fetchedAt),
        timezone = displayTimeZone.id
    )
}

private data class NormalizedHour(
    val forecast: HourForecast,
    val precipitation: Double?
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
    val nextHour = data?.next1Hours
    val symbol = nextHour?.summary?.symbolCode.toWeatherSymbol()
    val precipitation = nextHour?.details?.precipitationAmount
    return NormalizedHour(
        forecast = HourForecast(
            time = timestamp,
            temperature = temperature,
            apparentTemperature = null,
            humidity = details.relativeHumidity?.roundToInt(),
            dewPoint = details.dewPoint,
            precipitation = precipitation ?: 0.0,
            precipitationProbability = null,
            condition = symbol.condition,
            isDay = symbol.isDay
        ),
        precipitation = precipitation
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

private fun List<NormalizedHour>.toDailyForecasts(displayTimeZone: TimeZone): List<DayForecast> =
    groupBy { it.forecast.time.toLocalDateTime(displayTimeZone).date }
        .toSortedMap()
        .map { (date, hours) ->
            val precipitation = hours.mapNotNull { it.precipitation }
                .takeIf { it.isNotEmpty() }
                ?.sum()
            DayForecast(
                date = date,
                condition = hours.first().forecast.condition,
                temperatureMin = hours.minOf { it.forecast.temperature },
                temperatureMax = hours.maxOf { it.forecast.temperature },
                precipitation = precipitation,
                sunrise = null,
                sunset = null
            )
        }

/*
 * The domain hourly model requires a numeric precipitation value. The
 * normalized wrapper above keeps the provider's distinction between zero and
 * unavailable for current and daily aggregation.
 */

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
