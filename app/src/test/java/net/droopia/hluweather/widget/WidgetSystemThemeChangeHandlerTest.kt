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
    fun night_mode_changes_update_both_installed_sizes_without_enqueuing_weather_work() {
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

        handler.onConfigurationChanged(configuration(night = true))

        assertEquals(2, updates.size)
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

        // Other configuration changes and duplicate night-mode notifications don't re-render.
        handler.onConfigurationChanged(configuration(night = true).apply { fontScale = 1.2f })
        assertEquals(2, updates.size)

        handler.onConfigurationChanged(configuration(night = false))
        assertEquals(4, updates.size)
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

        handler.onConfigurationChanged(configuration(night = true))

        assertTrue(updates.isEmpty())
        assertTrue(!dispatcher.installedWidgets.value)
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
