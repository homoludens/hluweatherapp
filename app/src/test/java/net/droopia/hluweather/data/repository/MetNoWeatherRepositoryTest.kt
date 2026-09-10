package net.droopia.hluweather.data.repository

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import net.droopia.hluweather.data.MoonPhaseCalculator
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.network.MetNoApi
import net.droopia.hluweather.data.network.MetNoData
import net.droopia.hluweather.data.network.MetNoDetails
import net.droopia.hluweather.data.network.MetNoInstant
import net.droopia.hluweather.data.network.MetNoResponse
import net.droopia.hluweather.data.network.MetNoSummary
import net.droopia.hluweather.data.network.MetNoTimeSeries
import net.droopia.hluweather.data.network.MetNoTimeSeriesData
import net.droopia.hluweather.data.network.MetNoProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.function.ThrowingRunnable

class MetNoWeatherRepositoryTest {

    @Test
    fun getForecast_normalizes_utc_times_and_groups_daily_values_in_the_device_zone() = runTest {
        val fetchedAt = Instant.parse("2026-09-10T12:00:00Z")
        val forecast = repository(response = aggregatedResponse, clock = fixedClock(fetchedAt))
            .getForecast(WeatherProvider.MET_NO, location)

        assertEquals(location, forecast.location)
        assertEquals(WeatherProvider.MET_NO, forecast.provider)
        assertEquals(fetchedAt, forecast.fetchedAt)
        assertEquals("Europe/Belgrade", forecast.timezone)
        assertEquals(MoonPhaseCalculator.phase(fetchedAt), forecast.moonPhase, 0.0)

        assertEquals(3, forecast.hourly.size)
        assertEquals(Instant.parse("2026-09-10T00:00:00Z"), forecast.hourly[0].time)
        assertEquals(5.0, forecast.hourly[1].temperature, 0.0)
        assertEquals(WeatherCondition.RAIN, forecast.hourly[2].condition)
        assertEquals(false, forecast.hourly[2].isDay)

        assertEquals(2, forecast.daily.size)
        assertEquals("2026-09-10", forecast.daily[0].date.toString())
        assertEquals(5.0, forecast.daily[0].temperatureMin, 0.0)
        assertEquals(20.0, forecast.daily[0].temperatureMax, 0.0)
        assertEquals(1.25, forecast.daily[0].precipitation!!, 0.0)
        assertEquals("2026-09-11", forecast.daily[1].date.toString())
        assertEquals(8.0, forecast.daily[1].temperatureMin, 0.0)
        assertEquals(8.0, forecast.daily[1].temperatureMax, 0.0)
        assertEquals(2.5, forecast.daily[1].precipitation!!, 0.0)
    }

    @Test
    fun getForecast_keeps_the_seventh_local_date_and_excludes_the_eighth() = runTest {
        val response = responseWithTimeseries(
            *(aggregatedResponse.properties.timeseries + listOf(
                timeSeries("fair_day", time = "2026-09-15T22:00:00Z"),
                timeSeries("fair_day", time = "2026-09-16T22:00:00Z")
            )).toTypedArray()
        )

        val forecast = repository(
            response = response,
            clock = fixedClock(Instant.parse("2026-09-10T12:00:00Z"))
        ).getForecast(WeatherProvider.MET_NO, location)

        assertEquals(
            listOf("2026-09-10", "2026-09-11", "2026-09-16"),
            forecast.daily.map { it.date.toString() }
        )
        assertEquals(4, forecast.hourly.size)
        assertEquals(Instant.parse("2026-09-15T22:00:00Z"), forecast.hourly.last().time)
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
        }

