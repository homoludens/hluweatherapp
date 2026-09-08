package net.droopia.hluweather.data

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class FormatTest {

    @Test
    fun temperature_rounds_to_whole_degrees() {
        assertEquals("21°", 21.4.temperatureText())
        assertEquals("22°", 21.6.temperatureText())
    }

    @Test
    fun missing_optional_value_shows_dash() {
        val value: Double? = null
        assertEquals("—", value.temperatureText())
    }

    @Test
    fun humidity_formats_percent() {
        assertEquals("51%", 51.percentText())
    }

    @Test
    fun precipitation_formats_millimetres() {
        assertEquals("0 mm", 0.2.precipitationText())
        assertEquals("1 mm", 0.8.precipitationText())
    }

    @Test
    fun hour_formats_in_requested_zone() {
        val instant = Instant.fromEpochSeconds(0L)

        assertEquals("00h", instant.hourText(ZoneId.of("UTC")))
    }

    @Test
    fun day_formats_in_english() {
        val date = LocalDate(2026, 9, 7)

        assertEquals("Mon, Sep 7", date.dayText(ZoneId.of("UTC")))
    }
}
