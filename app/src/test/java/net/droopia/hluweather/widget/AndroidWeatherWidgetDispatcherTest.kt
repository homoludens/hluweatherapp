package net.droopia.hluweather.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidWeatherWidgetDispatcherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun inventory_tracks_both_receiver_sizes_and_explicitly_updates_each_installed_size() {
        val ids = mutableMapOf<String, IntArray>()
        val updates = mutableListOf<Intent>()
        val dispatcher = AndroidWeatherWidgetDispatcher(
            context = context,
            widgetIdsFor = { ids[it.className] ?: intArrayOf() },
            sendBroadcast = { updates += it }
        )

        assertFalse(dispatcher.refreshInventory())
        assertFalse(dispatcher.installedWidgets.value)

        val smallReceiver = ComponentName(
            context.packageName,
            "net.droopia.hluweather.widget.SmallWeatherWidgetReceiver"
        )
        val mediumReceiver = ComponentName(
            context.packageName,
            "net.droopia.hluweather.widget.MediumWeatherWidgetReceiver"
        )
        ids[smallReceiver.className] = intArrayOf(2, 4)
        assertTrue(dispatcher.refreshInventory())
        assertTrue(dispatcher.installedWidgets.value)

        ids[mediumReceiver.className] = intArrayOf(9)
        dispatcher.updateAllWidgets()

        assertEquals(2, updates.size)
        assertEquals(AppWidgetManager.ACTION_APPWIDGET_UPDATE, updates[0].action)
        assertEquals(AppWidgetManager.ACTION_APPWIDGET_UPDATE, updates[1].action)
        assertEquals(smallReceiver, updates[0].component)
        assertEquals(mediumReceiver, updates[1].component)
        assertArrayEquals(intArrayOf(2, 4), updates[0].getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS))
        assertArrayEquals(intArrayOf(9), updates[1].getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS))

        ids.remove(smallReceiver.className)
        assertTrue(dispatcher.refreshInventory())
        ids.remove(mediumReceiver.className)
        assertFalse(dispatcher.refreshInventory())
        assertFalse(dispatcher.installedWidgets.value)
    }

    @Test
    fun deleted_ids_are_ignored_while_stale_and_new_ids_of_the_same_size_remain_visible() {
        val ids = mutableMapOf<String, IntArray>()
        val updates = mutableListOf<Intent>()
        val dispatcher = AndroidWeatherWidgetDispatcher(context, { ids[it.className] ?: intArrayOf() }, { updates += it })
        val small = SmallWeatherWidgetReceiver::class.java.name
        ids[small] = intArrayOf(1, 2)
        dispatcher.recordDeletedIds(small, intArrayOf(1))
        assertTrue(dispatcher.refreshInventory())
        dispatcher.updateAllWidgets()
        assertArrayEquals(intArrayOf(2), updates.single().getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS))

        dispatcher.recordDeletedIds(small, intArrayOf(2))
        assertFalse(dispatcher.refreshInventory())
        ids[small] = intArrayOf()
        assertFalse(dispatcher.refreshInventory()) // retire tombstones when the manager catches up
        ids[small] = intArrayOf(1)
        assertTrue(dispatcher.refreshInventory())
    }
}
