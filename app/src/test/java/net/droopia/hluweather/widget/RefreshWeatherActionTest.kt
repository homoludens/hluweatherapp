package net.droopia.hluweather.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.glance.GlanceId
import androidx.glance.action.actionParametersOf
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = RefreshActionTestApplication::class, sdk = [34])
class RefreshWeatherActionTest {
    private lateinit var application: RefreshActionTestApplication
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application,
            Configuration.Builder().build()
        )
        workManager = WorkManager.getInstance(application)
        application.weatherWidgetUpdater = WeatherWidgetUpdater(
            workManager = workManager,
            workerClass = RefreshActionTestWorker::class.java,
            hasInstalledWidgets = { true }
        )
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork()
    }

    @Test
    fun repeated_actions_enqueue_one_manual_request_without_opening_an_activity() {
        val action = RefreshWeatherAction()
        val glanceId = object : GlanceId {}
        runBlocking {
            repeat(5) {
                action.onAction(application, glanceId, actionParametersOf())
            }
        }

        val requests = workManager
            .getWorkInfosForUniqueWork(WeatherWidgetUpdater.MANUAL_REFRESH_WORK_NAME)
            .get()

        assertEquals(1, requests.size)
        assertFalse(requests.single().state.isFinished)
        assertEquals(0, application.activityLaunchCount)
    }
}

class RefreshActionTestApplication : android.app.Application(), WeatherWidgetUpdaterOwner {
    override lateinit var weatherWidgetUpdater: WeatherWidgetUpdater
    var activityLaunchCount = 0
        private set

    override fun startActivity(intent: Intent) {
        activityLaunchCount++
    }

    override fun startActivity(intent: Intent, options: Bundle?) {
        activityLaunchCount++
    }
}

class RefreshActionTestWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = Result.retry()
}
