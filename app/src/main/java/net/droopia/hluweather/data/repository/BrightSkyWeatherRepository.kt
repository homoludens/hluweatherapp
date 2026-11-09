package net.droopia.hluweather.data.repository

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.droopia.hluweather.data.MoonPhaseCalculator
import net.droopia.hluweather.data.model.CurrentWeather
import net.droopia.hluweather.data.model.CurrentWeatherProvenance
import net.droopia.hluweather.data.model.DayForecast
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.network.BrightSkyApi
import net.droopia.hluweather.data.network.BrightSkyApiException
import net.droopia.hluweather.data.network.BrightSkyCurrentWeatherRecord
import net.droopia.hluweather.data.network.BrightSkyCurrentWeatherResponse
import net.droopia.hluweather.data.network.BrightSkySource
import net.droopia.hluweather.data.network.BrightSkyWeatherRecord
import net.droopia.hluweather.data.network.BrightSkyWeatherResponse
import net.droopia.hluweather.data.timezone.LocationTimeZone
import net.droopia.hluweather.data.timezone.TimeZoneResolver

private const val MAX_STATION_DISTANCE_METERS = 50_000
// Bright Sky's /current_weather endpoint compiles observations from roughly
// the preceding 1.5 hours; reject older/future records as non-current.
private val MAX_CURRENT_OBSERVATION_AGE = 90.minutes
// Only use a forecast record close enough to represent the current hour.
private val MAX_CURRENT_FORECAST_TIME_OFFSET = 1.hours

