package net.droopia.hluweather.widget

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.ForecastLoad
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherWidgetRepositoryTest {

    @Test
    fun saved_mode_uses_canonical_active_saved_location_and_latest_name() = runTest {
        val selected = savedLocation("belgrade", "Belgrade", altitude = 117)
        val staleActive = selected.copy(name = "Old name")
        val other = savedLocation("trieste", "Trieste")

        val request = resolve(
            settings = PersistedSettings(selectedLocationId = other.id, trackMeEnabled = true),
            mode = LocationMode.SAVED_LOCATION,
            active = ActiveLocation.Saved(staleActive),
            locations = listOf(selected, other)
        )

        assertEquals(selected, (request?.location as? ActiveLocation.Saved)?.location)
        assertEquals("Belgrade", request?.locationName)
        assertEquals(
            WeatherWidgetRequestIdentity(
                WeatherProvider.OPEN_METEO,
                WeatherWidgetLocationIdentity.SavedLocation(
                    id = "belgrade",
                    latitude = 44.8176,
                    longitude = 20.4633,
                    altitude = 117
                )
            ),
            request?.identity
        )

        val renamed = resolve(
            mode = LocationMode.SAVED_LOCATION,
            active = ActiveLocation.Saved(selected.copy(name = "A newer name")),
            locations = listOf(selected.copy(name = "A newer name"), other)
        )
        assertEquals(request?.identity, renamed?.identity)
    }

    @Test
    fun saved_mode_uses_repository_fallback_after_deleted_selection_or_missing_active_location() = runTest {
        val deleted = savedLocation("deleted", "Deleted")
        val first = savedLocation("first", "First")
        val selected = savedLocation("selected", "Selected")

        val afterDelete = resolve(
            settings = PersistedSettings(selectedLocationId = deleted.id),
            mode = LocationMode.SAVED_LOCATION,
            active = ActiveLocation.Saved(deleted),
            locations = listOf(first, selected)
        )
        val withoutActive = resolve(
            settings = PersistedSettings(selectedLocationId = selected.id),
            mode = LocationMode.SAVED_LOCATION,
            active = null,
            locations = listOf(first, selected)
        )

        assertEquals(first, (afterDelete?.location as ActiveLocation.Saved).location)
        assertEquals(first, (withoutActive?.location as ActiveLocation.Saved).location)
    }

    @Test
    fun canonical_location_mode_wins_over_legacy_track_me_setting() = runTest {
        val saved = savedLocation("saved", "Saved")
        val current = ActiveLocation.Current(GeoPoint(45.0, 20.0))

        val savedMode = resolve(
            settings = PersistedSettings(trackMeEnabled = true),
            mode = LocationMode.SAVED_LOCATION,
            active = ActiveLocation.Saved(saved),
            passive = ActiveLocation.Current(GeoPoint(46.0, 21.0)),
            locations = listOf(saved)
        )
        val trackMeMode = resolve(
            settings = PersistedSettings(trackMeEnabled = false),
            mode = LocationMode.TRACK_ME,
            active = current,
            locations = listOf(saved)
        )

        assertEquals(saved, (savedMode?.location as ActiveLocation.Saved).location)
        assertEquals(current, trackMeMode?.location)
    }

    @Test
    fun track_me_prefers_valid_live_fix_then_passive_fix_and_uses_generic_label() = runTest {
        val saved = savedLocation("saved", "Saved")
        val live = ActiveLocation.Current(GeoPoint(45.0, 20.0), altitude = 123)
        val passive = ActiveLocation.Current(GeoPoint(46.0, 21.0), altitude = 456)

        val liveRequest = resolve(
            mode = LocationMode.TRACK_ME,
            active = live,
            passive = passive,
            locations = listOf(saved)
        )
        val passiveRequest = resolve(
            mode = LocationMode.TRACK_ME,
            active = null,
            passive = passive,
            locations = listOf(saved)
        )

        assertEquals(live, liveRequest?.location)
        assertEquals("Current location", liveRequest?.locationName)
        assertEquals(passive, passiveRequest?.location)
        assertEquals("Current location", passiveRequest?.locationName)
    }

    @Test
    fun invalid_live_and_passive_coordinates_are_skipped() = runTest {
        val selected = savedLocation("selected", "Selected")
        val liveInvalid = ActiveLocation.Current(GeoPoint(Double.NaN, 20.0))
        val passiveInvalid = ActiveLocation.Current(GeoPoint(91.0, 20.0))

        val request = resolve(
            settings = PersistedSettings(selectedLocationId = selected.id),
            mode = LocationMode.TRACK_ME,
            active = liveInvalid,
            passive = passiveInvalid,
            locations = listOf(selected)
        )

        assertEquals(selected, (request?.location as ActiveLocation.Saved).location)
    }

    @Test
    fun track_me_falls_back_to_last_selected_then_first_valid_saved_location() = runTest {
        val first = savedLocation("first", "First")
        val selected = savedLocation("selected", "Selected")
        val selectedRequest = resolve(
            settings = PersistedSettings(selectedLocationId = selected.id),
            mode = LocationMode.TRACK_ME,
            locations = listOf(first, selected)
        )
        val firstFallback = resolve(
            settings = PersistedSettings(selectedLocationId = "deleted"),
            mode = LocationMode.TRACK_ME,
            locations = listOf(first, selected)
        )

        assertEquals(selected, (selectedRequest?.location as ActiveLocation.Saved).location)
        assertEquals(first, (firstFallback?.location as ActiveLocation.Saved).location)
    }

    @Test
    fun invalid_saved_locations_and_missing_locations_resolve_to_no_location() = runTest {
        val invalid = savedLocation("invalid", "Invalid", latitude = -90.01)

        val noValidLocation = resolve(
            settings = PersistedSettings(selectedLocationId = invalid.id),
            mode = LocationMode.TRACK_ME,
            locations = listOf(invalid)
        )
        val noLocations = resolve(mode = LocationMode.SAVED_LOCATION)

        assertNull(noValidLocation)
        assertNull(noLocations)
    }

    @Test
    fun request_identity_uses_saved_id_coordinates_altitude_and_provider_but_not_label() = runTest {
        val location = savedLocation("belgrade", "Belgrade", altitude = 117)
        val request = resolve(
            settings = PersistedSettings(provider = WeatherProvider.MET_NO),
            mode = LocationMode.SAVED_LOCATION,
            active = ActiveLocation.Saved(location.copy(name = "Old label")),
            locations = listOf(location)
        )

        assertEquals(
            WeatherWidgetRequestIdentity(
                WeatherProvider.MET_NO,
                WeatherWidgetLocationIdentity.SavedLocation(
                    "belgrade", 44.8176, 20.4633, 117
                )
            ),
            request?.identity
        )

        val changedAltitude = resolve(
            settings = PersistedSettings(provider = WeatherProvider.MET_NO),
            mode = LocationMode.SAVED_LOCATION,
            active = ActiveLocation.Saved(location.copy(altitude = 118)),
            locations = listOf(location.copy(altitude = 118))
        )
        val changedCoordinates = resolve(
            settings = PersistedSettings(provider = WeatherProvider.MET_NO),
            mode = LocationMode.SAVED_LOCATION,
            active = ActiveLocation.Saved(location.copy(latitude = location.latitude + 0.0001)),
            locations = listOf(location.copy(latitude = location.latitude + 0.0001))
        )
        assertNotEquals(request?.identity, changedAltitude?.identity)
        assertNotEquals(request?.identity, changedCoordinates?.identity)
    }

    @Test
    fun current_request_identity_matches_repository_rounding_and_includes_altitude_and_provider() = runTest {
        val first = resolve(
            settings = PersistedSettings(provider = WeatherProvider.OPEN_METEO),
            mode = LocationMode.TRACK_ME,
            active = ActiveLocation.Current(GeoPoint(43.9001, 20.3001), altitude = 23)
        )
        val sameRoundedKey = resolve(
            settings = PersistedSettings(provider = WeatherProvider.OPEN_METEO),
            mode = LocationMode.TRACK_ME,
            active = ActiveLocation.Current(GeoPoint(43.9002, 20.3002), altitude = 23)
        )
        val changedAltitude = resolve(
            settings = PersistedSettings(provider = WeatherProvider.OPEN_METEO),
            mode = LocationMode.TRACK_ME,
            active = ActiveLocation.Current(GeoPoint(43.9002, 20.3002), altitude = 24)
        )
        val changedProvider = resolve(
            settings = PersistedSettings(provider = WeatherProvider.MET_NO),
            mode = LocationMode.TRACK_ME,
            active = ActiveLocation.Current(GeoPoint(43.9002, 20.3002), altitude = 23)
        )

        assertEquals(first?.identity, sameRoundedKey?.identity)
        assertEquals(
            WeatherWidgetLocationIdentity.CurrentLocation("current:43.900:20.300", 23),
            first?.identity?.location
        )
        assertNotEquals(first?.identity, changedAltitude?.identity)
        assertNotEquals(first?.identity, changedProvider?.identity)
    }

    @Test
    fun resolves_passive_fix_using_only_settings_and_location_repositories() = runTest {
        val passive = ActiveLocation.Current(GeoPoint(45.0, 20.0))

        val request = resolve(
            mode = LocationMode.TRACK_ME,
            passive = passive
        )

        assertEquals(passive, request?.location)
        assertEquals("Current location", request?.locationName)
    }

    private suspend fun resolve(
        settings: PersistedSettings = PersistedSettings(),
        mode: LocationMode = LocationMode.SAVED_LOCATION,
        active: ActiveLocation? = null,
        passive: ActiveLocation.Current? = null,
        locations: List<WeatherLocation> = emptyList()
    ): WeatherWidgetRequest? = WeatherWidgetRepository(
        settingsRepository = FakeSettingsRepository(settings),
        locationRepository = FakeLocationRepository(locations, active, mode, passive),
        weatherRepository = UnusedWeatherRepository
    ).resolveRequest()

    private fun savedLocation(
        id: String,
        name: String,
        latitude: Double = 44.8176,
        longitude: Double = 20.4633,
        altitude: Int? = null
    ) = WeatherLocation(id, name, latitude, longitude, altitude)

    private class FakeSettingsRepository(
        settingsValue: PersistedSettings
    ) : SettingsRepository {
        override val settings: Flow<PersistedSettings> = flowOf(settingsValue)
        override suspend fun save(settings: PersistedSettings) = Unit
    }

    private class FakeLocationRepository(
        locationsValue: List<WeatherLocation>,
        activeLocationValue: ActiveLocation?,
        locationModeValue: LocationMode,
        lastKnownCurrentLocationValue: ActiveLocation.Current?
    ) : LocationRepository {
        override val locations: Flow<List<WeatherLocation>> = flowOf(locationsValue)
        override val activeLocation: Flow<ActiveLocation?> = flowOf(activeLocationValue)
        override val locationMode: Flow<LocationMode> = flowOf(locationModeValue)
        override val lastKnownCurrentLocation: Flow<ActiveLocation.Current?> =
            flowOf(lastKnownCurrentLocationValue)

        override suspend fun add(location: WeatherLocation) = Unit
        override suspend fun update(location: WeatherLocation) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun selectSaved(id: String) = Unit
        override suspend fun setTrackMe(enabled: Boolean) = Unit
    }

    private object UnusedWeatherRepository : WeatherRepository {
        override suspend fun getForecast(
            provider: WeatherProvider,
            location: ActiveLocation
        ): ForecastLoad = error("Request resolution must not fetch weather")

        override suspend fun clearCache() = Unit
    }
}
