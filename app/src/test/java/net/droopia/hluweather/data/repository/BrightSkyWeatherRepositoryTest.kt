package net.droopia.hluweather.data.repository

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.droopia.hluweather.data.MoonPhaseCalculator
import net.droopia.hluweather.data.model.CurrentWeatherProvenance
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.network.BrightSkyApi
import net.droopia.hluweather.data.network.BrightSkyCurrentWeatherRecord
import net.droopia.hluweather.data.network.BrightSkyCurrentWeatherResponse
import net.droopia.hluweather.data.network.BrightSkySource
import net.droopia.hluweather.data.network.BrightSkyWeatherRecord
import net.droopia.hluweather.data.network.BrightSkyWeatherResponse
import net.droopia.hluweather.data.timezone.LocationTimeZone
import net.droopia.hluweather.data.timezone.TimeZoneResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.function.ThrowingRunnable

class BrightSkyWeatherRepositoryTest {

    @Test
    fun resolves_location_zone_before_requests_and_maps_current_observation_and_hourly_fields() = runTest {
        val events = mutableListOf<String>()
        val resolver = TimeZoneResolver { latitude, longitude ->
            assertEquals(52.52, latitude, 0.0)
            assertEquals(13.419, longitude, 0.0)
            events += "resolve"
            LocationTimeZone("Europe/Berlin", isUtcFallback = false)
        }
        val api = FakeBrightSkyApi(
            weatherResponse = fixtureWeather(),
            currentResponse = fixtureCurrent(),
            onWeather = { zone ->
                assertEquals(TimeZone.of("Europe/Berlin"), zone)
                events += "weather"
            },
            onCurrent = { zone ->
                assertEquals(TimeZone.of("Europe/Berlin"), zone)
                events += "current"
            }
        )
        val fetchedAt = Instant.parse("2026-11-09T08:30:00Z")

        val forecast = repository(api, resolver, fetchedAt).getForecast(location)

        assertEquals(listOf("resolve", "weather", "current"), events)
        assertEquals(WeatherProvider.BRIGHT_SKY, forecast.provider)
        assertEquals("Europe/Berlin", forecast.timezone)
        assertFalse(forecast.timezoneIsUtcFallback)
        assertEquals(CurrentWeatherProvenance.OBSERVATION, forecast.current.provenance)
        assertEquals(Instant.parse("2026-11-09T08:30:00Z"), forecast.current.validTime)
        assertEquals(8.4, forecast.current.temperature, 0.0)
        assertEquals(82, forecast.current.humidity)
        assertEquals(5.4, forecast.current.dewPoint!!, 0.0)
        assertEquals(0.0, forecast.current.precipitation!!, 0.0)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, forecast.current.condition)
        assertEquals(true, forecast.current.isDay)
        assertNull(forecast.current.apparentTemperature)

