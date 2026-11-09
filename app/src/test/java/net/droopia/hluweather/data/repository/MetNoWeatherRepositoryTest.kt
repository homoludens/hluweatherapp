package net.droopia.hluweather.data.repository

import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import net.droopia.hluweather.data.MoonPhaseCalculator
import net.droopia.hluweather.data.hourText
import net.droopia.hluweather.ui.weather.toHourlyTableData
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.network.MetNoApi
import net.droopia.hluweather.data.network.MetNoData
import net.droopia.hluweather.data.network.MetNoDetails
import net.droopia.hluweather.data.network.MetNoInstant
import net.droopia.hluweather.data.network.MetNoPrecipitationDetails
import net.droopia.hluweather.data.network.MetNoProperties
import net.droopia.hluweather.data.network.MetNoResponse
import net.droopia.hluweather.data.network.MetNoSummary
import net.droopia.hluweather.data.network.MetNoTimeSeries
import net.droopia.hluweather.data.network.MetNoTimeSeriesData
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.widget.toWeatherWidgetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.function.ThrowingRunnable

class MetNoWeatherRepositoryTest {

    @Test
    fun getForecast_normalizes_utc_times_and_uses_an_explicit_utc_fallback() = runTest {
        val fetchedAt = Instant.parse("2026-09-10T12:00:00Z")
        val forecast = repository(response = aggregatedResponse, clock = fixedClock(fetchedAt))
            .getForecast(location)

        assertEquals(location, forecast.location)
        assertEquals(WeatherProvider.MET_NO, forecast.provider)
        assertEquals(fetchedAt, forecast.fetchedAt)
        assertEquals("UTC", forecast.timezone)
        assertTrue(forecast.timezoneIsUtcFallback)
        assertEquals(MoonPhaseCalculator.phase(fetchedAt), forecast.moonPhase, 0.0)

        assertEquals(3, forecast.hourly.size)
        assertEquals(Instant.parse("2026-09-10T00:00:00Z"), forecast.hourly[0].time)
        assertEquals(5.0, forecast.hourly[1].temperature, 0.0)
        assertEquals(36.0, forecast.hourly[0].windSpeedKmh!!, 0.0)
        assertEquals(180.0, forecast.hourly[0].windDirectionDegrees!!, 0.0)
        assertEquals(WeatherCondition.RAIN, forecast.hourly[2].condition)
        assertEquals(false, forecast.hourly[2].isDay)

        assertEquals(1, forecast.daily.size)
        assertEquals("2026-09-10", forecast.daily.single().date.toString())
        assertEquals(5.0, forecast.daily.single().temperatureMin, 0.0)
        assertEquals(20.0, forecast.daily.single().temperatureMax, 0.0)
        assertEquals(3.75, forecast.daily.single().precipitation!!, 0.0)
    }

    @Test
    fun groups_approximate_daily_values_by_utc_instead_of_the_device_zone() = runTest {
        val original = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
        try {
            val midnight = repository(
                responseWithTimeseries(
                    timeSeries("clearsky_day", time = "2026-03-28T22:00:00Z"),
                    timeSeries("clearsky_day", time = "2026-03-28T23:00:00Z"),
                    timeSeries("clearsky_day", time = "2026-03-29T00:00:00Z"),
                    timeSeries("clearsky_day", time = "2026-03-29T01:00:00Z")
                ), fixedClock(Instant.parse("2026-03-28T12:00:00Z"))
            ).getForecast(location)
            assertEquals("UTC", midnight.timezone)
            assertTrue(midnight.timezoneIsUtcFallback)
            assertEquals(listOf("2026-03-28", "2026-03-29"), midnight.daily.map { it.date.toString() })
            val zone = java.time.ZoneId.of("UTC")
            assertEquals(listOf("22h", "23h", "00h", "01h"), midnight.hourly.map { it.time.hourText(zone) })

            val repeated = repository(
                responseWithTimeseries(
                    timeSeries("clearsky_day", time = "2026-10-25T00:00:00Z"),
                    timeSeries("clearsky_day", time = "2026-10-25T01:00:00Z")
                ), fixedClock(Instant.parse("2026-10-25T00:15:00Z"))
            ).getForecast(location)
            assertEquals(listOf("00h", "01h"), repeated.hourly.map { it.time.hourText(zone) })
            assertEquals(listOf("2026-10-25"), repeated.daily.map { it.date.toString() })
            val table = repeated.toHourlyTableData(Instant.parse("2026-10-25T01:15:00Z"))
            assertEquals(listOf(Instant.parse("2026-10-25T01:00:00Z")), table.hourItems.map { it.hour.time })
        } finally {
            java.util.TimeZone.setDefault(original)
        }
    }

