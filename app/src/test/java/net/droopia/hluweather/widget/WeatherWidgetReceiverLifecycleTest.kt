package net.droopia.hluweather.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBroadcastPendingResult

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = WeatherWidgetReceiverLifecycleTest.WidgetTestApplication::class)
class WeatherWidgetReceiverLifecycleTest {
    private lateinit var app: WidgetTestApplication
    private lateinit var work: WorkManager
    private val small = SmallWeatherWidgetReceiver()
    private val medium = MediumWeatherWidgetReceiver()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().build())
        work = WorkManager.getInstance(app)
        app.ids.clear()
        app.initialize(work)
    }

    @After
    fun tearDown() {
        work.cancelAllWork().result.get()
    }

    @Test
    fun receiver_events_share_schedule_and_last_deletion_cancels_all_widget_work() = runBlocking {
        app.ids[small.javaClass.name] = intArrayOf(1)
        receive(small, AppWidgetManager.ACTION_APPWIDGET_ENABLED)
        receive(small, AppWidgetManager.ACTION_APPWIDGET_UPDATE, intArrayOf(1))
        val first = workFor(WeatherWidgetUpdater.PERIODIC_REFRESH_WORK_NAME).single()
        assertTrue(app.dispatcher.installedWidgets.value)

        app.ids[small.javaClass.name] = intArrayOf(1, 2)
        app.ids[medium.javaClass.name] = intArrayOf(3)
        receive(small, AppWidgetManager.ACTION_APPWIDGET_UPDATE, intArrayOf(2))
        receive(medium, AppWidgetManager.ACTION_APPWIDGET_ENABLED)
        receive(medium, AppWidgetManager.ACTION_APPWIDGET_UPDATE, intArrayOf(3))
        assertEquals(first.id, workFor(WeatherWidgetUpdater.PERIODIC_REFRESH_WORK_NAME).single().id)
        app.weatherWidgetUpdater.enqueueManualRefresh()
        app.weatherWidgetUpdater.enqueueSelectionRefresh()

        // The manager still reports deleted IDs while these broadcasts are delivered.
        receive(small, AppWidgetManager.ACTION_APPWIDGET_DELETED, intArrayOf(1, 2))
        receive(small, AppWidgetManager.ACTION_APPWIDGET_DISABLED)
        assertTrue(app.dispatcher.installedWidgets.value)
        assertEquals(first.id, workFor(WeatherWidgetUpdater.PERIODIC_REFRESH_WORK_NAME).single().id)
        assertFalse(workFor(WeatherWidgetUpdater.MANUAL_REFRESH_WORK_NAME).single().state.isFinished)

        receive(medium, AppWidgetManager.ACTION_APPWIDGET_DELETED, intArrayOf(3))
        receive(medium, AppWidgetManager.ACTION_APPWIDGET_DISABLED)
        assertFalse(app.dispatcher.installedWidgets.value)
        for (name in widgetWorkNames) {
            assertEquals(WorkInfo.State.CANCELLED, workFor(name).single().state)
        }
    }

    @Test
    fun restore_readd_and_late_callbacks_recheck_both_sizes_without_reviving_deleted_ids() {
        app.ids[small.javaClass.name] = intArrayOf(10)
        receive(small, AppWidgetManager.ACTION_APPWIDGET_UPDATE, intArrayOf(10))
        val initial = workFor(WeatherWidgetUpdater.PERIODIC_REFRESH_WORK_NAME).single().id

        app.ids[small.javaClass.name] = intArrayOf(10, 11)
        deliver(small, Intent(AppWidgetManager.ACTION_APPWIDGET_RESTORED)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS, intArrayOf(10))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(11)))
        assertTrue(app.dispatcher.installedWidgets.value)
        assertEquals(initial, workFor(WeatherWidgetUpdater.PERIODIC_REFRESH_WORK_NAME).single().id)

        receive(small, AppWidgetManager.ACTION_APPWIDGET_DELETED, intArrayOf(11))
        assertFalse(app.dispatcher.installedWidgets.value)
        assertEquals(WorkInfo.State.CANCELLED, workFor(WeatherWidgetUpdater.PERIODIC_REFRESH_WORK_NAME).single().state)

        // A new instance must survive an old disable/update callback from either provider.
        app.ids[medium.javaClass.name] = intArrayOf(12)
        receive(medium, AppWidgetManager.ACTION_APPWIDGET_UPDATE, intArrayOf(12))
        receive(small, AppWidgetManager.ACTION_APPWIDGET_DISABLED)
        receive(small, AppWidgetManager.ACTION_APPWIDGET_UPDATE, intArrayOf(10))
        assertTrue(app.dispatcher.installedWidgets.value)
        val active = workFor(WeatherWidgetUpdater.PERIODIC_REFRESH_WORK_NAME).filterNot { it.state.isFinished }
        assertEquals(1, active.size)
        assertTrue(active.single().id != initial)
    }

    @Test
    fun concurrent_delete_and_late_update_preserve_new_instance_and_one_schedule() {
        app.ids[small.javaClass.name] = intArrayOf(1)
        app.ids[medium.javaClass.name] = intArrayOf(2, 3)
        receive(small, AppWidgetManager.ACTION_APPWIDGET_UPDATE, intArrayOf(1))
        val first = workFor(WeatherWidgetUpdater.PERIODIC_REFRESH_WORK_NAME).single().id
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(3)
        try {
            val callbacks = listOf<() -> Unit>(
                { receive(SmallWeatherWidgetReceiver(), AppWidgetManager.ACTION_APPWIDGET_DELETED, intArrayOf(1)) },
                { receive(MediumWeatherWidgetReceiver(), AppWidgetManager.ACTION_APPWIDGET_DELETED, intArrayOf(2)) },
                { receive(MediumWeatherWidgetReceiver(), AppWidgetManager.ACTION_APPWIDGET_UPDATE, intArrayOf(3)) }
            ).map { callback -> executor.submit { start.await(); callback() } }
            start.countDown()
            callbacks.forEach { it.get(20, TimeUnit.SECONDS) }
        } finally {
            executor.shutdown()
        }
        assertTrue(app.dispatcher.installedWidgets.value)
        assertEquals(first, workFor(WeatherWidgetUpdater.PERIODIC_REFRESH_WORK_NAME).single().id)
    }

    @Test
    fun startup_inventory_reconciliation_preserves_the_existing_periodic_request() {
        app.ids[small.javaClass.name] = intArrayOf(4)
        receive(small, AppWidgetManager.ACTION_APPWIDGET_UPDATE, intArrayOf(4))
        val first = workFor(WeatherWidgetUpdater.PERIODIC_REFRESH_WORK_NAME).single().id
        app.initialize(work) // New process-owned dispatcher and updater, same persisted WorkManager.
        app.weatherWidgetUpdater.refreshInventory()
        assertTrue(app.dispatcher.installedWidgets.value)
        assertEquals(first, workFor(WeatherWidgetUpdater.PERIODIC_REFRESH_WORK_NAME).single().id)
    }

    @Test
    fun restore_keeps_an_id_that_is_also_in_the_new_id_mapping() {
        app.ids[small.javaClass.name] = intArrayOf(5)
        deliver(small, Intent(AppWidgetManager.ACTION_APPWIDGET_RESTORED)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS, intArrayOf(5))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(5)))
        assertTrue(app.dispatcher.installedWidgets.value)
        assertFalse(workFor(WeatherWidgetUpdater.PERIODIC_REFRESH_WORK_NAME).single().state.isFinished)
    }

    private fun receive(receiver: WeatherWidgetReceiver, action: String, ids: IntArray = intArrayOf()) {
        val intent = Intent(action)
        when (action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            AppWidgetManager.ACTION_APPWIDGET_DELETED -> intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, ids.first())
        }
        // Android sends one deleted ID per broadcast.
        if (action == AppWidgetManager.ACTION_APPWIDGET_DELETED && ids.size > 1) {
            ids.forEach { receive(receiver, action, intArrayOf(it)) }
        } else deliver(receiver, intent)
    }

    private fun deliver(receiver: WeatherWidgetReceiver, intent: Intent) {
        // Direct onReceive calls need the PendingResult Android normally installs. Glance consumes
        // it with goAsync for update/deletion, so wait for its cleanup before reusing the receiver.
        val create = ShadowBroadcastPendingResult::class.java.getDeclaredMethod(
            "create", Int::class.javaPrimitiveType, String::class.java,
            android.os.Bundle::class.java, Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
        val pending = create.invoke(null, 0, null, null, false) as BroadcastReceiver.PendingResult
        BroadcastReceiver::class.java.getMethod("setPendingResult", BroadcastReceiver.PendingResult::class.java)
            .invoke(receiver, pending)
        receiver.onReceive(app, intent)
        val result = shadowOf(receiver).originalPendingResult
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE ||
            intent.action == AppWidgetManager.ACTION_APPWIDGET_DELETED ||
            intent.action == AppWidgetManager.ACTION_APPWIDGET_RESTORED
        ) {
            assertSame("Glance must consume this broadcast's PendingResult", pending, result)
        }
        if (result != null && shadowOf(receiver).wentAsync()) {
            shadowOf(result).future.get(10, TimeUnit.SECONDS)
        }
    }

    private fun workFor(name: String) = work.getWorkInfosForUniqueWork(name).get()

    private val widgetWorkNames = listOf(
        WeatherWidgetUpdater.PERIODIC_REFRESH_WORK_NAME,
        WeatherWidgetUpdater.MANUAL_REFRESH_WORK_NAME,
        WeatherWidgetUpdater.SELECTION_REFRESH_WORK_NAME
    )

    class WidgetTestApplication : Application(), WeatherWidgetUpdaterOwner {
        val ids = mutableMapOf<String, IntArray>()
        lateinit var dispatcher: AndroidWeatherWidgetDispatcher
        override lateinit var weatherWidgetUpdater: WeatherWidgetUpdater

        fun initialize(work: WorkManager) {
            dispatcher = AndroidWeatherWidgetDispatcher(this, widgetIdsFor = { ids[it.className] ?: intArrayOf() })
            weatherWidgetUpdater = WeatherWidgetUpdater(
                work, WeatherWidgetUpdaterTest.TestWidgetWorker::class.java,
                dispatcher::hasInstalledWidgets,
                { dispatcher.refreshInventory() }, dispatcher::recordDeletedIds
            )
        }
    }
}
