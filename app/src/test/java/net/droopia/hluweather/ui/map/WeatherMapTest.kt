package net.droopia.hluweather.ui.map

import kotlinx.datetime.Instant
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.WeatherLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class WeatherMapTest {

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
}
