package net.droopia.hluweather.data.weatherroute

import kotlinx.datetime.Instant
import net.droopia.hluweather.data.model.DrivingRoute
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RouteSamplingTest {

    @Test
    fun buildRouteSamples_interpolates_each_hour_on_the_route_geometry() {
        val route = DrivingRoute("OSRM", listOf(
            GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0), GeoPoint(1.0, 1.0)
        ), distanceMeters = 222_390.0, providerDurationSeconds = 10_000.0)

        val samples = buildRouteSamples(
            route = route,
            departure = Instant.parse("2026-09-12T10:00:00Z"),
            averageSpeedKmh = 80
        )

        assertEquals(7, samples.size)
        assertEquals(0.0, samples[0].distanceMeters, 0.1)
        assertEquals(40_000.0, samples[1].distanceMeters, 0.1)
        assertEquals(80_000.0, samples[2].distanceMeters, 0.1)
        assertEquals(222_390.0, samples.last().distanceMeters, 0.1)
        assertEquals(Instant.parse("2026-09-12T12:46:47.550Z"), samples.last().arrivalTime)
        assertEquals(0.0, samples[2].point.latitude, 0.01)
        assertEquals(0.719, samples[2].point.longitude, 0.01)
    }

    @Test
    fun route_estimate_distributes_provider_duration_over_route_distance() {
        val route = DrivingRoute(
            "OSRM",
            listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0), GeoPoint(1.0, 1.0)),
            distanceMeters = 222_390.0,
            providerDurationSeconds = 7_200.0
        )
        val departure = Instant.parse("2026-09-12T10:00:00Z")

        val samples = buildRouteSamples(route, departure, 80, RouteTimingMode.ROUTE_ESTIMATE)

        assertEquals(5, samples.size)
        assertEquals(7_200L, samples.last().arrivalTime.epochSeconds - departure.epochSeconds)
        assertEquals(route.distanceMeters / 2.0, samples[2].distanceMeters, 0.1)
    }

    @Test
    fun route_estimate_requires_positive_finite_provider_duration() {
        val route = DrivingRoute(
            "OSRM",
            listOf(GeoPoint(44.8, 20.4), GeoPoint(44.81, 20.41)),
            distanceMeters = 1_000.0,
            providerDurationSeconds = 0.0
        )

        assertThrows(IllegalArgumentException::class.java) {
            buildRouteSamples(
                route,
                Instant.parse("2026-09-12T10:00:00Z"),
                80,
                RouteTimingMode.ROUTE_ESTIMATE
            )
        }
    }

    @Test
    fun buildRouteSamples_keeps_distinct_start_and_destination_for_short_route() {
        val route = DrivingRoute("OSRM", listOf(GeoPoint(44.8, 20.4), GeoPoint(44.81, 20.41)), 1_000.0, 100.0)
        val samples = buildRouteSamples(route, Instant.parse("2026-09-12T10:00:00Z"), 80)

        assertEquals(2, samples.size)
        assertEquals(0.0, samples.first().distanceMeters, 0.1)
        assertEquals(1_000.0, samples.last().distanceMeters, 0.1)
    }

    @Test
    fun buildRouteSamples_does_not_duplicate_destination_when_arrival_is_exactly_on_an_hour() {
        val route = DrivingRoute("OSRM", listOf(GeoPoint(44.8, 20.4), GeoPoint(44.81, 20.41)), 80_000.0, 100.0)
        val samples = buildRouteSamples(route, Instant.parse("2026-09-12T10:00:00Z"), 80)

        assertEquals(3, samples.size)
        assertEquals(Instant.parse("2026-09-12T11:00:00Z"), samples.last().arrivalTime)
        assertEquals(route.polyline.last(), samples.last().point)
    }

    @Test
    fun buildRouteSamples_rejects_non_positive_speed() {
        val route = DrivingRoute("OSRM", listOf(GeoPoint(44.8, 20.4), GeoPoint(44.81, 20.41)), 1_000.0, 100.0)

        assertThrows(IllegalArgumentException::class.java) {
            buildRouteSamples(route, Instant.parse("2026-09-12T10:00:00Z"), 0)
        }
    }

    @Test
    fun buildRouteSamples_accepts_supported_speed_boundaries() {
        val route = DrivingRoute("OSRM", listOf(GeoPoint(44.8, 20.4), GeoPoint(44.81, 20.41)), 1_000.0, 100.0)

        assertEquals(2, buildRouteSamples(route, Instant.parse("2026-09-12T10:00:00Z"), 40).size)
        assertEquals(2, buildRouteSamples(route, Instant.parse("2026-09-12T10:00:00Z"), 130).size)
    }

    @Test
    fun buildRouteSamples_rejects_speed_outside_supported_range() {
        val route = DrivingRoute("OSRM", listOf(GeoPoint(44.8, 20.4), GeoPoint(44.81, 20.41)), 1_000.0, 100.0)

        assertThrows(IllegalArgumentException::class.java) {
            buildRouteSamples(route, Instant.parse("2026-09-12T10:00:00Z"), 39)
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildRouteSamples(route, Instant.parse("2026-09-12T10:00:00Z"), 131)
        }
    }

    @Test
    fun buildRouteSamples_rejects_invalid_geometry_and_distance() {
        assertThrows(IllegalArgumentException::class.java) {
            buildRouteSamples(
                DrivingRoute("OSRM", listOf(GeoPoint(44.8, 20.4)), 1_000.0, 100.0),
                Instant.parse("2026-09-12T10:00:00Z"),
                80
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildRouteSamples(
                DrivingRoute("OSRM", listOf(GeoPoint(44.8, 20.4), GeoPoint(44.8, 20.4)), 1_000.0, 100.0),
                Instant.parse("2026-09-12T10:00:00Z"),
                80
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildRouteSamples(
                DrivingRoute("OSRM", listOf(GeoPoint(44.8, 20.4), GeoPoint(44.81, 20.41)), 0.0, 100.0),
                Instant.parse("2026-09-12T10:00:00Z"),
                80
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildRouteSamples(
                DrivingRoute("OSRM", listOf(GeoPoint(44.8, 20.4), GeoPoint(44.81, 20.41)), Double.NaN, 100.0),
                Instant.parse("2026-09-12T10:00:00Z"),
                80
            )
        }
    }

    @Test(timeout = 1_000)
    fun buildRouteSamples_rejects_positive_infinite_distance() {
        assertThrows(IllegalArgumentException::class.java) {
            buildRouteSamples(
                DrivingRoute("OSRM", listOf(GeoPoint(44.8, 20.4), GeoPoint(44.81, 20.41)), Double.POSITIVE_INFINITY, 100.0),
                Instant.parse("2026-09-12T10:00:00Z"),
                80
            )
        }
    }

    @Test
    fun weatherSeverity_maps_each_weather_condition() {
        assertEquals(WeatherSeverity.FAVORABLE, weatherSeverity(WeatherCondition.CLEAR))
        assertEquals(WeatherSeverity.FAVORABLE, weatherSeverity(WeatherCondition.MOSTLY_CLEAR))
        assertEquals(WeatherSeverity.FAVORABLE, weatherSeverity(WeatherCondition.PARTLY_CLOUDY))
        assertEquals(WeatherSeverity.CAUTION, weatherSeverity(WeatherCondition.CLOUDY))
        assertEquals(WeatherSeverity.CAUTION, weatherSeverity(WeatherCondition.FOG))
        assertEquals(WeatherSeverity.CAUTION, weatherSeverity(WeatherCondition.DRIZZLE))
        assertEquals(WeatherSeverity.ADVERSE, weatherSeverity(WeatherCondition.RAIN))
        assertEquals(WeatherSeverity.ADVERSE, weatherSeverity(WeatherCondition.SNOW))
        assertEquals(WeatherSeverity.SEVERE, weatherSeverity(WeatherCondition.THUNDERSTORM))
        assertEquals(WeatherSeverity.UNAVAILABLE, weatherSeverity(WeatherCondition.UNKNOWN))
        assertEquals(WeatherSeverity.UNAVAILABLE, weatherSeverity(null))
    }
}
