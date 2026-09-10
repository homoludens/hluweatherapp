package net.droopia.hluweather.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.model.WeatherProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidWeatherNotificationPublisherTest {

    @Test
    fun posted_notification_opens_the_main_activity_when_tapped() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val event = WeatherAlertEvent(
            WeatherProvider.OPEN_METEO,
            "belgrade",
            Instant.parse("2026-09-10T10:00:00Z")
        )
        val publisher = AndroidWeatherNotificationPublisher(context)

        publisher.publishAlert(
            event,
            "Alert",
            "Storm"
        )

        val notification = context
            .getSystemService(NotificationManager::class.java)
            .activeNotifications
            .single()
        assertEquals(1000, notification.id)
        assertEquals(event.key, notification.tag)
        val postedNotification = notification.notification
        assertNotNull(postedNotification.contentIntent)
        assertNotNull(postedNotification.contentIntent!!.creatorPackage)
    }
}
