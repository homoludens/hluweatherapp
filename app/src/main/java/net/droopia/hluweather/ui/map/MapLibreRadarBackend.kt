package net.droopia.hluweather.ui.map

import androidx.compose.runtime.snapshotFlow
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.droopia.hluweather.data.model.RadarProviderDescriptors
import net.droopia.hluweather.data.model.RadarTileCoverage
import net.droopia.hluweather.data.network.RadarMapResourceProvider
import net.droopia.hluweather.data.network.RadarNativeTileObservation
import net.droopia.hluweather.data.network.RadarTileLoader
import net.droopia.hluweather.data.network.RadarTileRegistry
import net.droopia.hluweather.data.network.RegisteredRadarSource
import net.droopia.hluweather.data.network.radarViewportBounds
import org.maplibre.compose.map.DelicateMapApi
import org.maplibre.compose.map.MapEvent
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.RenderStats
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.withPlatformMap
import org.maplibre.compose.sources.MutableSourceHandle
import org.maplibre.compose.sources.RasterTileSource
import org.maplibre.compose.sources.TileCoordinateSystem
import org.maplibre.compose.sources.TileSetOptions
import org.maplibre.compose.sources.isVolatile

/** Counts actual Full events locally; native frameCount can reset when a surface is recreated. */
internal class RadarFullRenderEvents {
    private val mutableOrdinal = MutableStateFlow(0L)
    val ordinal = mutableOrdinal.asStateFlow()

    fun record(event: MapEvent) {
        val stats = (event as? MapEvent.FrameRendered)?.stats
        if (stats?.mode == RenderStats.Mode.Full) mutableOrdinal.update { it + 1 }
    }
}

