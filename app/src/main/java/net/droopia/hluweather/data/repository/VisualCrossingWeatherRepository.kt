package net.droopia.hluweather.data.repository

import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import net.droopia.hluweather.data.MoonPhaseCalculator
import net.droopia.hluweather.data.model.CurrentWeather
import net.droopia.hluweather.data.model.DayForecast
import net.droopia.hluweather.data.model.EuropeanAqiScale
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.network.VisualCrossingApi
import net.droopia.hluweather.data.network.VisualCrossingApiException
import net.droopia.hluweather.data.network.VisualCrossingCurrent
import net.droopia.hluweather.data.network.VisualCrossingDay
import net.droopia.hluweather.data.network.VisualCrossingHour
import net.droopia.hluweather.data.network.VisualCrossingTimelineResponse

class VisualCrossingWeatherRepository(
    private val api: VisualCrossingApi,
    private val clock: Clock = Clock.System
) : WeatherSource {

    override val provider: WeatherProvider = WeatherProvider.VISUAL_CROSSING

    override suspend fun getForecast(location: WeatherLocation): WeatherForecast {
        val response = try {
            api.forecast(location)
        } catch (error: CancellationException) {
            throw error
        } catch (error: VisualCrossingApiException) {
            // The API exception contains only a status or a generic message, never a URL.
            throw WeatherRepositoryException(error.message ?: "Weather request failed")
        } catch (_: Throwable) {
            throw WeatherRepositoryException("Unable to load Visual Crossing weather data")
        }

        val resolvedZone = response.resolveTimeZone()

        val forecast = try {
            response.toWeatherForecast(location, clock.now(), resolvedZone)
        } catch (error: CancellationException) {
            throw error
        } catch (error: WeatherRepositoryException) {
            throw error
        } catch (_: Throwable) {
            throw WeatherRepositoryException("Unable to map Visual Crossing weather data")
        }

        val airQuality = try {
            api.airQuality(location)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }

        return forecast.mergeAirQuality(airQuality)
    }
}

private fun VisualCrossingTimelineResponse.resolveTimeZone(): ForecastTimeZone {
    val providerZone = timezone?.let { id ->
        runCatching { TimeZone.of(id) }.getOrNull()
    }
    return providerZone?.let { ForecastTimeZone(it, isUtcFallback = false) }
        ?: ForecastTimeZone(TimeZone.of("UTC"), isUtcFallback = true)
}

private data class ForecastTimeZone(val zone: TimeZone, val isUtcFallback: Boolean)

private fun VisualCrossingTimelineResponse.toWeatherForecast(
    location: WeatherLocation,
    fetchedAt: Instant,
    resolvedZone: ForecastTimeZone
): WeatherForecast {
    val zone = resolvedZone.zone
    val today = fetchedAt.toLocalDateTime(zone).date
    val afterForecast = today.plus(7, DateTimeUnit.DAY)
    val current = currentConditions.required("current conditions").toCurrentWeather()
    val hourly = days.orEmpty()
        .flatMap { it.hours.orEmpty() }
        .mapNotNull { it.toHourForecast(zone, today, afterForecast) }
        .sortedBy { it.time }

    if (hourly.isEmpty()) {
        throw WeatherRepositoryException("Missing usable Visual Crossing hourly data")
    }

    // The upstream day keys are local civil dates. Without a verified location
    // zone, retain the explicit UTC fallback rather than assigning those days
    // to UTC dates by guesswork.
    val daily = if (resolvedZone.isUtcFallback) {
        emptyList()
    } else {
        days.orEmpty()
            .mapNotNull { it.toDayForecast(today, afterForecast) }
            .sortedBy { it.forecast.date }
    }

    val moonPhase = if (resolvedZone.isUtcFallback) {
        null
    } else {
        days.orEmpty().firstNotNullOfOrNull { day ->
            day.localDateOrNull()
                ?.takeIf { it == today }
                ?.let { day.moonphase.valid(0.0, 1.0) }
        }
    }
        ?: MoonPhaseCalculator.phase(fetchedAt)

    return WeatherForecast(
        location = location,
        provider = WeatherProvider.VISUAL_CROSSING,
        fetchedAt = fetchedAt,
        current = current,
        hourly = hourly,
        daily = daily.map { it.forecast },
        moonPhase = moonPhase,
        timezone = zone.id,
        timezoneIsUtcFallback = resolvedZone.isUtcFallback
    )
}

private fun WeatherForecast.mergeAirQuality(
    response: VisualCrossingTimelineResponse?
): WeatherForecast {
    if (response == null) return this

    val valuesByInstant = response.days.orEmpty()
        .asSequence()
        .flatMap { it.hours.orEmpty().asSequence() }
        .mapNotNull { hour ->
            val instant = hour.datetimeEpoch?.toInstantOrNull() ?: return@mapNotNull null
            instant to VisualCrossingAirQuality(
                europeanAqi = hour.aqieur.categoricalAqi(),
                pm2_5 = hour.pm2p5.nonNegative(),
                pm10 = hour.pm10.nonNegative()
            )
        }
        .toMap()

    if (valuesByInstant.isEmpty()) return this

    return copy(
        hourly = hourly.map { hour ->
            val airQuality = valuesByInstant[hour.time] ?: return@map hour
            hour.copy(
                europeanAqi = airQuality.europeanAqi,
                europeanAqiScale = EuropeanAqiScale.CATEGORICAL_1_TO_6,
                pm2_5 = airQuality.pm2_5,
                pm10 = airQuality.pm10
            )
        }
    )
}

