package net.droopia.hluweather

import androidx.test.core.app.ApplicationProvider
import net.droopia.hluweather.data.repository.CachingWeatherRepository
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
    }
}
