package net.droopia.hluweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.droopia.hluweather.ui.app.HluWeatherApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!inRobolectricTest) {
            setContent {
                HluWeatherApp()
            }
        }
    }

    companion object {
        private val inRobolectricTest = try {
            Class.forName("org.robolectric.RuntimeEnvironment")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}
