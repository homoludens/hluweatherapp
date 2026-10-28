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

    /** Emits changes when startup or a receiver lifecycle callback calls [refreshInventory]. */
    val installedWidgets: StateFlow<Boolean> = _installedWidgets.asStateFlow()

    fun hasInstalledWidgets(): Boolean = receivers.any { widgetIdsFor(it).isNotEmpty() }

    /** Refreshes the collector's inventory and reports whether either widget size is installed. */
    fun refreshInventory(): Boolean = hasInstalledWidgets().also { _installedWidgets.value = it }

    /** Sends the platform update action directly to every installed receiver, without Glance types. */
    fun updateAllWidgets() {
        receivers.forEach { receiver ->
            val ids = widgetIdsFor(receiver)
            if (ids.isNotEmpty()) {
                sendBroadcast(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .setComponent(receiver)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                )
            }
        }
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
