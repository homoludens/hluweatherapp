package net.droopia.hluweather.data.network

import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.compose.resource.MapResourceKind
import org.maplibre.compose.resource.MapResourceLoad
import org.maplibre.compose.resource.MapResourceLoadRequest
import org.maplibre.compose.resource.MapResourceRequest
import kotlin.time.Instant

class RadarMapResourceProviderTest {
    @Test
    fun onlyRadarAliasesRouteAndUnknownOrUnsupportedTilesFailClosed() = runTest {
        val registry = RadarTileRegistry()
        val source = registry.register("https://tiles.example.test", frame())
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        var attempts = 0
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ ->
            attempts++
            RadarTileHttpResult(200, RadarTilePayload(byteArrayOf(1), 100_000))
        }, { testScheduler.currentTime })
        val provider = RadarMapResourceProvider(registry, loader, scheduler)
        val valid = source.template.replace("{z}", "3").replace("{x}", "4").replace("{y}", "5")
        assertTrue(provider.accepts(MapResourceRequest(valid, MapResourceKind.Tile)))
        assertTrue(provider.accepts(MapResourceRequest(valid, MapResourceKind.Unknown)))
        listOf(MapResourceKind.Style, MapResourceKind.Source, MapResourceKind.Glyphs,
            MapResourceKind.SpriteJson, MapResourceKind.SpriteImage, MapResourceKind.Image).forEach {
            assertFalse(provider.accepts(MapResourceRequest(valid, it)))
        }
        assertFalse(provider.accepts(MapResourceRequest("https://base.example.test/3/4/5.png", MapResourceKind.Tile)))
        assertFalse(provider.accepts(MapResourceRequest("https://base.example.test/style.json", MapResourceKind.Style)))

        val invalid = listOf(
            source.template.replace("{z}", "8").replace("{x}", "4").replace("{y}", "5"),
            source.template.replace("{z}", "3").replace("{x}", "9").replace("{y}", "5"),
            "hlu-radar-volatile-v2://tile/${"0".repeat(64)}/3/4/5.png",
            "hlu-radar-volatile-v2://tile/not-registered/unsupported"
        )
        invalid.forEach {
            assertTrue(provider.accepts(MapResourceRequest(it, MapResourceKind.Tile)))
            assertTrue(provider.load(volatileRequest(it)) is MapResourceLoad.Failed)
        }
        assertEquals(0, attempts)
        val rejected = provider.load(MapResourceLoadRequest(valid, MapResourceKind.Tile))
        assertTrue(rejected is MapResourceLoad.Failed)
        assertEquals(0, attempts)
        val served = provider.load(volatileRequest(valid)) as MapResourceLoad.Bytes
        assertArrayEquals(byteArrayOf(1), served.bytes)
        assertEquals(1, attempts)
        // Even an already cached app response must never be returned to a permanent source.
        assertTrue(provider.load(MapResourceLoadRequest(valid, MapResourceKind.Tile)) is MapResourceLoad.Failed)
        assertEquals(1, attempts)
    }

    @Test
    fun nativePriorIsNotTrustedButAppCacheIsUsedAndCacheOnlyNeverStartsNetwork() = runTest {
        val registry = RadarTileRegistry()
        val source = registry.register("https://tiles.example.test", frame())
        val url = source.template.replace("{z}", "2").replace("{x}", "1").replace("{y}", "1")
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        var attempts = 0
        var receivedPrior: RadarTilePayload? = null
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, prior ->
            attempts++
            receivedPrior = prior
            RadarTileHttpResult(200, RadarTilePayload(byteArrayOf(5), 100_000, etag = "v1"))
        }, { testScheduler.currentTime })
        val provider = RadarMapResourceProvider(registry, loader, scheduler)
        val cacheOnly = provider.load(MapResourceLoadRequest(url, MapResourceKind.Tile,
            loadingMethod = MapResourceLoadRequest.LoadingMethod.CacheOnly,
            storagePolicy = MapResourceLoadRequest.StoragePolicy.Volatile))
        assertTrue(cacheOnly is MapResourceLoad.Failed)
        assertEquals(0, attempts)

        val result = provider.load(MapResourceLoadRequest(url, MapResourceKind.Tile,
            loadingMethod = MapResourceLoadRequest.LoadingMethod.NetworkOnly,
            storagePolicy = MapResourceLoadRequest.StoragePolicy.Volatile,
            priorEtag = "untrusted", priorData = byteArrayOf(3, 4))) as MapResourceLoad.Bytes
        assertArrayEquals(byteArrayOf(5), result.bytes)
        assertEquals(null, receivedPrior)
        assertEquals(1, attempts)
        assertTrue(provider.load(MapResourceLoadRequest(url, MapResourceKind.Tile,
            loadingMethod = MapResourceLoadRequest.LoadingMethod.NetworkOnly,
            storagePolicy = MapResourceLoadRequest.StoragePolicy.Volatile)) is MapResourceLoad.Bytes)
        assertEquals(1, attempts)
    }

    @Test
    fun noStoreIsNeverAdvertisedAsFreshToTheVolatileNativeSource() = runTest {
        val registry = RadarTileRegistry()
        val source = registry.register("https://tiles.example.test", frame())
        val url = source.template.replace("{z}", "2").replace("{x}", "1").replace("{y}", "1")
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        var attempts = 0
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ ->
            attempts++
            RadarTileHttpResult(200, RadarTilePayload(byteArrayOf(1), 100_000, cacheable = false))
        }, { testScheduler.currentTime })
        val provider = RadarMapResourceProvider(registry, loader, scheduler)
        repeat(2) {
            val result = provider.load(volatileRequest(url)) as MapResourceLoad.Bytes
            assertTrue(result.mustRevalidate)
            assertEquals(Instant.fromEpochMilliseconds(0), result.expires)
        }
        assertEquals(2, attempts)
        assertEquals(0, loader.cachedEntries)
    }

    @Test
    fun differentProvidersGetDistinctNativeCacheAliasesEvenForTheSameRemoteUrl() {
        val registry = RadarTileRegistry()
        val libre = registry.register("https://tiles.example.test", frame().copy(provider = RadarProvider.LIBREWXR))
        val rain = registry.register("https://tiles.example.test", frame())
        assertFalse(libre.template == rain.template)
        assertTrue(rain.template.startsWith("hlu-radar-volatile-v2://"))
    }

    @Test
    fun releasingAnObsoleteRegistrationCannotRemoveANewerRegistrationOfTheSameSource() {
        val registry = RadarTileRegistry()
        val old = registry.register("https://tiles.example.test", frame())
        val url = old.template.replace("{z}", "2").replace("{x}", "1").replace("{y}", "1")
        assertTrue(registry.resolve(url) != null)
        registry.unregister(old)
        assertEquals(null, registry.resolve(url))
        val replacement = registry.register("https://tiles.example.test", frame())
        registry.unregister(old)
        assertTrue(registry.resolve(url) != null)
        registry.unregister(replacement)
        assertEquals(null, registry.resolve(url))
    }

    @Test
    fun overlappingPlanAndNativeSlotLeasesKeepTheAliasValidUntilBothRelease() {
        val registry = RadarTileRegistry()
        val plan = registry.register("https://tiles.example.test", frame())
        val slot = registry.register("https://tiles.example.test", frame())
        val url = plan.template.replace("{z}", "2").replace("{x}", "1").replace("{y}", "1")
        assertEquals(plan.template, slot.template)
        assertEquals(1, registry.registeredCount)
        registry.unregister(plan)
        registry.unregister(plan) // a delayed duplicate release cannot consume the slot lease
        assertTrue(registry.resolve(url) != null)
        assertEquals(1, registry.registeredCount)
        registry.unregister(slot)
        assertEquals(null, registry.resolve(url))
        assertEquals(0, registry.registeredCount)
    }

    private fun volatileRequest(url: String) = MapResourceLoadRequest(
        url, MapResourceKind.Tile, storagePolicy = MapResourceLoadRequest.StoragePolicy.Volatile
    )

    private fun frame() = RadarFrame(1, "/radar/frame", RadarFrameType.OBSERVED, RadarProvider.RAINVIEWER)
}
