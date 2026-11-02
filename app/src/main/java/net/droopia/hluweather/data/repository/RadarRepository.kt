package net.droopia.hluweather.data.repository

import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import net.droopia.hluweather.data.cache.CachedRadarMetadata
import net.droopia.hluweather.data.cache.RadarMetadataCacheStore
import net.droopia.hluweather.data.network.LibreWxrRadarApi
import net.droopia.hluweather.data.network.LibreWxrRadarApiException
import net.droopia.hluweather.data.model.RadarMetadata
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

data class RadarMetadataState(
    val metadata: RadarMetadata? = null,
    val fetchedAt: Instant? = null,
    val isLoading: Boolean = true,
    /** A refresh failed, the snapshot expired, or its fetch time is in the future. */
    val isStale: Boolean = false,
    val error: Exception? = null
)

interface RadarRepository {
    /** Collect only while radar is visible; the last collector leaving cancels all radar work. */
    val state: StateFlow<RadarMetadataState>
}

data class RadarRefreshPolicy(
    val refreshInterval: Duration = 5.minutes,
    val requestTimeout: Duration = 15.seconds,
    val maxRetries: Int = 3,
    val initialRetryDelay: Duration = 1.seconds,
    val maxRetryDelay: Duration = 8.seconds
) {
    init {
        require(refreshInterval.isFinite() && refreshInterval > Duration.ZERO)
        require(requestTimeout.isFinite() && requestTimeout > Duration.ZERO)
        require(maxRetries in 0..5)
        require(initialRetryDelay.isFinite() && initialRetryDelay > Duration.ZERO)
        require(maxRetryDelay.isFinite() && maxRetryDelay >= initialRetryDelay)
    }
}

class LibreWxrRadarRepository(
    private val api: LibreWxrRadarApi,
    private val cache: RadarMetadataCacheStore,
    scope: CoroutineScope,
    private val policy: RadarRefreshPolicy = RadarRefreshPolicy(),
    private val now: () -> Instant = { Clock.System.now() },
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : RadarRepository {
    // The shared upstream has one writer, even across observer stop/start transitions.
    private var lastSuccessful: CachedRadarMetadata? = null

    override val state: StateFlow<RadarMetadataState> = observeMetadata()
        .flowOn(dispatcher)
        .stateIn(
            scope,
            // No requests/timers or replayed loading/error state survive the last observer.
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
            RadarMetadataState()
        )

    private fun observeMetadata(): Flow<RadarMetadataState> = flow {
        var cached = lastSuccessful ?: try {
            cache.get()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        lastSuccessful = cached
        var failure: Exception? = null

        // Returning to Map does not refetch a still-fresh snapshot on every visit.
        val remainingFreshness = cached?.remainingFreshness() ?: Duration.ZERO
        if (remainingFreshness > Duration.ZERO) {
            emit(cached.toState(isLoading = false))
            delay(remainingFreshness)
        }

        while (true) {
            emit(cached.toState(isLoading = true, error = failure))
            val fresh = try {
                CachedRadarMetadata(fetchWithRetry(), now())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failure = error
                null
            }
            if (fresh != null) {
                cached = fresh
                lastSuccessful = fresh
                failure = null
                try {
                    cache.put(fresh)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // A live result remains usable when persistence is unavailable.
                }
            }
            emit(cached.toState(isLoading = false, error = failure))
            // Even exhausted/permanent failures can recover on the next scheduled refresh.
            delay(policy.refreshInterval)
        }
    }

    private suspend fun fetchWithRetry(): RadarMetadata {
        var retries = 0
        var retryDelay = policy.initialRetryDelay
        while (true) {
            try {
                val metadata = withTimeoutOrNull(policy.requestTimeout) { api.metadata() }
                    ?: throw IOException("LibreWXR radar metadata request timed out")
                check(metadata.frames.isNotEmpty()) { "LibreWXR radar metadata contains no frames" }
                return metadata
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!error.isTransient() || retries >= policy.maxRetries) throw error
                delay(retryDelay)
                retries++
                retryDelay = (retryDelay * 2).coerceAtMost(policy.maxRetryDelay)
            }
        }
    }

    private fun Exception.isTransient(): Boolean = when (this) {
        is LibreWxrRadarApiException -> statusCode == 408 || statusCode == 429 ||
            statusCode in 500..599
        is IOException -> true
        else -> false
    }

    private fun CachedRadarMetadata.remainingFreshness(): Duration {
        val age = now() - fetchedAt
        return if (age >= Duration.ZERO && age < policy.refreshInterval) {
            policy.refreshInterval - age
        } else {
            Duration.ZERO
        }
    }

    private fun CachedRadarMetadata?.toState(
        isLoading: Boolean,
        error: Exception? = null
    ) = RadarMetadataState(
        metadata = this?.metadata,
        fetchedAt = this?.fetchedAt,
        isLoading = isLoading,
        isStale = this != null && (error != null || remainingFreshness() == Duration.ZERO),
        error = error
    )
}