    @Test
    fun fallbackUsesUtcAndGroupsAvailableValuesIntoApproximateDailySummaries() = runTest {
        val forecast = repository(aggregatedResponse).getForecast(location)
        assertEquals("UTC", forecast.timezone)
        assertTrue(forecast.timezoneIsUtcFallback)
        assertEquals(1, forecast.daily.size)
        assertEquals(3, forecast.hourly.size)
    }

    @Test
    fun getForecast_keeps_the_seventh_utc_date_and_excludes_the_eighth() = runTest {
        val response = responseWithTimeseries(
            *(aggregatedResponse.properties.timeseries + listOf(
                timeSeries("fair_day", time = "2026-09-15T22:00:00Z"),
                timeSeries("fair_day", time = "2026-09-16T22:00:00Z"),
                timeSeries("fair_day", time = "2026-09-17T22:00:00Z")
            )).toTypedArray()
        )

        val forecast = repository(
            response = response,
            clock = fixedClock(Instant.parse("2026-09-10T12:00:00Z"))
        ).getForecast(location)

        assertEquals(listOf("2026-09-10", "2026-09-15", "2026-09-16"), forecast.daily.map { it.date.toString() })
        assertEquals(5, forecast.hourly.size)
        assertEquals(Instant.parse("2026-09-16T22:00:00Z"), forecast.hourly.last().time)
    }

    @Test
    fun getForecast_maps_all_symbol_families_and_day_night_variants() = runTest {
        val suffixes = listOf(
            "_day" to true,
            "_night" to false,
            "_polartwilight" to null
        )
        val baseFamilies = listOf(
            "clearsky" to WeatherCondition.CLEAR,
            "fair" to WeatherCondition.MOSTLY_CLEAR,
            "partlycloudy" to WeatherCondition.PARTLY_CLOUDY,
            "cloudy" to WeatherCondition.CLOUDY,
            "fog" to WeatherCondition.FOG,
            "thunder" to WeatherCondition.THUNDERSTORM,
            "snow" to WeatherCondition.SNOW,
            "sleet" to WeatherCondition.SNOW,
            "rain" to WeatherCondition.RAIN
        )
        val directLightHeavyFamilies = listOf(
            "lightrain" to WeatherCondition.RAIN,
            "heavyrain" to WeatherCondition.RAIN,
            "lightsnow" to WeatherCondition.SNOW,
            "heavysnow" to WeatherCondition.SNOW,
            "lightsleet" to WeatherCondition.SNOW,
            "heavysleet" to WeatherCondition.SNOW
        )
        val thunderCompounds = listOf(
            "rainandthunder" to WeatherCondition.THUNDERSTORM,
            "snowandthunder" to WeatherCondition.THUNDERSTORM,
            "sleetandthunder" to WeatherCondition.THUNDERSTORM
        )
        val showerFamilies = listOf(
            "rainshowers" to WeatherCondition.RAIN,
            "lightrainshowers" to WeatherCondition.RAIN,
            "heavyrainshowers" to WeatherCondition.RAIN,
            "lightsnowshowers" to WeatherCondition.SNOW,
            "heavysnowshowers" to WeatherCondition.SNOW,
            "lightsleetshowers" to WeatherCondition.SNOW,
            "heavysleetshowers" to WeatherCondition.SNOW,
            "rainshowersandthunder" to WeatherCondition.THUNDERSTORM,
            "snowshowersandthunder" to WeatherCondition.THUNDERSTORM,
            "sleetshowersandthunder" to WeatherCondition.THUNDERSTORM
        )
        val expected = buildList {
            baseFamilies.forEachIndexed { index, (family, condition) ->
                suffixes.forEach { (suffix, isDay) ->
                    val symbol = if (index == 0) "ClEaRsKy$suffix" else "$family$suffix"
                    add(ExpectedSymbol(symbol, condition, isDay))
                }
            }
            (directLightHeavyFamilies + thunderCompounds).forEach { (family, condition) ->
                suffixes.forEach { (suffix, isDay) ->
                    add(ExpectedSymbol("$family$suffix", condition, isDay))
                }
            }
            showerFamilies.forEach { (family, condition) ->
                suffixes.forEach { (suffix, isDay) ->
                    add(ExpectedSymbol("$family$suffix", condition, isDay))
                }
            }
            add(ExpectedSymbol("ClEaRsKy_NiGhT", WeatherCondition.CLEAR, false))
            add(ExpectedSymbol("RaInShowers_PoLaRtWiLiGhT", WeatherCondition.RAIN, null))
        }

        expected.forEach { (symbol, condition, isDay) ->
            val forecast = repository(
                response = responseWithTimeseries(timeSeries(symbol)),
                clock = fixedClock(Instant.parse("2026-09-10T12:00:00Z"))
            ).getForecast(location)

            assertEquals(condition, forecast.current.condition)
            assertEquals(isDay, forecast.current.isDay)
        }
    }

