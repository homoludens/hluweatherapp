package net.droopia.hluweather.data.network

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Job
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarProviderDescriptors
import net.droopia.hluweather.data.model.RadarTileCoordinate

data class RadarTileKey internal constructor(
    val provider: RadarProvider,
    val url: String,
    val coordinate: RadarTileCoordinate
)

/** One ownership lease for a validated source and its non-network MapLibre URL. */
class RegisteredRadarSource internal constructor(
    val provider: RadarProvider,
    val host: String,
    val frame: RadarFrame,
    internal val id: String,
    internal val registration: Any = Any()
) {
    val template: String = "$RADAR_SCHEME://tile/$id/{z}/{x}/{y}.png"

    fun tile(coordinate: RadarTileCoordinate): RadarTileKey = radarTileKey(host, frame, coordinate)
}

internal fun radarTileKey(host: String, frame: RadarFrame, coordinate: RadarTileCoordinate): RadarTileKey =
    RadarTileKey(
        frame.provider,
        RadarTileUrlBuilder.build(
            RadarProviderDescriptors[frame.provider], host, frame.path,
            coordinate.zoom, coordinate.x, coordinate.y
        ),
        coordinate
    )

/**
 * MapLibre must never fall back to its own HTTP client for an unrecognized radar URL. The private
 * scheme gives every radar URL a fail-closed route, including unsupported coordinates and stale
 * registrations; only registered sources can be resolved to validated HTTPS destinations.
 */
class RadarTileRegistry {
    private enum class Role { DEMAND, OPTIONAL, FAILED, CANCELLED }
    private class AdoptionUnavailable : Exception()

    private class Entry(
        var owner: RegisteredRadarSource,
        var role: Role,
        val required: Set<RadarTileCoordinate>
    ) {
        val requests = mutableSetOf<NativeRequest>()
        var nextSequence = 0L
    }

    /** A request's role is fixed at admission, even if the registration is later adopted. */
    internal class NativeRequest internal constructor(
        val source: RegisteredRadarSource,
        val key: RadarTileKey,
        val optional: Boolean,
        val cacheOnly: Boolean,
        val observe: Boolean,
        val sequence: Long
    ) {
        var job: Job? = null
    }

    private val lock = Any()
    private val sources = mutableMapOf<String, Entry>()
    internal val registeredCount: Int get() = synchronized(lock) { sources.size }

    /** Each native source has its own alias and lease, regardless of its remote tile identity. */
    fun register(host: String, frame: RadarFrame): RegisteredRadarSource =
        register(host, frame, Role.DEMAND, emptySet())

    /** Optional registrations can only read fresh app-cache entries, including native fallbacks. */
    fun registerOptional(
        host: String,
        frame: RadarFrame,
        required: Collection<RadarTileCoordinate>
    ): RegisteredRadarSource {
        val coordinates = required.toSet()
        require(coordinates.isNotEmpty()) { "Optional radar source has no required tiles" }
        // Validate the complete planned set before allocating a lease.
        coordinates.forEach { radarTileKey(host, frame, it) }
        return register(host, frame, Role.OPTIONAL, coordinates)
    }

    private fun register(
        host: String,
        frame: RadarFrame,
        role: Role,
        required: Set<RadarTileCoordinate>
    ): RegisteredRadarSource {
        RadarTileUrlBuilder.buildTemplate(RadarProviderDescriptors[frame.provider], host, frame.path)
        val serial = nextAlias.incrementAndGet()
        check(serial > 0) { "Radar registration identifiers exhausted" }
        val id = processAlias + serial.toString(16).padStart(32, '0')
        return synchronized(lock) {
            // The process-wide serial never reuses an alias, even across registry instances.
            RegisteredRadarSource(frame.provider, host, frame, id).also {
                sources[id] = Entry(it, role, required)
            }
        }
    }