        assertEquals(2, forecast.hourly.size)
        val first = forecast.hourly[0]
        assertEquals(Instant.parse("2026-11-09T08:00:00Z"), first.time)
        assertEquals(8.4, first.temperature, 0.0)
        assertEquals(5.4, first.dewPoint!!, 0.0)
        assertEquals(82, first.humidity)
        assertEquals(0.0, first.precipitation!!, 0.0)
        assertNull(first.precipitationProbability)
        assertEquals(12.2, first.windSpeedKmh!!, 0.0)
        assertEquals(245.0, first.windDirectionDegrees!!, 0.0)
        assertEquals(WeatherCondition.CLOUDY, first.condition)
        val second = forecast.hourly[1]
        assertEquals(40, second.precipitationProbability)
        assertEquals(0.2, second.precipitation!!, 0.0)
        assertEquals(WeatherCondition.RAIN, second.condition)
        assertTrue(forecast.daily.isEmpty()) // The fixture is intentionally partial.
        assertEquals(MoonPhaseCalculator.phase(fetchedAt), forecast.moonPhase, 0.0)
    }

    @Test
    fun uses_nearest_appropriately_timed_forecast_when_observation_is_missing() = runTest {
        val now = Instant.parse("2026-11-09T09:40:00Z")
        val forecast = repository(
            api = FakeBrightSkyApi(
                weatherResponse = BrightSkyWeatherResponse(
                    weather = listOf(
                        weatherRecord("2026-11-09T09:00:00Z", temperature = 8.0),
                        weatherRecord("2026-11-09T10:00:00Z", temperature = 12.8, precipitationProbability = 70)
                    ),
                    sources = listOf(forecastStation())
                ),
                currentResponse = BrightSkyCurrentWeatherResponse()
            ),
            now = now
        ).getForecast(location)

        assertEquals(CurrentWeatherProvenance.FORECAST, forecast.current.provenance)
        assertEquals(Instant.parse("2026-11-09T10:00:00Z"), forecast.current.validTime)
        assertEquals(12.8, forecast.current.temperature, 0.0)
        assertEquals(0.0, forecast.current.precipitation!!, 0.0)
        assertEquals(70, forecast.hourly.last().precipitationProbability)
        assertTrue(forecast.daily.isEmpty())
    }

    @Test
    fun current_endpoint_must_identify_a_recent_synop_observation() = runTest {
        val now = Instant.parse("2026-11-09T09:30:00Z")
        val distantObservation = currentResponse(
            source = observationStation(distance = 50_001),
            record = currentRecord("2026-11-09T09:30:00Z")
        )
        val recentForecastResponse = BrightSkyCurrentWeatherResponse(
            weather = currentRecord("2026-11-09T09:30:00Z").copy(sourceId = 7001),
            sources = listOf(forecastStation())
        )
        val staleObservation = currentResponse(
            source = observationStation(),
            record = currentRecord("2026-11-09T07:59:00Z")
        )

        listOf(distantObservation, recentForecastResponse, staleObservation).forEach { currentResponse ->
            val forecast = repository(
                api = FakeBrightSkyApi(
                    weatherResponse = BrightSkyWeatherResponse(
                        weather = listOf(weatherRecord("2026-11-09T09:00:00Z", temperature = 10.0)),
                        sources = listOf(forecastStation())
                    ),
                    currentResponse = currentResponse
                ),
                now = now
            ).getForecast(location)

            assertEquals(CurrentWeatherProvenance.FORECAST, forecast.current.provenance)
            assertEquals(Instant.parse("2026-11-09T09:00:00Z"), forecast.current.validTime)
        }
    }

    @Test
    fun synop_current_observation_omits_fields_fallback_to_forecast_sources() = runTest {
        val now = Instant.parse("2026-11-09T09:30:00Z")
        val current = currentRecord("2026-11-09T09:30:00Z").copy(
            fallbackSourceIds = JsonObject(mapOf("dew_point" to JsonPrimitive(7001)))
        )
        val weather = weatherRecord(
            timestamp = "2026-11-09T10:00:00Z",
            temperature = 12.0
        ).copy(
            dewPoint = 6.5,
            fallbackSourceIds = JsonObject(mapOf("dew_point" to JsonPrimitive(6007)))
        )

        val forecast = repository(
            api = FakeBrightSkyApi(
                weatherResponse = BrightSkyWeatherResponse(
                    weather = listOf(weather),
                    sources = listOf(observationStation(), forecastStation())
                ),
                currentResponse = BrightSkyCurrentWeatherResponse(
                    weather = current,
                    sources = listOf(observationStation(), forecastStation())
                )
            ),
            now = now
        ).getForecast(location)

        assertEquals(CurrentWeatherProvenance.OBSERVATION, forecast.current.provenance)
        assertEquals(now, forecast.current.validTime)
        assertEquals(10.0, forecast.current.temperature, 0.0)
        assertEquals(70, forecast.current.humidity)
        assertNull(forecast.current.dewPoint)
        assertEquals(0.0, forecast.current.precipitation!!, 0.0)

        // Forecast hourly field handling continues to accept an in-range
        // fallback source as before; the stricter rule is current-only.
        assertEquals(6.5, forecast.hourly.single().dewPoint!!, 0.0)
    }

    @Test
    fun forecast_current_fallback_omits_fields_fallback_to_synop_sources() = runTest {
        val weather = weatherRecord(
            timestamp = "2026-11-09T10:00:00Z",
            temperature = 12.0,
            humidity = 75
        ).copy(
            dewPoint = 6.5,
            fallbackSourceIds = JsonObject(mapOf("dew_point" to JsonPrimitive(6007)))
        )
        val forecast = repository(
            api = FakeBrightSkyApi(
                weatherResponse = BrightSkyWeatherResponse(
                    weather = listOf(weather),
                    sources = listOf(forecastStation(), observationStation())
                ),
                currentResponse = BrightSkyCurrentWeatherResponse()
            ),
            now = Instant.parse("2026-11-09T09:30:00Z")
        ).getForecast(location)

        assertEquals(CurrentWeatherProvenance.FORECAST, forecast.current.provenance)
        assertEquals(Instant.parse("2026-11-09T10:00:00Z"), forecast.current.validTime)
        assertEquals(12.0, forecast.current.temperature, 0.0)
        assertEquals(75, forecast.current.humidity)
        assertNull(forecast.current.dewPoint)

        // The general hourly row still honors its in-range field fallback.
        assertEquals(6.5, forecast.hourly.single().dewPoint!!, 0.0)
    }

    @Test
    fun forecast_fallback_uses_forecast_source_even_when_a_closer_observation_shares_the_hour() = runTest {
        val forecast = repository(
            api = FakeBrightSkyApi(
                weatherResponse = BrightSkyWeatherResponse(
                    weather = listOf(
                        weatherRecord("2026-11-09T10:00:00Z", temperature = 8.0, sourceId = 6007),
                        weatherRecord("2026-11-09T10:00:00Z", temperature = 12.0, sourceId = 7001)
                    ),
                    sources = listOf(
                        observationStation(distance = 1_000),
                        forecastStation(distance = 20_000)
                    )
                ),
                currentResponse = BrightSkyCurrentWeatherResponse()
            ),
            now = Instant.parse("2026-11-09T10:00:00Z")
        ).getForecast(location)

        assertEquals(1, forecast.hourly.size)
        assertEquals(8.0, forecast.hourly.single().temperature, 0.0)
        assertEquals(CurrentWeatherProvenance.FORECAST, forecast.current.provenance)
        assertEquals(12.0, forecast.current.temperature, 0.0)
        assertEquals(Instant.parse("2026-11-09T10:00:00Z"), forecast.current.validTime)
    }

    @Test
    fun sparse_and_out_of_range_stations_do_not_fill_missing_values_or_create_fake_current_data() = runTest {
        val sparseResponse = BrightSkyWeatherResponse(
            weather = listOf(
                weatherRecord(
                    timestamp = "2026-11-09T09:00:00Z",
                    temperature = null,
                    precipitation = 0.0
                ),
                weatherRecord(
                    timestamp = "2026-11-09T10:00:00Z",
                    temperature = 10.0,
                    dewPoint = null,
                    humidity = null,
                    precipitation = null,
                    precipitationProbability = null,
                    windSpeed = null,
                    windDirection = null,
                    condition = null,
                    icon = null
                ),
                weatherRecord("2026-11-09T11:00:00Z", temperature = 99.0, sourceId = 2)
            ),
            sources = listOf(forecastStation(), forecastStation(id = 2, distance = 50_001))
        )
        val forecast = repository(
            FakeBrightSkyApi(sparseResponse, BrightSkyCurrentWeatherResponse()),
            now = Instant.parse("2026-11-09T10:00:00Z")
        ).getForecast(location)

        assertEquals(1, forecast.hourly.size)
        assertEquals(10.0, forecast.hourly.single().temperature, 0.0)
        assertNull(forecast.hourly.single().dewPoint)
        assertNull(forecast.hourly.single().humidity)
        assertNull(forecast.hourly.single().precipitation)
        assertNull(forecast.hourly.single().precipitationProbability)
        assertNull(forecast.hourly.single().windSpeedKmh)
        assertNull(forecast.hourly.single().windDirectionDegrees)
        assertEquals(WeatherCondition.UNKNOWN, forecast.hourly.single().condition)
        assertNull(forecast.current.precipitation)
        assertTrue(forecast.daily.isEmpty())

        val onlyDistantStations = sparseResponse.copy(
            weather = listOf(weatherRecord("2026-11-09T10:00:00Z", temperature = 10.0, sourceId = 2))
        )
        val error = assertThrows(WeatherRepositoryException::class.java, ThrowingRunnable {
            kotlinx.coroutines.runBlocking {
                repository(
                    FakeBrightSkyApi(onlyDistantStations, BrightSkyCurrentWeatherResponse()),
                    now = Instant.parse("2026-11-09T10:00:00Z")
                ).getForecast(location)
            }
        })
        assertEquals("Missing usable Bright Sky hourly data", error.message)
    }

    @Test
    fun out_of_range_field_fallback_source_makes_only_that_field_unavailable() = runTest {
        val response = BrightSkyWeatherResponse(
            weather = listOf(
                weatherRecord("2026-11-09T10:00:00Z", temperature = 10.0).copy(
                    dewPoint = 8.0,
                    fallbackSourceIds = JsonObject(mapOf("dew_point" to JsonPrimitive(2)))
                )
            ),
            sources = listOf(forecastStation(), forecastStation(id = 2, distance = 50_001))
        )

        val forecast = repository(
            FakeBrightSkyApi(response, BrightSkyCurrentWeatherResponse()),
            now = Instant.parse("2026-11-09T10:00:00Z")
        ).getForecast(location)

        assertEquals(10.0, forecast.hourly.single().temperature, 0.0)
        assertNull(forecast.hourly.single().dewPoint)
    }

    @Test
    fun complete_local_days_use_23_24_or_25_instants_and_preceding_hour_rain_boundaries() = runTest {
        val cases = listOf(
            Triple("2026-03-29", "Europe/Berlin", 23),
            Triple("2026-10-25", "Europe/Berlin", 25),
            Triple("2026-11-09", "Europe/Berlin", 24)
        )

        cases.forEach { (dateText, zoneId, expectedCount) ->
            val zone = TimeZone.of(zoneId)
            val date = LocalDate.parse(dateText)
            val start = date.atStartOfDayIn(zone)
            val records = localDayRecords(start, expectedCount)
            val now = start + (expectedCount / 2).toLong().hours
            val forecast = repository(
                FakeBrightSkyApi(
                    BrightSkyWeatherResponse(weather = records, sources = listOf(forecastStation())),
                    BrightSkyCurrentWeatherResponse()
                ),
                zone = zoneId,
                now = now
            ).getForecast(location)

            val day = forecast.daily.single()
            assertEquals(dateText, day.date.toString())
            assertEquals(expectedCount, forecast.hourly.count {
                it.time.toLocalDateTime(zone).date == date
            })
            assertEquals(1.0, day.temperatureMin, 0.0)
            assertEquals(expectedCount.toDouble(), day.temperatureMax, 0.0)
            assertNull(day.sunrise)
            assertNull(day.sunset)
            // The record at local midnight is from the preceding day. The
            // following N interval-ending records cover exactly this day.
            assertEquals(expectedCount.toDouble(), day.precipitation!!, 0.0)
        }

        val fallZone = TimeZone.of("Europe/Berlin")
        val fallDate = LocalDate.parse("2026-10-25")
        val fallForecast = repository(
            FakeBrightSkyApi(
                BrightSkyWeatherResponse(
                    weather = localDayRecords(fallDate.atStartOfDayIn(fallZone), 25),
                    sources = listOf(forecastStation())
                ),
                BrightSkyCurrentWeatherResponse()
            ),
            zone = "Europe/Berlin",
            now = Instant.parse("2026-10-25T12:00:00Z")
        ).getForecast(location)
        val repeatedTwoOClock = fallForecast.hourly.filter {
            it.time.toLocalDateTime(fallZone).date == fallDate && it.time.toLocalDateTime(fallZone).hour == 2
        }
        assertEquals(2, repeatedTwoOClock.size)
        assertTrue(repeatedTwoOClock[0].time < repeatedTwoOClock[1].time)
        assertEquals(
            listOf("+02:00", "+01:00"),
            repeatedTwoOClock.map {
                java.time.Instant.ofEpochMilli(it.time.toEpochMilliseconds())
                    .atZone(java.time.ZoneId.of("Europe/Berlin")).offset.toString()
            }
        )
    }

    @Test
    fun partial_days_and_missing_daily_precipitation_intervals_are_not_filled_in() = runTest {
        val zone = TimeZone.of("Europe/Berlin")
        val start = LocalDate.parse("2026-11-09").atStartOfDayIn(zone)
        val complete = localDayRecords(start, 24)
        val missingHour = complete.filterNot { it.timestamp == (start + 7.hours).toString() }
        val partial = repository(
            FakeBrightSkyApi(
                BrightSkyWeatherResponse(weather = missingHour, sources = listOf(forecastStation())),
                BrightSkyCurrentWeatherResponse()
            ),
            now = start + 12.hours
        ).getForecast(location)
        assertTrue(partial.daily.isEmpty())

        val observationsThenForecast = complete.mapIndexed { index, record ->
            if (index < 12) record.copy(sourceId = 6007) else record
        }
        val mixedSourceDay = repository(
            FakeBrightSkyApi(
                BrightSkyWeatherResponse(
                    weather = observationsThenForecast,
                    sources = listOf(observationStation(distance = 1_000), forecastStation())
                ),
                BrightSkyCurrentWeatherResponse()
            ),
            now = start + 12.hours
        ).getForecast(location)
        assertEquals(24, mixedSourceDay.hourly.count {
            it.time.toLocalDateTime(zone).date.toString() == "2026-11-09"
        })
        assertTrue("Observed hours must not complete a forecast daily summary", mixedSourceDay.daily.isEmpty())

        val missingRainInterval = complete.map { record ->
            if (record.timestamp == (start + 9.hours).toString()) record.copy(precipitation = null) else record
        }
        val daily = repository(
            FakeBrightSkyApi(
                BrightSkyWeatherResponse(weather = missingRainInterval, sources = listOf(forecastStation())),
                BrightSkyCurrentWeatherResponse()
            ),
            now = start + 12.hours
        ).getForecast(location).daily.single()
        assertNull(daily.precipitation)
    }

    @Test
    fun utc_fallback_keeps_instants_but_never_claims_location_local_days() = runTest {
        val start = Instant.parse("2026-11-09T00:00:00Z")
        val forecast = repository(
            FakeBrightSkyApi(
                BrightSkyWeatherResponse(
                    weather = localDayRecords(start, 24),
                    sources = listOf(forecastStation())
                ),
                BrightSkyCurrentWeatherResponse()
            ),
            resolver = TimeZoneResolver { _, _ -> LocationTimeZone("UTC", isUtcFallback = true) },
            now = start + 12.hours
        ).getForecast(location)

        assertTrue(forecast.timezoneIsUtcFallback)
        assertEquals("UTC", forecast.timezone)
        assertTrue(forecast.daily.isEmpty())
        assertEquals(24, forecast.hourly.count {
            it.time.toLocalDateTime(TimeZone.UTC).date.toString() == "2026-11-09"
        })
    }

    @Test
    fun no_appropriately_timed_forecast_and_unusable_observations_report_unavailability() = runTest {
        val api = FakeBrightSkyApi(
            weatherResponse = BrightSkyWeatherResponse(
                weather = listOf(weatherRecord("2026-11-09T12:00:00Z", temperature = 10.0)),
                sources = listOf(forecastStation())
            ),
            currentResponse = currentResponse(
                source = observationStation(),
                record = currentRecord("2026-11-09T07:00:00Z", temperature = null)
            )
        )
        val error = assertThrows(WeatherRepositoryException::class.java, ThrowingRunnable {
            kotlinx.coroutines.runBlocking {
                repository(api, now = Instant.parse("2026-11-09T09:00:00Z")).getForecast(location)
            }
        })
        assertEquals("Missing usable Bright Sky current weather", error.message)
    }

    @Test
    fun cancellation_is_not_converted_to_unavailability() = runTest {
        val cancellation = CancellationException("cancelled")
        val api = object : BrightSkyApi {
            override suspend fun weather(location: WeatherLocation, timeZone: TimeZone): BrightSkyWeatherResponse =
                throw cancellation

            override suspend fun currentWeather(
                location: WeatherLocation,
                timeZone: TimeZone
            ): BrightSkyCurrentWeatherResponse = error("not reached")
        }

        val thrown = assertThrows(CancellationException::class.java, ThrowingRunnable {
            kotlinx.coroutines.runBlocking {
                repository(api, now = Instant.parse("2026-11-09T09:00:00Z")).getForecast(location)
            }
        })
        assertEquals(cancellation, thrown)
    }

    private fun repository(
        api: BrightSkyApi,
        resolver: TimeZoneResolver = TimeZoneResolver { _, _ -> LocationTimeZone("Europe/Berlin", false) },
        now: Instant = Instant.parse("2026-11-09T09:30:00Z")
    ) = BrightSkyWeatherRepository(api, resolver, fixedClock(now))

    private fun repository(
        api: BrightSkyApi,
        zone: String,
        now: Instant
    ) = repository(api, TimeZoneResolver { _, _ -> LocationTimeZone(zone, false) }, now)

    private fun fixtureWeather(): BrightSkyWeatherResponse = decodeFixture("bright-sky-weather.json")

    private fun fixtureCurrent(): BrightSkyCurrentWeatherResponse = decodeFixture("bright-sky-current-weather.json")

    private inline fun <reified T> decodeFixture(name: String): T = Json { ignoreUnknownKeys = true }
        .decodeFromString(
            requireNotNull(javaClass.getResourceAsStream("/provider-fixtures/$name"))
                .bufferedReader().use { it.readText() }
        )

    private fun localDayRecords(start: Instant, hourCount: Int): List<BrightSkyWeatherRecord> =
        List(hourCount + 1) { index ->
            weatherRecord(
                timestamp = (start + index.toLong().hours).toString(),
                temperature = (index + 1).toDouble(),
                precipitation = if (index == 0) 99.0 else 1.0,
                condition = if (index % 2 == 0) "rain" else "dry",
                icon = if (index % 2 == 0) "rain" else "clear-day"
            )
        }

    private fun weatherRecord(
        timestamp: String,
        temperature: Double?,
        dewPoint: Double? = 5.0,
        humidity: Int? = 70,
        precipitation: Double? = 0.0,
        precipitationProbability: Int? = null,
        precipitationProbability6h: Int? = null,
        windSpeed: Double? = 12.0,
        windDirection: Int? = 180,
        condition: String? = "dry",
        icon: String? = "clear-day",
        sourceId: Int = 7001
    ) = BrightSkyWeatherRecord(
        timestamp = timestamp,
        sourceId = sourceId,
        condition = condition,
        dewPoint = dewPoint,
        icon = icon,
        relativeHumidity = humidity,
        temperature = temperature,
        precipitation = precipitation,
        windDirection = windDirection,
        windSpeed = windSpeed,
        precipitationProbability = precipitationProbability,
        precipitationProbability6h = precipitationProbability6h
    )

    private fun currentRecord(
        timestamp: String,
        temperature: Double? = 10.0
    ) = BrightSkyCurrentWeatherRecord(
        timestamp = timestamp,
        sourceId = 6007,
        condition = "dry",
        dewPoint = 5.0,
        icon = "clear-day",
        relativeHumidity = 70,
        temperature = temperature,
        precipitation60 = 0.0,
        windDirection60 = 180,
        windSpeed60 = 12.0
    )

    private fun forecastStation(id: Int = 7001, distance: Int = 0) = BrightSkySource(
        id = id,
        stationName = "Forecast station",
        observationType = "forecast",
        distance = distance
    )

    private fun observationStation(distance: Int = 12_000) = BrightSkySource(
        id = 6007,
        stationName = "Observation station",
        observationType = "synop",
        distance = distance
    )

    private fun currentResponse(
        source: BrightSkySource,
        record: BrightSkyCurrentWeatherRecord
    ) = BrightSkyCurrentWeatherResponse(weather = record, sources = listOf(source))

    private fun fixedClock(instant: Instant): Clock = object : Clock {
        override fun now(): Instant = instant
    }

    private class FakeBrightSkyApi(
        private val weatherResponse: BrightSkyWeatherResponse,
        private val currentResponse: BrightSkyCurrentWeatherResponse,
        private val onWeather: (TimeZone) -> Unit = {},
        private val onCurrent: (TimeZone) -> Unit = {}
    ) : BrightSkyApi {
        override suspend fun weather(
            location: WeatherLocation,
            timeZone: TimeZone
        ): BrightSkyWeatherResponse {
            onWeather(timeZone)
            return weatherResponse
        }

        override suspend fun currentWeather(
            location: WeatherLocation,
            timeZone: TimeZone
        ): BrightSkyCurrentWeatherResponse {
            onCurrent(timeZone)
            return currentResponse
        }
    }

    private companion object {
        val location = WeatherLocation("berlin", "Berlin", 52.52, 13.419)
    }
}
