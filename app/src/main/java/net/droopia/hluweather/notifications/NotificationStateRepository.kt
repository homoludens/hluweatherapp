package net.droopia.hluweather.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

interface NotificationStateRepository {
    suspend fun wasDelivered(eventKey: String): Boolean

    suspend fun markDelivered(eventKey: String)
}

class DataStoreNotificationStateRepository(
    private val dataStore: DataStore<Preferences>
) : NotificationStateRepository {

    override suspend fun wasDelivered(eventKey: String): Boolean {
        val stateKey = eventKey.stateKey() ?: return false
        return dataStore.data.first()[stateKey] == eventKey
    }

    override suspend fun markDelivered(eventKey: String) {
        val stateKey = eventKey.stateKey() ?: return
        dataStore.edit { preferences -> preferences[stateKey] = eventKey }
    }
}

fun notificationStateRepository(context: Context): NotificationStateRepository =
    DataStoreNotificationStateRepository(context.notificationDataStore)

internal val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notifications"
)

private val weatherAlertEventKey = Regex("^weather-alert:([^:]+):(.+):(-?\\d+)$")

private fun String.stateKey(): Preferences.Key<String>? {
    val match = weatherAlertEventKey.matchEntire(this) ?: return null
    val provider = match.groupValues[1]
    val locationId = match.groupValues[2]
    return stringPreferencesKey("notification.last_event.$provider.$locationId")
}
