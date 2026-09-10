package net.droopia.hluweather.notifications

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun notification_channels_are_created_with_expected_importance() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)

        NotificationChannels.create(context)

        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            manager.getNotificationChannel(NotificationChannels.WEATHER_ALERTS_CHANNEL_ID)?.importance
        )
        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            manager.getNotificationChannel(NotificationChannels.DAILY_SUMMARY_CHANNEL_ID)?.importance
        )
    }

    @Test
    fun published_summary_contains_a_tap_action() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        assumeTrue(AndroidNotificationPermissionChecker.isGranted(context))

        val publisher = AndroidWeatherNotificationPublisher(context)
        publisher.publishDailySummary("Daily weather summary", "Belgrade: Clear, current 20.0°C.")

        val notification = manager.activeNotifications
            .first { it.notification.channelId == NotificationChannels.DAILY_SUMMARY_CHANNEL_ID }
        assertNotNull(notification.notification.contentIntent)
        manager.cancel(notification.id)
    }
}