/** Uses the shared application resolver; tests should inject a deterministic fake. */
class BrightSkyWeatherRepository(
    private val api: BrightSkyApi,
    private val timeZoneResolver: TimeZoneResolver,
    private val clock: Clock = Clock.System
) : WeatherSource {

    override val provider: WeatherProvider = WeatherProvider.BRIGHT_SKY

    override suspend fun getForecast(location: WeatherLocation): WeatherForecast {
        val resolvedZone = try {
            timeZoneResolver.resolve(location.latitude, location.longitude)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            throw WeatherRepositoryException("Unable to resolve weather timezone")
        }

        val zone = try {
            resolvedZone.zone
        } catch (_: Throwable) {
            throw WeatherRepositoryException("Unable to resolve weather timezone")
        }

        val weather = try {
            api.weather(location, zone)
        } catch (error: CancellationException) {
            throw error
        } catch (error: BrightSkyApiException) {
            throw WeatherRepositoryException(error.message ?: "Weather request failed")
        } catch (_: Throwable) {
            throw WeatherRepositoryException("Unable to load Bright Sky weather data")
        }

        // The current-observation endpoint is optional. Keep usable forecast
        // data when it is unavailable, and never relabel it as an observation.
        val currentResponse = try {
            api.currentWeather(location, zone)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }

        val fetchedAt = clock.now()
        return try {
            weather.toWeatherForecast(
                location = location,
                fetchedAt = fetchedAt,
                resolvedZone = resolvedZone,
                currentResponse = currentResponse
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: WeatherRepositoryException) {
            throw error
        } catch (_: Throwable) {
            throw WeatherRepositoryException("Unable to map Bright Sky weather data")
        }
    }
}

private fun BrightSkyWeatherResponse.toWeatherForecast(
    location: WeatherLocation,
    fetchedAt: Instant,
    resolvedZone: LocationTimeZone,
    currentResponse: BrightSkyCurrentWeatherResponse?
): WeatherForecast {
    val zone = resolvedZone.zone
    val today = fetchedAt.toLocalDateTime(zone).date
    val afterForecast = today.plus(7, DateTimeUnit.DAY)
    val recordCandidates = normalizedRecordCandidates()
    val records = recordCandidates.nearestRecordsPerInstant()
    val hourly = records.mapNotNull { record ->
        val localDate = record.time.toLocalDateTime(zone).date
        if (localDate < today || localDate >= afterForecast) return@mapNotNull null
        record.hour
    }.sortedBy { it.time }

    if (hourly.isEmpty()) {
        throw WeatherRepositoryException("Missing usable Bright Sky hourly data")
    }

    val current = currentResponse?.observationOrNull(fetchedAt)
        ?: recordCandidates.nearestForecastCurrent(fetchedAt)
        ?: throw WeatherRepositoryException("Missing usable Bright Sky current weather")

    return WeatherForecast(
        location = location,
        provider = WeatherProvider.BRIGHT_SKY,
        fetchedAt = fetchedAt,
        current = current,
        hourly = hourly,
        daily = if (resolvedZone.isUtcFallback) {
            emptyList()
        } else {
            recordCandidates.asSequence()
                .filter { it.source.isForecast() }
                .toList()
                .nearestRecordsPerInstant()
                .toCompleteLocalDays(zone, today, afterForecast)
        },
        moonPhase = MoonPhaseCalculator.phase(fetchedAt),
        timezone = zone.id,
        timezoneIsUtcFallback = resolvedZone.isUtcFallback
    )
}

private data class TimedBrightSkyRecord(
    val time: Instant,
    val source: BrightSkySource,
    val record: BrightSkyWeatherRecord,
    val hour: HourForecast?,
    val precipitation: Double?,
    val forecastCurrent: CurrentWeather?
)

private fun BrightSkyWeatherResponse.normalizedRecordCandidates(): List<TimedBrightSkyRecord> {
    val sourcesById = sources.sourcesById()
    return weather.mapNotNull { record ->
        val time = record.timestamp.toInstantOrNull() ?: return@mapNotNull null
        val source = record.sourceId?.let(sourcesById::get) ?: return@mapNotNull null
        if (!source.isWithinDistanceLimit()) return@mapNotNull null
        TimedBrightSkyRecord(
            time = time,
            source = source,
            record = record,
            hour = record.toHourForecast(time, sourcesById),
            precipitation = record.precipitation
                .fromFieldIfInRange("precipitation", record.fallbackSourceIds, sourcesById)
                .nonNegative(),
            forecastCurrent = if (source.isForecast()) {
                record.toForecastCurrent(time, source, sourcesById)
            } else {
                null
            }
        )
    }
        .sortedWith(compareBy<TimedBrightSkyRecord>({ it.time }, { it.source.distance ?: Int.MAX_VALUE }))
}

private fun List<TimedBrightSkyRecord>.nearestRecordsPerInstant(): List<TimedBrightSkyRecord> =
    groupBy { it.time }
        .values
        .mapNotNull { candidates ->
            candidates.minWithOrNull(
                compareBy<TimedBrightSkyRecord>(
                    { if (it.hour == null) 1 else 0 },
                    { it.source.distance ?: Int.MAX_VALUE },
                    { it.source.id ?: Int.MAX_VALUE }
                )
            )
        }
        .sortedBy { it.time }

private fun BrightSkyCurrentWeatherResponse.observationOrNull(now: Instant): CurrentWeather? {
    val record = weather ?: return null
    val sourcesById = sources.sourcesById()
    val source = record.sourceId?.let(sourcesById::get) ?: return null
    if (!source.isWithinDistanceLimit() || !source.isSynopObservation()) return null

    val time = record.timestamp.toInstantOrNull() ?: return null
    if (time > now || now - time > MAX_CURRENT_OBSERVATION_AGE) return null

    return record.toCurrentWeather(time, sourcesById)
}

private fun List<TimedBrightSkyRecord>.nearestForecastCurrent(now: Instant): CurrentWeather? =
    asSequence()
        .filter { it.forecastCurrent != null }
        .filter { it.time.absoluteDifference(now) <= MAX_CURRENT_FORECAST_TIME_OFFSET }
        .minWithOrNull(
            compareBy<TimedBrightSkyRecord>(
                { it.time.absoluteDifference(now) },
                { if (it.time >= now) 0 else 1 },
                { it.source.distance ?: Int.MAX_VALUE },
                { it.source.id ?: Int.MAX_VALUE }
            )
        )
        ?.forecastCurrent

private fun BrightSkyWeatherRecord.toHourForecast(
    time: Instant,
    sourcesById: Map<Int, BrightSkySource>
): HourForecast? {
    val normalizedTemperature = temperature
        .fromFieldIfInRange("temperature", fallbackSourceIds, sourcesById)
        .finite() ?: return null
    val normalizedCondition = condition
        .fromFieldIfInRange("condition", fallbackSourceIds, sourcesById)
    val normalizedIcon = icon
        .fromFieldIfInRange("icon", fallbackSourceIds, sourcesById)

    return HourForecast(
        time = time,
        temperature = normalizedTemperature,
        apparentTemperature = null,
        humidity = relativeHumidity
            .fromFieldIfInRange("relative_humidity", fallbackSourceIds, sourcesById)
            .percentage(),
        dewPoint = dewPoint
            .fromFieldIfInRange("dew_point", fallbackSourceIds, sourcesById)
            .finite(),
        precipitation = precipitation
            .fromFieldIfInRange("precipitation", fallbackSourceIds, sourcesById)
            .nonNegative(),
        // Bright Sky's probability describes the preceding hour. The 6-hour
        // probability is deliberately not interchangeable with this field.
        precipitationProbability = precipitationProbability
            .fromFieldIfInRange("precipitation_probability", fallbackSourceIds, sourcesById)
            .percentage(),
        condition = weatherCondition(normalizedCondition, normalizedIcon),
        isDay = normalizedIcon.isDayIcon(),
        windSpeedKmh = windSpeed
            .fromFieldIfInRange("wind_speed", fallbackSourceIds, sourcesById)
            .nonNegative(),
        windDirectionDegrees = windDirection
            .fromFieldIfInRange("wind_direction", fallbackSourceIds, sourcesById)
            ?.toDouble()
            .valid(0.0, 360.0)
    )
}

private fun BrightSkyCurrentWeatherRecord.toCurrentWeather(
    time: Instant,
    sourcesById: Map<Int, BrightSkySource>
): CurrentWeather? {
    val normalizedTemperature = temperature
        .fromObservedFieldIfInRange("temperature", fallbackSourceIds, sourcesById)
        .finite() ?: return null
    val normalizedCondition = condition
        .fromObservedFieldIfInRange("condition", fallbackSourceIds, sourcesById)
    val normalizedIcon = icon
        .fromObservedFieldIfInRange("icon", fallbackSourceIds, sourcesById)

    return CurrentWeather(
        temperature = normalizedTemperature,
        apparentTemperature = null,
        humidity = relativeHumidity
            .fromObservedFieldIfInRange("relative_humidity", fallbackSourceIds, sourcesById)
            .percentage(),
        dewPoint = dewPoint
            .fromObservedFieldIfInRange("dew_point", fallbackSourceIds, sourcesById)
            .finite(),
        precipitation = precipitation60
            .fromObservedFieldIfInRange("precipitation_60", fallbackSourceIds, sourcesById)
            .nonNegative(),
        condition = weatherCondition(normalizedCondition, normalizedIcon),
        isDay = normalizedIcon.isDayIcon(),
        provenance = CurrentWeatherProvenance.OBSERVATION,
        validTime = time
    )
}

private fun BrightSkyWeatherRecord.toForecastCurrent(
    time: Instant,
    source: BrightSkySource,
    sourcesById: Map<Int, BrightSkySource>
): CurrentWeather? {
    if (!source.isWithinDistanceLimit() || !source.isForecast()) return null
    val normalizedTemperature = temperature
        .fromForecastFieldIfInRange("temperature", fallbackSourceIds, sourcesById)
        .finite() ?: return null
    val normalizedCondition = condition
        .fromForecastFieldIfInRange("condition", fallbackSourceIds, sourcesById)
    val normalizedIcon = icon
        .fromForecastFieldIfInRange("icon", fallbackSourceIds, sourcesById)

    return CurrentWeather(
        temperature = normalizedTemperature,
        apparentTemperature = null,
        humidity = relativeHumidity
            .fromForecastFieldIfInRange("relative_humidity", fallbackSourceIds, sourcesById)
            .percentage(),
        dewPoint = dewPoint
            .fromForecastFieldIfInRange("dew_point", fallbackSourceIds, sourcesById)
            .finite(),
        precipitation = precipitation
            .fromForecastFieldIfInRange("precipitation", fallbackSourceIds, sourcesById)
            .nonNegative(),
        condition = weatherCondition(normalizedCondition, normalizedIcon),
        isDay = normalizedIcon.isDayIcon(),
        provenance = CurrentWeatherProvenance.FORECAST,
        validTime = time
    )
}

private fun List<TimedBrightSkyRecord>.toCompleteLocalDays(
    zone: TimeZone,
    firstDate: LocalDate,
    afterDate: LocalDate
): List<DayForecast> {
    val recordsByTime = associateBy { it.time }
    return recordsByTime.values
        .asSequence()
        .map { it.time.toLocalDateTime(zone).date }
        .filter { it >= firstDate && it < afterDate }
        .distinct()
        .sorted()
        .mapNotNull { date ->
            val start = date.atStartOfDayIn(zone)
            val end = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
            val durationSeconds = (end - start).inWholeSeconds
            if (durationSeconds % SECONDS_PER_HOUR != 0L) return@mapNotNull null
            val hourCountLong = durationSeconds / SECONDS_PER_HOUR
            if (hourCountLong !in MINIMUM_LOCAL_DAY_HOURS..MAXIMUM_LOCAL_DAY_HOURS) return@mapNotNull null
            val hourCount = hourCountLong.toInt()

            // Build the day by instants, not local hour labels. This includes
            // both copies of a repeated fall-back hour and skips spring gaps.
            val expectedTimes = List(hourCount) { index -> start + index.hours }
            val dayHours = expectedTimes.map { recordsByTime[it]?.hour ?: return@mapNotNull null }
            val precipitationEnds = expectedTimes.map { it + 1.hours }
            val precipitationValues = precipitationEnds.map { recordsByTime[it]?.precipitation }
            val dailyPrecipitation = if (precipitationValues.all { it != null && it.isFinite() && it >= 0.0 }) {
                precipitationValues.sumOf { requireNotNull(it) }
            } else {
                null
            }
            val summarizedCondition = dayHours.asSequence()
                .map { it.condition }
                .filter { it != WeatherCondition.UNKNOWN }
                .toList()
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key ?: WeatherCondition.UNKNOWN

            DayForecast(
                date = date,
                condition = summarizedCondition,
                temperatureMin = dayHours.minOf { it.temperature },
                temperatureMax = dayHours.maxOf { it.temperature },
                precipitation = dailyPrecipitation,
                sunrise = null,
                sunset = null
            )
        }
        .toList()
}

private fun List<BrightSkySource>.sourcesById(): Map<Int, BrightSkySource> =
    mapNotNull { source -> source.id?.let { it to source } }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (id, matches) -> matches.singleOrNull()?.let { id to it } }
        .toMap()

