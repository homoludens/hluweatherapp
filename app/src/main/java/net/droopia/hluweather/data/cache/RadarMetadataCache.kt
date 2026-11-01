package net.droopia.hluweather.data.cache

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.droopia.hluweather.data.network.LibreWxrRadarMetadata
import net.droopia.hluweather.data.network.LibreWxrRadarTileUrlBuilder
import kotlin.time.Instant

data class CachedRadarMetadata(
    val metadata: LibreWxrRadarMetadata,
    val fetchedAt: Instant
)

interface RadarMetadataCacheStore {
    suspend fun get(): CachedRadarMetadata?
    suspend fun put(entry: CachedRadarMetadata)
}

/** One LibreWXR snapshot, separate from forecast caches and user preferences. */
class RadarMetadataCache(private val dataStore: DataStore<Preferences>) : RadarMetadataCacheStore {
    override suspend fun get(): CachedRadarMetadata? {
        val preferences = dataStore.data.first()
        return try {
            val serialized = preferences[entryKey] ?: return null
            val entry = json.decodeFromString<CacheEntry>(serialized)
            LibreWxrRadarTileUrlBuilder.validateHost(entry.metadata.host)
            entry.metadata.frames.forEach { LibreWxrRadarTileUrlBuilder.validateFramePath(it.path) }
            if (entry.metadata.frames.isEmpty()) return null
            CachedRadarMetadata(entry.metadata, Instant.parse(entry.fetchedAt))
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: ClassCastException) {
            null
        }
    }

    override suspend fun put(entry: CachedRadarMetadata) {
        val serialized = json.encodeToString(CacheEntry(entry.metadata, entry.fetchedAt.toString()))
        dataStore.edit { it[entryKey] = serialized }
    }

    @Serializable
    private data class CacheEntry(val metadata: LibreWxrRadarMetadata, val fetchedAt: String)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val entryKey = stringPreferencesKey("radar.librewxr_metadata_cache")
    }
}