/** Explicitly owned radar resources; none are declared in Compose's style content. */
@OptIn(DelicateMapApi::class)
internal class MapLibreRadarBackend(
    private val mapState: MapState,
    private val registry: RadarTileRegistry,
    private val loader: RadarTileLoader,
    private val provider: RadarMapResourceProvider,
    scope: CoroutineScope
) : RadarRenderBackend {
    private data class NativeSlot(
        val sourceId: String,
        val layerId: String,
        val lease: RegisteredRadarSource,
        val handle: MutableSourceHandle,
        val observations: RadarNativeTileObservation
    )

    private val instance = instances.incrementAndGet()
    private val slots = mutableMapOf<Long, NativeSlot>()
    private val failedCreates = RadarFailedSourceCleanup()
    private val fullRenders = RadarFullRenderEvents()
    // Unconfined delivery records the event on the emitter's thread; it never invokes map commands.
    // This avoids mistaking an event waiting on the UI dispatcher for a new native render.
    private val eventJob: Job = scope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
        mapState.events.collect { event ->
            fullRenders.record(event)
        }
    }

    override fun create(slot: RadarRenderSlot) {
        failedCreates.retry()
        check(slot.id !in slots && !failedCreates.contains(slot.id)) { "Radar slot is already registered" }
        check(slots.size + failedCreates.size < 2) { "No free radar source slot" }
        val request = slot.request
        val lease = registry.register(request.config.host, request.config.frame)
        val sourceId = "hlu-radar-source-$instance-${slot.id}"
        val layerId = "hlu-radar-layer-$instance-${slot.id}"
        var observation: RadarNativeTileObservation? = null
        var handle: MutableSourceHandle? = null
        var addAttempted = false
        try {
            val keys = request.coordinates.map(lease::tile)
            observation = provider.observeNativeTiles(lease, keys)
            val source = radarRasterTileSource(sourceId, lease)
            addAttempted = true
            handle = mapState.style.sources.add(source)
            handle.isVolatile = true
            check(handle.isVolatile) { "Radar source did not become volatile" }
            slots[slot.id] = NativeSlot(sourceId, layerId, lease, handle, observation)
        } catch (error: Exception) {
            val release = { observation?.close(); registry.unregister(lease) }
            if (!addAttempted || (handle != null && runCatching { handle.remove() }.getOrDefault(false))) {
                release()
            } else {
                // A failed add may have partially installed a source even without returning a
                // handle. Never release its lease or allocate a third source on that assumption.
                val sourceHandle = handle
                failedCreates.retain(slot.id, remove = { sourceHandle?.remove() == true }, release = release)
            }
            throw error
        }
    }

    override suspend fun install(slot: RadarRenderSlot, opacity: Float, guard: RadarMutationGuard) {
        val native = slots.getValue(slot.id)
        mapState.withPlatformMap {
            val accepted = guard {
                checkReady(slot, native)
                check(!this.map.styleLayerExists(native.layerId))
                val before = this.map.styleLayerIds().firstOrNull { this.map.styleLayerType(it) == "symbol" }.orEmpty()
                val json = """{"id":"${native.layerId}","type":"raster","source":"${native.sourceId}",
                    "paint":{"raster-opacity":$opacity,"raster-fade-duration":0,
                    "raster-opacity-transition":{"duration":0,"delay":0}}}"""
                this.map.addStyleLayerJson(json.toByteArray(), before)
                this.map.requestRepaint()
            }
            if (!accepted) throw CancellationException("Obsolete radar installation")
        }
    }

    override suspend fun paint(slot: RadarRenderSlot, opacity: Float, guard: RadarMutationGuard) {
        val native = slots.getValue(slot.id)
        mapState.withPlatformMap {
            val accepted = guard {
                checkReady(slot, native, requireLayer = true, checkViewport = false)
                this.map.setLayerProperty(native.layerId, "raster-opacity", opacity.toString().toByteArray())
                this.map.requestRepaint()
            }
            if (!accepted) throw CancellationException("Obsolete radar paint")
        }
    }

    override suspend fun promote(
        next: RadarRenderSlot,
        previous: RadarRenderSlot?,
        opacity: Float,
        guard: RadarMutationGuard
    ) {
        val newNative = slots.getValue(next.id)
        val oldNative = previous?.let { slots.getValue(it.id) }
        mapState.withPlatformMap {
            val accepted = guard {
                checkReady(next, newNative, requireLayer = true)
                check(!newNative.observations.hasFailures) { "A native radar tile failed" }
                if (previous != null && oldNative != null) {
                    checkReady(previous, oldNative, requireLayer = true, checkViewport = false)
                }
                this.map.setLayerProperty(newNative.layerId, "raster-opacity", opacity.toString().toByteArray())
                try {
                    oldNative?.let { this.map.setLayerProperty(it.layerId, "raster-opacity", "0".toByteArray()) }
                } catch (error: Exception) {
                    this.map.setLayerProperty(newNative.layerId, "raster-opacity", RadarRenderController.STAGING_OPACITY.toString().toByteArray())
                    throw error
                }
                this.map.requestRepaint()
            }
            if (!accepted) throw CancellationException("Obsolete radar promotion")
        }
    }

    override suspend fun rollback(
        failed: RadarRenderSlot,
        previous: RadarRenderSlot?,
        opacity: Float,
        guard: RadarMutationGuard
    ) {
        val failedNative = slots.getValue(failed.id)
        val oldNative = previous?.let { slots.getValue(it.id) }
        mapState.withPlatformMap {
            val accepted = guard {
                check(mapState.style.loadState == StyleLoadState.Ready) { "Radar style is not ready" }
                // A missing failed layer is already hidden. Still restore the old slot when a
                // layer/source loss was the reason the post-promotion barrier failed.
                val failedLayerExists = this.map.styleLayerExists(failedNative.layerId)
                check(!failedLayerExists || this.map.layerSourceId(failedNative.layerId) == failedNative.sourceId) {
                    "Failed radar layer is stale"
                }
                // A changed viewport must not prevent restoring the last displayed imagery.
                if (previous != null && oldNative != null) {
                    checkReady(previous, oldNative, requireLayer = true, checkViewport = false)
                    this.map.setLayerProperty(oldNative.layerId, "raster-opacity", opacity.toString().toByteArray())
                }
                try {
                    if (failedLayerExists) {
                        this.map.setLayerProperty(failedNative.layerId, "raster-opacity", "0".toByteArray())
                    }
                } catch (error: Exception) {
                    // Leave the old promotion intact if hiding its new slot fails.
                    oldNative?.let { old ->
                        runCatching { this.map.setLayerProperty(old.layerId, "raster-opacity", "0".toByteArray()) }
                    }
                    throw error
                }
                this.map.requestRepaint()
            }
            if (!accepted) throw CancellationException("Obsolete radar rollback")
        }
    }

    override suspend fun awaitCoverage(slot: RadarRenderSlot) {
        val native = slots.getValue(slot.id)
        require(slot.request.coordinates.isNotEmpty()) { "Radar viewport has no tiles" }
        loader.awaitCoverage(slot.request.coordinates.map(native.lease::tile))
    }

    override fun createOptional(slot: RadarRenderSlot): Boolean {
        // Optional native source creation remains disabled until the backend consumes the
        // registration-scoped cache-only and ownership-transfer contract.
        return false
    }

    override suspend fun hasCachedCoverage(slot: RadarRenderSlot): Boolean {
        val native = slots[slot.id] ?: return false
        if (slot.request.coordinates.isEmpty()) return false
        if (!registry.allowsCachedReuse(native.lease, slot.request.coordinates)) return false
        if (!loader.hasFreshCachedCoverage(slot.request.coordinates.map(native.lease::tile))) return false
        return registry.allowsCachedReuse(native.lease, slot.request.coordinates)
    }

    override suspend fun adoptOptional(slot: RadarRenderSlot, guard: RadarMutationGuard): Boolean {
        // Keep adoption disabled until the backend transfers the exact registration under guard.
        return false
    }

    override suspend fun awaitFullRender(slot: RadarRenderSlot, viewport: RadarRenderRequest, guard: RadarMutationGuard) {
        // Legacy opt-in diagnostics only. Normal handoff and cleanup use coverage/accepted commands.
        val native = slots.getValue(slot.id)
        // WARNING: Full events are not tagged with a style revision. The renderer can replay an
        // old update after these owner-thread repaint requests, so these barriers alone do not
        // prove that this slot has rendered. Promotion can therefore still be premature.
        repeat(2) {
            var previous = -1L
            mapState.withPlatformMap {
                val accepted = guard {
                    checkReady(slot, native, requireLayer = true, viewport = viewport)
                    check(!native.observations.hasFailures) { "A native radar tile failed" }
                    previous = fullRenders.ordinal.value
                    this.map.requestRepaint()
                }
                if (!accepted) throw CancellationException("Obsolete radar render barrier")
            }
            fullRenders.ordinal.first { it > previous }
            mapState.withPlatformMap {
                val accepted = guard {
                    checkReady(slot, native, requireLayer = true, viewport = viewport)
                    check(!native.observations.hasFailures) { "A native radar tile failed" }
                }
                if (!accepted) throw CancellationException("Obsolete radar render")
            }
        }
    }

    override suspend fun remove(slot: RadarRenderSlot) {
        val native = slots[slot.id] ?: return
        if (!mapState.isClosed) {
            mapState.withPlatformMap {
                if (this.map.styleLayerExists(native.layerId) && this.map.layerSourceId(native.layerId) == native.sourceId) {
                    this.map.removeStyleLayer(native.layerId)
                    this.map.requestRepaint()
                }
            }
            // While a style is loading, its old handle cannot remove the source. Keep the lease
            // until the new style is ready and the old source is known to be gone.
            snapshotFlow { mapState.style.loadState == StyleLoadState.Ready || mapState.isClosed }.first { it }
            if (!mapState.isClosed) {
                val exists = mapState.withPlatformMap { this.map.styleSourceExists(native.sourceId) }
                if (exists) check(native.handle.remove()) { "Could not remove radar source" }
            }
        }
        if (mapState.isClosed) mapState.awaitClosed()
        native.observations.close()
        registry.unregister(native.lease)
        // A removal can suspend; never erase a different owner if the slot entry changed.
        if (slots[slot.id] === native) slots.remove(slot.id)
    }

    /** Release only after map destruction if Compose disposes the whole map. */
    suspend fun closeAfterMap() {
        eventJob.cancel()
        mapState.awaitClosed()
        slots.values.forEach { it.observations.close(); registry.unregister(it.lease) }
        slots.clear()
        failedCreates.releaseAfterMap()
    }

    private fun org.maplibre.compose.map.PlatformMapScope.checkReady(
        slot: RadarRenderSlot,
        native: NativeSlot,
        requireLayer: Boolean = false,
        checkViewport: Boolean = true,
        viewport: RadarRenderRequest = slot.request
    ) {
        check(mapState.style.loadState == StyleLoadState.Ready) { "Radar style is not ready" }
        val source = map.styleSourceInfo(native.sourceId)
        check(source?.volatileSource == true && source.tileJson?.tileUrls == listOf(native.lease.template)) {
            "Radar source is stale or not volatile"
        }
        if (requireLayer) {
            check(map.styleLayerExists(native.layerId) && map.layerSourceId(native.layerId) == native.sourceId) {
                "Radar layer is stale"
            }
        }
        if (checkViewport) {
            val currentViewport = requireNotNull(mapState.viewport) { "Radar viewport unavailable" }
            val current = RadarTileCoverage.tiles(
                radarViewportBounds(currentViewport, mapState.cameraPosition),
                RadarProviderDescriptors[viewport.config.frame.provider]
            ).sortedWith(tileOrder)
            check(current == viewport.coordinates.sortedWith(tileOrder)) { "Radar viewport changed" }
        }
    }

    companion object {
        private val instances = AtomicLong()
        internal val tileOrder = compareBy<net.droopia.hluweather.data.model.RadarTileCoordinate> { it.zoom }
            .thenBy { it.x }.thenBy { it.y }
    }
}

