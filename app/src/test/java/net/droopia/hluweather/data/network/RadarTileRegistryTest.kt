package net.droopia.hluweather.data.network

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.Job
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarTileCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarTileRegistryTest {
    private val host = "https://tiles.example.test"
    private val frame = RadarFrame(1, "/radar/frame", RadarFrameType.OBSERVED, RadarProvider.RAINVIEWER)
    private val required = RadarTileCoordinate(5, 12, 13)
    private val parent = RadarTileCoordinate(4, 6, 6)

    @Test
    fun sameFrameRegistrationsAndRegistriesHaveSeparateAliasesButIdenticalRemoteKeys() {
        val registry = RadarTileRegistry()
        val demand = registry.register(host, frame)
        val optional = registry.registerOptional(host, frame, listOf(required))
        val otherMap = RadarTileRegistry().register(host, frame)
        assertEquals(3, setOf(demand.template, optional.template, otherMap.template).size)
        assertEquals(demand.tile(required), optional.tile(required))
        assertEquals(demand.tile(required), otherMap.tile(required))
        assertNull(registry.resolve(url(otherMap, required)))
        assertTrue(registry.allowsCachedReuse(optional, listOf(required)))
        assertFalse(registry.allowsCachedReuse(optional, listOf(parent)))
        assertTrue(runCatching { registry.registerOptional(host, frame, emptyList()) }.isFailure)
        assertTrue(runCatching {
            registry.registerOptional(host, frame, listOf(RadarTileCoordinate(5, 32, 0)))
        }.isFailure)
        assertEquals(2, registry.registeredCount)
    }

    @Test
    fun adoptionWaitsForRequiredCallbacksButNotParentsAndOldOwnerCannotReleaseAdoptedLease() {
        val registry = RadarTileRegistry()
        val optional = registry.registerOptional(host, frame, listOf(required))
        val pendingParent = requireNotNull(registry.beginRequest(url(optional, parent), nativeCacheOnly = false))
        val pendingRequired = requireNotNull(registry.beginRequest(url(optional, required), nativeCacheOnly = false))
        assertTrue(pendingParent.cacheOnly)
        assertTrue(pendingRequired.cacheOnly)
        var applied = false
        assertNull(adopt(registry, optional) { mutation -> mutation(); applied = true; true })
        assertFalse("A rejected transfer must not run the controller's on-applied bookkeeping", applied)
        assertTrue("An in-flight parent is not a required coverage failure",
            registry.allowsCachedReuse(optional, listOf(required)))
        assertTrue(registry.finishRequest(pendingRequired, failed = false))
        assertTrue(registry.allowsCachedReuse(optional, listOf(required)))

        // The caller executes this transfer within the current selection's mutation guard.
        var insideGuard = false
        var installed: RegisteredRadarSource? = null
        val guarded = registry.adoptOptional(optional, { mutation ->
            insideGuard = true
            try { mutation(); true } finally { insideGuard = false }
        }) { owner ->
            assertTrue("The exact slot must receive its new owner inside the selection guard", insideGuard)
            installed = owner
        }
        assertTrue(guarded)
        val demand = requireNotNull(installed)
        assertNotEquals(optional, demand)
        assertEquals(optional.template, demand.template)
        val parentJob = Job()
        assertTrue("A pre-adoption parent remains a cache-only consumer of this registration",
            registry.attachRequest(pendingParent, parentJob))
        assertTrue(pendingParent.cacheOnly)
        assertTrue(registry.finishRequest(pendingParent, failed = true))
        parentJob.complete()
        assertTrue(registry.allowsCachedReuse(demand, listOf(required)))
        assertFalse(registry.cancelOptional(optional))
        registry.unregister(optional)
        assertEquals(1, registry.registeredCount)
        assertFalse(registry.allowsCachedReuse(optional, listOf(required)))
        val later = requireNotNull(registry.beginRequest(url(demand, parent), nativeCacheOnly = false))
        assertFalse(later.cacheOnly)
        assertTrue(registry.finishRequest(later, failed = false))
        registry.unregister(demand)
        assertNull(registry.resolve(url(demand, required)))
    }

    @Test
    fun obsoleteGuardCannotGrantDemandAndRejectedTransferFailsClosed() {
        val registry = RadarTileRegistry()
        val optional = registry.registerOptional(host, frame, listOf(required))
        assertNull(adopt(registry, optional) { false })
        val request = requireNotNull(registry.beginRequest(url(optional, parent), false))
        assertTrue(request.cacheOnly)
        assertTrue(registry.finishRequest(request, failed = true))
        assertTrue(registry.allowsCachedReuse(optional, listOf(required)))

        // Defensive handling of an invalid guard that rejects after running the mutation.
        var installed: RegisteredRadarSource? = null
        assertFalse(registry.adoptOptional(optional, { mutation -> mutation(); false }) { installed = it })
        assertNull(registry.resolve(url(optional, required)))
        assertNull(adopt(registry, optional))
        assertEquals(1, registry.registeredCount)
        registry.unregister(optional)
        assertEquals(1, registry.registeredCount)
        registry.unregister(requireNotNull(installed))
        assertEquals(0, registry.registeredCount)
    }

    @Test
    fun cancelClaimsRemovalWithoutReleasingTheLeaseOrTouchingAnotherSource() {
        val registry = RadarTileRegistry()
        val optional = registry.registerOptional(host, frame, listOf(required))
        val other = registry.register(host, frame)
        val pending = requireNotNull(registry.beginRequest(url(optional, required), nativeCacheOnly = false))
        val job = Job()
        assertTrue(registry.attachRequest(pending, job))
        assertTrue(registry.cancelOptional(optional))
        assertTrue(job.isCancelled)
        assertEquals(2, registry.registeredCount) // native removal has not happened yet
        assertNull(adopt(registry, optional))
        assertNull(registry.resolve(url(optional, required)))
        assertFalse(registry.finishRequest(pending, failed = false))
        assertTrue(registry.cancelOptional(optional)) // repeated cancellation is harmless
        registry.unregister(optional)
        registry.unregister(optional)
        assertEquals(other.tile(required), registry.resolve(url(other, required)))
        assertEquals(1, registry.registeredCount)
        registry.unregister(other)
    }

    @Test
    fun failedRequiredCallbackCannotBeRepairedOrAdoptedByALateSuccess() {
        val registry = RadarTileRegistry()
        val optional = registry.registerOptional(host, frame, listOf(required))
        val older = requireNotNull(registry.beginRequest(url(optional, required), false))
        val newer = requireNotNull(registry.beginRequest(url(optional, required), false))
        assertTrue(registry.finishRequest(newer, failed = true))
        assertFalse(registry.finishRequest(older, failed = false))
        assertFalse(registry.allowsCachedReuse(optional, listOf(required)))
        assertNull(adopt(registry, optional))
        assertNull(registry.beginRequest(url(optional, required), false))
        assertTrue(registry.cancelOptional(optional))
        registry.unregister(optional)
        val recreated = registry.register(host, frame)
        assertNotEquals(optional.template, recreated.template)
        assertNull(registry.resolve(url(optional, required)))
        assertEquals(recreated.tile(required), registry.resolve(url(recreated, required)))
    }

    @Test
    fun attachAfterRemovalFailsClosedAndCannotCancelANewerOwner() {
        val registry = RadarTileRegistry()
        val old = registry.register(host, frame)
        val request = requireNotNull(registry.beginRequest(url(old, required), false))
        registry.unregister(old)
        val replacement = registry.register(host, frame)
        val job = Job()
        assertFalse(registry.attachRequest(request, job))
        assertFalse(registry.finishRequest(request, failed = true))
        registry.unregister(old)
        assertTrue(job.isActive)
        assertEquals(replacement.tile(required), registry.resolve(url(replacement, required)))
        job.cancel()
    }

    @Test
    fun adoptionAndOptionalCancellationAreMutuallyExclusiveAcrossThreads() {
        repeat(100) {
            val registry = RadarTileRegistry()
            val optional = registry.registerOptional(host, frame, listOf(required))
            val start = CountDownLatch(1)
            val adopted = AtomicReference<RegisteredRadarSource?>()
            val cancelled = AtomicReference<Boolean>()
            val a = thread { start.await(); adopted.set(adopt(registry, optional)) }
            val b = thread { start.await(); cancelled.set(registry.cancelOptional(optional)) }
            start.countDown()
            a.join(TimeUnit.SECONDS.toMillis(5))
            b.join(TimeUnit.SECONDS.toMillis(5))
            assertFalse(a.isAlive || b.isAlive)
            val demand = adopted.get()
            if (demand == null) {
                assertTrue(cancelled.get())
                assertNull(registry.resolve(url(optional, required)))
                registry.unregister(optional)
            } else {
                assertFalse(cancelled.get())
                registry.unregister(optional)
                assertEquals(demand.tile(required), registry.resolve(url(demand, required)))
                registry.unregister(demand)
            }
            assertEquals(0, registry.registeredCount)
        }
    }

    private fun url(source: RegisteredRadarSource, coordinate: RadarTileCoordinate): String = source.template
        .replace("{z}", coordinate.zoom.toString()).replace("{x}", coordinate.x.toString())
        .replace("{y}", coordinate.y.toString())

    private val currentGuard: ((() -> Unit) -> Boolean) = { mutation -> mutation(); true }

    private fun adopt(
        registry: RadarTileRegistry,
        source: RegisteredRadarSource,
        guard: ((() -> Unit) -> Boolean) = currentGuard
    ): RegisteredRadarSource? {
        var owner: RegisteredRadarSource? = null
        return if (registry.adoptOptional(source, guard) { owner = it }) owner else null
    }
}
