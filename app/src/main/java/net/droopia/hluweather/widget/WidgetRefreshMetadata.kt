package net.droopia.hluweather.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Presentation metadata only. Forecasts remain exclusively in WeatherRepository's cache. */
data class WidgetRefreshStatus(
    val startedAt: Instant? = null,
    val endedAt: Instant? = null,
    val isRefreshing: Boolean = false,
    val lastAttemptFailed: Boolean = false
)

/** Opaque token prevents an old completion from ending a newer attempt. */
data class WidgetRefreshAttempt internal constructor(val token: String)

interface WidgetRefreshMetadataStore {
    fun observe(identity: WeatherWidgetRequestIdentity, now: () -> Instant): Flow<WidgetRefreshStatus>
    suspend fun start(identity: WeatherWidgetRequestIdentity, now: Instant): WidgetRefreshAttempt
    suspend fun finish(identity: WeatherWidgetRequestIdentity, attempt: WidgetRefreshAttempt, now: Instant, failed: Boolean)
}

/** One bounded record, atomically replaced on each start. No forecast or location label is stored. */
class DataStoreWidgetRefreshMetadataStore(private val dataStore: DataStore<Preferences>) : WidgetRefreshMetadataStore {
    override fun observe(identity: WeatherWidgetRequestIdentity, now: () -> Instant): Flow<WidgetRefreshStatus> =
        dataStore.data.map { it.status(identity, now()) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(WidgetRefreshStatus())
            }.distinctUntilChanged()

    override suspend fun start(identity: WeatherWidgetRequestIdentity, now: Instant): WidgetRefreshAttempt {
        val attempt = WidgetRefreshAttempt(UUID.randomUUID().toString())
        try {
            dataStore.edit {
                it[identityKey] = identity.fingerprint()
                it[tokenKey] = attempt.token
                it[startKey] = now.toEpochMilliseconds()
                it.remove(endKey)
                it[failedKey] = false
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Weather remains usable even when status persistence is unavailable.
        }
        return attempt
    }

    override suspend fun finish(identity: WeatherWidgetRequestIdentity, attempt: WidgetRefreshAttempt, now: Instant, failed: Boolean) {
        try {
            dataStore.edit {
                if (it[identityKey] == identity.fingerprint() && it[tokenKey] == attempt.token && it[endKey] == null) {
                    it[endKey] = now.toEpochMilliseconds()
                    it[failedKey] = failed
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // An orphaned start expires on a later render.
        }
    }
}

internal object NoWidgetRefreshMetadataStore : WidgetRefreshMetadataStore {
    override fun observe(identity: WeatherWidgetRequestIdentity, now: () -> Instant): Flow<WidgetRefreshStatus> =
        kotlinx.coroutines.flow.flowOf(WidgetRefreshStatus())
    override suspend fun start(identity: WeatherWidgetRequestIdentity, now: Instant) = WidgetRefreshAttempt(UUID.randomUUID().toString())
    override suspend fun finish(identity: WeatherWidgetRequestIdentity, attempt: WidgetRefreshAttempt, now: Instant, failed: Boolean) = Unit
}

internal suspend fun WidgetRefreshMetadataStore.read(identity: WeatherWidgetRequestIdentity, now: () -> Instant): WidgetRefreshStatus =
    observe(identity, now).first()

private fun Preferences.status(identity: WeatherWidgetRequestIdentity, now: Instant): WidgetRefreshStatus {
    if (this[identityKey] != identity.fingerprint() || this[tokenKey] == null) return WidgetRefreshStatus()
    val start = this[startKey]?.let(Instant::fromEpochMilliseconds) ?: return WidgetRefreshStatus()
    val end = this[endKey]?.let(Instant::fromEpochMilliseconds)
    // Future start times (clock rollback/corruption) must not leave loading stuck either.
    val loading = end == null && now >= start && now - start < REFRESH_LOADING_TTL
    return WidgetRefreshStatus(start, end, loading, this[failedKey] == true || (end == null && !loading))
}

// Length prefixes avoid ambiguity for arbitrary saved IDs; coordinate bits preserve exact identity.
private fun WeatherWidgetRequestIdentity.fingerprint(): String {
    fun part(value: String) = "${value.length}:$value"
    val locationParts = when (val value = location) {
        is WeatherWidgetLocationIdentity.SavedLocation -> listOf("saved", value.id, value.latitude.toBits().toString(), value.longitude.toBits().toString(), value.altitude?.toString() ?: "null")
        is WeatherWidgetLocationIdentity.CurrentLocation -> listOf("current", value.cacheLocationKey, value.altitude?.toString() ?: "null")
    }
    val bytes = (listOf(provider.name) + locationParts).joinToString("") { part(it) }.toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

internal val REFRESH_LOADING_TTL = 15.minutes
private val identityKey = stringPreferencesKey("widget.refresh.identity")
private val tokenKey = stringPreferencesKey("widget.refresh.token")
private val startKey = longPreferencesKey("widget.refresh.start")
private val endKey = longPreferencesKey("widget.refresh.end")
private val failedKey = booleanPreferencesKey("widget.refresh.failed")
