package net.droopia.hluweather.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reads both widget sizes and explicitly asks each installed Glance receiver to render again. */
class AndroidWeatherWidgetDispatcher(
    context: Context,
    private val widgetIdsFor: (ComponentName) -> IntArray = {
        AppWidgetManager.getInstance(context).getAppWidgetIds(it)
    },
    private val sendBroadcast: (Intent) -> Unit = { context.applicationContext.sendBroadcast(it) }
) {
    private val receivers = RECEIVER_CLASS_NAMES.map { ComponentName(context.packageName, it) }
    private val _installedWidgets = MutableStateFlow(false)
    // A deletion broadcast can precede AppWidgetManager's inventory update. Ignore those IDs
    // until the manager stops reporting them, without suppressing newly added instances.
    private val deletedIds = mutableMapOf<ComponentName, MutableSet<Int>>()

    /** Emits changes when startup or a receiver lifecycle callback calls [refreshInventory]. */
    val installedWidgets: StateFlow<Boolean> = _installedWidgets.asStateFlow()

    @Synchronized
    fun hasInstalledWidgets(): Boolean = receivers.any { installedIdsFor(it).isNotEmpty() }

    @Synchronized
    fun recordDeletedIds(receiverClassName: String, ids: IntArray) {
        val receiver = receivers.firstOrNull { it.className == receiverClassName } ?: return
        deletedIds.getOrPut(receiver) { mutableSetOf() }.addAll(ids.toList())
    }

    /** Refreshes the collector's inventory and reports whether either widget size is installed. */
    @Synchronized
    fun refreshInventory(): Boolean = hasInstalledWidgets().also { _installedWidgets.value = it }

    /** Sends the platform update action directly to every installed receiver, without Glance types. */
    @Synchronized
    fun updateAllWidgets() {
        receivers.forEach { receiver ->
            val ids = installedIdsFor(receiver)
            if (ids.isNotEmpty()) {
                sendBroadcast(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .setComponent(receiver)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                )
            }
        }
    }

    private fun installedIdsFor(receiver: ComponentName): IntArray {
        val reported = widgetIdsFor(receiver)
        val deleted = deletedIds[receiver] ?: return reported
        deleted.retainAll(reported.toSet())
        if (deleted.isEmpty()) deletedIds.remove(receiver)
        return reported.filterNot { it in deleted }.toIntArray()
    }

    private companion object {
        // These names are the receiver contract added by the widget-registration task. Keeping
        // them as component names lets application startup avoid loading Glance provider classes.
        val RECEIVER_CLASS_NAMES = listOf(
            "net.droopia.hluweather.widget.SmallWeatherWidgetReceiver",
            "net.droopia.hluweather.widget.MediumWeatherWidgetReceiver"
        )
    }
}
