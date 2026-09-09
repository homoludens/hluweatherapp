package net.droopia.hluweather.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.network.NominatimApi
import net.droopia.hluweather.data.network.usefulPlaceName

interface ReverseGeocoder {
    suspend fun reverse(point: GeoPoint): String?
}

class NominatimRateLimiter(
    private val intervalMillis: Long = 1_000,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) }
) {
    private val mutex = Mutex()
    private var lastRequestAt: Long? = null

    suspend fun <T> execute(block: suspend () -> T): T = mutex.withLock {
        val now = nowMillis()
        val waitMillis = lastRequestAt
            ?.let { (it + intervalMillis - now).coerceAtLeast(0) }
            ?: 0
        if (waitMillis > 0) {
            delayMillis(waitMillis)
        }
        lastRequestAt = nowMillis()
        block()
    }

    companion object {
        val Global = NominatimRateLimiter()
    }
}

class NominatimReverseGeocoder(
    private val api: NominatimApi,
    private val throttle: NominatimRateLimiter = NominatimRateLimiter.Global
) : ReverseGeocoder {

    override suspend fun reverse(point: GeoPoint): String? = try {
        throttle.execute {
            api.reverse(point).usefulPlaceName()
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        null
    }
}
