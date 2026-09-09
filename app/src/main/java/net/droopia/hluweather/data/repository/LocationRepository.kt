package net.droopia.hluweather.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.WeatherLocation
import java.io.IOException

interface LocationRepository {
    val locations: Flow<List<WeatherLocation>>
    val activeLocation: Flow<ActiveLocation?>
    suspend fun add(location: WeatherLocation)
    suspend fun update(location: WeatherLocation)
    suspend fun delete(id: String)
    suspend fun selectSaved(id: String)
    suspend fun setTrackMe(enabled: Boolean)
}

internal val Context.applicationDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

fun locationRepository(context: Context): LocationRepository =
    DataStoreLocationRepository(context.applicationDataStore)

class DataStoreLocationRepository(
    private val dataStore: DataStore<Preferences>
) : LocationRepository {

    private val snapshot = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val savedLocations = preferences[locationsKey]
                ?.let(::decodeLocations)
                ?: emptyList()
            Snapshot(
                locations = savedLocations,
                selectedId = preferences[selectedLocationIdKey],
                mode = preferences[locationModeKey]
                    ?.let { value -> LocationMode.entries.firstOrNull { it.name == value } }
                    ?: LocationMode.SAVED_LOCATION
            )
        }
        .distinctUntilChanged()

    override val locations: Flow<List<WeatherLocation>> = snapshot
        .map { it.locations }
        .distinctUntilChanged()

    override val activeLocation: Flow<ActiveLocation?> = snapshot.map { state ->
        if (state.mode == LocationMode.TRACK_ME) {
            null
        } else {
            state.locations
                .firstOrNull { it.id == state.selectedId }
                ?.let(ActiveLocation::Saved)
                ?: state.locations.firstOrNull()?.let(ActiveLocation::Saved)
        }
    }.distinctUntilChanged()

    override suspend fun add(location: WeatherLocation) {
        dataStore.edit { preferences ->
            val current = preferences.decodeLocations()
            val replacementIndex = current.indexOfFirst { it.id == location.id }
            val updated = if (replacementIndex == -1) {
                current + location
            } else {
                current.toMutableList().apply { set(replacementIndex, location) }
            }
            preferences[locationsKey] = encodeLocations(updated)
        }
    }

    override suspend fun update(location: WeatherLocation) {
        dataStore.edit { preferences ->
            val current = preferences.decodeLocations()
            val index = current.indexOfFirst { it.id == location.id }
            if (index >= 0) {
                preferences[locationsKey] = encodeLocations(
                    current.toMutableList().apply { set(index, location) }
                )
            }
        }
    }

    override suspend fun delete(id: String) {
        dataStore.edit { preferences ->
            val remaining = preferences.decodeLocations().filterNot { it.id == id }
            preferences[locationsKey] = encodeLocations(remaining)

            if (preferences[selectedLocationIdKey] == id ||
                preferences[selectedLocationIdKey] !in remaining.map(WeatherLocation::id)
            ) {
                val fallbackId = remaining.firstOrNull()?.id
                if (fallbackId == null) {
                    preferences.remove(selectedLocationIdKey)
                } else {
                    preferences[selectedLocationIdKey] = fallbackId
                }
            }
        }
    }

    override suspend fun selectSaved(id: String) {
        dataStore.edit { preferences ->
            if (preferences.decodeLocations().any { it.id == id }) {
                preferences[selectedLocationIdKey] = id
                preferences[locationModeKey] = LocationMode.SAVED_LOCATION.name
            }
        }
    }

    override suspend fun setTrackMe(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[locationModeKey] = if (enabled) {
                LocationMode.TRACK_ME.name
            } else {
                LocationMode.SAVED_LOCATION.name
            }
        }
    }

    private fun Preferences.decodeLocations(): List<WeatherLocation> =
        this[locationsKey]?.let(::decodeLocations) ?: emptyList()

    private fun decodeLocations(serialized: String): List<WeatherLocation> =
        runCatching {
            json.decodeFromString<List<PersistedLocation>>(serialized).map(PersistedLocation::toWeatherLocation)
        }.getOrDefault(emptyList())

    private fun encodeLocations(locations: List<WeatherLocation>): String =
        json.encodeToString(locations.map(WeatherLocation::toPersistedLocation))

    private data class Snapshot(
        val locations: List<WeatherLocation>,
        val selectedId: String?,
        val mode: LocationMode
    )
}

@Serializable
private data class PersistedLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Int?
) {
    fun toWeatherLocation() = WeatherLocation(id, name, latitude, longitude, altitude)
}

private fun WeatherLocation.toPersistedLocation() = PersistedLocation(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude
)

private val json = Json { ignoreUnknownKeys = true }
private val locationsKey = stringPreferencesKey("locations.saved")
private val selectedLocationIdKey = stringPreferencesKey("settings.selected_location_id")
private val locationModeKey = stringPreferencesKey("settings.location_mode")
