package net.droopia.hluweather.notifications

import android.app.NotificationManager
import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.time.Instant
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
            WeatherProvider.VISUAL_CROSSING,
            "belgrade",
            Instant.parse("2026-09-10T10:00:00Z")
        )
        val publisher = AndroidWeatherNotificationPublisher(context)

        publisher.publishAlert(
            event,
            "Alert",
            "Storm",
            attribution = event.provider.attribution?.notificationText
        )

        val notification = context
            .getSystemService(NotificationManager::class.java)
            .activeNotifications
            .single()
        assertEquals(1000, notification.id)
        assertEquals(event.key, notification.tag)
        val postedNotification = notification.notification
        assertEquals("Storm", postedNotification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals(
            "Weather data provided by Visual Crossing Weather",
            postedNotification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        )
        assertNotNull(postedNotification.contentIntent)
        assertNotNull(postedNotification.contentIntent!!.creatorPackage)
    }
}