    @Test
    fun getForecast_maps_absent_next_hour_data_to_unknown_and_null_optional_values() = runTest {
        val forecast = repository(
            response = responseWithTimeseries(timeSeries(null, humidity = null, dewPoint = null)),
            clock = fixedClock(Instant.parse("2026-09-10T12:00:00Z"))
        ).getForecast(location)

        assertEquals(WeatherCondition.UNKNOWN, forecast.current.condition)
        assertNull(forecast.current.apparentTemperature)
        assertNull(forecast.current.precipitation)
        assertNull(forecast.current.isDay)
        assertNull(forecast.current.humidity)
        assertNull(forecast.current.dewPoint)
        assertNull(forecast.hourly.single().precipitation)
        assertNull(forecast.hourly.single().precipitationProbability)
    }

    @Test
    fun one_hour_probability_maps_to_its_matching_hour_ending_row_and_widget_state() = runTest {
        val forecast = repository(
            responseWithTimeseries(
                timeSeries(
                    "rain_day",
                    time = "2026-09-10T00:00:00Z",
                    precipitationProbability = 40.4,
                    windSpeed = 3.4
                ),
                timeSeries(
                    "rain_day",
                    time = "2026-09-10T01:00:00Z",
                    precipitationProbability = 0.0
                ),
                timeSeries(
                    "rain_day",
                    time = "2026-09-10T02:00:00Z",
                    next6Probability = 75.0,
                    next12Probability = 90.0
                ),
                timeSeries("rain_day", time = "2026-09-10T03:00:00Z")
            ),
            clock = fixedClock(Instant.parse("2026-09-10T00:30:00Z"))
        ).getForecast(location)

        assertEquals(4, forecast.hourly.size)
        assertEquals(listOf(null, 40, 0, null), forecast.hourly.map { it.precipitationProbability })
        assertEquals(20.0, forecast.hourly[3].temperature, 0.0)
        assertEquals(12.24, forecast.hourly.first().windSpeedKmh!!, 0.0001)

        val widget = ForecastLoad(forecast).toWeatherWidgetState(
            settings = PersistedSettings(),
            clock = fixedClock(Instant.parse("2026-09-10T00:30:00Z"))
        )
        assertEquals(listOf(40, 0, null), widget.hours.map { it.precipitationProbability })
        assertEquals(listOf("40%", "0%", "—"), widget.hours.map { it.precipitationProbabilityText })
        assertEquals(
            listOf(
                "Rain probability 40 percent",
                "Rain probability 0 percent",
                "Rain probability unavailable"
            ),
            widget.hours.map { it.precipitationProbabilityContentDescription }
        )
    }