    /**
     * Release only after native removal/map destruction. An old optional owner cannot release an
     * adopted lease: adoption replaces the owner handle without changing the native alias.
     */
    fun unregister(source: RegisteredRadarSource) {
        val jobs = synchronized(lock) {
            val entry = sources[source.id] ?: return
            if (entry.owner !== source) return
            sources.remove(source.id)
            entry.requests.mapNotNull { it.job }
        }
        jobs.forEach { it.cancel() }
    }

    /**
     * Claim optional cleanup before native removal. A late optional cleanup cannot cancel demand;
     * the cancelled registration retains its lease until native removal actually succeeds.
     */
    fun cancelOptional(source: RegisteredRadarSource): Boolean {
        val jobs = synchronized(lock) {
            val entry = sources[source.id] ?: return false
            if (entry.owner !== source || entry.role == Role.DEMAND) return false
            entry.role = Role.CANCELLED
            entry.requests.mapNotNull { it.job }
        }
        jobs.forEach { it.cancel() }
        return true
    }

    /**
     * Transfer inside the current selection's mutation guard. The returned handle, not the old
     * optional handle, owns the same native registration. Store it in the exact native slot in
     * [onAdopted], while the selection guard is still held. Outstanding required-tile callbacks
     * must finish first; a failed required tile permanently rejects this preparation. Optional
     * parent callbacks remain cache-only and may finish after adoption. The guard must
     * serialize selection changes and run its mutation only for current demand. The callback must
     * only update slot ownership; it must not suspend, dispatch native work, or reenter the guard.
     */
    fun adoptOptional(
        source: RegisteredRadarSource,
        guard: ((() -> Unit) -> Boolean),
        onAdopted: (RegisteredRadarSource) -> Unit
    ): Boolean {
        var adopted: RegisteredRadarSource? = null
        var ownerPublished = false
        fun cancelUnexpectedTransfer() {
            val jobs = synchronized(lock) {
                val entry = sources[source.id]
                if (entry == null || adopted == null || entry.owner !== adopted) emptyList()
                else {
                    // A guard must not reject after executing this callback, but fail closed if
                    // it does. Keep whichever owner was installed in the native slot so cleanup
                    // can release the lease after native removal.
                    if (!ownerPublished) entry.owner = source
                    entry.role = Role.CANCELLED
                    entry.requests.mapNotNull { it.job }
                }
            }
            jobs.forEach { it.cancel() }
        }
        val accepted = try {
            guard {
                synchronized(lock) {
                    val entry = sources[source.id] ?: throw AdoptionUnavailable()
                    if (entry.owner !== source || entry.role != Role.OPTIONAL ||
                        entry.requests.any { it.optional && it.key.coordinate in entry.required }) {
                        // Do not run the guard's on-applied bookkeeping for a rejected transfer.
                        throw AdoptionUnavailable()
                    }
                    adopted = RegisteredRadarSource(source.provider, source.host, source.frame, source.id, source.registration)
                    entry.owner = requireNotNull(adopted)
                    entry.role = Role.DEMAND
                }
                // The short registry lock is released, but the selection guard is still held.
                adopted?.let {
                    onAdopted(it)
                    ownerPublished = true
                }
            }
        } catch (_: AdoptionUnavailable) {
            return false
        } catch (error: Throwable) {
            cancelUnexpectedTransfer()
            throw error
        }
        if (!accepted) {
            cancelUnexpectedTransfer()
            return false
        }
        return adopted != null
    }

    /** Recheck both the planned set and the registration's state around cache coverage checks. */
    fun allowsCachedReuse(source: RegisteredRadarSource, required: Collection<RadarTileCoordinate>): Boolean =
        synchronized(lock) {
            val entry = sources[source.id] ?: return false
            if (entry.owner !== source) return false
            when (entry.role) {
                Role.DEMAND -> true
                Role.OPTIONAL -> entry.required == required.toSet()
                Role.FAILED, Role.CANCELLED -> false
            }
        }

