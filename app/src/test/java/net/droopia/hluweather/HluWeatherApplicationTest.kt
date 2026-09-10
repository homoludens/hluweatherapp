package net.droopia.hluweather

import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.notifications.AppWorkerFactory
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.repository.CachingWeatherRepository
import net.droopia.hluweather.notifications.NotificationScheduler
import net.droopia.hluweather.ui.settings.PersistedSettings
import org.junit.Assert.assertEquals
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
    }

    @Test
    fun application_uses_branded_launcher_metadata() {
        val application = ApplicationProvider
            .getApplicationContext<HluWeatherApplication>()
        val applicationInfo = application.applicationInfo
        val icon = applicationInfo.icon
        val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)

        assertEquals("ic_launcher", application.resources.getResourceEntryName(icon))
        assertEquals("mipmap", application.resources.getResourceTypeName(icon))
        assertEquals(32, packageInfo.versionCode)
        assertEquals("3.2.0", packageInfo.versionName)
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