    @Test
    fun one_hour_probability_does_not_jump_across_a_sparse_timestamp() = runTest {
        val forecast = repository(
            responseWithTimeseries(
                timeSeries(
                    "rain_day",
                    time = "2026-09-10T00:00:00Z",
                    precipitationProbability = 75.0
                ),
                timeSeries(
                    "rain_day",
                    time = "2026-09-10T02:00:00Z",
                    precipitationProbability = 35.0
                ),
                timeSeries("rain_day", time = "2026-09-10T03:00:00Z")
            ),
            clock = fixedClock(Instant.parse("2026-09-10T00:30:00Z"))
        ).getForecast(location)

        assertEquals(
            listOf(
                Instant.parse("2026-09-10T00:00:00Z"),
                Instant.parse("2026-09-10T02:00:00Z"),
                Instant.parse("2026-09-10T03:00:00Z")
            ),
            forecast.hourly.map { it.time }
        )
        assertEquals(listOf(null, null, 35), forecast.hourly.map { it.precipitationProbability })
    }

    @Test
    fun getForecast_uses_next_six_hours_when_next_hour_data_is_absent() = runTest {
        val forecast = repository(
            response = responseWithTimeseries(
                timeSeries(
                    symbol = null,
                    precipitation = null,
                    next6Symbol = "heavyrain_night",
                    next6Precipitation = 4.5
                )
            ),
            clock = fixedClock(Instant.parse("2026-09-10T12:00:00Z"))
        ).getForecast(location)

        assertEquals(WeatherCondition.RAIN, forecast.current.condition)
        assertEquals(false, forecast.current.isDay)
        assertEquals(4.5, forecast.current.precipitation!!, 0.0)
        assertEquals(4.5, forecast.hourly.single().precipitation!!, 0.0)
        assertEquals(4.5, forecast.daily.single().precipitation!!, 0.0)
    }

    @Test
    fun getForecast_transitions_to_next_six_hours_and_summarizes_approximate_utc_day() = runTest {
        val forecast = repository(
            response = responseWithTimeseries(
                timeSeries(
                    symbol = "clearsky_day",
                    temperature = 20.0,
                    precipitation = 0.25
                ),
                timeSeries(
                    symbol = null,
                    time = "2026-09-10T01:00:00Z",
                    temperature = 5.0,
                    precipitation = null,
                    next6Symbol = "rain_night",
                    next6Precipitation = 1.0
                ),
                timeSeries(
                    symbol = "cloudy_day",
                    time = "2026-09-10T23:00:00Z",
                    temperature = 8.0,
                    precipitation = 2.5
                )
            ),
            clock = fixedClock(Instant.parse("2026-09-10T12:00:00Z"))
        ).getForecast(location)

        assertEquals(WeatherCondition.RAIN, forecast.hourly[1].condition)
        assertEquals(1.0, forecast.hourly[1].precipitation!!, 0.0)
        assertEquals(1, forecast.daily.size)
        assertEquals(5.0, forecast.daily.single().temperatureMin, 0.0)
        assertEquals(20.0, forecast.daily.single().temperatureMax, 0.0)
        assertEquals(3.75, forecast.daily.single().precipitation!!, 0.0)
    }

    @Test
    fun getForecast_rejects_empty_or_malformed_timeseries_with_controlled_errors() = runTest {
        assertRepositoryFailure(MetNoResponse(MetNoProperties(emptyList())))
        assertRepositoryFailure(responseWithTimeseries(timeSeries("rain_day", time = "not-an-instant")))
        assertRepositoryFailure(responseWithTimeseries(timeSeries("rain_day", temperature = null)))
    }