    /** Select the exact source and its effective role together, before any loader work starts. */
    internal fun beginRequest(url: String, nativeCacheOnly: Boolean): NativeRequest? {
        val match = ALIAS_PATTERN.matchEntire(url) ?: return null
        val zoom = match.groupValues[2].toIntOrNull() ?: return null
        val x = match.groupValues[3].toIntOrNull() ?: return null
        val y = match.groupValues[4].toIntOrNull() ?: return null
        return synchronized(lock) {
            val entry = sources[match.groupValues[1]] ?: return null
            if (entry.role != Role.DEMAND && entry.role != Role.OPTIONAL) return null
            val source = entry.owner
            val key = try {
                source.tile(RadarTileCoordinate(zoom, x, y))
            } catch (_: IllegalArgumentException) {
                return null
            }
            val optional = entry.role == Role.OPTIONAL
            NativeRequest(source, key, optional, optional || nativeCacheOnly,
                !optional && !nativeCacheOnly, ++entry.nextSequence).also { entry.requests.add(it) }
        }
    }

    /** Attach a private child job, so releasing one lease cannot cancel another lease's consumer. */
    internal fun attachRequest(request: NativeRequest, job: Job): Boolean = synchronized(lock) {
        val entry = sources[request.source.id] ?: return false
        if (request !in entry.requests || !entry.owns(request)) return false
        request.job = job
        true
    }

    private fun Entry.owns(request: NativeRequest): Boolean = when {
        owner === request.source -> role == if (request.optional) Role.OPTIONAL else Role.DEMAND
        // An already-admitted parent stays cache-only after the exact registration is adopted.
        request.optional && role == Role.DEMAND && owner.registration === request.source.registration ->
            request.key.coordinate !in required
        else -> false
    }

    /** Complete exactly once. Failure reporting and lease validation share this short lock. */
    internal fun finishRequest(
        request: NativeRequest,
        failed: Boolean,
        cancelled: Boolean = false,
        onDemandResult: (NativeRequest, Boolean) -> Unit = { _, _ -> }
    ): Boolean = synchronized(lock) {
        val entry = sources[request.source.id] ?: return false
        if (!entry.requests.remove(request) || !entry.owns(request)) return false
        if (request.optional) {
            if (entry.role == Role.OPTIONAL && (failed || cancelled) && request.key.coordinate in entry.required) {
                entry.role = Role.FAILED
            }
        } else {
            if (request.observe && !cancelled) onDemandResult(request, failed)
        }
        true
    }

    fun isRadarUrl(url: String): Boolean = url.startsWith("$RADAR_SCHEME:", ignoreCase = true)

    /** Null means an unregistered, malformed or unsupported radar URL: never fetch it. */
    fun resolve(url: String): RadarTileKey? {
        val match = ALIAS_PATTERN.matchEntire(url) ?: return null
        val zoom = match.groupValues[2].toIntOrNull() ?: return null
        val x = match.groupValues[3].toIntOrNull() ?: return null
        val y = match.groupValues[4].toIntOrNull() ?: return null
        return synchronized(lock) {
            val entry = sources[match.groupValues[1]] ?: return null
            if (entry.role != Role.DEMAND && entry.role != Role.OPTIONAL) return null
            try {
                entry.owner.tile(RadarTileCoordinate(zoom, x, y))
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    private companion object {
        // A fresh process prefix also separates aliases from previous runtime/cache lifetimes.
        val processAlias = UUID.randomUUID().toString().replace("-", "")
        val nextAlias = AtomicLong()
        val ALIAS_PATTERN = Regex("^$RADAR_SCHEME://tile/([0-9a-f]{64})/([0-9]+)/([0-9]+)/([0-9]+)\\.png$")
    }
}

// Disjoint from experimental/older permanent-source aliases. Native cache hits bypass the hook.
private const val RADAR_SCHEME = "hlu-radar-volatile-v2"
