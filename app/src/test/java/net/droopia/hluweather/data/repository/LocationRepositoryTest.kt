package net.droopia.hluweather.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.ui.settings.DataStoreSettingsRepository
import net.droopia.hluweather.ui.settings.PersistedSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocationRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun new_store_has_no_saved_or_active_location() = runTest {
        val repository = repository(backgroundScope)

        assertEquals(emptyList<WeatherLocation>(), repository.locations.first())
        assertNull(repository.activeLocation.first())
    }

    @Test
    fun locations_persist_after_repository_recreation() = runTest {
        val file = temporaryFolder.newFile("locations.preferences_pb")
        val dataStore = dataStore(backgroundScope, file)
        val firstRepository = DataStoreLocationRepository(dataStore)
        firstRepository.add(belgrade)

        val recreatedRepository = DataStoreLocationRepository(dataStore)

        assertEquals(listOf(belgrade), recreatedRepository.locations.first())
        assertEquals(belgrade, (recreatedRepository.activeLocation.first() as ActiveLocation.Saved).location)
    }

    @Test
    fun update_replaces_the_saved_location() = runTest {
        val repository = repository(backgroundScope)
        repository.add(belgrade)

        val renamed = belgrade.copy(name = "Updated Belgrade", altitude = 117)
        repository.update(renamed)

        assertEquals(listOf(renamed), repository.locations.first())
        assertEquals(renamed, (repository.activeLocation.first() as ActiveLocation.Saved).location)
    }

    @Test
    fun deleting_active_location_selects_first_remaining_location() = runTest {
        val repository = repository(backgroundScope)
        repository.add(belgrade)
        repository.add(trieste)
        repository.selectSaved("belgrade")

        repository.delete("belgrade")

        assertEquals("trieste", (repository.activeLocation.first() as ActiveLocation.Saved).location.id)
    }

    @Test
    fun deleting_the_final_location_clears_the_active_location() = runTest {
        val repository = repository(backgroundScope)
        repository.add(belgrade)
        repository.selectSaved("belgrade")

        repository.delete("belgrade")

        assertNull(repository.activeLocation.first())
        assertEquals(emptyList<WeatherLocation>(), repository.locations.first())
    }

    @Test
    fun invalid_serialized_list_is_treated_as_empty() = runTest {
        val file = temporaryFolder.newFile("locations.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { file }
        )
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("locations.saved")] = "not-json"
        }

        val repository = DataStoreLocationRepository(dataStore)

        assertEquals(emptyList<WeatherLocation>(), repository.locations.first())
        assertNull(repository.activeLocation.first())
    }

    @Test
    fun track_me_preserves_the_selected_saved_location() = runTest {
        val repository = repository(backgroundScope)
        repository.add(belgrade)
        repository.selectSaved("belgrade")

        repository.setTrackMe(true)

        assertNull(repository.activeLocation.first())
        repository.setTrackMe(false)
        assertEquals("belgrade", (repository.activeLocation.first() as ActiveLocation.Saved).location.id)
    }

    @Test
    fun interleaved_settings_and_location_writes_keep_canonical_state() = runTest {
        val file = temporaryFolder.newFile("locations.preferences_pb")
        val dataStore = dataStore(backgroundScope, file)
        val locationRepository = DataStoreLocationRepository(dataStore)
        val settingsRepository = DataStoreSettingsRepository(dataStore)
        locationRepository.add(belgrade)
        locationRepository.add(trieste)
        locationRepository.selectSaved("trieste")

        settingsRepository.save(
            PersistedSettings(selectedLocationId = "belgrade", trackMeEnabled = true)
        )

        assertEquals("trieste", savedLocation(locationRepository).id)
        assertEquals(LocationMode.SAVED_LOCATION, locationRepository.locationMode.first())

        locationRepository.setTrackMe(true)
        settingsRepository.save(
            PersistedSettings(selectedLocationId = "belgrade", trackMeEnabled = false)
        )

        assertNull(locationRepository.activeLocation.first())
        assertEquals(LocationMode.TRACK_ME, locationRepository.locationMode.first())
        val recreatedRepository = DataStoreLocationRepository(dataStore)
        assertNull(recreatedRepository.activeLocation.first())
        assertEquals(LocationMode.TRACK_ME, recreatedRepository.locationMode.first())
    }

    @Test
    fun selecting_saved_location_disables_track_me() = runTest {
        val repository = repository(backgroundScope)
        repository.add(belgrade)
        repository.setTrackMe(true)

        repository.selectSaved("belgrade")

        assertEquals("belgrade", (repository.activeLocation.first() as ActiveLocation.Saved).location.id)
    }

    @Test
    fun selecting_saved_location_clears_the_previous_current_fix() = runTest {
        val repository = repository(backgroundScope)
        repository.add(belgrade)
        repository.setTrackMe(true)
        repository.setCurrentLocation(GeoPoint(44.8176, 20.4633))

        repository.selectSaved("belgrade")
        repository.setTrackMe(true)

        assertNull(repository.activeLocation.first())
    }

    private fun repository(
        scope: CoroutineScope,
        file: java.io.File = temporaryFolder.newFile("locations.preferences_pb")
    ): LocationRepository {
        return DataStoreLocationRepository(dataStore(scope, file))
    }

    private fun dataStore(scope: CoroutineScope, file: java.io.File) =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )

    private suspend fun savedLocation(repository: LocationRepository): WeatherLocation =
        (repository.activeLocation.first() as ActiveLocation.Saved).location

    private companion object {
        val belgrade = WeatherLocation(
            id = "belgrade",
            name = "Belgrade",
            latitude = 44.8176,
            longitude = 20.4633
        )
        val trieste = WeatherLocation(
            id = "trieste",
            name = "Trieste",
            latitude = 45.6495,
            longitude = 13.7768,
            altitude = 2
        )
    }
}
