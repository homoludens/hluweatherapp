package net.droopia.hluweather.data.cache

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.data.network.LibreWxrRadarColorScheme
import net.droopia.hluweather.data.network.LibreWxrRadarFrame
import net.droopia.hluweather.data.network.LibreWxrRadarFrameType
import net.droopia.hluweather.data.network.LibreWxrRadarMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Instant

class RadarMetadataCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun empty_cache_returns_null() = runTest {
        assertNull(RadarMetadataCache(dataStore(backgroundScope)).get())
    }

    @Test
    fun persisted_snapshot_round_trips_through_another_cache_instance_without_changing_preferences() = runTest {
        val store = dataStore(backgroundScope)
        val unrelatedKey = stringPreferencesKey("unrelated")
        store.edit { it[unrelatedKey] = "preserved" }
        RadarMetadataCache(store).put(entry)

        assertEquals(entry, RadarMetadataCache(store).get())
        assertEquals("preserved", store.data.first()[unrelatedKey])
    }

    @Test
    fun cache_keeps_only_latest_snapshot_and_its_original_fetch_time() = runTest {
        val cache = RadarMetadataCache(dataStore(backgroundScope))
        cache.put(entry)
        val next = entry.copy(fetchedAt = Instant.parse("2026-11-01T20:05:00Z"))
        cache.put(next)

        assertEquals(next, cache.get())
    }

    @Test
    fun corrupt_schema_timestamp_host_path_and_empty_frames_are_cache_misses() = runTest {
        val store = dataStore(backgroundScope)
        val cache = RadarMetadataCache(store)
        cache.put(entry)
        val serialized = requireNotNull(store.data.first()[cacheKey])
        val invalidEntries = listOf(
            "not JSON",
            "{}",
            serialized.replace("2026-11-01T20:00:00Z", "bad timestamp"),
            serialized.replace("https://tiles.librewxr.example.test", "http://tiles.librewxr.example.test"),
            serialized.replace("/v2/radar/1793563200", "//other.example.test/frame")
        )
        for (invalid in invalidEntries) {
            store.edit { it[cacheKey] = invalid }
            assertNull(cache.get())
        }
        cache.put(entry.copy(metadata = entry.metadata.copy(frames = emptyList())))
        assertNull(cache.get())
    }

    @Test
    fun wrong_preference_type_is_a_cache_miss() = runTest {
        val store = dataStore(backgroundScope)
        store.edit { it[intPreferencesKey(cacheKey.name)] = 123 }

        assertNull(RadarMetadataCache(store).get())
    }

    private fun dataStore(scope: CoroutineScope) = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { temporaryFolder.newFolder().resolve("radar.preferences_pb") }
    )

    private companion object {
        val cacheKey = stringPreferencesKey("radar.librewxr_metadata_cache")
        val entry = CachedRadarMetadata(
            metadata = LibreWxrRadarMetadata(
                host = "https://tiles.librewxr.example.test",
                frames = listOf(
                    LibreWxrRadarFrame(1_793_563_200L, "/v2/radar/1793563200", LibreWxrRadarFrameType.OBSERVED),
                    LibreWxrRadarFrame(1_793_563_800L, "/v2/radar/1793563800", LibreWxrRadarFrameType.FORECAST)
                ),
                colorSchemes = listOf(LibreWxrRadarColorScheme(2, "Universal Blue"))
            ),
            fetchedAt = Instant.parse("2026-11-01T20:00:00Z")
        )
    }
}