        expected.forEach { (symbol, condition, isDay) ->
            val forecast = repository(
                response = responseWithTimeseries(timeSeries(symbol)),
                clock = fixedClock(Instant.parse("2026-09-10T12:00:00Z"))
            ).getForecast(WeatherProvider.MET_NO, location)

            assertEquals(condition, forecast.current.condition)
            assertEquals(isDay, forecast.current.isDay)
        }
    }

    @Test
    fun getForecast_maps_absent_next_hour_data_to_unknown_and_null_optional_values() = runTest {
        val forecast = repository(
            response = responseWithTimeseries(timeSeries(null, humidity = null, dewPoint = null)),
            clock = fixedClock(Instant.parse("2026-09-10T12:00:00Z"))
        ).getForecast(WeatherProvider.MET_NO, location)

        assertEquals(WeatherCondition.UNKNOWN, forecast.current.condition)
        assertNull(forecast.current.apparentTemperature)
        assertNull(forecast.current.precipitation)
        assertNull(forecast.current.isDay)
        assertNull(forecast.current.humidity)
        assertNull(forecast.current.dewPoint)
        assertEquals(0.0, forecast.hourly.single().precipitation, 0.0)
        assertNull(forecast.hourly.single().precipitationProbability)
    }

    @Test
    fun getForecast_preserves_a_reported_zero_daily_precipitation() = runTest {
        val forecast = repository(
            response = responseWithTimeseries(timeSeries("clearsky_day", precipitation = 0.0)),
            clock = fixedClock(Instant.parse("2026-09-10T12:00:00Z"))
        ).getForecast(WeatherProvider.MET_NO, location)

        assertEquals(0.0, forecast.daily.single().precipitation!!, 0.0)
    }

    @Test
    fun getForecast_rejects_empty_or_malformed_timeseries_with_controlled_errors() = runTest {
        assertRepositoryFailure(MetNoResponse(MetNoProperties(emptyList())))
        assertRepositoryFailure(responseWithTimeseries(timeSeries("rain_day", time = "not-an-instant")))
        assertRepositoryFailure(responseWithTimeseries(timeSeries("rain_day", temperature = null)))
    }

    @Test
    fun getForecast_rejects_an_unsupported_provider_without_calling_the_api() = runTest {
        var called = false
        val repository = MetNoWeatherRepository(
            api = object : MetNoApi {
                override suspend fun forecast(location: WeatherLocation): MetNoResponse {
                    called = true
                    return aggregatedResponse
                }
            }
        )

        assertThrows(WeatherRepositoryException::class.java, ThrowingRunnable {
            runBlocking { repository.getForecast(WeatherProvider.OPEN_METEO, location) }
        })
        assertEquals(false, called)
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
            runBlocking { repository.getForecast(WeatherProvider.MET_NO, location) }
        })

        assertSame(cancellation, thrown)
    }

    private fun assertRepositoryFailure(response: MetNoResponse) {
        val exception = assertThrows(WeatherRepositoryException::class.java, ThrowingRunnable {
            runBlocking {
                repository(response, fixedClock(Instant.parse("2026-09-10T12:00:00Z")))
                    .getForecast(WeatherProvider.MET_NO, location)
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
        clock = clock,
        displayTimeZone = TimeZone.of("Europe/Belgrade")
    )

    private fun timeSeries(
        symbol: String?,
        time: String = "2026-09-10T00:00:00Z",
        temperature: Double? = 20.0,
        precipitation: Double? = 1.25,
        humidity: Int? = 60,
        dewPoint: Double? = 10.0
    ) = MetNoTimeSeries(
        time = time,
        data = MetNoTimeSeriesData(
            instant = MetNoInstant(MetNoDetails(temperature, humidity?.toDouble(), dewPoint)),
            next1Hours = symbol?.let {
                MetNoData(
                    summary = MetNoSummary(it),
                    precipitationAmount = precipitation
                )
            }
        )
    )

    private fun responseWithTimeseries(vararg timeSeries: MetNoTimeSeries) =
        MetNoResponse(MetNoProperties(timeSeries.toList()))

    private val aggregatedResponse: MetNoResponse
        get() = responseWithTimeseries(
            timeSeries("clearsky_day", temperature = 20.0, precipitation = 0.25),
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
