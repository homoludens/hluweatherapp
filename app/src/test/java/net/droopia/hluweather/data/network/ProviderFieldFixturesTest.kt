package net.droopia.hluweather.data.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderFieldFixturesTest {

    @Test
    fun offline_provider_samples_cover_documented_fields_and_non_hourly_cases() {
        val openMeteo = fixture("open-meteo-weather.json")
        assertEquals("Europe/Berlin", openMeteo.at("timezone").jsonPrimitive.content)
        assertEquals(35, openMeteo.at("hourly", "precipitation_probability").jsonArray[1].jsonPrimitive.int)
        assertEquals(0.1, openMeteo.at("hourly", "evapotranspiration").jsonArray[1].jsonPrimitive.double, 0.0)
        assertNotNull(openMeteo.at("daily", "sunrise"))
        assertNotNull(openMeteo.at("daily", "moon_phase"))

        val openMeteoAir = fixture("open-meteo-air-quality.json")
        assertEquals(42, openMeteoAir.at("current", "european_aqi").jsonPrimitive.int)
        assertEquals(8.2, openMeteoAir.at("current", "pm2_5").jsonPrimitive.double, 0.0)

        val metNoTimeseries = fixture("met-no-complete.json").at("properties", "timeseries").jsonArray
        assertEquals(
            40.0,
            metNoTimeseries[0].at("data", "next_1_hours", "details", "probability_of_precipitation").jsonPrimitive.double,
            0.0
        )
        assertEquals(
            70.0,
            metNoTimeseries[1].at("data", "next_6_hours", "details", "probability_of_precipitation").jsonPrimitive.double,
            0.0
        )

        val visualCrossing = fixture("visual-crossing-timeline.json")
        val visualAir = fixture("visual-crossing-air-quality.json")
        val visualWeatherHours = visualCrossing.at("days").jsonArray[0].at("hours").jsonArray
        val visualAirHours = visualAir.at("days").jsonArray[0].at("hours").jsonArray
        assertEquals(
            visualWeatherHours[0].at("datetimeEpoch").jsonPrimitive.long,
            visualAirHours[0].at("datetimeEpoch").jsonPrimitive.long
        )
        assertEquals(3, visualAirHours[0].at("aqieur").jsonPrimitive.int)
        assertEquals(8.2, visualAirHours[0].at("pm2p5").jsonPrimitive.double, 0.0)
        assertNull(visualCrossing.at("currentConditions").jsonObject["isday"])
        assertNull(visualAirHours[0].jsonObject["et0"])

        val brightSky = fixture("bright-sky-weather.json")
        val brightForecast = brightSky.at("weather").jsonArray[1]
        assertEquals(40, brightForecast.at("precipitation_probability").jsonPrimitive.int)
        assertEquals(70, brightForecast.at("precipitation_probability_6h").jsonPrimitive.int)
        assertEquals("forecast", brightSky.at("sources").jsonArray[1].at("observation_type").jsonPrimitive.content)

        val brightCurrent = fixture("bright-sky-current-weather.json")
        assertEquals(0.0, brightCurrent.at("weather", "precipitation_60").jsonPrimitive.double, 0.0)
        assertEquals(12.2, brightCurrent.at("weather", "wind_speed_60").jsonPrimitive.double, 0.0)
        assertNull(brightCurrent.at("weather").jsonObject["apparent_temperature"])
    }

    private fun fixture(name: String): JsonElement = requireNotNull(
        javaClass.getResourceAsStream("/provider-fixtures/$name")
    ) { "Missing offline provider fixture: $name" }
        .bufferedReader()
        .use { Json.parseToJsonElement(it.readText()) }

    private fun JsonElement.at(vararg keys: String): JsonElement = keys.fold(this) { value, key ->
        value.jsonObject.getValue(key)
    }

}
