package net.droopia.hluweather.data.repository

import java.io.IOException
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import net.droopia.hluweather.data.model.CurrentWeather
import net.droopia.hluweather.data.model.DayForecast
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.network.OpenMeteoApi
import net.droopia.hluweather.data.network.OpenMeteoResponse

class WeatherRepositoryException(message: String, cause: Throwable? = null) : IOException(message, cause)

class OpenMeteoWeatherRepository(
    private val api: OpenMeteoApi,
    private val clock: Clock = Clock.System
) : WeatherRepository {

    override suspend fun getForecast(location: WeatherLocation): WeatherForecast {
        val response = try {
            api.forecast(location)
        } catch (error: WeatherRepositoryException) {
            throw error
        } catch (error: Throwable) {
            throw WeatherRepositoryException("Unable to load weather data", error)
        }

        return try {
            response.toWeatherForecast(location, clock.now())
        } catch (error: WeatherRepositoryException) {
            throw error
        } catch (error: Throwable) {
            throw WeatherRepositoryException("Unable to map weather data", error)
        }
    }
}

private fun OpenMeteoResponse.toWeatherForecast(
    location: WeatherLocation,
    fetchedAt: Instant
): WeatherForecast {
    val timezone = TimeZone.of(timezone.required("timezone"))
    val current = current.required("current")
    val hourly = hourly.required("hourly")
    val daily = daily.required("daily")

    return WeatherForecast(
        location = location,
        provider = WeatherProvider.OPEN_METEO,
        fetchedAt = fetchedAt,
        current = current.toCurrentWeather(timezone),
        hourly = hourly.toHourlyForecasts(timezone),
        daily = daily.toDailyForecasts(timezone),
        moonPhase = daily.moonPhase.firstOrNull().required("daily.moon_phase[0]")
    )
}

private fun net.droopia.hluweather.data.network.CurrentDto.toCurrentWeather(
    timezone: TimeZone
): CurrentWeather {
    parseTimestamp(time.required("current.time"), timezone)
    return CurrentWeather(
        temperature = temperature.required("current.temperature_2m"),
        apparentTemperature = apparentTemperature,
        humidity = humidity,
        dewPoint = dewPoint,
        precipitation = precipitation,
        condition = weatherCode.required("current.weather_code").toWeatherCondition(),
        isDay = isDay.toDayFlag("current.is_day")
    )
}

private fun net.droopia.hluweather.data.network.HourlyDto.toHourlyForecasts(
    timezone: TimeZone
): List<HourForecast> {
    if (time.isEmpty()) {
        throw WeatherRepositoryException("Missing hourly data")
    }
    validateLengths(
        "hourly",
        time,
        temperature,
        humidity,
        dewPoint,
        apparentTemperature,
        precipitation,
        precipitationProbability,
        weatherCode,
        isDay
    )

    return time.indices.map { index ->
        HourForecast(
            time = parseTimestamp(time[index].required("hourly.time[$index]"), timezone),
            temperature = temperature[index].required("hourly.temperature_2m[$index]"),
            apparentTemperature = apparentTemperature[index],
            humidity = humidity[index],
            dewPoint = dewPoint[index],
            precipitation = precipitation[index].required("hourly.precipitation[$index]"),
            precipitationProbability = precipitationProbability[index],
            condition = weatherCode[index].required("hourly.weather_code[$index]").toWeatherCondition(),
            isDay = isDay[index].toDayFlag("hourly.is_day[$index]")
        )
    }
}

private fun net.droopia.hluweather.data.network.DailyDto.toDailyForecasts(
    timezone: TimeZone
): List<DayForecast> {
    moonPhase.forEachIndexed { index, value ->
        value.required("daily.moon_phase[$index]")
    }
    validateLengths(
        "daily",
        time,
        weatherCode,
        temperatureMax,
        temperatureMin,
        precipitation,
        sunrise,
        sunset,
        moonPhase
    )

    return time.indices.map { index ->
        DayForecast(
            date = parseDate(time[index].required("daily.time[$index]")),
            condition = weatherCode[index].required("daily.weather_code[$index]").toWeatherCondition(),
            temperatureMin = temperatureMin[index].required("daily.temperature_2m_min[$index]"),
            temperatureMax = temperatureMax[index].required("daily.temperature_2m_max[$index]"),
            precipitation = precipitation[index],
            sunrise = sunrise[index]?.let { parseTimestamp(it, timezone) },
            sunset = sunset[index]?.let { parseTimestamp(it, timezone) }
        )
    }
}

private fun validateLengths(name: String, time: List<*>, vararg fields: List<*>) {
    if (fields.any { it.size != time.size }) {
        throw WeatherRepositoryException("Mismatched $name array lengths")
    }
}

private fun parseDate(value: String): LocalDate = try {
    LocalDate.parse(value)
} catch (error: Throwable) {
    throw WeatherRepositoryException("Invalid date: $value", error)
}

private fun parseTimestamp(value: String, timezone: TimeZone): Instant {
    try {
        return Instant.parse(value)
    } catch (_: Throwable) {
        try {
            return LocalDateTime.parse(value).toInstant(timezone)
        } catch (error: Throwable) {
            throw WeatherRepositoryException("Invalid timestamp: $value", error)
        }
    }
}

private fun Int?.toDayFlag(field: String): Boolean? = when (this) {
    null -> null
    0 -> false
    1 -> true
    else -> throw WeatherRepositoryException("Invalid $field value: $this")
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

private fun <T> T?.required(field: String): T = this
    ?: throw WeatherRepositoryException("Missing required value: $field")
