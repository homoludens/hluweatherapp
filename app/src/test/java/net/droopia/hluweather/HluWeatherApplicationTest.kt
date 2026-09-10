package net.droopia.hluweather

import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import net.droopia.hluweather.notifications.AppWorkerFactory
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.repository.CachingWeatherRepository
import net.droopia.hluweather.ui.settings.PersistedSettings
import org.junit.Assert.assertFalse
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
    fun startup_reconciliation_uses_canonical_location_mode_over_settings_mirror() {
        val settings = PersistedSettings(trackMeEnabled = true)

        val reconciled = notificationSettingsForReconciliation(
            settings = settings,
            activeLocation = ActiveLocation.Saved(
                net.droopia.hluweather.data.model.WeatherLocation(
                    id = "belgrade",
                    name = "Belgrade",
                    latitude = 44.8176,
                    longitude = 20.4633
                )
            ),
            locationMode = LocationMode.SAVED_LOCATION
        )

        assertFalse(reconciled.trackMeEnabled)
        assertTrue(reconciled.selectedLocationId == "belgrade")
    }
}
