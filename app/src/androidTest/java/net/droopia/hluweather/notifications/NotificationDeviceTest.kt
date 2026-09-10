package net.droopia.hluweather.notifications

import android.Manifest
import android.app.Activity
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
import androidx.work.WorkManager
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import net.droopia.hluweather.MainActivity
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.ui.settings.PersistedSettings
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
    private val testNotificationTag = "device_test_notification_$testId"
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

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        var launchedActivity: Activity? = null
        try {
            val publisher = AndroidWeatherNotificationPublisher(
                context = context,
                weatherAlertsChannelId = testWeatherChannelId,
                dailySummaryChannelId = testSummaryChannelId,
                notificationTag = testNotificationTag,
                dailySummaryNotificationId = testNotificationId
            )
            publisher.publishDailySummary("Daily weather summary", "Belgrade: Clear, current 20.0°C.")

            val notification = manager.activeNotifications
                .firstOrNull { it.tag == testNotificationTag && it.id == testNotificationId }
            assertNotNull("The notification published by this test was not found", notification)
            assertEquals(testNotificationTag, notification!!.tag)
            assertEquals(testNotificationId, notification.id)
            assertEquals(testSummaryChannelId, notification.notification.channelId)
            assertEquals(
                "Daily weather summary",
                notification.notification.extras.getCharSequence(Notification.EXTRA_TITLE)
            )
            val contentIntent = notification.notification.contentIntent
            assertNotNull(contentIntent)
            contentIntent!!.send()
            launchedActivity = monitor.waitForActivityWithTimeout(5_000)
            assertNotNull("Notification tap did not launch MainActivity", launchedActivity)
            assertFalse(launchedActivity!!.isFinishing)
        } finally {
            launchedActivity?.let { activity ->
                instrumentation.runOnMainSync { activity.finish() }
            }
            instrumentation.removeMonitor(monitor)
            manager.cancel(testNotificationTag, testNotificationId)
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

    @Test
    fun daily_summary_delivery_is_scheduled_with_the_selected_location() {
        val workManager = WorkManager.getInstance(context)
        val location = WeatherLocation("device-test-belgrade", "Belgrade", 44.8176, 20.4633)
        val settings = PersistedSettings(
            selectedLocationId = location.id,
            dailySummary = true,
            dailySummaryTime = LocalTime(8, 0)
        )
        workManager.cancelUniqueWork(NotificationScheduler.DAILY_SUMMARY_WORK_NAME).result.get()

        try {
            WorkManagerNotificationScheduler(
                workManager = workManager,
                alertWorkerClass = WeatherAlertWorker::class.java,
                summaryWorkerClass = DailySummaryWorker::class.java,
                clock = FixedClock,
                timeZone = TimeZone.UTC
            ).reconcile(settings, ActiveLocation.Saved(location))

            val scheduled = workManager
                .getWorkInfosForUniqueWork(NotificationScheduler.DAILY_SUMMARY_WORK_NAME)
                .get()
                .single()
            assertTrue(scheduled.tags.contains("notification.schedule.summary.OPEN_METEO.${location.id}.08:00"))
        } finally {
            workManager.cancelUniqueWork(NotificationScheduler.DAILY_SUMMARY_WORK_NAME).result.get()
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
        val FixedClock = object : kotlin.time.Clock {
            override fun now() = kotlin.time.Instant.parse("2026-09-10T00:00:00Z")
        }
    }
}
