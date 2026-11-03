package net.droopia.hluweather.data.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameIdentity
import net.droopia.hluweather.data.model.RadarMetadata
import net.droopia.hluweather.data.model.RadarProviderDescriptors
import net.droopia.hluweather.data.model.RadarTileCoverage
import net.droopia.hluweather.data.model.RadarTimelineNeighborhoodPlanner
import net.droopia.hluweather.data.model.RadarViewportBounds
import kotlin.coroutines.cancellation.CancellationException
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.Viewport

data class RadarTilePlan(
    val requestedSource: RegisteredRadarSource,
    val demand: List<RadarTileKey>,
    val speculative: List<RadarTileKey>,
    val nextFrame: RadarFrame?,
    val nextFrameCoverage: List<RadarTileKey>,
    val viewport: RadarViewportBounds,
    val metadataTimeline: List<RadarFrameIdentity>
)

/** A single cache-coverage opportunity for one plan generation; it is not renderer readiness. */
data class RadarNextFrameCoverageOpportunity(
    val generation: Long,
    val requestedFrame: RadarFrameIdentity,
    val nextFrame: RadarFrameIdentity,
    val viewport: RadarViewportBounds
)

/** The visible bounds already incorporate the map's actual size, rotation, tilt and world wrap. */
fun radarViewportBounds(viewport: Viewport, camera: CameraPosition): RadarViewportBounds =
    RadarViewportBounds(
        west = viewport.visibleBounds.west,
        south = viewport.visibleBounds.south,
        east = viewport.visibleBounds.east,
        north = viewport.visibleBounds.north,
        zoom = camera.zoom,
        centerLongitude = camera.target.longitude,
        centerLatitude = camera.target.latitude
    )

/**
 * The requested frame is exposed as demand; the 64 nearest neighbor tiles are best effort.
 * The caller owns requestedSource's registration until its plan/native slot no longer needs it.
 */
fun planRadarTiles(
    registry: RadarTileRegistry,
    metadata: RadarMetadata,
    requested: RadarFrameIdentity,
    viewport: RadarViewportBounds
): RadarTilePlan {
    require(metadata.provider == requested.provider && metadata.host == requested.host)
    val timeline = RadarTimelineNeighborhoodPlanner.orderedFrames(metadata)
    val requestedFrame = requireNotNull(timeline.firstOrNull { it.identity(metadata.host) == requested })
    val coordinates = RadarTileCoverage.tiles(viewport, RadarProviderDescriptors[metadata.provider])
    val neighbors = RadarTimelineNeighborhoodPlanner.plan(metadata, requested)
    val nextFrame = RadarTimelineNeighborhoodPlanner.nextFrame(metadata, requested)
    val demand = coordinates.map { radarTileKey(metadata.host, requestedFrame, it) }
    val speculative = neighbors.asSequence().flatMap { frame ->
        // Prefetch uses remote keys directly; only actual native sources need registrations.
        coordinates.asSequence().map { radarTileKey(metadata.host, frame, it) }
    }.distinct().take(RadarNetworkScheduler.MAX_SPECULATIVE_QUEUE).toList()
    val nextFrameCoverage = nextFrame?.let { frame ->
        coordinates.map { radarTileKey(metadata.host, frame, it) }.distinct()
    }.orEmpty()
    return RadarTilePlan(
        requestedSource = registry.register(metadata.host, requestedFrame),
        demand = demand,
        speculative = speculative,
        nextFrame = nextFrame,
        nextFrameCoverage = nextFrameCoverage,
        viewport = viewport,
        metadataTimeline = timeline.map { it.identity(metadata.host) }
    )
}

