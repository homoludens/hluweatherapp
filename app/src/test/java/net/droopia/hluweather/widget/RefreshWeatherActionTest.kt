package net.droopia.hluweather.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.MainActivity
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.SettingsScreen
import net.droopia.hluweather.ui.settings.SettingsUiState
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = RefreshActionTestApplication::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RefreshWeatherActionTest {
    private lateinit var application: RefreshActionTestApplication
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        shadowOf(application).clearNextStartedActivities()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application,
            Configuration.Builder().build()
        )
        workManager = WorkManager.getInstance(application)
        application.weatherWidgetUpdater = WeatherWidgetUpdater(
            workManager = workManager,
            workerClass = RefreshActionTestWorker::class.java,
            hasInstalledWidgets = { true }
        )
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork()
    }

    @Test
    fun translated_refresh_clicks_in_both_sizes_run_the_callback_without_opening_an_activity() {
        val small = renderSmallActionWidget(widgetActionSnapshot(), 200f, 160f)
        val medium = renderMediumActionWidget(widgetActionSnapshot(), 320f, 144f)

        repeat(5) {
            clickDescription(small, "Refresh weather")
        }
        clickDescription(medium, "Refresh weather")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val requests = workManager
            .getWorkInfosForUniqueWork(WeatherWidgetUpdater.MANUAL_REFRESH_WORK_NAME)
            .get()

        assertEquals(1, requests.size)
        assertFalse(requests.single().state.isFinished)
        assertNull(shadowOf(application).peekNextStartedActivity())
    }

    @Test
    fun cold_translated_card_and_location_clicks_in_both_sizes_start_main_activity_with_widget_task_flags() {
        assertNull(shadowOf(application).peekNextStartedActivity())

        val small = renderSmallActionWidget(widgetActionSnapshot(), 200f, 160f)
        val medium = renderMediumActionWidget(widgetActionSnapshot(), 320f, 144f)

        listOf(small, medium).forEach { remoteView ->
            val card = findViews(remoteView).first { it.isClickable }
            assertTrue("the translated card background is clickable", card.performClick())
            assertMainWeatherIntent(requireNotNull(shadowOf(application).getNextStartedActivity()))

            clickDescription(remoteView, "Weather for Sample town")
            assertMainWeatherIntent(requireNotNull(shadowOf(application).getNextStartedActivity()))
        }

        assertNull(shadowOf(application).peekNextStartedActivity())
    }

    @Test
    fun ordinary_launcher_intent_does_not_inherit_widget_task_reset_flags() {
        val launcherIntent = requireNotNull(
            application.packageManager.getLaunchIntentForPackage(application.packageName)
        )

        assertEquals(MainActivity::class.java.name, launcherIntent.component?.className)
        assertEquals(Intent.ACTION_MAIN, launcherIntent.action)
        assertTrue(launcherIntent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true)
        // PackageManager may add NEW_TASK for a launcher Intent; CLEAR_TASK is widget-specific.
        assertEquals(0, launcherIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    private fun assertMainWeatherIntent(intent: Intent) {
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        val widgetTaskFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        assertEquals(widgetTaskFlags, intent.flags and widgetTaskFlags)
    }
}

class RefreshActionTestApplication : android.app.Application(), WeatherWidgetUpdaterOwner {
    override lateinit var weatherWidgetUpdater: WeatherWidgetUpdater
}

class RefreshActionTestWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = Result.retry()
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
private fun renderSmallActionWidget(
    snapshot: WeatherWidgetSnapshot,
    widthDp: Float,
    heightDp: Float
): View = renderActionWidget(widthDp, heightDp) { SmallWeatherWidgetContent(snapshot) }

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
private fun renderMediumActionWidget(
    snapshot: WeatherWidgetSnapshot,
    widthDp: Float,
    heightDp: Float
): View = renderActionWidget(widthDp, heightDp) { MediumWeatherWidgetContent(snapshot) }

/** Translate production widget content through Glance's RemoteViews composer, not preview composition. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
private fun renderActionWidget(
    widthDp: Float,
    heightDp: Float,
    content: @Composable () -> Unit
): View {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val density = context.resources.displayMetrics.density
    val widthPx = (widthDp * density).roundToInt()
    val heightPx = (heightDp * density).roundToInt()
    val remoteViews = runBlocking {
        GlanceRemoteViews().compose(
            context = context,
            size = DpSize(widthDp.dp, heightDp.dp),
            state = null,
            appWidgetOptions = Bundle()
        ) {
            content()
        }.remoteViews
    }
    val view = applyRemoteViewsWithHostInteractionHandler(
        remoteViews = remoteViews,
        context = context,
        parent = FrameLayout(context),
        size = SizeF(widthPx.toFloat(), heightPx.toFloat())
    )
    view.measure(
        View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
    )
    view.layout(0, 0, widthPx, heightPx)
    return view
}

/** Android's AppWidgetHost supplies this hidden API handler when applying widget RemoteViews. */
private fun applyRemoteViewsWithHostInteractionHandler(
    remoteViews: android.widget.RemoteViews,
    context: Context,
    parent: ViewGroup,
    size: SizeF
): View {
    val paramsClass = Class.forName("android.widget.RemoteViews\$ActionApplyParams")
    val interactionHandlerClass = Class.forName("android.widget.RemoteViews\$InteractionHandler")
    val params = paramsClass.getDeclaredConstructor().newInstance()
    val handler = Proxy.newProxyInstance(
        interactionHandlerClass.classLoader,
        arrayOf(interactionHandlerClass)
    ) { _, method, arguments ->
        if (method.name == "onInteraction") {
            (arguments!![1] as android.app.PendingIntent).send()
            true
        } else {
            null
        }
    }
    paramsClass.getField("handler").set(params, handler)
    val apply = android.widget.RemoteViews::class.java.getMethod(
        "apply",
        Context::class.java,
        ViewGroup::class.java,
        SizeF::class.java,
        paramsClass
    )
    return apply.invoke(remoteViews, context, parent, size, params) as View
}

private fun clickDescription(root: View, description: String) {
    val described = findViews(root).first { it.contentDescription == description }
    var target: View = described
    while (target.parent is View && !target.isClickable) target = target.parent as View
    assertTrue("$description resolves to a translated click target", target.isClickable)
    assertTrue(target.performClick())
}

private fun findViews(root: View): List<View> = buildList {
    add(root)
    if (root is ViewGroup) {
        for (index in 0 until root.childCount) addAll(findViews(root.getChildAt(index)))
    }
}

private fun widgetActionSnapshot(): WeatherWidgetSnapshot.NoWeather {
    val location = WeatherLocation("sample", "Sample town", 43.9, 20.3)
    return WeatherWidgetSnapshot.NoWeather(
        settings = PersistedSettings(),
        request = WeatherWidgetRequest(
            provider = WeatherProvider.OPEN_METEO,
            location = ActiveLocation.Saved(location),
            locationName = location.name
        ),
        refresh = WidgetRefreshStatus()
    )
}

@RunWith(RobolectricTestRunner::class)
@Config(application = RefreshActionTestApplication::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetOpenFromSettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    private lateinit var application: RefreshActionTestApplication
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application,
            Configuration.Builder().build()
        )
        workManager = WorkManager.getInstance(application)
        application.weatherWidgetUpdater = WeatherWidgetUpdater(
            workManager = workManager,
            workerClass = RefreshActionTestWorker::class.java,
            hasInstalledWidgets = { true }
        )
        shadowOf(application).clearNextStartedActivities()
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork()
    }

    @Test
    fun both_widget_sizes_open_weather_with_a_fresh_task_while_settings_is_visible() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                SettingsScreen(
                    state = SettingsUiState(isInitialized = true),
                    onBackClick = {},
                    onProviderChange = {},
                    onProviderInfoClick = {},
                    onPlaceSearchProviderChange = {},
                    onTrackMeChange = {},
                    onLocationSelect = {},
                    onLocationMenuClick = {},
                    onAddLocationClick = {},
                    onThemeChange = {},
                    onTemperatureUnitChange = {},
                    onWindUnitChange = {},
                    onDistanceUnitChange = {},
                    onPrecipitationUnitChange = {},
                    onWeatherAlertsChange = {},
                    onDailySummaryChange = {},
                    onDailySummaryTimeChange = {},
                    onClearCacheClick = {}
                )
            }
        }
        composeRule.onNodeWithText("Settings").assertIsDisplayed()

        val small = renderSmallActionWidget(widgetActionSnapshot(), 200f, 160f)
        val medium = renderMediumActionWidget(widgetActionSnapshot(), 320f, 144f)
        val card = findViews(small).first { it.isClickable }
        assertTrue(card.performClick())
        assertMainWeatherIntent(requireNotNull(shadowOf(application).getNextStartedActivity()))

        clickDescription(medium, "Weather for Sample town")
        assertMainWeatherIntent(requireNotNull(shadowOf(application).getNextStartedActivity()))
    }

    private fun assertMainWeatherIntent(intent: Intent) {
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        val widgetTaskFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        assertEquals(widgetTaskFlags, intent.flags and widgetTaskFlags)
    }
}
