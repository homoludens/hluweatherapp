package net.droopia.hluweather.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

interface NotificationStateRepository {
    suspend fun wasDelivered(eventKey: String): Boolean

    suspend fun markDelivered(eventKey: String)
}

class DataStoreNotificationStateRepository(
    private val dataStore: DataStore<Preferences>
) : NotificationStateRepository {

    override suspend fun wasDelivered(eventKey: String): Boolean {
        val stateKeys = eventKey.stateKeys() ?: return false
        val preferences = dataStore.data.first()
        return eventKey in preferences[stateKeys.deliveredEventsKey].decodeEventKeys() ||
            preferences[stateKeys.legacyLastEventKey] == eventKey
    }

    override suspend fun markDelivered(eventKey: String) {
        val stateKeys = eventKey.stateKeys() ?: return
        dataStore.edit { preferences ->
            val existing = preferences[stateKeys.deliveredEventsKey].decodeEventKeys()
            val legacy = preferences[stateKeys.legacyLastEventKey]
            val delivered = (existing + listOfNotNull(legacy) + eventKey)
                .distinct()
                .takeLast(MAX_DELIVERED_EVENTS)
            preferences[stateKeys.deliveredEventsKey] = json.encodeToString(delivered)
            preferences.remove(stateKeys.legacyLastEventKey)
        }
    }
}

fun notificationStateRepository(context: Context): NotificationStateRepository =
    DataStoreNotificationStateRepository(context.notificationDataStore)

internal val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notifications"
)

private val weatherAlertEventKey = Regex("^weather-alert:([^:]+):(.+):(-?\\d+)$")

private fun String.stateKeys(): StateKeys? {
    val match = weatherAlertEventKey.matchEntire(this) ?: return null
    val provider = match.groupValues[1]
    val locationId = match.groupValues[2]
    return StateKeys(
        deliveredEventsKey = stringPreferencesKey("notification.delivered_events.$provider.$locationId"),
        legacyLastEventKey = stringPreferencesKey("notification.last_event.$provider.$locationId")
    )
}

private data class StateKeys(
    val deliveredEventsKey: Preferences.Key<String>,
    val legacyLastEventKey: Preferences.Key<String>
)

private fun String?.decodeEventKeys(): List<String> =
    this?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) }
        ?: emptyList()

private const val MAX_DELIVERED_EVENTS = 64
private val json = Json
