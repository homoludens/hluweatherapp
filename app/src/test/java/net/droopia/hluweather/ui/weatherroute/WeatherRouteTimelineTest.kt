package net.droopia.hluweather.ui.weatherroute

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.data.model.DrivingRoute
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.RouteEndpoint
import net.droopia.hluweather.data.model.RouteWeatherSample
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherRouteResult
import net.droopia.hluweather.ui.settings.DistanceUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.settings.WindUnit
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherRouteTimelineTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun timeline_shows_destination_formatted_values_distance_and_unavailable_weather() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherRouteTimeline(
                    result = routeResult(),
                    selectedSampleIndex = null,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    windUnit = WindUnit.MPH,
                    distanceUnit = DistanceUnit.MILES,
                    timeZone = ZoneId.of("UTC"),
                    onSampleSelected = {}
                )
            }
        }

        composeRule.onNodeWithTag("route_timeline_destination").assertIsDisplayed()
        composeRule.onNodeWithText("Destination").assertIsDisplayed()
        composeRule.onNodeWithText("11h").assertIsDisplayed()
        composeRule.onNodeWithText("68°F").assertIsDisplayed()
        composeRule.onNodeWithTag("route_timeline_item_1").assertTextContains("6.21 mph")
        composeRule.onNodeWithText("6.21 mi").assertIsDisplayed()
        composeRule.onNodeWithText("40%").assertIsDisplayed()
        composeRule.onNodeWithText("Weather unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("—").assertIsDisplayed()
    }

    @Test
    fun clicking_a_timeline_item_reports_its_sample_index() {
        var selectedIndex: Int? = null

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherRouteTimeline(
                    result = routeResult(),
                    selectedSampleIndex = null,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    windUnit = WindUnit.KMH,
                    distanceUnit = DistanceUnit.KM,
                    onSampleSelected = { selectedIndex = it }
                )
            }
        }

        composeRule.onNodeWithTag("route_timeline_item_1").performClick()

        assertEquals(1, selectedIndex)
    }

    private fun routeResult() = WeatherRouteResult(
        start = RouteEndpoint("Start", GeoPoint(44.0, 21.0)),
        end = RouteEndpoint("Destination", GeoPoint(45.0, 22.0)),
        route = DrivingRoute(
            providerName = "OSRM",
            polyline = listOf(GeoPoint(44.0, 21.0), GeoPoint(45.0, 22.0)),
            distanceMeters = 10_000.0,
            providerDurationSeconds = 900.0
        ),
        departure = Instant.parse("2026-09-12T10:00:00Z"),
        averageSpeedKmh = 80,
        samples = listOf(
            RouteWeatherSample(
                point = GeoPoint(44.0, 21.0),
                distanceMeters = 0.0,
                arrivalTime = Instant.parse("2026-09-12T10:00:00Z"),
                condition = WeatherCondition.CLEAR,
                temperatureCelsius = 20.0,
                windSpeedKmh = 10.0,
                precipitationProbability = 20
            ),
            RouteWeatherSample(
                point = GeoPoint(44.5, 21.5),
                distanceMeters = 10_000.0,
                arrivalTime = Instant.parse("2026-09-12T11:00:00Z"),
                condition = WeatherCondition.RAIN,
                temperatureCelsius = 10.0,
                windSpeedKmh = 10.0,
                precipitationProbability = 40
            ),
            RouteWeatherSample(
                point = GeoPoint(45.0, 22.0),
                distanceMeters = 20_000.0,
                arrivalTime = Instant.parse("2026-09-12T12:00:00Z")
            )
        )
    )
}
