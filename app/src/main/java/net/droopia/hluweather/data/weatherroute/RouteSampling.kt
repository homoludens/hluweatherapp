package net.droopia.hluweather.data.weatherroute

import kotlinx.datetime.Instant
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

fun buildRouteSamples(
    route: DrivingRoute,
    departure: Instant,
    averageSpeedKmh: Int
): List<RouteWeatherSample> {
    require(route.polyline.size >= 2) { "Route geometry must contain at least two points" }
    require(route.distanceMeters > 0.0) { "Route distance must be positive" }
    require(averageSpeedKmh > 0) { "Average speed must be positive" }

    val speedMetersPerSecond = averageSpeedKmh * 1_000.0 / SECONDS_PER_HOUR
    val durationSeconds = route.distanceMeters / speedMetersPerSecond
    val geometryDistances = route.polyline.zipWithNext(::haversineDistance)
    val totalGeometryDistance = geometryDistances.sum()
    val cumulativeGeometryDistances = geometryDistances.runningFold(0.0, Double::plus)
    val offsets = buildList {
        add(0.0)
        var offset = SECONDS_PER_HOUR
        while (offset < durationSeconds) {
            add(offset)
            offset += SECONDS_PER_HOUR
        }
        add(durationSeconds)
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