private fun BrightSkySource.isWithinDistanceLimit(): Boolean =
    distance?.let { it in 0..MAX_STATION_DISTANCE_METERS } == true

private fun BrightSkySource.isSynopObservation(): Boolean =
    observationType.equals("synop", ignoreCase = true)

private fun BrightSkySource.isForecast(): Boolean =
    observationType.equals("forecast", ignoreCase = true)

private fun kotlinx.serialization.json.JsonElement?.isAllowedFallbackSource(
    sourcesById: Map<Int, BrightSkySource>
): Boolean {
    if (this == null) return true
    val sourceId = (this as? JsonPrimitive)?.content?.toIntOrNull() ?: return false
    return sourcesById[sourceId]?.isWithinDistanceLimit() == true
}

private fun kotlinx.serialization.json.JsonElement?.isAllowedObservationFallbackSource(
    sourcesById: Map<Int, BrightSkySource>
): Boolean {
    if (this == null) return true
    val sourceId = (this as? JsonPrimitive)?.content?.toIntOrNull() ?: return false
    val source = sourcesById[sourceId] ?: return false
    return source.isWithinDistanceLimit() && source.isSynopObservation()
}

private fun kotlinx.serialization.json.JsonElement?.isAllowedForecastFallbackSource(
    sourcesById: Map<Int, BrightSkySource>
): Boolean {
    if (this == null) return true
    val sourceId = (this as? JsonPrimitive)?.content?.toIntOrNull() ?: return false
    val source = sourcesById[sourceId] ?: return false
    return source.isWithinDistanceLimit() && source.isForecast()
}

