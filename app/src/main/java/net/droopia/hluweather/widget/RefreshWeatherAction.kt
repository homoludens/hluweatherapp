package net.droopia.hluweather.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/** Enqueues a coalesced manual refresh; callbacks never perform weather or location work. */
class RefreshWeatherAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val owner = context.applicationContext as? WeatherWidgetUpdaterOwner
            ?: error("Application must provide the shared WeatherWidgetUpdater")
        owner.weatherWidgetUpdater.enqueueManualRefresh()
    }
}
