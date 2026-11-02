package net.droopia.hluweather.data.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    val speculative: List<RadarTileKey>
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
    val requestedFrame = requireNotNull(metadata.frames.firstOrNull { it.identity(metadata.host) == requested })
    val coordinates = RadarTileCoverage.tiles(viewport, RadarProviderDescriptors[metadata.provider])
    val neighbors = RadarTimelineNeighborhoodPlanner.plan(metadata, requested)
    val demand = coordinates.map { radarTileKey(metadata.host, requestedFrame, it) }
    val speculative = neighbors.asSequence().flatMap { frame ->
        // Prefetch uses remote keys directly; only actual native sources need registrations.
        coordinates.asSequence().map { radarTileKey(metadata.host, frame, it) }
    }.distinct().take(RadarNetworkScheduler.MAX_SPECULATIVE_QUEUE).toList()
    return RadarTilePlan(
        requestedSource = registry.register(metadata.host, requestedFrame),
        demand = demand,
        speculative = speculative
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
        val demand: List<RadarTileKey>,
        val speculative: List<RadarTileKey>
    )
    private val lock = Any()
    private val jobs = linkedMapOf<RadarTileKey, Consumer>()
    private var currentPlan: PlanKey? = null

    /** Call after establishing demand consumers, so a promoted prefetch retains its shared fetch. */
    fun replan(plan: RadarTilePlan) {
        val planKey = PlanKey(plan.requestedSource.frame.identity(plan.requestedSource.host), plan.demand, plan.speculative)
        val wanted = plan.speculative.toSet()
        val cancel = mutableListOf<Job>()
        val start = mutableListOf<Job>()
        synchronized(lock) {
            if (currentPlan == planKey) return
            currentPlan = planKey
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
    }

    fun stop() {
        val cancel = synchronized(lock) {
            jobs.values.map { it.owner.cancel(); it.job }.also {
                jobs.clear()
                currentPlan = null
            }
        }
        cancel.forEach(Job::cancel)
    }
}