private fun String?.toInstantOrNull(): Instant? =
    this?.let { runCatching { Instant.parse(it) }.getOrNull() }

private fun Instant.absoluteDifference(other: Instant) =
    if (this >= other) this - other else other - this

private fun weatherCondition(condition: String?, icon: String?): WeatherCondition {
    val fromIcon = icon.toWeatherCondition()
    return if (fromIcon != WeatherCondition.UNKNOWN) fromIcon else condition.toWeatherCondition()
}

private fun String?.toWeatherCondition(): WeatherCondition {
    val value = this?.lowercase()?.replace('_', '-')?.replace(' ', '-') ?: return WeatherCondition.UNKNOWN
    return when {
        "thunder" in value -> WeatherCondition.THUNDERSTORM
        "hail" in value -> WeatherCondition.UNKNOWN
        "sleet" in value || "snow" in value -> WeatherCondition.SNOW
        "drizzle" in value -> WeatherCondition.DRIZZLE
        "rain" in value -> WeatherCondition.RAIN
        "fog" in value -> WeatherCondition.FOG
        "partly-cloudy" in value || "partlycloudy" in value -> WeatherCondition.PARTLY_CLOUDY
        "mostly-clear" in value || "mostlyclear" in value -> WeatherCondition.MOSTLY_CLEAR
        "cloudy" in value -> WeatherCondition.CLOUDY
        "clear" in value -> WeatherCondition.CLEAR
        else -> WeatherCondition.UNKNOWN
    }
}

