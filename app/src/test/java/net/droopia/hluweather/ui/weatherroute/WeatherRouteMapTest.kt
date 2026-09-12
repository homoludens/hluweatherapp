package net.droopia.hluweather.ui.weatherroute

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.data.model.DrivingRoute
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.RouteEndpoint
import net.droopia.hluweather.data.model.RouteWeatherSample
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherRouteResult
import net.droopia.hluweather.data.weatherroute.WeatherSeverity
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherRouteMapTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun route_markers_use_a_distinct_color_for_each_weather_severity_and_neutral_for_unavailable() {
        val markers = weatherRouteMarkers(
            result = routeResult(
                conditions = listOf(
                    WeatherCondition.CLEAR,
                    WeatherCondition.CLOUDY,
                    WeatherCondition.RAIN,
                    WeatherCondition.THUNDERSTORM,
                    null
                )
            ),
            selectedSampleIndex = 2
        )

        assertEquals(
            listOf(
                WeatherSeverity.FAVORABLE,
                WeatherSeverity.CAUTION,
                WeatherSeverity.ADVERSE,
                WeatherSeverity.SEVERE,
                WeatherSeverity.UNAVAILABLE
            ),
            markers.map { it.severity }
        )
        assertEquals(5, markers.map { it.color }.distinct().size)
        assertEquals(weatherRouteMarkerColor(WeatherSeverity.UNAVAILABLE), markers.last().color)
        assertTrue(markers[2].isSelected)
    }

    @Test
    fun route_geometry_is_encoded_as_a_linestring_with_longitude_first_coordinates() {
        val json = routeLineGeoJson(
            listOf(GeoPoint(44.0, 21.0), GeoPoint(45.0, 22.0))
        )

        assertEquals(
            "{\"type\":\"LineString\",\"coordinates\":[[21.0,44.0],[22.0,45.0]]}",
            json
        )
    }

    @Test
    fun clicking_a_sample_marker_reports_its_sample_index() {
        var selectedIndex: Int? = null
        val result = routeResult(conditions = listOf(WeatherCondition.CLEAR, WeatherCondition.RAIN))

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherRouteMapMarkers(
                    result = result,
                    selectedSampleIndex = null,
                    onSampleSelected = { selectedIndex = it }
                )
            }
        }

        composeRule.onNodeWithTag("route_weather_marker_1")
            .assertHasClickAction()
            .performClick()

        assertEquals(1, selectedIndex)
    }

    @Test
    fun sample_markers_have_the_required_touch_target() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherRouteMapMarkers(
                    result = routeResult(conditions = listOf(WeatherCondition.CLEAR)),
                    selectedSampleIndex = 0,
                    onSampleSelected = {}
                )
            }
        }

        composeRule.onNodeWithTag("route_weather_marker_0").assertWidthIsAtLeast(48.dp)
    }

    private fun routeResult(conditions: List<WeatherCondition?>): WeatherRouteResult {
        val points = conditions.indices.map { index -> GeoPoint(44.0 + index, 21.0 + index) }
        return WeatherRouteResult(
            start = RouteEndpoint("Start", points.first()),
            end = RouteEndpoint("Destination", points.last()),
            route = DrivingRoute("OSRM", points, 10_000.0, 900.0),
            departure = Instant.parse("2026-09-12T10:00:00Z"),
            averageSpeedKmh = 80,
            samples = conditions.mapIndexed { index, condition ->
                RouteWeatherSample(
                    point = points[index],
                    distanceMeters = index * 2_500.0,
                    arrivalTime = Instant.parse("2026-09-12T10:00:00Z") + index.hours,
                    condition = condition
                )
            }
        )
    }
}
