package net.droopia.hluweather.data.repository

import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.coroutines.cancellation.CancellationException
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.network.CurrentDto
import net.droopia.hluweather.data.network.DailyDto
import net.droopia.hluweather.data.network.HourlyDto
import net.droopia.hluweather.data.network.OpenMeteoApi
import net.droopia.hluweather.data.network.OpenMeteoResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.function.ThrowingRunnable

class OpenMeteoWeatherRepositoryTest {

    @Test
    fun getForecast_maps_the_response_using_its_timezone_and_injected_clock() = runTest {
        val location = WeatherLocation("svilajnac", "Svilajnac", 44.22, 21.20)
        val fetchedAt = Instant.parse("2026-09-09T14:30:00Z")

        val forecast = repository(clock = fixedClock(fetchedAt)).getForecast(location)

        assertEquals(location, forecast.location)
        assertEquals(WeatherProvider.OPEN_METEO, forecast.provider)
        assertEquals(fetchedAt, forecast.fetchedAt)
        assertEquals("Europe/Belgrade", forecast.timezone)
        assertEquals(0.5, forecast.moonPhase, 0.0)

        assertEquals(21.0, forecast.current.temperature, 0.0)
        assertEquals(21.0, forecast.current.apparentTemperature)
        assertEquals(51, forecast.current.humidity)
        assertEquals(10.0, forecast.current.dewPoint)
        assertEquals(0.0, forecast.current.precipitation)
        assertEquals(WeatherCondition.CLEAR, forecast.current.condition)
        assertTrue(forecast.current.isDay!!)

        val hour = forecast.hourly.single()
        assertEquals(Instant.parse("2026-09-09T10:00:00Z"), hour.time)
        assertEquals(21.0, hour.temperature, 0.0)
        assertEquals(21.0, hour.apparentTemperature)
        assertEquals(51, hour.humidity)
        assertEquals(10.0, hour.dewPoint)
        assertEquals(0.0, hour.precipitation!!, 0.0)
        assertEquals(0, hour.precipitationProbability)
        assertEquals(12.5, hour.windSpeedKmh)
        assertEquals(240.0, hour.windDirectionDegrees)
        assertEquals(0.1, hour.evapotranspiration)
        assertEquals(WeatherCondition.CLEAR, hour.condition)
        assertTrue(hour.isDay!!)

        val day = forecast.daily.single()
        assertEquals("2026-09-09", day.date.toString())
        assertEquals(WeatherCondition.CLEAR, day.condition)
        assertEquals(16.0, day.temperatureMin, 0.0)
        assertEquals(30.0, day.temperatureMax, 0.0)
        assertEquals(0.0, day.precipitation)
        assertEquals(Instant.parse("2026-09-09T04:10:00Z"), day.sunrise)
        assertEquals(Instant.parse("2026-09-09T17:00:00Z"), day.sunset)
    }

    @Test
    fun getForecast_maps_optional_values_to_null() = runTest {
        val response = validResponse.copy(
            current = validResponse.current!!.copy(
                apparentTemperature = null,
                humidity = null,
                dewPoint = null,
                precipitation = null,
                isDay = null
            ),
            hourly = validResponse.hourly!!.copy(
                apparentTemperature = listOf(null),
                humidity = listOf(null),
                dewPoint = listOf(null),
                precipitation = listOf(null),
                precipitationProbability = listOf(null),
                windSpeed = listOf(null),
                windDirection = listOf(null),
                evapotranspiration = listOf(null),
                isDay = listOf(null)
            ),
            daily = validResponse.daily!!.copy(
                precipitation = listOf(null),
                sunrise = listOf(null),
                sunset = listOf(null)
            )
        )

        val forecast = repository(response).getForecast(location)

        assertNull(forecast.current.apparentTemperature)
        assertNull(forecast.current.humidity)
        assertNull(forecast.current.dewPoint)
        assertNull(forecast.current.precipitation)
        assertNull(forecast.current.isDay)
        assertNull(forecast.hourly.single().apparentTemperature)
        assertNull(forecast.hourly.single().humidity)
        assertNull(forecast.hourly.single().dewPoint)
        assertNull(forecast.hourly.single().precipitation)
        assertNull(forecast.hourly.single().precipitationProbability)
        assertNull(forecast.hourly.single().windSpeedKmh)
        assertNull(forecast.hourly.single().windDirectionDegrees)
        assertNull(forecast.hourly.single().evapotranspiration)
        assertNull(forecast.hourly.single().isDay)
        assertNull(forecast.daily.single().precipitation)
        assertNull(forecast.daily.single().sunrise)
        assertNull(forecast.daily.single().sunset)
    }