private fun String?.isDayIcon(): Boolean? = when {
    this == null -> null
    endsWith("-day", ignoreCase = true) -> true
    endsWith("-night", ignoreCase = true) -> false
    else -> null
}

private fun Double?.fromFieldIfInRange(
    field: String,
    fallbackSourceIds: JsonObject,
    sourcesById: Map<Int, BrightSkySource>
): Double? = takeIf { fallbackSourceIds[field].isAllowedFallbackSource(sourcesById) }

private fun Int?.fromFieldIfInRange(
    field: String,
    fallbackSourceIds: JsonObject,
    sourcesById: Map<Int, BrightSkySource>
): Int? = takeIf { fallbackSourceIds[field].isAllowedFallbackSource(sourcesById) }

private fun String?.fromFieldIfInRange(
    field: String,
    fallbackSourceIds: JsonObject,
    sourcesById: Map<Int, BrightSkySource>
): String? = takeIf { fallbackSourceIds[field].isAllowedFallbackSource(sourcesById) }

private fun <T> T?.fromObservedFieldIfInRange(
    field: String,
    fallbackSourceIds: JsonObject,
    sourcesById: Map<Int, BrightSkySource>
): T? = takeIf { fallbackSourceIds[field].isAllowedObservationFallbackSource(sourcesById) }

private fun <T> T?.fromForecastFieldIfInRange(
    field: String,
    fallbackSourceIds: JsonObject,
    sourcesById: Map<Int, BrightSkySource>
): T? = takeIf { fallbackSourceIds[field].isAllowedForecastFallbackSource(sourcesById) }

private fun Double?.finite(): Double? = this?.takeIf { it.isFinite() }

private fun Double?.nonNegative(): Double? = finite()?.takeIf { it >= 0.0 }

private fun Double?.valid(minimum: Double, maximum: Double): Double? =
    finite()?.takeIf { it in minimum..maximum }

private fun Int?.percentage(): Int? = this?.takeIf { it in 0..100 }

private fun Double?.percentage(): Int? = valid(0.0, 100.0)?.toInt()

private const val SECONDS_PER_HOUR = 3_600L
private const val MINIMUM_LOCAL_DAY_HOURS = 23L
private const val MAXIMUM_LOCAL_DAY_HOURS = 25L
