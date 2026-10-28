package net.droopia.hluweather.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.impl.WorkManagerImpl
import net.droopia.hluweather.notifications.NotificationScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherWidgetUpdaterTest {

    private lateinit var workManager: WorkManager
    private lateinit var smallWidgetIds: Set<Int>
    private lateinit var mediumWidgetIds: Set<Int>
    private lateinit var updater: WeatherWidgetUpdater

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().build()
        )
        workManager = WorkManager.getInstance(context)
        smallWidgetIds = emptySet()
        mediumWidgetIds = emptySet()
        updater = WeatherWidgetUpdater(
            workManager = workManager,
            workerClass = TestWidgetWorker::class.java,
            hasInstalledWidgets = { smallWidgetIds.isNotEmpty() || mediumWidgetIds.isNotEmpty() }
        )
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork()
    }

    @Test
    fun all_widget_instances_share_one_hourly_connected_periodic_request() {
        smallWidgetIds = setOf(1, 2)
        mediumWidgetIds = setOf(3)

        updater.reconcile()
        val first = periodicWork().single()
        updater.reconcile()
        smallWidgetIds = setOf(1, 2, 4)
        updater.reconcile()

        val requests = periodicWork()
        assertEquals(1, requests.size)
        assertEquals(first.id, requests.single().id)
        assertEquals(
            TimeUnit.MINUTES.toMillis(WeatherWidgetUpdater.REFRESH_INTERVAL_MINUTES),
            requests.single().periodicityInfo?.repeatIntervalMillis
        )
        assertEquals(NetworkType.CONNECTED, requests.single().constraints.requiredNetworkType)
        assertEquals(WorkInfo.State.ENQUEUED, requests.single().state)
        assertFalse(
            WeatherWidgetUpdater.PERIODIC_REFRESH_WORK_NAME in notificationWorkNames ||
                WeatherWidgetUpdater.MANUAL_REFRESH_WORK_NAME in notificationWorkNames
        )
        assertFalse(
            WeatherWidgetUpdater.PERIODIC_REFRESH_WORK_NAME == WeatherWidgetUpdater.MANUAL_REFRESH_WORK_NAME
        )
    }

    @Test
    fun manual_refresh_is_unique_coalesced_and_has_no_network_constraint() {
        mediumWidgetIds = setOf(8)

        updater.enqueueManualRefresh()
        val first = manualWork().single()
        updater.enqueueManualRefresh()
        updater.enqueueManualRefresh()

        val requests = manualWork()
        assertEquals(1, requests.size)
        assertEquals(first.id, requests.single().id)
        assertEquals(NetworkType.NOT_REQUIRED, requests.single().constraints.requiredNetworkType)
        assertEquals(WorkInfo.State.ENQUEUED, requests.single().state)
    }

    @Test
    fun scheduled_requests_distinguish_manual_input_and_use_bounded_backoff() {
        smallWidgetIds = setOf(1)
        updater.reconcile()
        updater.enqueueManualRefresh()
        val periodicId = periodicWork().single().id
        val manualId = manualWork().single().id
        val executor = Executors.newSingleThreadExecutor()
        try {
            val (periodic, manual) = executor.submit<Pair<androidx.work.impl.model.WorkSpec, androidx.work.impl.model.WorkSpec>> {
                val database = WorkManagerImpl::class.java.getMethod("getWorkDatabase").invoke(workManager)
                val dao = database.javaClass.getMethod("workSpecDao").invoke(database) as androidx.work.impl.model.WorkSpecDao
                dao.getWorkSpec(periodicId.toString())!! to dao.getWorkSpec(manualId.toString())!!
            }.get()
            assertFalse(periodic.input.getBoolean(WeatherWidgetWorker.INPUT_MANUAL, false))
            assertTrue(manual.input.getBoolean(WeatherWidgetWorker.INPUT_MANUAL, false))
            for (spec in listOf(periodic, manual)) {
                assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
                assertEquals(TimeUnit.MINUTES.toMillis(WeatherWidgetUpdater.BACKOFF_MINUTES), spec.backoffDelayDuration)
            }
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun no_installed_widgets_means_no_periodic_or_manual_work() {
        updater.reconcile()
        updater.enqueueManualRefresh()

        assertTrue(periodicWork().isEmpty())
        assertTrue(manualWork().isEmpty())
    }

    @Test
    fun last_widget_removal_across_sizes_cancels_both_widget_work_names() {
        smallWidgetIds = setOf(10)
        mediumWidgetIds = setOf(20)
        updater.reconcile()
        updater.enqueueManualRefresh()

        smallWidgetIds = emptySet()
        updater.reconcile()
        assertFalse(periodicWork().single().state.isFinished)
        assertFalse(manualWork().single().state.isFinished)

        mediumWidgetIds = emptySet()
        updater.reconcile()

        assertEquals(WorkInfo.State.CANCELLED, periodicWork().single().state)
        assertEquals(WorkInfo.State.CANCELLED, manualWork().single().state)
    }

    private fun periodicWork() = workManager
        .getWorkInfosForUniqueWork(WeatherWidgetUpdater.PERIODIC_REFRESH_WORK_NAME)
        .get()

    private fun manualWork() = workManager
        .getWorkInfosForUniqueWork(WeatherWidgetUpdater.MANUAL_REFRESH_WORK_NAME)
        .get()

    private val notificationWorkNames = setOf(
        NotificationScheduler.WEATHER_ALERT_WORK_NAME,
        NotificationScheduler.DAILY_SUMMARY_WORK_NAME
    )

    class TestWidgetWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
        // Keep manual requests unfinished so the tests exercise WorkManager's KEEP policy.
        override fun doWork(): Result = Result.retry()
    }
}