    @Test
    fun getForecast_maps_omitted_optional_series_to_null() = runTest {
        val response = Json.decodeFromString<OpenMeteoResponse>(
            """
            {
              "timezone": "Europe/Belgrade",
              "current": {
                "time": "2026-09-09T12:00",
                "temperature_2m": 21.0,
                "precipitation": 0.0,
                "weather_code": 0
              },
              "hourly": {
                "time": ["2026-09-09T12:00"],
                "temperature_2m": [21.0],
                "precipitation": [0.0],
                "weather_code": [0]
              },
              "daily": {
                "time": ["2026-09-09"],
                "weather_code": [0],
                "temperature_2m_max": [30.0],
                "temperature_2m_min": [16.0],
                "moon_phase": [0.5]
              }
            }
            """.trimIndent()
        )

        val forecast = repository(response).getForecast(location)

        assertNull(forecast.current.apparentTemperature)
        assertNull(forecast.current.humidity)
        assertNull(forecast.current.dewPoint)
        assertNull(forecast.current.isDay)
        assertNull(forecast.hourly.single().apparentTemperature)
        assertNull(forecast.hourly.single().humidity)
        assertNull(forecast.hourly.single().dewPoint)
        assertNull(forecast.hourly.single().precipitationProbability)
        assertNull(forecast.hourly.single().isDay)
        assertNull(forecast.daily.single().precipitation)
        assertNull(forecast.daily.single().sunrise)
        assertNull(forecast.daily.single().sunset)
    }

    @Test
    fun getForecast_maps_wmo_weather_code_ranges() = runTest {
        val expected = mapOf(
            0 to WeatherCondition.CLEAR,
            1 to WeatherCondition.MOSTLY_CLEAR,
            2 to WeatherCondition.PARTLY_CLOUDY,
            3 to WeatherCondition.CLOUDY,
            45 to WeatherCondition.FOG,
            51 to WeatherCondition.DRIZZLE,
            61 to WeatherCondition.RAIN,
            71 to WeatherCondition.SNOW,
            95 to WeatherCondition.THUNDERSTORM,
            999 to WeatherCondition.UNKNOWN
        )

        expected.forEach { (code, condition) ->
            val response = validResponse.copy(
                current = validResponse.current!!.copy(weatherCode = code),
                hourly = validResponse.hourly!!.copy(weatherCode = listOf(code)),
                daily = validResponse.daily!!.copy(weatherCode = listOf(code))
            )

            val forecast = repository(response).getForecast(location)

            assertEquals(condition, forecast.current.condition)
            assertEquals(condition, forecast.hourly.single().condition)
            assertEquals(condition, forecast.daily.single().condition)
        }
    }

    @Test
    fun getForecast_rejects_a_missing_current_block() = runTest {
        assertRepositoryFailure(validResponse.copy(current = null))
    }

    @Test
    fun getForecast_rejects_mismatched_hourly_array_lengths() = runTest {
        val hourly = validResponse.hourly!!.copy(temperature = emptyList())
        assertRepositoryFailure(validResponse.copy(hourly = hourly))
    }

    @Test
    fun getForecast_rejects_an_empty_hourly_payload() = runTest {
        assertRepositoryFailure(validResponse.copy(hourly = HourlyDto()))
    }

