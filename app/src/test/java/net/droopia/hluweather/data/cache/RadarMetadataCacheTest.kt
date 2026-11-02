package net.droopia.hluweather.data.cache

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarMetadata
import net.droopia.hluweather.data.network.LibreWxrRadarColorScheme
import net.droopia.hluweather.data.network.LibreWxrRadarFrame
import net.droopia.hluweather.data.network.LibreWxrRadarFrameType
import net.droopia.hluweather.data.network.LibreWxrRadarMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Instant

class RadarMetadataCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun empty_cache_returns_null() = runTest {
        val cache = RadarMetadataCache(dataStore(backgroundScope))
        assertNull(cache.get(RadarProvider.LIBREWXR))
        assertNull(cache.get(RadarProvider.RAINVIEWER))
    }

    @Test
    fun persisted_snapshot_round_trips_through_another_cache_instance_without_changing_preferences() = runTest {
        val store = dataStore(backgroundScope)
        val unrelatedKey = stringPreferencesKey("unrelated")
        store.edit { it[unrelatedKey] = "preserved" }
        RadarMetadataCache(store).put(entry)

        assertEquals(entry, RadarMetadataCache(store).get(RadarProvider.LIBREWXR))
        assertEquals("preserved", store.data.first()[unrelatedKey])
    }

    @Test
    fun cache_keeps_only_latest_snapshot_and_its_original_fetch_time() = runTest {
        val cache = RadarMetadataCache(dataStore(backgroundScope))
        cache.put(entry)
        val next = entry.copy(fetchedAt = Instant.parse("2026-11-01T20:05:00Z"))
        cache.put(next)

        assertEquals(next, cache.get(RadarProvider.LIBREWXR))
    }

    @Test
    fun legacy_librewxr_snapshot_migrates_to_a_scoped_envelope_without_deleting_old_key() = runTest {
        val store = dataStore(backgroundScope)
        store.edit { it[legacyKey] = legacySnapshot }
        val cache = RadarMetadataCache(store)

        assertEquals(entry, cache.get(RadarProvider.LIBREWXR))

        val preferences = store.data.first()
        assertEquals(legacySnapshot, preferences[legacyKey])
        assertTrue(preferences[newLibreWxrKey] != null)
        assertNull(preferences[newRainViewerKey])
        assertNull(cache.get(RadarProvider.RAINVIEWER))
    }

    @Test
    fun provider_entries_are_isolated_and_written_to_different_keys() = runTest {
        val store = dataStore(backgroundScope)
        val cache = RadarMetadataCache(store)
        cache.put(entry)
        cache.put(rainViewerEntry)

        assertEquals(entry, cache.get(RadarProvider.LIBREWXR))
        assertEquals(rainViewerEntry, cache.get(RadarProvider.RAINVIEWER))
        val preferences = store.data.first()
        assertTrue(preferences[newLibreWxrKey] != null)
        assertTrue(preferences[newRainViewerKey] != null)
        assertNull(preferences[legacyKey])
    }

    @Test
    fun corrupt_timestamp_host_path_schema_and_provider_mismatch_are_cache_misses() = runTest {
        val store = dataStore(backgroundScope)
        val cache = RadarMetadataCache(store)
        cache.put(entry)
        val serialized = requireNotNull(store.data.first()[newLibreWxrKey])
        val invalidEntries = listOf(
            "not JSON",
            "{}",
            serialized.replace("2026-11-01T20:00:00Z", "bad timestamp"),
            serialized.replace("https://tiles.librewxr.example.test", "http://tiles.librewxr.example.test"),
            serialized.replace("/v2/radar/1793563200", "//other.example.test/frame"),
            serialized.replace("\"schemaVersion\":1", "\"schemaVersion\":99"),
            serialized.replace("\"provider\":\"LIBREWXR\"", "\"provider\":\"RAINVIEWER\"")
        )
        for (invalid in invalidEntries) {
            store.edit { it[newLibreWxrKey] = invalid }
            assertNull(cache.get(RadarProvider.LIBREWXR))
        }
        store.edit { it[newLibreWxrKey] = mismatchedFrameProviderEnvelope }
        assertNull(cache.get(RadarProvider.LIBREWXR))
        store.edit { it[newLibreWxrKey] = emptyFramesEnvelope }
        assertNull(cache.get(RadarProvider.LIBREWXR))
    }

    @Test
    fun wrong_preference_type_is_a_cache_miss() = runTest {
        val store = dataStore(backgroundScope)
        store.edit { it[intPreferencesKey(newLibreWxrKey.name)] = 123 }

        assertNull(RadarMetadataCache(store).get(RadarProvider.LIBREWXR))
    }

    private fun dataStore(scope: CoroutineScope) = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { temporaryFolder.newFolder().resolve("radar.preferences_pb") }
    )

    private companion object {
        val legacyKey = stringPreferencesKey("radar.librewxr_metadata_cache")
        val newLibreWxrKey = stringPreferencesKey("radar.librewxr_metadata_cache_v2")
        val newRainViewerKey = stringPreferencesKey("radar.rainviewer_metadata_cache_v2")
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
        val rainViewerEntry = CachedRadarMetadata(
            metadata = RadarMetadata(
                host = "https://tilecache.rainviewer.com",
                frames = listOf(
                    LibreWxrRadarFrame(
                        1_793_563_200L,
                        "/v2/radar/1793563200",
                        LibreWxrRadarFrameType.OBSERVED,
                        RadarProvider.RAINVIEWER
                    )
                ),
                provider = RadarProvider.RAINVIEWER
            ),
            fetchedAt = Instant.parse("2026-11-01T20:00:00Z")
        )
        val legacySnapshot = """
            {
              "metadata": {
                "host": "https://tiles.librewxr.example.test",
                "frames": [
                  {"timestampSeconds": 1793563200, "path": "/v2/radar/1793563200", "type": "OBSERVED"},
                  {"timestampSeconds": 1793563800, "path": "/v2/radar/1793563800", "type": "FORECAST"}
                ],
                "colorSchemes": [{"id": 2, "name": "Universal Blue"}]
              },
              "fetchedAt": "2026-11-01T20:00:00Z"
            }
        """.trimIndent()
        val mismatchedFrameProviderEnvelope = """
            {
              "schemaVersion": 1,
              "provider": "LIBREWXR",
              "metadata": {
                "host": "https://tiles.librewxr.example.test",
                "frames": [{
                  "timestampSeconds": 1793563200,
                  "path": "/v2/radar/1793563200",
                  "type": "OBSERVED",
                  "provider": "RAINVIEWER"
                }],
                "provider": "LIBREWXR"
              },
              "fetchedAt": "2026-11-01T20:00:00Z"
            }
        """.trimIndent()
        val emptyFramesEnvelope = """
            {
              "schemaVersion": 1,
              "provider": "LIBREWXR",
              "metadata": {
                "host": "https://tiles.librewxr.example.test",
                "frames": [],
                "provider": "LIBREWXR"
              },
              "fetchedAt": "2026-11-01T20:00:00Z"
            }
        """.trimIndent()
    }
}