private data class VisualCrossingAirQuality(
    val europeanAqi: Double?,
    val pm2_5: Double?,
    val pm10: Double?
)

private fun VisualCrossingCurrent.toCurrentWeather(): CurrentWeather {
    val normalizedTemperature = temp.finite()
        ?: throw WeatherRepositoryException("Missing current temperature")
    return CurrentWeather(
        temperature = normalizedTemperature,
        apparentTemperature = feelslike.finite(),
        humidity = humidity.percentage(),
        dewPoint = dew.finite(),
        precipitation = precip.nonNegative(),
        condition = condition(conditions, icon),
        isDay = icon.isDayIcon(),
        europeanAqiScale = EuropeanAqiScale.CATEGORICAL_1_TO_6
    )
}

private fun VisualCrossingHour.toHourForecast(
    zone: TimeZone,
    today: LocalDate,
    afterForecast: LocalDate
): HourForecast? {
    val instant = datetimeEpoch?.toInstantOrNull() ?: return null
    val localDate = instant.toLocalDateTime(zone).date
    if (localDate < today || localDate >= afterForecast) return null
    val normalizedTemperature = temp.finite() ?: return null

    return HourForecast(
        time = instant,
        temperature = normalizedTemperature,
        apparentTemperature = feelslike.finite(),
        humidity = humidity.percentage(),
        dewPoint = dew.finite(),
        precipitation = precip.nonNegative(),
        precipitationProbability = precipprob.percentage(),
        condition = condition(conditions, icon),
        isDay = icon.isDayIcon(),
        // Timeline unitGroup=metric reports windspeed in km/h; precipitation is mm.
        windSpeedKmh = windspeed.nonNegative(),
        windDirectionDegrees = winddir.valid(0.0, 360.0),
        europeanAqiScale = EuropeanAqiScale.CATEGORICAL_1_TO_6
    )
}

private data class NormalizedDay(val forecast: DayForecast)

private fun VisualCrossingDay.toDayForecast(
    today: LocalDate,
    afterForecast: LocalDate
): NormalizedDay? {
    val date = localDateOrNull() ?: return null
    if (date < today || date >= afterForecast) return null
    val minimum = tempmin.finite() ?: return null
    val maximum = tempmax.finite()?.takeIf { it >= minimum } ?: return null

    return NormalizedDay(
        forecast = DayForecast(
            date = date,
            condition = condition(conditions, icon),
            temperatureMin = minimum,
            temperatureMax = maximum,
            precipitation = precip.nonNegative(),
            sunrise = sunriseEpoch?.toInstantOrNull(),
            sunset = sunsetEpoch?.toInstantOrNull()
        )
    )
}

private fun VisualCrossingDay.localDateOrNull(): LocalDate? =
    datetime?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun condition(conditions: String?, icon: String?): WeatherCondition {
    val description = conditions?.lowercase().orEmpty()
    val iconName = icon?.lowercase().orEmpty()

    return when {
        "thunder" in description || "thunder" in iconName -> WeatherCondition.THUNDERSTORM
        "drizzle" in description -> WeatherCondition.DRIZZLE
        "snow" in description || "sleet" in description || "snow" in iconName -> WeatherCondition.SNOW
        "rain" in description || "shower" in description || "rain" in iconName -> WeatherCondition.RAIN
        "fog" in description || "fog" in iconName -> WeatherCondition.FOG
        "mostly clear" in description -> WeatherCondition.MOSTLY_CLEAR
        "partly cloudy" in description || "partially cloudy" in description ||
            "partly-cloudy" in iconName -> WeatherCondition.PARTLY_CLOUDY
        "overcast" in description || "cloudy" in description || "cloudy" in iconName -> WeatherCondition.CLOUDY
        "clear" in description || "clear" in iconName -> WeatherCondition.CLEAR
        else -> WeatherCondition.UNKNOWN
    }
}

private fun String?.isDayIcon(): Boolean? = when {
    this == null -> null
    endsWith("-day", ignoreCase = true) -> true
    endsWith("-night", ignoreCase = true) -> false
    else -> null
}

private fun Long.toInstantOrNull(): Instant? = runCatching { Instant.fromEpochSeconds(this) }.getOrNull()

private fun Double?.finite(): Double? = this?.takeIf { it.isFinite() }

private fun Double?.nonNegative(): Double? = finite()?.takeIf { it >= 0.0 }

private fun Double?.valid(minimum: Double, maximum: Double): Double? =
    finite()?.takeIf { it in minimum..maximum }

private fun Double?.percentage(): Int? = valid(0.0, 100.0)?.roundToInt()

private fun Double?.categoricalAqi(): Double? = finite()
    ?.takeIf { it in 1.0..6.0 && it % 1.0 == 0.0 }

private fun <T> T?.required(field: String): T = this
    ?: throw WeatherRepositoryException("Missing $field")