    @Test
    fun getForecast_rejects_mismatched_daily_array_lengths() = runTest {
        val daily = validResponse.daily!!.copy(sunset = emptyList())
        assertRepositoryFailure(validResponse.copy(daily = daily))
    }

    @Test
    fun getForecast_rejects_a_null_later_daily_moon_phase() = runTest {
        val daily = validResponse.daily!!.copy(
            time = listOf("2026-09-09", "2026-09-10"),
            weatherCode = listOf(0, 0),
            temperatureMax = listOf(30.0, 31.0),
            temperatureMin = listOf(16.0, 17.0),
            precipitation = listOf(0.0, 0.0),
            sunrise = listOf("2026-09-09T06:10", "2026-09-10T06:08"),
            sunset = listOf("2026-09-09T19:00", "2026-09-10T19:01"),
            moonPhase = listOf(0.5, null)
        )

        assertRepositoryFailure(validResponse.copy(daily = daily))
    }

    @Test
    fun getForecast_rejects_an_invalid_timezone() = runTest {
        assertRepositoryFailure(validResponse.copy(timezone = "Not/A_Timezone"))
    }

    @Test
    fun getForecast_rejects_an_invalid_timestamp() = runTest {
        val current = validResponse.current!!.copy(time = "not-a-timestamp")
        assertRepositoryFailure(validResponse.copy(current = current))
    }

    @Test
    fun getForecast_rethrows_cancellation_from_the_api() {
        val repository = OpenMeteoWeatherRepository(
            api = object : OpenMeteoApi {
                override suspend fun forecast(location: WeatherLocation): OpenMeteoResponse {
                    throw CancellationException("cancelled")
                }
            }
        )

        val exception = assertThrows(CancellationException::class.java, ThrowingRunnable {
            runBlocking { repository.getForecast(location) }
        })

        assertEquals("cancelled", exception.message)
    }

    private fun assertRepositoryFailure(response: OpenMeteoResponse) {
        val exception = assertThrows(WeatherRepositoryException::class.java, ThrowingRunnable {
            runBlocking {
                repository(response).getForecast(location)
            }
        })

        assertTrue(!exception.message.isNullOrBlank())
    }

    private fun repository(
        response: OpenMeteoResponse = validResponse,
        clock: Clock = fixedClock(Instant.parse("2026-09-09T14:30:00Z"))
    ): OpenMeteoWeatherRepository = OpenMeteoWeatherRepository(
        api = object : OpenMeteoApi {
            override suspend fun forecast(location: WeatherLocation): OpenMeteoResponse = response
        },
        clock = clock
    )

    private fun fixedClock(instant: Instant): Clock = object : Clock {
        override fun now(): Instant = instant
    }

    private companion object {
        val location = WeatherLocation("svilajnac", "Svilajnac", 44.22, 21.20)

        val validResponse = OpenMeteoResponse(
            timezone = "Europe/Belgrade",
            current = CurrentDto(
                time = "2026-09-09T12:00",
                temperature = 21.0,
                humidity = 51,
                apparentTemperature = 21.0,
                dewPoint = 10.0,
                precipitation = 0.0,
                weatherCode = 0,
                isDay = 1
            ),
            hourly = HourlyDto(
                time = listOf("2026-09-09T12:00"),
                temperature = listOf(21.0),
                humidity = listOf(51),
                dewPoint = listOf(10.0),
                apparentTemperature = listOf(21.0),
                precipitation = listOf(0.0),
                precipitationProbability = listOf(0),
                windSpeed = listOf(12.5),
                windDirection = listOf(240.0),
                evapotranspiration = listOf(0.1),
                weatherCode = listOf(0),
                isDay = listOf(1)
            ),
            daily = DailyDto(
                time = listOf("2026-09-09"),
                weatherCode = listOf(0),
                temperatureMax = listOf(30.0),
                temperatureMin = listOf(16.0),
                precipitation = listOf(0.0),
                sunrise = listOf("2026-09-09T06:10"),
                sunset = listOf("2026-09-09T19:00"),
                moonPhase = listOf(0.5)
            )
        )
    }
}
