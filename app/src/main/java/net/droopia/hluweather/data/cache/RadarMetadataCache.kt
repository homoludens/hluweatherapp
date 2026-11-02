package net.droopia.hluweather.data.cache

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.droopia.hluweather.data.model.RadarMetadata
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.network.RadarTileUrlBuilder
import kotlin.time.Instant

data class CachedRadarMetadata(
    val metadata: RadarMetadata,
    val fetchedAt: Instant,
    val provider: RadarProvider = metadata.provider
)

interface RadarMetadataCacheStore {
    suspend fun get(provider: RadarProvider): CachedRadarMetadata?
    suspend fun put(entry: CachedRadarMetadata)
}

/** Provider-scoped radar snapshots, separate from forecast caches and user preferences. */
class RadarMetadataCache(private val dataStore: DataStore<Preferences>) : RadarMetadataCacheStore {
    override suspend fun get(provider: RadarProvider): CachedRadarMetadata? {
        val preferences = dataStore.data.first()
        readString(preferences, providerKey(provider))
            ?.let { decodeEnvelope(it, provider) }
            ?.let { return it }

        if (provider != RadarProvider.LIBREWXR) return null
        val legacy = readString(preferences, legacyKey) ?: return null
        val migrated = decodeLegacy(legacy) ?: return null
        try {
            put(migrated)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A validated legacy snapshot remains usable if writing the new envelope fails.
        }
        return migrated
    }

    override suspend fun put(entry: CachedRadarMetadata) {
        validateMetadata(entry.metadata, entry.provider)
        val serialized = json.encodeToString(
            CacheEnvelope(
                schemaVersion = SCHEMA_VERSION,
                provider = entry.provider,
                metadata = entry.metadata,
                fetchedAt = entry.fetchedAt.toString()
            )
        )
        dataStore.edit { it[providerKey(entry.provider)] = serialized }
    }

    private fun decodeEnvelope(serialized: String, provider: RadarProvider): CachedRadarMetadata? =
        try {
            val entry = json.decodeFromString<CacheEnvelope>(serialized)
            if (entry.schemaVersion != SCHEMA_VERSION || entry.provider != provider) {
                null
            } else {
                validatedEntry(entry.metadata, entry.fetchedAt, provider)
            }
        } catch (_: Exception) {
            null
        }

    private fun decodeLegacy(serialized: String): CachedRadarMetadata? = try {
        val entry = json.decodeFromString<LegacyCacheEntry>(serialized)
        validatedEntry(entry.metadata, entry.fetchedAt, RadarProvider.LIBREWXR)
    } catch (_: Exception) {
        null
    }

    private fun validatedEntry(
        metadata: RadarMetadata,
        fetchedAt: String,
        provider: RadarProvider
    ): CachedRadarMetadata? = try {
        validateMetadata(metadata, provider)
        CachedRadarMetadata(metadata, Instant.parse(fetchedAt), provider)
    } catch (_: Exception) {
        null
    }

    private fun validateMetadata(metadata: RadarMetadata, provider: RadarProvider) {
        require(metadata.provider == provider) { "Radar cache provider does not match metadata" }
        require(metadata.frames.isNotEmpty()) { "Radar cache metadata contains no frames" }
        require(metadata.frames.all { it.provider == provider }) {
            "Radar cache frame provider does not match metadata"
        }
        RadarTileUrlBuilder.validateHost(metadata.host)
        metadata.frames.forEach { RadarTileUrlBuilder.validateFramePath(it.path) }
    }

    private fun readString(
        preferences: Preferences,
        key: Preferences.Key<String>
    ): String? =
        try {
            preferences[key]
        } catch (_: ClassCastException) {
            null
        }

    @Serializable
    private data class CacheEnvelope(
        val schemaVersion: Int,
        val provider: RadarProvider,
        val metadata: RadarMetadata,
        val fetchedAt: String
    )

    @Serializable
    private data class LegacyCacheEntry(val metadata: RadarMetadata, val fetchedAt: String)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val SCHEMA_VERSION = 1
        val legacyKey = stringPreferencesKey("radar.librewxr_metadata_cache")

        fun providerKey(provider: RadarProvider) = stringPreferencesKey(
            "radar.${provider.name.lowercase(Locale.ROOT)}_metadata_cache_v2"
        )
    }
}