/** Owns only speculative consumers. It never changes the requested/displayed frame. */
class RadarPrefetchController(
    private val scope: CoroutineScope,
    private val loader: RadarTileLoader
) {
    private data class Consumer(val owner: RadarSpeculativeOwner, val job: Job)
    private data class PlanKey(
        val requested: RadarFrameIdentity,
        val metadataTimeline: List<RadarFrameIdentity>,
        val viewport: RadarViewportBounds,
        val demand: List<RadarTileKey>,
        val speculative: List<RadarTileKey>,
        val nextFrame: RadarFrameIdentity?,
        val nextFrameCoverage: List<RadarTileKey>
    )
    private data class ActivePlan(
        val key: PlanKey,
        val generation: Long,
        val nextFrame: RadarFrameIdentity?,
        val nextFrameCoverage: List<RadarTileKey>,
        var opportunityEmitted: Boolean = false
    )
    private data class CoverageCheck(
        val generation: Long,
        val requestedFrame: RadarFrameIdentity,
        val nextFrame: RadarFrameIdentity,
        val viewport: RadarViewportBounds,
        val coverage: List<RadarTileKey>
    )

    private val lock = Any()
    private val jobs = linkedMapOf<RadarTileKey, Consumer>()
    private val mutableNextFrameCoverageOpportunity = MutableStateFlow<RadarNextFrameCoverageOpportunity?>(null)
    private var currentPlan: ActivePlan? = null
    private var generation = 0L

    /** Emits once per matching plan generation when every next-frame coarse byte is fresh in cache. */
    val nextFrameCoverageOpportunity: StateFlow<RadarNextFrameCoverageOpportunity?> =
        mutableNextFrameCoverageOpportunity.asStateFlow()

    /**
     * Call after establishing demand consumers, so a promoted prefetch retains its shared fetch.
     * Returns the generation that owns this plan, including when its exact key is unchanged.
     */
    fun replan(plan: RadarTilePlan): Long {
        val requested = plan.requestedSource.frame.identity(plan.requestedSource.host)
        val nextFrame = plan.nextFrame?.identity(plan.requestedSource.host)
        val planKey = PlanKey(
            requested = requested,
            metadataTimeline = plan.metadataTimeline,
            viewport = plan.viewport,
            demand = plan.demand,
            speculative = plan.speculative,
            nextFrame = nextFrame,
            nextFrameCoverage = plan.nextFrameCoverage
        )
        val wanted = plan.speculative.toSet()
        val cancel = mutableListOf<Job>()
        val start = mutableListOf<Job>()
        val planGeneration: Long
        synchronized(lock) {
            if (currentPlan?.key == planKey) return requireNotNull(currentPlan).generation
            generation++
            planGeneration = generation
            currentPlan = ActivePlan(planKey, planGeneration, nextFrame, plan.nextFrameCoverage)
            mutableNextFrameCoverageOpportunity.value = null
            val iterator = jobs.iterator()
            while (iterator.hasNext()) {
                val (key, consumer) = iterator.next()
                if (key !in wanted || consumer.job.isCompleted) {
                    iterator.remove()
                    consumer.owner.cancel()
                    cancel.add(consumer.job)
                }
            }
            plan.speculative.forEach { key ->
                if (key !in jobs) {
                    val owner = RadarSpeculativeOwner()
                    val job = scope.launch(start = CoroutineStart.LAZY) {
                        try {
                            loader.load(key, speculative = true, speculativeOwner = owner)
                            publishCoverageOpportunity(completedKey = key)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            // Best effort; a later plan or foreground request can retry.
                        }
                    }
                    jobs[key] = Consumer(owner, job)
                    start.add(job)
                }
            }
        }
        cancel.forEach(Job::cancel)
        start.forEach(Job::start)
        // Also recognize a complete cache hit, including coverage keys outside the bounded
        // speculative queue. This check is once per changed plan, never a polling loop.
        scope.launch { publishCoverageOpportunity(expectedGeneration = planGeneration) }
        return planGeneration
    }

    fun stop() {
        val cancel = synchronized(lock) {
            jobs.values.map { it.owner.cancel(); it.job }.also {
                jobs.clear()
                currentPlan = null
                generation++
                mutableNextFrameCoverageOpportunity.value = null
            }
        }
        cancel.forEach(Job::cancel)
    }

    private suspend fun publishCoverageOpportunity(
        completedKey: RadarTileKey? = null,
        expectedGeneration: Long? = null
    ) {
        val check = synchronized(lock) {
            val active = currentPlan ?: return
            if (active.opportunityEmitted ||
                (expectedGeneration != null && active.generation != expectedGeneration) ||
                (completedKey != null && completedKey !in active.nextFrameCoverage)
            ) return
            val nextFrame = active.nextFrame ?: return
            if (active.nextFrameCoverage.isEmpty()) return
            CoverageCheck(
                generation = active.generation,
                requestedFrame = active.key.requested,
                nextFrame = nextFrame,
                viewport = active.key.viewport,
                coverage = active.nextFrameCoverage
            )
        }

        if (!loader.hasFreshCachedCoverage(check.coverage)) return

        synchronized(lock) {
            val active = currentPlan ?: return
            if (active.generation != check.generation || active.opportunityEmitted) return
            active.opportunityEmitted = true
            mutableNextFrameCoverageOpportunity.value = RadarNextFrameCoverageOpportunity(
                generation = check.generation,
                requestedFrame = check.requestedFrame,
                nextFrame = check.nextFrame,
                viewport = check.viewport
            )
        }
    }
}
