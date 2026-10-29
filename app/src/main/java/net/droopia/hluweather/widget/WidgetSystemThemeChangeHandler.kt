package net.droopia.hluweather.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.glance.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The resource inputs used by widget layout and rendering. Keep equality limited to values that
 * affect translated widget output so unrelated configuration changes do not recompose sessions.
 */
internal data class WidgetRenderConfiguration(
    val fontScale: Float,
    val uiModeNight: Int,
    val densityDpi: Int
) {
    fun contextFrom(context: Context): Context {
        val configuration = Configuration(context.resources.configuration).apply {
            fontScale = this@WidgetRenderConfiguration.fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or uiModeNight
            if (this@WidgetRenderConfiguration.densityDpi > 0) {
                densityDpi = this@WidgetRenderConfiguration.densityDpi
            }
        }
        return context.createConfigurationContext(configuration)
    }

    companion object {
        fun from(configuration: Configuration) = WidgetRenderConfiguration(
            fontScale = configuration.fontScale,
            uiModeNight = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
            densityDpi = configuration.densityDpi
        )
    }
}

/** Supplies current Android resource metrics to the Glance content being translated. */
@Composable
internal fun WidgetRenderConfigurationContext(
    configuration: WidgetRenderConfiguration,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuredContext = remember(context, configuration) {
        configuration.contextFrom(context)
    }
    CompositionLocalProvider(LocalContext provides configuredContext, content = content)
}

/** Glance translates palette, font metrics, and density-dependent bitmaps into RemoteViews. */
internal class WidgetSystemThemeChangeHandler(
    initialConfiguration: Configuration,
    private val refreshWidgetInventory: () -> Boolean,
    private val updateAllWidgets: () -> Unit
) {
    private val _renderConfiguration = MutableStateFlow(WidgetRenderConfiguration.from(initialConfiguration))
    val renderConfiguration: StateFlow<WidgetRenderConfiguration> = _renderConfiguration.asStateFlow()

    fun onConfigurationChanged(configuration: Configuration) {
        val renderConfiguration = WidgetRenderConfiguration.from(configuration)
        if (renderConfiguration == _renderConfiguration.value) return

        _renderConfiguration.value = renderConfiguration
        if (refreshWidgetInventory()) updateAllWidgets()
    }
}
