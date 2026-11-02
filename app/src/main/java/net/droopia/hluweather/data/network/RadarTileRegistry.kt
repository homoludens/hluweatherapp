package net.droopia.hluweather.data.network

import java.security.MessageDigest
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
    internal val id: String
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
    private val lock = Any()
    private val sources = mutableMapOf<String, MutableSet<RegisteredRadarSource>>()
    internal val registeredCount: Int get() = synchronized(lock) { sources.size }

    /** Each call creates an independent lease, even when another owner uses the same source. */
    fun register(host: String, frame: RadarFrame): RegisteredRadarSource {
        val descriptor = RadarProviderDescriptors[frame.provider]
        val remoteTemplate = RadarTileUrlBuilder.buildTemplate(descriptor, host, frame.path)
        val identity = "${frame.provider.name}\u0000$remoteTemplate"
        val id = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val lease = RegisteredRadarSource(frame.provider, host, frame, id)
        synchronized(lock) { sources.getOrPut(id) { linkedSetOf() }.add(lease) }
        return lease
    }

    /** Release this lease when its plan or native slot no longer needs it; repeated release is safe. */
    fun unregister(source: RegisteredRadarSource) {
        synchronized(lock) {
            val owners = sources[source.id] ?: return
            owners.remove(source)
            if (owners.isEmpty()) sources.remove(source.id)
        }
    }

    fun isRadarUrl(url: String): Boolean = url.startsWith("$RADAR_SCHEME:", ignoreCase = true)

    /** Null means an unregistered, malformed or unsupported radar URL: never fetch it. */
    fun resolve(url: String): RadarTileKey? {
        val match = ALIAS_PATTERN.matchEntire(url) ?: return null
        val source = synchronized(lock) { sources[match.groupValues[1]]?.firstOrNull() } ?: return null
        val zoom = match.groupValues[2].toIntOrNull() ?: return null
        val x = match.groupValues[3].toIntOrNull() ?: return null
        val y = match.groupValues[4].toIntOrNull() ?: return null
        return try {
            source.tile(RadarTileCoordinate(zoom, x, y))
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        val ALIAS_PATTERN = Regex("^$RADAR_SCHEME://tile/([0-9a-f]{64})/([0-9]+)/([0-9]+)/([0-9]+)\\.png$")
    }
}

// Disjoint from experimental/older permanent-source aliases. Native cache hits bypass the hook.
private const val RADAR_SCHEME = "hlu-radar-volatile-v2"
