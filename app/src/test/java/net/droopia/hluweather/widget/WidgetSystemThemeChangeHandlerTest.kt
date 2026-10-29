package net.droopia.hluweather.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class WidgetSystemThemeChangeHandlerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun font_scale_density_and_night_mode_changes_update_both_sizes_once() {
        val smallReceiver = ComponentName(context.packageName, SMALL_RECEIVER)
        val mediumReceiver = ComponentName(context.packageName, MEDIUM_RECEIVER)
        val ids = mapOf(
            smallReceiver.className to intArrayOf(2, 4),
            mediumReceiver.className to intArrayOf(9)
        )
        val updates = mutableListOf<Intent>()
        val dispatcher = AndroidWeatherWidgetDispatcher(
            context = context,
            widgetIdsFor = { ids[it.className] ?: intArrayOf() },
            sendBroadcast = { updates += it }
        )
        val handler = WidgetSystemThemeChangeHandler(
            initialConfiguration = configuration(night = false),
            refreshWidgetInventory = dispatcher::refreshInventory,
            updateAllWidgets = dispatcher::updateAllWidgets
        )

        val fontScaled = configuration(night = false).apply { fontScale = 1.3f }
        handler.onConfigurationChanged(fontScaled)

        assertEquals(2, updates.size)
        assertEquals(1.3f, handler.renderConfiguration.value.fontScale, 0f)
        assertEquals(setOf(smallReceiver, mediumReceiver), updates.map { it.component }.toSet())
        assertTrue(updates.all { it.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE })
        assertArrayEquals(
            intArrayOf(2, 4),
            updates.single { it.component == smallReceiver }
                .getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
        )
        assertArrayEquals(
            intArrayOf(9),
            updates.single { it.component == mediumReceiver }
                .getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
        )

        // Exact duplicates and unrelated configuration changes do not invalidate the sessions.
        handler.onConfigurationChanged(fontScaled)
        handler.onConfigurationChanged(fontScaled.apply { orientation = Configuration.ORIENTATION_LANDSCAPE })
        assertEquals(2, updates.size)

        val dark = Configuration(fontScaled).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                Configuration.UI_MODE_NIGHT_YES
        }
        handler.onConfigurationChanged(dark)
        assertEquals(4, updates.size)
        assertEquals(
            Configuration.UI_MODE_NIGHT_YES,
            handler.renderConfiguration.value.uiModeNight
        )

        handler.onConfigurationChanged(Configuration(dark).apply { densityDpi += 40 })
        assertEquals(6, updates.size)

        handler.onConfigurationChanged(configuration(night = false).apply { fontScale = 1.3f })
        assertEquals(8, updates.size)
        assertTrue(updates.all { it.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE })
    }

    @Test
    fun system_theme_change_with_no_widgets_does_not_dispatch_updates_or_start_weather_work() {
        val updates = mutableListOf<Intent>()
        val dispatcher = AndroidWeatherWidgetDispatcher(
            context = context,
            widgetIdsFor = { intArrayOf() },
            sendBroadcast = { updates += it }
        )
        val handler = WidgetSystemThemeChangeHandler(
            initialConfiguration = configuration(night = false),
            refreshWidgetInventory = dispatcher::refreshInventory,
            updateAllWidgets = dispatcher::updateAllWidgets
        )

        handler.onConfigurationChanged(configuration(night = true).apply { fontScale = 1.3f })

        assertTrue(updates.isEmpty())
        assertTrue(!dispatcher.installedWidgets.value)
        assertEquals(1.3f, handler.renderConfiguration.value.fontScale, 0f)
    }

    private fun configuration(night: Boolean) = Configuration(context.resources.configuration).apply {
        val nightMode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
    }

    private companion object {
        const val SMALL_RECEIVER = "net.droopia.hluweather.widget.SmallWeatherWidgetReceiver"
        const val MEDIUM_RECEIVER = "net.droopia.hluweather.widget.MediumWeatherWidgetReceiver"
    }
}
