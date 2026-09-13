package net.droopia.hluweather.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import net.droopia.hluweather.data.model.WeatherProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NotificationStateRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun event_is_not_delivered_until_it_is_marked() = runTest {
        val repository = repository(backgroundScope)
        val event = event(WeatherProvider.OPEN_METEO, "belgrade", 1_780_000_000)

        assertFalse(repository.wasDelivered(event.key))

        repository.markDelivered(event.key)

        assertTrue(repository.wasDelivered(event.key))
    }

    @Test
    fun multiple_event_periods_are_retained_for_each_provider_and_location() = runTest {
        val repository = repository(backgroundScope)
        val first = event(WeatherProvider.OPEN_METEO, "belgrade", 1_780_000_000)
        val second = event(WeatherProvider.OPEN_METEO, "belgrade", 1_780_000_900)

        repository.markDelivered(first.key)
        repository.markDelivered(second.key)

        assertTrue(repository.wasDelivered(first.key))
        assertTrue(repository.wasDelivered(second.key))
    }

    @Test
    fun provider_and_location_have_separate_deduplication_state() = runTest {
        val repository = repository(backgroundScope)
        val openMeteo = event(WeatherProvider.OPEN_METEO, "belgrade", 1_780_000_000)
        val metNo = event(WeatherProvider.MET_NO, "belgrade", 1_780_000_000)
        val otherLocation = event(WeatherProvider.OPEN_METEO, "trieste", 1_780_000_000)

        repository.markDelivered(openMeteo.key)

        assertFalse(repository.wasDelivered(metNo.key))
        assertFalse(repository.wasDelivered(otherLocation.key))
    }

    private fun repository(scope: CoroutineScope): DataStoreNotificationStateRepository =
        DataStoreNotificationStateRepository(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(temporaryFolder.newFolder(), "notifications.preferences_pb") }
            )
        )

    private fun event(provider: WeatherProvider, locationId: String, epochSeconds: Long) =
        WeatherAlertEvent(provider, locationId, Instant.fromEpochSeconds(epochSeconds))
}
