package net.droopia.hluweather.data.weatherroute

import kotlin.time.Instant
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.RouteWeatherSample
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.DrivingRoute
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val SECONDS_PER_HOUR = 3_600.0
private const val SAMPLE_INTERVAL_SECONDS = 30 * 60.0

enum class RouteTimingMode { AVERAGE_SPEED, ROUTE_ESTIMATE }

fun buildRouteSamples(
    route: DrivingRoute,
    departure: Instant,
    averageSpeedKmh: Int,
    timingMode: RouteTimingMode = RouteTimingMode.AVERAGE_SPEED
): List<RouteWeatherSample> {
    require(route.polyline.size >= 2) { "Route geometry must contain at least two points" }
    require(route.distanceMeters.isFinite() && route.distanceMeters > 0.0) {
        "Route distance must be finite and positive"
    }
    require(averageSpeedKmh in 40..130) { "Average speed must be between 40 and 130 km/h" }

    val speedMetersPerSecond = averageSpeedKmh * 1_000.0 / SECONDS_PER_HOUR
    val durationSeconds = when (timingMode) {
        RouteTimingMode.AVERAGE_SPEED -> route.distanceMeters / speedMetersPerSecond
        RouteTimingMode.ROUTE_ESTIMATE -> route.providerDurationSeconds.also {
            require(it.isFinite() && it > 0.0) {
                "Provider duration must be finite and positive for route estimate timing"
            }
        }
    }
    val geometryDistances = route.polyline.zipWithNext(::haversineDistance)
    val totalGeometryDistance = geometryDistances.sum()
    require(totalGeometryDistance.isFinite() && totalGeometryDistance > 0.0) {
        "Route geometry must have finite positive length"
    }
    val cumulativeGeometryDistances = geometryDistances.runningFold(0.0, Double::plus)
    val offsets = buildList {
        add(0.0)
        var offset = SAMPLE_INTERVAL_SECONDS
        while (offset < durationSeconds) {
            add(offset)
            offset += SAMPLE_INTERVAL_SECONDS
        }
        if (last() != durationSeconds) {
            add(durationSeconds)
        }
    }

    return offsets.map { offsetSeconds ->
        RouteWeatherSample(
            point = interpolatePoint(
                route.polyline,
                cumulativeGeometryDistances,
                totalGeometryDistance * offsetSeconds / durationSeconds
            ),
            distanceMeters = route.distanceMeters * offsetSeconds / durationSeconds,
            arrivalTime = departure + offsetSeconds.seconds
        )
    }
}

enum class WeatherSeverity { FAVORABLE, CAUTION, ADVERSE, SEVERE, UNAVAILABLE }

fun weatherSeverity(condition: WeatherCondition?): WeatherSeverity = when (condition) {
    WeatherCondition.CLEAR, WeatherCondition.MOSTLY_CLEAR, WeatherCondition.PARTLY_CLOUDY -> WeatherSeverity.FAVORABLE
    WeatherCondition.CLOUDY, WeatherCondition.FOG, WeatherCondition.DRIZZLE -> WeatherSeverity.CAUTION
    WeatherCondition.RAIN, WeatherCondition.SNOW -> WeatherSeverity.ADVERSE
    WeatherCondition.THUNDERSTORM -> WeatherSeverity.SEVERE
    null, WeatherCondition.UNKNOWN -> WeatherSeverity.UNAVAILABLE
}

private fun interpolatePoint(
    polyline: List<GeoPoint>,
    cumulativeDistances: List<Double>,
    targetDistance: Double
): GeoPoint {
    if (targetDistance <= 0.0) return polyline.first()
    if (targetDistance >= cumulativeDistances.last()) return polyline.last()

    val segmentIndex = cumulativeDistances.indexOfFirst { it >= targetDistance }.coerceAtLeast(1) - 1
    val segmentStart = cumulativeDistances[segmentIndex]
    val segmentLength = cumulativeDistances[segmentIndex + 1] - segmentStart
    if (segmentLength == 0.0) return polyline[segmentIndex]

    val fraction = (targetDistance - segmentStart) / segmentLength
    val start = polyline[segmentIndex]
    val end = polyline[segmentIndex + 1]
    return GeoPoint(
        latitude = start.latitude + (end.latitude - start.latitude) * fraction,
        longitude = start.longitude + (end.longitude - start.longitude) * fraction
    )
}

private fun haversineDistance(first: GeoPoint, second: GeoPoint): Double {
    val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
    val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
    val firstLatitude = Math.toRadians(first.latitude)
    val secondLatitude = Math.toRadians(second.latitude)
    val haversine = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
        cos(firstLatitude) * cos(secondLatitude) *
        sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
    return EARTH_RADIUS_METERS * 2 * asin(sqrt(haversine))
}
