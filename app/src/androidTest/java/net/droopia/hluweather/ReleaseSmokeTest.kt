package net.droopia.hluweather

import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertFalse
import org.junit.Test

class ReleaseSmokeTest {
    @Test
    fun launch_reaches_weather_or_location_setup_without_crashing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }
}