    @Test
    fun getForecast_rethrows_the_same_api_cancellation_unchanged() {
        val cancellation = CancellationException("cancelled")
        val repository = MetNoWeatherRepository(
            api = object : MetNoApi {
                override suspend fun forecast(location: WeatherLocation): MetNoResponse {
                    throw cancellation
                }
            }
        )

        val thrown = assertThrows(CancellationException::class.java, ThrowingRunnable {
            runBlocking { repository.getForecast(location) }
        })

        assertSame(cancellation, thrown)
    }

    private fun assertRepositoryFailure(response: MetNoResponse) {
        val exception = assertThrows(WeatherRepositoryException::class.java, ThrowingRunnable {
            runBlocking {
                repository(response, fixedClock(Instant.parse("2026-09-10T12:00:00Z")))
                    .getForecast(location)
            }
        })
        assertTrue(!exception.message.isNullOrBlank())
    }

    private fun repository(
        response: MetNoResponse,
        clock: Clock = fixedClock(Instant.parse("2026-09-10T12:00:00Z"))
    ) = MetNoWeatherRepository(
        api = object : MetNoApi {
            override suspend fun forecast(location: WeatherLocation): MetNoResponse = response
        },
        clock = clock
    )

    private fun timeSeries(
        symbol: String?,
        time: String = "2026-09-10T00:00:00Z",
        temperature: Double? = 20.0,
        precipitation: Double? = 1.25,
        humidity: Int? = 60,
        dewPoint: Double? = 10.0,
        windSpeed: Double? = null,
        windDirection: Double? = null,
        precipitationProbability: Double? = null,
        next6Symbol: String? = null,
        next6Precipitation: Double? = null,
        next6Probability: Double? = null,
        next12Symbol: String? = null,
        next12Precipitation: Double? = null,
        next12Probability: Double? = null
    ) = MetNoTimeSeries(
        time = time,
        data = MetNoTimeSeriesData(
            instant = MetNoInstant(
                MetNoDetails(temperature, humidity?.toDouble(), dewPoint, windSpeed, windDirection)
            ),
            next1Hours = symbol?.let {
                MetNoData(
                    summary = MetNoSummary(it),
                    details = MetNoPrecipitationDetails(precipitation, precipitationProbability)
                )
            },
            next6Hours = if (next6Symbol != null || next6Precipitation != null || next6Probability != null) {
                MetNoData(
                    summary = next6Symbol?.let(::MetNoSummary),
                    details = MetNoPrecipitationDetails(next6Precipitation, next6Probability)
                )
            } else null,
            next12Hours = if (next12Symbol != null || next12Precipitation != null || next12Probability != null) {
                MetNoData(
                    summary = next12Symbol?.let(::MetNoSummary),
                    details = MetNoPrecipitationDetails(next12Precipitation, next12Probability)
                )
            } else null
        )
    )

    private fun responseWithTimeseries(vararg timeSeries: MetNoTimeSeries) =
        MetNoResponse(MetNoProperties(timeSeries.toList()))

    private val aggregatedResponse: MetNoResponse
        get() = responseWithTimeseries(
            timeSeries(
                "clearsky_day",
                temperature = 20.0,
                precipitation = 0.25,
                windSpeed = 10.0,
                windDirection = 180.0
            ),
            timeSeries(
                "cloudy_day",
                temperature = 5.0,
                precipitation = 1.0,
                humidity = null,
                dewPoint = null
            ),
            timeSeries(
                "rain_night",
                time = "2026-09-10T23:00:00Z",
                temperature = 8.0,
                precipitation = 2.5
            )
        )

    private fun fixedClock(instant: Instant): Clock = object : Clock {
        override fun now(): Instant = instant
    }

    private data class ExpectedSymbol(
        val symbol: String,
        val condition: WeatherCondition,
        val isDay: Boolean?
    )

    private companion object {
        val location = WeatherLocation("nis", "Nis", 44.8176, 20.4633)
    }
}
