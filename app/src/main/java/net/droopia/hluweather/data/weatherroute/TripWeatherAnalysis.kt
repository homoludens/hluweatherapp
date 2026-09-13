package net.droopia.hluweather.data.weatherroute

import kotlin.time.Instant
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.RouteWeatherSample
import net.droopia.hluweather.data.model.WeatherCondition
import kotlin.math.abs

data class RouteWeatherForecastHour(
    val time: Instant,
    val condition: WeatherCondition?,
    val temperatureCelsius: Double?,
    val windSpeedKmh: Double?,
    val precipitationProbability: Int?,
    val precipitationMm: Double?,
    val humidityPercent: Int?,
    val isDay: Boolean?
)

data class RouteWeatherSnapshot(
    val points: List<GeoPoint>,
    val hourlyByPoint: List<List<RouteWeatherForecastHour>>,
    val fetchedAt: Instant
)

data class TripWeatherSummary(
    val departureTemperatureCelsius: Double?,
    val maximumTemperatureCelsius: Double?,
    val rainyDurationMinutes: Long,
    val strongestWindKmh: Double?
)

enum class TripWarningType { RAIN, SNOW, THUNDERSTORM, FOG, STRONG_WIND }

data class TripWeatherWarning(
    val type: TripWarningType,
    val startTime: Instant,
    val endTime: Instant,
    val point: GeoPoint,
    val placeLabel: String?
)

fun RouteWeatherSnapshot.covers(samples: List<RouteWeatherSample>): Boolean = samples.all { sample ->
    hourlyFor(sample).covers(sample.arrivalTime)
}

fun RouteWeatherSnapshot.enrich(samples: List<RouteWeatherSample>): List<RouteWeatherSample> =
    samples.map { sample ->
        val forecast = hourlyFor(sample)
            .takeIf { it.covers(sample.arrivalTime) }
            ?.minWithOrNull(
            compareBy<RouteWeatherForecastHour> {
                instantDistanceNanos(it.time, sample.arrivalTime)
            }.thenByDescending { it.time }
        )
        forecast?.let {
            sample.copy(
                condition = it.condition,
                temperatureCelsius = it.temperatureCelsius,
                windSpeedKmh = it.windSpeedKmh,
                precipitationProbability = it.precipitationProbability,
                precipitationMm = it.precipitationMm,
                humidityPercent = it.humidityPercent,
                isDay = it.isDay
            )
        } ?: sample.copy(
            condition = null,
            temperatureCelsius = null,
            windSpeedKmh = null,
            precipitationProbability = null,
            precipitationMm = null,
            humidityPercent = null,
            isDay = null
        )
    }

fun displayRouteSampleIndices(
    samples: List<RouteWeatherSample>,
    departure: Instant
): List<Int> {
    if (samples.isEmpty()) return emptyList()
    return samples.mapIndexedNotNull { index, sample ->
        if (
        index != samples.lastIndex &&
            (sample.arrivalTime == departure || isHourlyCheckpoint(sample.arrivalTime, departure))
        ) index else null
    } + samples.lastIndex
}

fun displayRouteSamples(
    samples: List<RouteWeatherSample>,
    departure: Instant
): List<RouteWeatherSample> = displayRouteSampleIndices(samples, departure).map(samples::get)

fun summarizeTripWeather(samples: List<RouteWeatherSample>): TripWeatherSummary {
    val rainyDurationMinutes = samples.zipWithNext().sumOf { (sample, next) ->
        if (sample.isRainy()) {
            (next.arrivalTime - sample.arrivalTime).inWholeMinutes.coerceAtLeast(0L)
        } else {
            0
        }
    }

    return TripWeatherSummary(
        departureTemperatureCelsius = samples.firstOrNull()?.temperatureCelsius,
        maximumTemperatureCelsius = samples.mapNotNull { it.temperatureCelsius }.maxOrNull(),
        rainyDurationMinutes = rainyDurationMinutes,
        strongestWindKmh = samples.mapNotNull { it.windSpeedKmh?.takeIf(Double::isFinite) }.maxOrNull()
    )
}

