package net.droopia.hluweather.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Glance owns the broadcast's asynchronous rendering and deletion cleanup. Inventory updates
 * only submit WorkManager operations; they must not call goAsync or perform network work here. */
abstract class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    private fun updater(context: Context) =
        (context.applicationContext as WeatherWidgetUpdaterOwner).weatherWidgetUpdater

    override fun onEnabled(context: Context) {
        updater(context).refreshInventory()
        super.onEnabled(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updater(context).refreshInventory()
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        updater(context).widgetsDeleted(javaClass.name, appWidgetIds)
        super.onDeleted(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        updater(context).refreshInventory()
        super.onDisabled(context)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val newIds = newWidgetIds.toSet()
        updater(context).widgetsDeleted(javaClass.name, oldWidgetIds.filterNot { it in newIds }.toIntArray())
        super.onRestored(context, oldWidgetIds, newWidgetIds)
    }
}

/** Launcher entry point for the compact current-weather card. */
class SmallWeatherWidgetReceiver : WeatherWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmallWeatherWidget()
}

/** Launcher entry point for the current-weather and hourly-forecast card. */
class MediumWeatherWidgetReceiver : WeatherWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MediumWeatherWidget()
}
