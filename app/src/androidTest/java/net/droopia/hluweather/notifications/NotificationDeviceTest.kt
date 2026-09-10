package net.droopia.hluweather.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader

@RunWith(AndroidJUnit4::class)
class NotificationDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val testId = System.nanoTime().toString()
    private val testWeatherChannelId = "device_test_weather_$testId"
    private val testSummaryChannelId = "device_test_summary_$testId"
    private val testNotificationId = TEST_NOTIFICATION_ID

    @Test
    fun notification_channels_are_created_with_expected_importance() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        try {
            NotificationChannels.create(context, testWeatherChannelId, testSummaryChannelId)

            assertEquals(
                NotificationManager.IMPORTANCE_LOW,
                manager.getNotificationChannel(testWeatherChannelId)?.importance
            )
            assertEquals(
                NotificationManager.IMPORTANCE_LOW,
                manager.getNotificationChannel(testSummaryChannelId)?.importance
            )
        } finally {
            deleteTestChannels()
        }
    }

    @Test
    fun published_summary_contains_a_tap_action() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        assertNotificationDeliveryPermission()

        try {
            val publisher = AndroidWeatherNotificationPublisher(
                context = context,
                weatherAlertsChannelId = testWeatherChannelId,
                dailySummaryChannelId = testSummaryChannelId,
                dailySummaryNotificationId = testNotificationId
            )
            publisher.publishDailySummary("Daily weather summary", "Belgrade: Clear, current 20.0°C.")

            val notification = manager.activeNotifications
                .firstOrNull { it.id == testNotificationId }
            assertNotNull("The notification published by this test was not found", notification)
            assertEquals(testSummaryChannelId, notification!!.notification.channelId)
            assertEquals(
                "Daily weather summary",
                notification.notification.extras.getCharSequence(Notification.EXTRA_TITLE)
            )
            assertNotNull(notification.notification.contentIntent)
        } finally {
            manager.cancel(testNotificationId)
            deleteTestChannels()
        }
    }

    @Test
    fun notification_permission_is_an_explicit_delivery_prerequisite() {
        assertNotificationDeliveryPermission()
    }

    @Test
    fun denied_notification_permission_can_be_restored_for_delivery() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val wasGranted = hasPostNotificationsPermission()
        try {
            setPostNotificationsPermission(granted = false)
            assertFalse(hasPostNotificationsPermission())

            setPostNotificationsPermission(granted = true)
            assertTrue(hasPostNotificationsPermission())
        } finally {
            val restorationFailure = runCatching {
                setPostNotificationsPermission(granted = wasGranted)
            }.exceptionOrNull()
            assertEquals(
                "Notification permission was not restored",
                wasGranted,
                hasPostNotificationsPermission()
            )
            restorationFailure?.let { throw it }
        }
    }

    private fun assertNotificationDeliveryPermission() {
        assertTrue(
            "Notification delivery prerequisite is not met; grant notification permission and enable app notifications",
            AndroidNotificationPermissionChecker.isGranted(context)
        )
    }

    private fun hasPostNotificationsPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun setPostNotificationsPermission(granted: Boolean) {
        val action = if (granted) "grant" else "revoke"
        val command = "pm $action ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}" +
            "; printf '\\n$SHELL_EXIT_STATUS_MARKER=%s\\n' \"\$?\""
        val output = ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand(command)
        ).use { input ->
            BufferedReader(InputStreamReader(input)).readText()
        }
        val exitStatus = output.lineSequence()
            .lastOrNull { it.startsWith("$SHELL_EXIT_STATUS_MARKER=") }
            ?.substringAfter('=')
            ?.toIntOrNull()
        check(exitStatus == 0) {
            "Permission command failed with exit status $exitStatus: ${output.trim()}"
        }
    }

    private fun deleteTestChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.deleteNotificationChannel(testWeatherChannelId)
            manager.deleteNotificationChannel(testSummaryChannelId)
        }
    }

    private companion object {
        const val SHELL_EXIT_STATUS_MARKER = "NOTIFICATION_TEST_EXIT_STATUS"
        const val TEST_NOTIFICATION_ID = 2_000_001
    }
}