fun findTripWeatherWarnings(
    samples: List<RouteWeatherSample>,
    strongWindThresholdKmh: Double = 50.0
): List<TripWeatherWarning> = TripWarningType.entries
    .flatMap { type -> warningRanges(samples, type, strongWindThresholdKmh) }
    .sortedWith(compareBy<TripWeatherWarning> { it.startTime }.thenBy { it.type.ordinal })

private fun warningRanges(
    samples: List<RouteWeatherSample>,
    type: TripWarningType,
    strongWindThresholdKmh: Double
): List<TripWeatherWarning> {
    val ranges = mutableListOf<TripWeatherWarning>()
    var rangeStart: RouteWeatherSample? = null
    var rangeEnd: RouteWeatherSample? = null
    var previousIndex = -2

    samples.forEachIndexed { index, sample ->
        if (sample.hasWarning(type, strongWindThresholdKmh)) {
            if (rangeStart == null || index != previousIndex + 1) {
                if (rangeStart != null && rangeEnd != null) ranges += warningRange(type, rangeStart, rangeEnd)
                rangeStart = sample
            }
            rangeEnd = sample
            previousIndex = index
        } else if (rangeStart != null && rangeEnd != null) {
            ranges += warningRange(type, rangeStart, rangeEnd)
            rangeStart = null
            rangeEnd = null
            previousIndex = -2
        }
    }
    if (rangeStart != null && rangeEnd != null) ranges += warningRange(type, rangeStart, rangeEnd)
    return ranges
}

private fun warningRange(
    type: TripWarningType,
    start: RouteWeatherSample,
    end: RouteWeatherSample
) = TripWeatherWarning(
    type = type,
    startTime = start.arrivalTime,
    endTime = end.arrivalTime,
    point = start.point,
    placeLabel = start.placeLabel
)

private fun RouteWeatherSample.hasWarning(
    type: TripWarningType,
    strongWindThresholdKmh: Double
): Boolean = when (type) {
    TripWarningType.RAIN -> isRainy()
    TripWarningType.SNOW -> condition == WeatherCondition.SNOW
    TripWarningType.THUNDERSTORM -> condition == WeatherCondition.THUNDERSTORM
    TripWarningType.FOG -> condition == WeatherCondition.FOG
    TripWarningType.STRONG_WIND -> windSpeedKmh?.let { it.isFinite() && it >= strongWindThresholdKmh } == true
}

private fun RouteWeatherSample.isRainy(): Boolean =
    condition == WeatherCondition.RAIN ||
        condition == WeatherCondition.DRIZZLE ||
        precipitationMm?.let { it.isFinite() && it > 0.0 } == true

private fun List<RouteWeatherForecastHour>.covers(arrivalTime: Instant): Boolean =
    isNotEmpty() && (any { it.time == arrivalTime } ||
        (any { it.time < arrivalTime } && any { it.time > arrivalTime }))

private fun RouteWeatherSnapshot.hourlyFor(sample: RouteWeatherSample): List<RouteWeatherForecastHour> =
    hourlyByPoint.getOrNull(points.indexOf(sample.point)).orEmpty()

private fun isHourlyCheckpoint(arrivalTime: Instant, departure: Instant): Boolean {
    if (arrivalTime == departure) return true
    if (arrivalTime < departure) return false
    return arrivalTime.nanosecondsOfSecond == departure.nanosecondsOfSecond &&
        (arrivalTime.epochSeconds - departure.epochSeconds) % 3_600L == 0L
}

private fun instantDistanceNanos(first: Instant, second: Instant): Long = abs(
    (first.epochSeconds - second.epochSeconds) * 1_000_000_000L +
        first.nanosecondsOfSecond - second.nanosecondsOfSecond
)
