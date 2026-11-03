package net.droopia.hluweather.data.network

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarTileCoordinate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.compose.resource.MapResourceKind
import org.maplibre.compose.resource.MapResourceLoad
import org.maplibre.compose.resource.MapResourceLoadRequest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RadarNativeOwnershipTest {
    private val host = "https://tiles.example.test"
    private val frame = RadarFrame(1, "/radar/frame", RadarFrameType.OBSERVED, RadarProvider.RAINVIEWER)
    private val required = RadarTileCoordinate(5, 12, 13)
    private val parent = RadarTileCoordinate(4, 6, 6)

    @Test
    fun optionalParentsMissWithoutHttpOrCoverageFailureAndAdoptionAllowsLaterDemand() = runTest {
        val registry = RadarTileRegistry()
        val demandOnAnotherMap = registry.register(host, frame)
        val requiredBytes = RadarTileCoordinate(5, 13, 13)
        val planned = listOf(required, requiredBytes)
        val optional = registry.registerOptional(host, frame, planned)
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val attempts = mutableListOf<RadarTileKey>()
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { key, _ ->
            attempts += key
            if (key.coordinate == required) RadarTileHttpResult(204, RadarTilePayload(null, 100_000))
            else RadarTileHttpResult(200, RadarTilePayload(byteArrayOf(7), 100_000))
        }, { testScheduler.currentTime })
        val provider = RadarMapResourceProvider(registry, loader, scheduler)
        val observation = provider.observeNativeTiles(optional, planned.map(optional::tile))
        loader.load(demandOnAnotherMap.tile(required), speculative = true)
        loader.load(demandOnAnotherMap.tile(requiredBytes), speculative = true)
        assertEquals(2, attempts.size)

        for (method in MapResourceLoadRequest.LoadingMethod.entries) {
            assertTrue(provider.load(request(optional, parent, method)) is MapResourceLoad.Failed)
            assertTrue(registry.allowsCachedReuse(optional, planned))
            assertFalse(observation.hasFailures)
        }
        assertTrue(provider.load(request(optional, required, MapResourceLoadRequest.LoadingMethod.NetworkOnly)) is MapResourceLoad.NoContent)
        assertArrayEquals(byteArrayOf(7), (provider.load(request(optional, requiredBytes,
            MapResourceLoadRequest.LoadingMethod.NetworkOnly)) as MapResourceLoad.Bytes).bytes)
        assertEquals("Native preparation never fetches, even when network is hinted", 2, attempts.size)

        val adopted = requireNotNull(adopt(registry, optional))
        assertFalse(registry.cancelOptional(optional))
        registry.unregister(optional)
        assertArrayEquals(byteArrayOf(7), (provider.load(request(adopted, parent)) as MapResourceLoad.Bytes).bytes)
        assertEquals(3, attempts.size)
        assertFalse(observation.hasFailures)
        observation.close()
        registry.unregister(adopted)
        registry.unregister(demandOnAnotherMap)
    }

    @Test
    fun requiredMissesExpiryAndNoStorePermanentlyRejectOnlyThatPreparation() = runTest {
        val registry = RadarTileRegistry()
        val demand = registry.register(host, frame)
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        var cacheable = true
        var attempts = 0
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ ->
            attempts++
            RadarTileHttpResult(200, RadarTilePayload(byteArrayOf(attempts.toByte()),
                testScheduler.currentTime + 5, cacheable = cacheable))
        }, { testScheduler.currentTime })
        val provider = RadarMapResourceProvider(registry, loader, scheduler)

        // Same-frame foreground privilege does not apply to an unrequested registration.
        val absent = registry.registerOptional(host, frame, listOf(required))
        assertTrue(provider.load(request(absent, required, MapResourceLoadRequest.LoadingMethod.NetworkOnly)) is MapResourceLoad.Failed)
        assertEquals(0, attempts)
        assertNull(adopt(registry, absent))
        assertFalse(registry.allowsCachedReuse(absent, listOf(required)))
        assertTrue(provider.load(request(demand, required)) is MapResourceLoad.Bytes)
        assertEquals(1, attempts)
        assertNull(adopt(registry, absent)) // later prefetch cannot repair native failure
        registry.unregister(absent)

        advanceTimeBy(5)
        runCurrent()
        val expired = registry.registerOptional(host, frame, listOf(required))
        assertTrue(provider.load(request(expired, required, MapResourceLoadRequest.LoadingMethod.CacheOnly)) is MapResourceLoad.Failed)
        assertNull(adopt(registry, expired))
        assertEquals("Expired optional requests must not revalidate", 1, attempts)
        registry.unregister(expired)

        cacheable = false
        assertTrue(provider.load(request(demand, required)) is MapResourceLoad.Bytes)
        assertEquals(2, attempts)
        val noStore = registry.registerOptional(host, frame, listOf(required))
        assertTrue(provider.load(request(noStore, required, MapResourceLoadRequest.LoadingMethod.NetworkOnly)) is MapResourceLoad.Failed)
        assertNull(adopt(registry, noStore))
        assertEquals("No-store bytes cannot be used for preparation", 2, attempts)
        registry.unregister(noStore)

        // Removal followed by ordinary demand gets a fresh alias and normal HTTP behavior.
        val recreated = registry.register(host, frame)
        assertTrue(provider.load(request(recreated, required)) is MapResourceLoad.Bytes)
        assertEquals(3, attempts)
        assertTrue(provider.load(request(noStore, required)) is MapResourceLoad.Failed)
        assertEquals(3, attempts)
        registry.unregister(recreated)
        registry.unregister(demand)
    }

    @Test
    fun optionalCacheMissDoesNotJoinOrPromoteAnotherSourcesInflightForeground() = runTest {
        val registry = RadarTileRegistry()
        val demand = registry.register(host, frame)
        val optional = registry.registerOptional(host, frame, listOf(required))
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val gate = CompletableDeferred<Unit>()
        var attempts = 0
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ ->
            attempts++
            gate.await()
            RadarTileHttpResult(200, RadarTilePayload(byteArrayOf(4), 100_000))
        }, { testScheduler.currentTime })
        val provider = RadarMapResourceProvider(registry, loader, scheduler)
        val demandObservation = provider.observeNativeTiles(demand, listOf(demand.tile(required)))
        val foreground = backgroundScope.async { provider.load(request(demand, required)) }
        runCurrent()
        assertEquals(1, attempts)
        assertTrue(provider.load(request(optional, required, MapResourceLoadRequest.LoadingMethod.NetworkOnly)) is MapResourceLoad.Failed)
        assertEquals(1, attempts)
        assertNull(adopt(registry, optional))
        assertFalse(demandObservation.hasFailures)
        gate.complete(Unit)
        runCurrent()
        assertArrayEquals(byteArrayOf(4), (foreground.await() as MapResourceLoad.Bytes).bytes)
        assertTrue(provider.load(request(demand, required)) is MapResourceLoad.Bytes)
        assertEquals(1, attempts)
        demandObservation.close()
    }

    @Test
    fun optionalOwnerCannotAdoptOrDeliverAnAlreadyAdmittedCallbackAfterCancellation() = runTest {
        val registry = RadarTileRegistry()
        val optional = registry.registerOptional(host, frame, listOf(required))
        val other = registry.register(host, frame)
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holdCacheLookup = AtomicBoolean(false)
        var attempts = 0
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ ->
            attempts++
            RadarTileHttpResult(200, RadarTilePayload(byteArrayOf(8), 100_000))
        }, {
            if (holdCacheLookup.compareAndSet(true, false)) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            0L
        })
        val provider = RadarMapResourceProvider(registry, loader, scheduler)
        loader.load(other.tile(required))
        holdCacheLookup.set(true)
        val pending = backgroundScope.async(Dispatchers.Default) { provider.load(request(optional, required)) }
        try {
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            assertNull(adopt(registry, optional))
            assertTrue(registry.cancelOptional(optional))
            assertEquals(2, registry.registeredCount)
            registry.unregister(optional) // after native removal
        } finally {
            release.countDown()
        }
        assertTrue(pending.await() is MapResourceLoad.Failed)
        assertTrue(provider.load(request(optional, required)) is MapResourceLoad.Failed)
        assertArrayEquals(byteArrayOf(8), (provider.load(request(other, required)) as MapResourceLoad.Bytes).bytes)
        assertEquals("The optional callback must never start HTTP", 1, attempts)
        registry.unregister(other)
    }

    @Test
    fun parentCallbackInFlightDuringAdoptionStaysCacheOnlyAndCannotFailRequiredCoverage() = runTest {
        val registry = RadarTileRegistry()
        val optional = registry.registerOptional(host, frame, listOf(required))
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holdLookup = AtomicBoolean(false)
        val now = AtomicLong(0)
        var attempts = 0
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { key, _ ->
            attempts++
            RadarTileHttpResult(200, RadarTilePayload(byteArrayOf(6),
                if (key.coordinate == parent) now.get() + 5 else 100_000))
        }, {
            if (holdLookup.compareAndSet(true, false)) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            now.get()
        })
        val provider = RadarMapResourceProvider(registry, loader, scheduler)
        val observation = provider.observeNativeTiles(optional, listOf(optional.tile(required)))
        loader.load(optional.tile(required))
        loader.load(optional.tile(parent))
        now.set(5)
        holdLookup.set(true)
        val pending = backgroundScope.async(Dispatchers.Default) {
            provider.load(request(optional, parent, MapResourceLoadRequest.LoadingMethod.NetworkOnly))
        }
        lateinit var adopted: RegisteredRadarSource
        try {
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            assertTrue(registry.allowsCachedReuse(optional, listOf(required)))
            adopted = requireNotNull(adopt(registry, optional))
            assertFalse(registry.cancelOptional(optional))
            registry.unregister(optional)
        } finally {
            release.countDown()
        }
        assertTrue(pending.await() is MapResourceLoad.Failed)
        assertFalse(observation.hasFailures)
        assertEquals("A pre-adoption fallback may not inherit demand HTTP permission", 2, attempts)
        assertArrayEquals(byteArrayOf(6), (provider.load(request(adopted, parent)) as MapResourceLoad.Bytes).bytes)
        assertEquals(3, attempts)
        observation.close()
        registry.unregister(adopted)
    }

    @Test
    fun unregisterCancelsAQueuedNativeDemandBeforeItCanStartHttp() = runTest {
        val registry = RadarTileRegistry()
        val old = registry.register(host, frame)
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val gate = CompletableDeferred<Unit>()
        repeat(RadarNetworkScheduler.MAX_CONCURRENT) {
            backgroundScope.launch { scheduler.execute(RadarProvider.LIBREWXR) { gate.await() } }
        }
        runCurrent()
        var attempts = 0
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ ->
            attempts++
            RadarTileHttpResult(200, RadarTilePayload(byteArrayOf(5), 100_000))
        }, { testScheduler.currentTime })
        val provider = RadarMapResourceProvider(registry, loader, scheduler)
        val queued = backgroundScope.async { provider.load(request(old, required)) }
        runCurrent()
        assertEquals(0, attempts)
        registry.unregister(old)
        runCurrent()
        assertTrue(queued.await() is MapResourceLoad.Failed)
        gate.complete(Unit)
        runCurrent()
        assertEquals(0, attempts)
        val replacement = registry.register(host, frame)
        assertArrayEquals(byteArrayOf(5), (provider.load(request(replacement, required)) as MapResourceLoad.Bytes).bytes)
        assertEquals(1, attempts)
        registry.unregister(replacement)
    }

    @Test
    fun releasingOneConcurrentDemandCancelsOnlyItsConsumerAndDropsLateResults() = runTest {
        val registry = RadarTileRegistry()
        val old = registry.register(host, frame)
        val other = registry.register(host, frame)
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val gate = CompletableDeferred<Unit>()
        var attempts = 0
        var cancelledTransport = false
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ ->
            attempts++
            try { gate.await() } finally { if (!gate.isCompleted) cancelledTransport = true }
            RadarTileHttpResult(200, RadarTilePayload(byteArrayOf(9), 100_000))
        }, { testScheduler.currentTime })
        val provider = RadarMapResourceProvider(registry, loader, scheduler)
        val oldObservation = provider.observeNativeTiles(old, listOf(old.tile(required)))
        val otherObservation = provider.observeNativeTiles(other, listOf(other.tile(required)))
        val oldCall = backgroundScope.async { provider.load(request(old, required)) }
        val otherCall = backgroundScope.async { provider.load(request(other, required)) }
        runCurrent()
        assertEquals(1, attempts)
        registry.unregister(old)
        runCurrent()
        assertTrue(oldCall.await() is MapResourceLoad.Failed)
        assertFalse(cancelledTransport)
        assertFalse(otherCall.isCompleted)
        gate.complete(Unit)
        runCurrent()
        assertArrayEquals(byteArrayOf(9), (otherCall.await() as MapResourceLoad.Bytes).bytes)
        assertFalse(oldObservation.hasFailures)
        assertFalse(otherObservation.hasFailures)
        assertTrue(provider.load(request(old, required)) is MapResourceLoad.Failed)
        registry.unregister(old)
        assertTrue(provider.load(request(other, required)) is MapResourceLoad.Bytes)
        assertEquals(1, attempts)
        oldObservation.close()
        otherObservation.close()
        registry.unregister(other)
    }

    @Test
    fun nonCooperativeLateTransportCannotResurrectReleasedAliasOrReplaceNewCache() = runTest {
        val registry = RadarTileRegistry()
        val old = registry.register(host, frame)
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val gate = CompletableDeferred<Unit>()
        var attempts = 0
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ ->
            val attempt = ++attempts
            if (attempt == 1) withContext(NonCancellable) { gate.await() }
            RadarTileHttpResult(200, RadarTilePayload(byteArrayOf(attempt.toByte()), 100_000))
        }, { testScheduler.currentTime })
        val provider = RadarMapResourceProvider(registry, loader, scheduler)
        val call = backgroundScope.async { provider.load(request(old, required)) }
        runCurrent()
        registry.unregister(old)
        runCurrent()
        val replacement = registry.register(host, frame)
        val next = backgroundScope.async { provider.load(request(replacement, required)) }
        runCurrent()
        assertArrayEquals(byteArrayOf(2), (next.await() as MapResourceLoad.Bytes).bytes)
        gate.complete(Unit)
        runCurrent()
        assertTrue(call.await() is MapResourceLoad.Failed)
        assertArrayEquals(byteArrayOf(2), (provider.load(request(replacement, required)) as MapResourceLoad.Bytes).bytes)
        assertTrue(provider.load(request(old, required)) is MapResourceLoad.Failed)
        assertEquals(2, attempts)
    }

    @Test
    fun nativeFailuresAndLateResultsAreScopedToRegistrationAndObservation() = runTest {
        val registry = RadarTileRegistry()
        val first = registry.register(host, frame)
        val second = registry.register(host, frame)
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        var fail = true
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ ->
            if (fail) RadarTileHttpResult(404, RadarTilePayload(null, 100_000))
            else RadarTileHttpResult(204, RadarTilePayload(null, 100_000))
        }, { testScheduler.currentTime })
        val provider = RadarMapResourceProvider(registry, loader, scheduler)
        val firstObservation = provider.observeNativeTiles(first, listOf(first.tile(required)))
        val secondObservation = provider.observeNativeTiles(second, listOf(second.tile(required)))
        assertTrue(provider.load(request(first, required)) is MapResourceLoad.Failed)
        assertTrue(firstObservation.hasFailures)
        assertFalse(secondObservation.hasFailures)
        fail = false
        assertTrue(provider.load(request(second, required)) is MapResourceLoad.NoContent)
        assertTrue("Another map's success cannot clear this failure", firstObservation.hasFailures)
        assertFalse(secondObservation.hasFailures)

        // Results from older bridge calls must not overwrite a newer completed result.
        firstObservation.record(first, first.tile(required), 100, false)
        firstObservation.record(first, first.tile(required), 99, true)
        assertFalse(firstObservation.hasFailures)
        firstObservation.close()
        firstObservation.record(first, first.tile(required), 101, true)
        assertFalse(firstObservation.hasFailures)
        secondObservation.close()
    }

    @Test
    fun observationFollowsTheExactRegistrationAcrossOwnershipTransfer() = runTest {
        val registry = RadarTileRegistry()
        val optional = registry.registerOptional(host, frame, listOf(required))
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ ->
            RadarTileHttpResult(404, RadarTilePayload(null, 100_000))
        }, { testScheduler.currentTime })
        val provider = RadarMapResourceProvider(registry, loader, scheduler)
        val observation = provider.observeNativeTiles(optional, listOf(optional.tile(required)))
        val adopted = requireNotNull(adopt(registry, optional))
        assertTrue(provider.load(request(adopted, required)) is MapResourceLoad.Failed)
        assertTrue(observation.hasFailures)
        observation.close()
    }

    private fun request(
        source: RegisteredRadarSource,
        coordinate: RadarTileCoordinate,
        method: MapResourceLoadRequest.LoadingMethod = MapResourceLoadRequest.LoadingMethod.NetworkOnly
    ): MapResourceLoadRequest = MapResourceLoadRequest(
        source.template.replace("{z}", coordinate.zoom.toString()).replace("{x}", coordinate.x.toString())
            .replace("{y}", coordinate.y.toString()),
        MapResourceKind.Tile,
        loadingMethod = method,
        storagePolicy = MapResourceLoadRequest.StoragePolicy.Volatile
    )

    private val currentGuard: ((() -> Unit) -> Boolean) = { mutation -> mutation(); true }

    private fun adopt(registry: RadarTileRegistry, source: RegisteredRadarSource): RegisteredRadarSource? {
        var owner: RegisteredRadarSource? = null
        return if (registry.adoptOptional(source, currentGuard) { owner = it }) owner else null
    }
}
