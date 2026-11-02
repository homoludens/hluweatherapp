package net.droopia.hluweather.ui.map

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.LinkAnnotation
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.WeatherLocation
import org.maplibre.compose.camera.CameraMoveReason
import kotlin.time.Instant
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherMapTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun selects_the_dark_open_free_map_style() {
        assertEquals(
            "https://tiles.openfreemap.org/styles/dark",
            mapStyleUrl(darkTheme = true)
        )
    }

    @Test
    fun marks_the_active_saved_location() {
        val locations = listOf(
            WeatherLocation("one", "One", 44.0, 21.0),
            WeatherLocation("two", "Two", 45.0, 22.0)
        )

        val markers = weatherMapMarkers(locations, activeLocationId = "two")

        assertFalse(markers.first { it.location.id == "one" }.isActive)
        assertTrue(markers.first { it.location.id == "two" }.isActive)
    }

    @Test
    fun location_markers_do_not_show_name_initials() {
        val source = File("app/src/main/java/net/droopia/hluweather/ui/map/WeatherMap.kt")
            .takeIf { it.isFile }
            ?: File("src/main/java/net/droopia/hluweather/ui/map/WeatherMap.kt")

        assertFalse(source.readText().contains("text = marker.location.name.take(1).uppercase()"))
    }

    @Test
    fun viewport_contains_all_saved_locations() {
        val viewport = weatherMapViewport(
            listOf(
                WeatherLocation("one", "One", 44.0, 21.0),
                WeatherLocation("two", "Two", 45.0, 22.0)
            )
        )

        assertEquals(44.5, viewport.center.latitude, 0.0001)
        assertEquals(21.5, viewport.center.longitude, 0.0001)
        assertTrue(viewport.zoom < 11.0)
    }

    @Test
    fun map_fit_zoom_is_one_step_out_from_the_fitted_zoom() {
        assertEquals(9.0, zoomOutWeatherMapFit(10.0), 0.0)
        assertEquals(0.0, zoomOutWeatherMapFit(0.5), 0.0)
    }

    @Test
    fun viewport_can_include_the_current_location_when_fitting_saved_locations() {
        val viewport = weatherMapViewport(
            locations = listOf(WeatherLocation("one", "One", 44.0, 21.0)),
            fallbackCenter = GeoPoint(46.0, 23.0),
            includeFallbackInBounds = true
        )

        assertEquals(45.0, viewport.center.latitude, 0.0001)
        assertEquals(22.0, viewport.center.longitude, 0.0001)
    }

    @Test
    fun viewport_for_one_saved_location_uses_a_close_zoom() {
        val viewport = weatherMapViewport(
            listOf(WeatherLocation("one", "One", 44.0, 21.0))
        )

        assertTrue(viewport.zoom > 12.0)
    }

    @Test
    fun bounds_for_one_location_have_a_small_area_to_fit() {
        val bounds = weatherMapBounds(listOf(GeoPoint(44.0, 21.0)))

        assertEquals(44.0, (bounds.south + bounds.north) / 2.0, 0.0001)
        assertEquals(21.0, (bounds.west + bounds.east) / 2.0, 0.0001)
        assertTrue(bounds.north - bounds.south <= 0.0051)
        assertTrue(bounds.east - bounds.west <= 0.0051)
    }

    @Test
    fun viewport_and_bounds_handle_locations_across_the_antimeridian() {
        val points = listOf(
            GeoPoint(44.0, 179.0),
            GeoPoint(44.0, -179.0)
        )

        val viewport = weatherMapViewport(
            points.mapIndexed { index, point ->
                WeatherLocation(index.toString(), index.toString(), point.latitude, point.longitude)
            }
        )
        val bounds = weatherMapBounds(points)

        assertTrue(kotlin.math.abs(viewport.center.longitude) > 179.9)
        assertEquals(179.0, bounds.west, 0.0001)
        assertEquals(-179.0, bounds.east, 0.0001)
    }

    @Test
    fun marker_selection_reports_the_selected_location_and_recenter_runs_callback() {
        val location = WeatherLocation("one", "One", 44.0, 21.0)
        var selected: WeatherLocation? = null
        var recentered = false

        selectWeatherMapLocation(listOf(location), "one") { selected = it }
        recenterWeatherMap { recentered = true }

        assertEquals(location, selected)
        assertTrue(recentered)
    }

    @Test
    fun camera_center_only_animates_when_it_differs_from_the_map_target() {
        val point = GeoPoint(44.8176, 20.4633)

        assertFalse(shouldAnimateWeatherMapCenter(point, point))
        assertTrue(shouldAnimateWeatherMapCenter(point, GeoPoint(45.0, 22.0)))
    }

    @Test
    fun camera_idle_updates_are_only_reported_for_user_camera_moves() {
        assertTrue(shouldReportWeatherMapCameraIdle(CameraMoveReason.GESTURE))
        assertFalse(shouldReportWeatherMapCameraIdle(CameraMoveReason.PROGRAMMATIC))
        assertFalse(shouldReportWeatherMapCameraIdle(CameraMoveReason.NONE))
    }

    @Test
    fun camera_move_while_gesture_is_active_is_not_reported_as_idle() {
        assertFalse(shouldReportWeatherMapCameraIdle(CameraMoveReason.GESTURE, cameraIsMoving = true))
    }

    @Test
    fun refreshes_after_five_kilometers_or_thirty_minutes() {
        val lastPoint = GeoPoint(44.8176, 20.4633)
        val movedBeyondFiveKm = GeoPoint(44.8650, 20.4633)
        val movedOneKm = GeoPoint(44.8266, 20.4633)
        val lastFetch = Instant.parse("2026-09-10T10:00:00Z")
        val beforeTimeThreshold = Instant.parse("2026-09-10T10:15:00Z")
        val atTimeThreshold = Instant.parse("2026-09-10T10:30:00Z")

        assertTrue(shouldRefresh(lastPoint, movedBeyondFiveKm, lastFetch, beforeTimeThreshold))
        assertFalse(shouldRefresh(lastPoint, movedOneKm, lastFetch, beforeTimeThreshold))
        assertTrue(shouldRefresh(lastPoint, movedOneKm, lastFetch, atTimeThreshold))
        assertFalse(shouldRefresh(lastPoint, movedOneKm, atTimeThreshold.minus(29.minutes), atTimeThreshold))
    }

    @Test
    fun map_recenter_action_is_labeled_and_meets_touch_target() {
        composeRule.setContent {
            WeatherMapRecenterButton(onClick = {})
        }

        composeRule.onNodeWithTag("weather_map_recenter")
            .assertHeightIsAtLeast(48.dp)
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("Recenter map")
            .assertContentDescriptionEquals("Recenter map")
    }

    @Test
    fun map_attribution_starts_collapsed_opens_style_links_and_can_be_closed() {
        composeRule.setContent {
            WeatherMapAttributionControl(
                attributions = listOf(
                    "<a href=\"https://tiles.example.test/credits\">Example map credit</a>"
                )
            )
        }

        composeRule.onNodeWithTag("weather_map_attribution_toggle")
            .assertHasClickAction()
            .assertContentDescriptionEquals("Show basemap information")
        composeRule.onNodeWithText("Example map credit").assertDoesNotExist()

        composeRule.onNodeWithTag("weather_map_attribution_toggle").performClick()

        val creditText = composeRule.onNodeWithText("Example map credit", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .single()
        val creditLink = creditText.getLinkAnnotations(0, creditText.length).single().item
        assertTrue(creditLink is LinkAnnotation.Url)
        assertEquals("https://tiles.example.test/credits", (creditLink as LinkAnnotation.Url).url)
        composeRule.onNodeWithTag("weather_map_attribution_toggle")
            .assertContentDescriptionEquals("Hide basemap information")

        composeRule.onNodeWithTag("weather_map_attribution_toggle").performClick()

        composeRule.onNodeWithText("Example map credit").assertDoesNotExist()
        composeRule.onNodeWithTag("weather_map_attribution_toggle")
            .assertContentDescriptionEquals("Show basemap information")
    }

    @Test
    fun only_the_main_map_opts_into_collapsed_first_attribution() {
        val mapSource = mainSource("ui/map/WeatherMap.kt")

        assertTrue(mapSource.contains("collapsedAttributionInitially: Boolean = false"))
        assertTrue(mapSource.contains("if (collapsedAttributionInitially)"))
        assertTrue(mapSource.contains("include(MapOverlay.Default)"))
        assertTrue(mapSource.contains("DisappearingScaleBar("))
        assertTrue(mapSource.contains("DisappearingCompassButton("))
        assertTrue(mapSource.contains("MaplibreLogo("))
        assertTrue(mapSource.contains("mapStyle.attributions()"))
        assertTrue(mainSource("ui/weather/WeatherScreen.kt").contains("collapsedAttributionInitially = true"))
        assertFalse(mainSource("ui/locationpicker/LocationPickerScreen.kt").contains("collapsedAttributionInitially = true"))
        assertFalse(mainSource("ui/weatherroute/RouteEndpointPicker.kt").contains("collapsedAttributionInitially = true"))
    }

    private fun mainSource(path: String): String =
        File("app/src/main/java/net/droopia/hluweather/$path").takeIf { it.isFile }
            ?.readText() ?: File("src/main/java/net/droopia/hluweather/$path").readText()
}
