package net.droopia.hluweather

import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertFalse
import org.junit.Test

class MainActivityLaunchTest {
    @Test
    fun launching_main_activity_does_not_finish_immediately() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }
}
