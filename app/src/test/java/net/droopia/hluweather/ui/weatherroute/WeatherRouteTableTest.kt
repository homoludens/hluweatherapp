package net.droopia.hluweather.ui.weatherroute

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.data.model.DrivingRoute
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.RouteEndpoint
import net.droopia.hluweather.data.model.RouteWeatherSample
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherRouteResult
import net.droopia.hluweather.data.weatherroute.TripWeatherWarning
import net.droopia.hluweather.data.weatherroute.TripWarningType
import net.droopia.hluweather.ui.settings.DistanceUnit
import net.droopia.hluweather.ui.settings.PrecipitationUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.settings.WindUnit
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherRouteTableTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun table_uses_compact_weather_measurement_columns_without_location_or_distance() {
        var selectedIndex: Int? = null
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherRouteTable(
                    result = tableResult(),
                    selectedSampleIndex = null,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    windUnit = WindUnit.MPH,
                    distanceUnit = DistanceUnit.MILES,
                    precipitationUnit = PrecipitationUnit.INCH,
                    onSampleSelected = { selectedIndex = it },
                    timeZone = ZoneId.of("UTC"),
                    modifier = Modifier.height(520.dp)
                )
            }
        }

        composeRule.onNodeWithTag("route_weather_table_header")
            .assertIsDisplayed()
            .assertTextContains("Time")
            .assertTextContains("Weather")
            .assertTextContains("Temp.")
            .assertTextContains("Precip.")
            .assertTextContains("Wind")
        composeRule.onNodeWithText("Location").assertDoesNotExist()
        composeRule.onNodeWithText("Distance").assertDoesNotExist()
        composeRule.onNodeWithTag("route_weather_table_row_2")
            .assertIsDisplayed()
            .assertTextContains("Weather unavailable")
            .performClick()
        composeRule.onNodeWithTag("route_weather_table_row_0")
            .assertTextContains("68°F")
            .assertTextContains("0 in")
            .assertTextContains("6.21 mph")
        assertEquals(2, selectedIndex)
        assertTrue(
            composeRule.onAllNodesWithTag("route_weather_table_vertical_scroll")
                .fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun table_keeps_route_warnings_inline_below_the_rows() {
        val result = tableResult()
        composeRule.setContent {
            HluWeatherTheme(darkTheme = true) {
                WeatherRouteTable(
                    result = result,
                    selectedSampleIndex = null,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    windUnit = WindUnit.KMH,
                    distanceUnit = DistanceUnit.KM,
                    onSampleSelected = {},
                    warnings = listOf(
                        TripWeatherWarning(
                            type = TripWarningType.RAIN,
                            startTime = result.departure + 1.hours,
                            endTime = result.departure + 2.hours,
                            point = result.samples[1].point,
                            placeLabel = "Mountain pass"
                        )
                    )
                )
            }
        }

        composeRule.onNodeWithTag("route_weather_table_warnings").assertIsDisplayed()
        composeRule.onNodeWithTag("route_weather_table_warning_rain_0")
            .assertIsDisplayed()
            .assertTextContains("Mountain pass", substring = true)
    }

    @Test
    fun table_rows_match_map_markers_for_half_hour_samples() {
        val result = halfHourlyTableResult()

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherRouteTable(
                    result = result,
                    selectedSampleIndex = null,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    windUnit = WindUnit.KMH,
                    distanceUnit = DistanceUnit.KM,
                    onSampleSelected = {},
                    timeZone = ZoneId.of("UTC"),
                    modifier = Modifier.height(520.dp)
                )
            }
        }

        listOf(0, 2, 4).forEach { index ->
            composeRule.onNodeWithTag("route_weather_table_row_$index").assertIsDisplayed()
        }
        listOf(1, 3).forEach { index ->
            assertTrue(
                composeRule.onAllNodesWithTag("route_weather_table_row_$index")
                    .fetchSemanticsNodes().isEmpty()
            )
        }
        assertEquals(
            listOf(0, 2, 4),
            weatherRouteMarkers(result, null).map { it.sampleIndex }
        )
    }

    private fun tableResult(): WeatherRouteResult {
        val departure = Instant.parse("2026-09-12T10:00:00Z")
        val points = listOf(
            GeoPoint(44.0, 21.0),
            GeoPoint(44.5, 21.5),
            GeoPoint(45.0, 22.0)
        )
        return WeatherRouteResult(
            start = RouteEndpoint("Start", points.first()),
            end = RouteEndpoint("Destination", points.last()),
            route = DrivingRoute("OSRM", points, 80_000.0, 7_200.0),
            departure = departure,
            averageSpeedKmh = 80,
            samples = points.mapIndexed { index, point ->
                RouteWeatherSample(
                    point = point,
                    distanceMeters = index * 40_000.0,
                    arrivalTime = departure + index.hours,
                    condition = if (index == 2) null else WeatherCondition.CLEAR,
                    temperatureCelsius = if (index == 2) null else 20.0,
                    windSpeedKmh = if (index == 2) null else 10.0,
                    precipitationProbability = if (index == 2) null else 0,
                    precipitationMm = if (index == 2) null else 0.0,
                    humidityPercent = if (index == 2) null else 60,
                    isDay = if (index == 2) null else true
                )
            }
        )
    }

    private fun halfHourlyTableResult(): WeatherRouteResult {
        val departure = Instant.parse("2026-09-12T10:00:00Z")
        val points = (0..4).map { index -> GeoPoint(44.0 + index, 21.0 + index) }
        return WeatherRouteResult(
            start = RouteEndpoint("Start", points.first()),
            end = RouteEndpoint("Destination", points.last()),
            route = DrivingRoute("OSRM", points, 160_000.0, 7_200.0),
            departure = departure,
            averageSpeedKmh = 80,
            samples = points.mapIndexed { index, point ->
                RouteWeatherSample(
                    point = point,
                    distanceMeters = index * 40_000.0,
                    arrivalTime = departure + (index * 30).toLong().minutes,
                    condition = WeatherCondition.CLEAR
                )
            }
        )
    }
}
