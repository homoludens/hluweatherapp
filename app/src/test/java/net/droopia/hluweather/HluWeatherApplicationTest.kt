package net.droopia.hluweather

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.testing.TestListenableWorkerBuilder
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.notifications.AppWorkerFactory
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.repository.CachingWeatherRepository
import net.droopia.hluweather.notifications.NotificationScheduler
import net.droopia.hluweather.widget.WeatherWidgetUpdaterOwner
import net.droopia.hluweather.widget.WeatherWidgetUpdater
import net.droopia.hluweather.widget.WeatherWidgetWorker
import net.droopia.hluweather.ui.settings.PersistedSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HluWeatherApplicationTest {

    @Test
    fun application_exposes_the_open_meteo_repository() {
        val application = ApplicationProvider
            .getApplicationContext<HluWeatherApplication>()

        assertTrue(application.weatherRepository is CachingWeatherRepository)
        assertTrue(WorkManager.isInitialized())
        assertTrue(application.workManagerConfiguration.workerFactory is AppWorkerFactory)
        assertTrue(application is WeatherWidgetUpdaterOwner)
        assertSame(application.weatherWidgetUpdater, (application as WeatherWidgetUpdaterOwner).weatherWidgetUpdater)
    }

    @Test
    fun application_factory_constructs_widget_workers_from_shared_dependencies() {
        val application = ApplicationProvider.getApplicationContext<HluWeatherApplication>()
        val factory = application.workManagerConfiguration.workerFactory

        val worker = TestListenableWorkerBuilder.from(application, WeatherWidgetWorker::class.java)
            .setWorkerFactory(factory)
            .build()

        assertTrue(worker is WeatherWidgetWorker)
        assertSame(
            application.weatherWidgetRefreshCoordinator,
            application.weatherWidgetWorkerDependencies.coordinator
        )
    }

    @Test
    fun startup_without_installed_widgets_does_not_enqueue_widget_work() {
        val application = ApplicationProvider.getApplicationContext<HluWeatherApplication>()
        val workManager = WorkManager.getInstance(application)

        listOf(
            WeatherWidgetUpdater.PERIODIC_REFRESH_WORK_NAME,
            WeatherWidgetUpdater.MANUAL_REFRESH_WORK_NAME,
            WeatherWidgetUpdater.SELECTION_REFRESH_WORK_NAME
        ).forEach { workName ->
            assertTrue(
                workManager.getWorkInfosForUniqueWork(workName).get()
                    .none { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
            )
        }
    }

    @Test
    fun system_theme_configuration_change_without_widgets_does_not_enqueue_weather_work() {
        val application = ApplicationProvider.getApplicationContext<HluWeatherApplication>()
        val configuration = Configuration(application.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                Configuration.UI_MODE_NIGHT_YES
        }

        application.onConfigurationChanged(configuration)

        listOf(
            WeatherWidgetUpdater.PERIODIC_REFRESH_WORK_NAME,
            WeatherWidgetUpdater.MANUAL_REFRESH_WORK_NAME,
            WeatherWidgetUpdater.SELECTION_REFRESH_WORK_NAME
        ).forEach { workName ->
            assertTrue(
                WorkManager.getInstance(application).getWorkInfosForUniqueWork(workName).get()
                    .none { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
            )
        }
    }

    @Test
    fun application_uses_branded_launcher_metadata() {
        val application = ApplicationProvider
            .getApplicationContext<HluWeatherApplication>()
        val applicationInfo = application.applicationInfo
        val icon = applicationInfo.icon
        val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)

        assertNotEquals(0, icon)
        assertEquals(R.drawable.hluweatherapp_icon, icon)
        assertEquals("hluweatherapp_icon", application.resources.getResourceEntryName(icon))
        assertEquals("drawable", application.resources.getResourceTypeName(icon))
        assertEquals(39, packageInfo.versionCode)
        assertEquals("3.6.0", packageInfo.versionName)
    }

    @Test
    fun release_signing_uses_environment_values_only() {
        val buildScript = File("app/build.gradle.kts").takeIf { it.isFile }
            ?: File("build.gradle.kts")

        assertTrue(buildScript.isFile)
        val source = buildScript.readText()
        listOf(
            "HLUWEATHER_STORE_FILE",
            "HLUWEATHER_STORE_PASSWORD",
            "HLUWEATHER_KEY_ALIAS",
            "HLUWEATHER_KEY_PASSWORD"
        ).forEach { name ->
            assertTrue(source.contains("\"$name\""))
        }
        assertTrue(source.contains("providers.environmentVariable"))
        assertTrue(source.contains("releaseSigningValues.all { !it.isNullOrBlank() }"))
        assertTrue(!source.contains("providers.gradleProperty"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startup_collector_passes_canonical_location_and_settings_to_scheduler() = runTest {
        val settings = PersistedSettings(
            selectedLocationId = "stale-settings-location",
            trackMeEnabled = true,
            weatherAlerts = true,
            dailySummary = true
        )
        val savedLocation = ActiveLocation.Saved(
            net.droopia.hluweather.data.model.WeatherLocation(
                id = "belgrade",
                name = "Belgrade",
                latitude = 44.8176,
                longitude = 20.4633
            )
        )
        val scheduler = RecordingNotificationScheduler()

        val reconciliation = startNotificationReconciliation(
            scope = this,
            settings = MutableStateFlow(settings),
            activeLocation = MutableStateFlow<ActiveLocation?>(savedLocation),
            scheduler = scheduler
        )
        runCurrent()

        assertEquals(listOf(settings to savedLocation), scheduler.reconciliations)
        reconciliation.cancel()
    }

    private class RecordingNotificationScheduler : NotificationScheduler {
        val reconciliations = mutableListOf<Pair<PersistedSettings, ActiveLocation?>>()

        override fun reconcile(settings: PersistedSettings, activeLocation: ActiveLocation?) {
            reconciliations += settings to activeLocation
        }

        override fun enqueueNextDailySummary(
            settings: PersistedSettings,
            activeLocation: ActiveLocation.Saved
        ) = Unit
    }
}