/** Failed native allocations retain their leases and consume capacity until confirmed removed. */
internal class RadarFailedSourceCleanup {
    private class Pending(val remove: () -> Boolean, val release: () -> Unit)
    private val pending = linkedMapOf<Long, Pending>()
    val size: Int get() = pending.size

    fun contains(slotId: Long): Boolean = slotId in pending

    fun retain(slotId: Long, remove: () -> Boolean, release: () -> Unit) {
        check(slotId !in pending) { "Radar cleanup is already retained" }
        pending[slotId] = Pending(remove, release)
    }

    fun retry() {
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next().value
            if (runCatching(item.remove).getOrDefault(false)) {
                item.release()
                iterator.remove()
            }
        }
    }

    fun releaseAfterMap() {
        pending.values.forEach { it.release() }
        pending.clear()
    }
}

/** Match app-side coverage and prefetch; the native source must never refine to detailed tiles. */
internal data class RadarRasterTileSourceConfig(
    val template: String,
    val tileSize: Int,
    val options: TileSetOptions
)

internal fun radarRasterTileSourceConfig(lease: RegisteredRadarSource): RadarRasterTileSourceConfig {
    val descriptor = RadarProviderDescriptors[lease.frame.provider]
    return RadarRasterTileSourceConfig(
        template = lease.template,
        tileSize = RadarTileCoverage.RENDER_TILE_SIZE,
        options = TileSetOptions(
            minZoom = descriptor.nativeMinZoom,
            maxZoom = RadarTileCoverage.renderMaxZoom(descriptor),
            tileCoordinateSystem = TileCoordinateSystem.XYZ
        )
    )
}

internal fun radarRasterTileSource(id: String, lease: RegisteredRadarSource): RasterTileSource {
    val config = radarRasterTileSourceConfig(lease)
    return RasterTileSource(
        id = id,
        tiles = listOf(config.template),
        tileSize = config.tileSize,
        options = config.options
    )
}
