package net.droopia.hluweather.widget

import android.content.res.Configuration

/**
 * Glance resolves this widget's palette and pre-tinted icon bitmaps while rendering RemoteViews.
 * Rebuild them when Android's system light/dark appearance changes; this also covers API 28,
 * where the host cannot switch rendered colors on its own.
 */
internal class WidgetSystemThemeChangeHandler(
    initialConfiguration: Configuration,
    private val refreshWidgetInventory: () -> Boolean,
    private val updateAllWidgets: () -> Unit
) {
    private var isNightMode = initialConfiguration.isNightMode()

    fun onConfigurationChanged(configuration: Configuration) {
        val newIsNightMode = configuration.isNightMode()
        if (newIsNightMode == isNightMode) return

        isNightMode = newIsNightMode
        if (refreshWidgetInventory()) updateAllWidgets()
    }
}

private fun Configuration.isNightMode(): Boolean =
    uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
