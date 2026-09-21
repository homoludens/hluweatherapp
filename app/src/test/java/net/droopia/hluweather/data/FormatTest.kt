package net.droopia.hluweather.data

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import net.droopia.hluweather.ui.settings.DistanceUnit
import net.droopia.hluweather.ui.settings.PrecipitationUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.settings.WindUnit
import net.droopia.hluweather.ui.settings.WindDirectionDisplay
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
    fun air_quality_formats_index_and_particulate_matter() {
        assertEquals("42", 42.4.airQualityIndexText())
        assertEquals("12.5", 12.5.particulateMatterText())
        assertEquals("—", null.particulateMatterText())
    }

    @Test
    fun temperature_formats_selected_unit() {
        assertEquals("20 degrees C", 20.0.temperatureText(TemperatureUnit.CELSIUS))
        assertEquals("68 degrees F", 20.0.temperatureText(TemperatureUnit.FAHRENHEIT))
        assertEquals("68°F", 20.0.temperatureValueText(TemperatureUnit.FAHRENHEIT))
    }

    @Test
    fun precipitation_formats_selected_unit() {
        assertEquals("10 mm", 10.0.precipitationText(PrecipitationUnit.MM))
        assertEquals("0.39 in", 10.0.precipitationText(PrecipitationUnit.INCH))
    }

    @Test
    fun wind_speed_formats_selected_unit() {
        assertEquals("100 km/h", 100.0.windSpeedText(WindUnit.KMH))
        assertEquals("62.14 mph", 100.0.windSpeedText(WindUnit.MPH))
    }

    @Test
    fun wind_direction_formats_degrees_and_eight_point_compass_labels() {
        assertEquals("270°", 270.0.windDirectionText(WindDirectionDisplay.DEGREES))
        assertEquals("N", 0.0.windDirectionText(WindDirectionDisplay.EIGHT_POINT))
        assertEquals("NE", 22.5.windDirectionText(WindDirectionDisplay.EIGHT_POINT))
        assertEquals("E", 67.5.windDirectionText(WindDirectionDisplay.EIGHT_POINT))
        assertEquals("NW", 337.4.windDirectionText(WindDirectionDisplay.EIGHT_POINT))
        assertEquals("N", 360.0.windDirectionText(WindDirectionDisplay.EIGHT_POINT))
    }

    @Test
    fun missing_wind_direction_is_unavailable_in_each_text_mode() {
        val value: Double? = null

        assertEquals("—", value.windDirectionText(WindDirectionDisplay.DEGREES))
        assertEquals("—", value.windDirectionText(WindDirectionDisplay.EIGHT_POINT))
        assertEquals("—", value.windDirectionText(WindDirectionDisplay.ARROW))
    }

    @Test
    fun distance_formats_selected_unit() {
        assertEquals("10 km", 10.0.distanceText(DistanceUnit.KM))
        assertEquals("6.21 mi", 10.0.distanceText(DistanceUnit.MILES))
    }

    @Test
    fun unit_aware_formatters_round_and_preserve_missing_values() {
        assertEquals("22 degrees C", 21.6.temperatureText(TemperatureUnit.CELSIUS))
        assertEquals("1 in", 25.4.precipitationText(PrecipitationUnit.INCH))
        assertEquals("—", null.temperatureText(TemperatureUnit.CELSIUS))
        assertEquals("—", null.precipitationText(PrecipitationUnit.INCH))
        assertEquals("—", null.windSpeedText(WindUnit.MPH))
        assertEquals("—", null.distanceText(DistanceUnit.MILES))
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
