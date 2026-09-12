package net.droopia.hluweather.data.weatherroute

import kotlinx.datetime.Instant
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.RouteWeatherSample
import net.droopia.hluweather.data.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripWeatherAnalysisTest {

    private val departure = Instant.parse("2026-09-12T10:00:00Z")
    private val point = GeoPoint(44.0, 21.0)

    @Test
    fun snapshot_matching_uses_nearest_hour_and_later_hour_on_a_tie() {
        val snapshot = RouteWeatherSnapshot(
            points = listOf(point),
            hourlyByPoint = listOf(
                listOf(
                    forecast("2026-09-12T10:00:00Z", 10.0),
                    forecast("2026-09-12T11:00:00Z", 11.0)
                )
            ),
            fetchedAt = departure
        )

        val result = snapshot.enrich(listOf(sampleAt("2026-09-12T10:30:00Z"))).single()

        assertEquals(11.0, result.temperatureCelsius)
    }

    @Test
    fun snapshot_covers_requires_forecast_before_and_after_each_arrival() {
        val snapshot = RouteWeatherSnapshot(
            points = listOf(point),
            hourlyByPoint = listOf(
                listOf(
                    forecast("2026-09-12T10:00:00Z", 10.0),
                    forecast("2026-09-12T11:00:00Z", 11.0)
                )
            ),
            fetchedAt = departure
        )

        assertEquals(true, snapshot.covers(listOf(sampleAt("2026-09-12T10:30:00Z"))))
        assertEquals(true, snapshot.covers(listOf(sampleAt("2026-09-12T10:00:00Z"))))
        assertEquals(false, snapshot.covers(listOf(sampleAt("2026-09-12T09:00:00Z"))))
    }

    @Test
    fun snapshot_enrichment_preserves_optional_place_and_elevation_values() {
        val snapshot = RouteWeatherSnapshot(
            points = listOf(point),
            hourlyByPoint = listOf(listOf(forecast("2026-09-12T10:00:00Z", 12.0))),
            fetchedAt = departure
        )
        val sample = sampleAt("2026-09-12T10:00:00Z").copy(
            placeLabel = "Hilltop",
            elevationMeters = 123.5
        )

        val result = snapshot.enrich(listOf(sample)).single()

        assertEquals("Hilltop", result.placeLabel)
        assertEquals(123.5, result.elevationMeters)
        assertEquals(12.0, result.temperatureCelsius)
    }

    @Test
    fun snapshot_enrichment_leaves_weather_unavailable_without_a_matching_hour() {
        val snapshot = RouteWeatherSnapshot(
            points = listOf(point),
            hourlyByPoint = listOf(emptyList()),
            fetchedAt = departure
        )

        val result = snapshot.enrich(listOf(sampleAt("2026-09-12T10:00:00Z"))).single()

        assertNull(result.condition)
        assertNull(result.temperatureCelsius)
        assertNull(result.windSpeedKmh)
    }

    @Test
    fun snapshot_enrichment_leaves_weather_unavailable_outside_forecast_range() {
        val snapshot = RouteWeatherSnapshot(
            points = listOf(point),
            hourlyByPoint = listOf(
                listOf(
                    forecast("2026-09-12T10:00:00Z", 10.0),
                    forecast("2026-09-12T11:00:00Z", 11.0)
                )
            ),
            fetchedAt = departure
        )

        val result = snapshot.enrich(listOf(sampleAt("2026-09-12T12:00:00Z"))).single()

        assertNull(result.condition)
        assertNull(result.temperatureCelsius)
        assertNull(result.windSpeedKmh)
    }

    @Test
    fun display_route_samples_keeps_departure_hourly_checkpoints_and_final_once() {
        val samples = listOf(
            sampleAt("2026-09-12T10:00:00Z"),
            sampleAt("2026-09-12T10:30:00Z"),
            sampleAt("2026-09-12T11:00:00Z"),
            sampleAt("2026-09-12T11:30:00Z"),
            sampleAt("2026-09-12T12:00:00Z")
        )

        val displayed = displayRouteSamples(samples, departure)

        assertEquals(
            listOf(
                "2026-09-12T10:00:00Z",
                "2026-09-12T11:00:00Z",
                "2026-09-12T12:00:00Z"
            ),
            displayed.map { it.arrivalTime.toString() }
        )
    }

    @Test
    fun summarize_trip_weather_uses_departure_maximum_rain_duration_and_strongest_wind() {
        val samples = listOf(
            sampleAt("2026-09-12T10:00:00Z").copy(temperatureCelsius = 10.0, windSpeedKmh = 20.0),
            sampleAt("2026-09-12T10:30:00Z").copy(
                condition = WeatherCondition.RAIN,
                temperatureCelsius = 15.0,
                windSpeedKmh = 55.0
            ),
            sampleAt("2026-09-12T11:00:00Z").copy(
                condition = WeatherCondition.DRIZZLE,
                temperatureCelsius = 13.0,
                windSpeedKmh = 35.0
            ),
            sampleAt("2026-09-12T11:30:00Z").copy(temperatureCelsius = 12.0, windSpeedKmh = 25.0)
        )

        val summary = summarizeTripWeather(samples)

        assertEquals(10.0, summary.departureTemperatureCelsius)
        assertEquals(15.0, summary.maximumTemperatureCelsius)
        assertEquals(60L, summary.rainyDurationMinutes)
        assertEquals(55.0, summary.strongestWindKmh)
    }

    @Test
    fun warnings_include_each_category_and_group_adjacent_samples() {
        val samples = listOf(
            sampleAt("2026-09-12T10:00:00Z").copy(condition = WeatherCondition.RAIN),
            sampleAt("2026-09-12T10:30:00Z").copy(condition = WeatherCondition.DRIZZLE),
            sampleAt("2026-09-12T11:00:00Z").copy(condition = WeatherCondition.SNOW),
            sampleAt("2026-09-12T11:30:00Z").copy(condition = WeatherCondition.THUNDERSTORM),
            sampleAt("2026-09-12T12:00:00Z").copy(condition = WeatherCondition.FOG),
            sampleAt("2026-09-12T12:30:00Z").copy(windSpeedKmh = 50.0),
            sampleAt("2026-09-12T13:00:00Z").copy(windSpeedKmh = 60.0)
        )

        val warnings = findTripWeatherWarnings(samples)

        assertEquals(
            listOf(
                TripWarningType.RAIN,
                TripWarningType.SNOW,
                TripWarningType.THUNDERSTORM,
                TripWarningType.FOG,
                TripWarningType.STRONG_WIND
            ),
            warnings.map { it.type }
        )
        assertEquals("2026-09-12T10:00:00Z", warnings[0].startTime.toString())
        assertEquals("2026-09-12T10:30:00Z", warnings[0].endTime.toString())
        assertEquals(point, warnings[0].point)
    }

    @Test
    fun warnings_use_positive_precipitation_for_rain_and_configured_wind_threshold() {
        val samples = listOf(
            sampleAt("2026-09-12T10:00:00Z").copy(precipitationMm = 0.1, windSpeedKmh = 40.0),
            sampleAt("2026-09-12T11:00:00Z").copy(precipitationMm = 0.0, windSpeedKmh = 45.0)
        )

        val warnings = findTripWeatherWarnings(samples, strongWindThresholdKmh = 40.0)

        assertEquals(listOf(TripWarningType.RAIN, TripWarningType.STRONG_WIND), warnings.map { it.type })
    }

    private fun sampleAt(time: String) = RouteWeatherSample(
        point = point,
        distanceMeters = 0.0,
        arrivalTime = Instant.parse(time)
    )

    private fun forecast(time: String, temperature: Double) = RouteWeatherForecastHour(
        time = Instant.parse(time),
        condition = WeatherCondition.CLEAR,
        temperatureCelsius = temperature,
        windSpeedKmh = 10.0,
        precipitationProbability = 0,
        precipitationMm = 0.0,
        humidityPercent = 50,
        isDay = true
    )
}
