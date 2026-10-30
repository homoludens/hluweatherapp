package net.droopia.hluweather.widget

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.glance.EmittableWithChildren
import androidx.glance.GlanceId
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.AppWidgetSession
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.session.runSession
import androidx.test.core.app.ApplicationProvider
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import net.droopia.hluweather.data.cache.ForecastCache
import net.droopia.hluweather.data.cache.ForecastCacheKey
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.CurrentWeather
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.CachingWeatherRepository
import net.droopia.hluweather.data.repository.ForecastLoad
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.WeatherSource
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.SettingsRepository
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.theme.hluMaterialColorScheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherWidgetSessionTest {
    @Test
    fun foreground_cache_recovers_completed_failure_in_both_live_sessions_without_requests() = runBlocking {
        for (medium in listOf(false, true)) {
            val f = Fixture()
            f.put(f.first, 23.0)
            val widget = TestWidget(f.widgets, f.widgets.loadCachedState(), medium,
                MutableStateFlow(WidgetRenderConfiguration.from(f.context.resources.configuration)))
            val session = RecordingSession(widget, f.context, medium, widget.size)
            val job = launch(Dispatchers.Default) { session.runSession(f.context) }
            try {
                session.awaitText("23°C")
                f.now += 5.minutes
                val request = f.widgets.resolveRequest()!!
                val failed = f.widgets.beginRefresh(request)
                f.widgets.finishRefresh(request, failed, null)
                if (medium) session.awaitAccessibleDescription("the last update failed", true)
                else session.awaitText("Stale weather")

                // A future-dated foreground cache must not hide the completed failure.
                val future = f.forecast(f.first, 26.0).copy(fetchedAt = f.now + 1.hours)
                f.cache.put(ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:${f.first.id}"), future)
                val skewed = session.awaitText("26°C")
                if (medium) {
                    assertTrue(allViews(skewed).any { it.contentDescription?.toString()?.contains("Weather update failed") == true })
                    assertTrue(texts(skewed).any { it.text.toString() == "13:05" })
                } else {
                    assertTrue(texts(skewed).any { it.text.toString().contains("Update failed") })
                }

                // A cache entry at the failure time is not a recovery.
                f.put(f.first, 24.0)
                val equal = session.awaitText("24°C")
                if (medium) assertTrue(allViews(equal).any { it.contentDescription?.toString()?.contains("the last update failed") == true })
                else assertTrue(texts(equal).any { it.text.toString().contains("Stale weather") })

                f.now += 1.minutes
                f.put(f.first, 25.0)
                val recovered = session.awaitView { view ->
                    texts(view).any { it.text.toString() == "25°C" } &&
                        allViews(view).none { it.contentDescription?.toString()?.contains("the last update failed") == true } &&
                        texts(view).none { it.text.toString().contains("Stale") || it.text.toString().contains("failed") }
                }
                if (medium) {
                    assertTrue(texts(recovered).any { it.text.toString() == "12:06" })
                    assertTrue(allViews(recovered).any { it.contentDescription?.toString() == "Last weather update Wed, Oct 28, 2026 • 12:06" })
                }

                f.now += 1.minutes
                val newerAttempt = f.widgets.beginRefresh(request)
                f.widgets.finishRefresh(request, failed, null)
                if (medium) session.awaitAccessibleDescription("Weather update in progress", true)
                else session.awaitText("Refreshing")
                f.widgets.finishRefresh(request, newerAttempt, null)
                if (medium) session.awaitAccessibleDescription("the last update failed", true)
                else session.awaitText("Stale weather")
                assertEquals(1, widget.calls)
                assertEquals(0, f.networkCalls)
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    @Test
    fun same_sessions_translate_cache_status_and_settings_changes_without_network() = runBlocking {
        for (medium in listOf(false, true)) {
            val f = Fixture()
            val initial = f.widgets.loadCachedState()
            val renderConfiguration = MutableStateFlow(
                WidgetRenderConfiguration.from(f.context.resources.configuration)
            )
            val widget = TestWidget(f.widgets, initial, medium, renderConfiguration)
            val session = RecordingSession(widget, f.context, medium, widget.size)
            val job = launch(Dispatchers.Default) { session.runSession(f.context) }
            try {
                session.awaitText("No weather available")
                f.put(f.first, 23.0)
                session.awaitText("23°C")
                val request = f.widgets.resolveRequest()!!
                val attempt = f.widgets.beginRefresh(request)
                if (medium) session.awaitAccessibleDescription("Weather update in progress", present = true)
                else session.awaitText("Refreshing")
                f.widgets.finishRefresh(request, attempt, ForecastLoad(f.forecast(f.first, 23.0), false))
                if (medium) session.awaitAccessibleDescription("Weather update in progress", present = false)
                else session.awaitText("23°C", absent = "Refreshing")

                // Give the failed attempt a later time than the cached forecast's real fetch time.
                f.now += 5.minutes
                val failed = f.widgets.beginRefresh(request)
                if (medium) session.awaitAccessibleDescription("Weather update in progress", present = true)
                else session.awaitText("Refreshing")
                f.widgets.finishRefresh(request, failed, ForecastLoad(f.forecast(f.first, 23.0), true))
                val stale = if (medium) {
                    session.awaitAccessibleDescription("Stale weather", present = true)
                } else {
                    session.awaitText("Stale weather")
                }
                if (medium) {
                    assertTrue("failed refresh preserves its original cached fetch time", texts(stale)
                        .any { it.text.toString() == "12:00" })
                    assertTrue("cached timestamp remains accessible after failure", allViews(stale)
                        .any { it.contentDescription?.toString() == "Last weather update Wed, Oct 28, 2026 • 12:00" })
                }
                val recovery = f.widgets.beginRefresh(request)
                if (medium) session.awaitAccessibleDescription("Weather update in progress", present = true)
                else session.awaitText("Refreshing")
                f.widgets.finishRefresh(request, recovery, ForecastLoad(f.forecast(f.first, 23.0), false))
                if (medium) {
                    val recovered = session.awaitAccessibleDescription("Weather update in progress", present = false)
                    assertFalse("successful refresh clears stale status", allViews(recovered)
                        .any { it.contentDescription?.toString()?.contains("Stale weather") == true })
                } else {
                    session.awaitText("23°C", absent = "Stale")
                }

                f.now += 5.minutes
                val failure = f.widgets.beginRefresh(request)
                if (medium) session.awaitAccessibleDescription("Weather update in progress", present = true)
                else session.awaitText("Refreshing")
                f.widgets.finishRefresh(request, failure, null)
                if (medium) {
                    val failedView = session.awaitAccessibleDescription("the last update failed", present = true)
                    assertTrue("null refresh failure leaves cached fetch time unchanged", texts(failedView)
                        .any { it.text.toString() == "12:00" })
                    assertTrue("cached timestamp remains available to accessibility after failure", allViews(failedView)
                        .any { it.contentDescription?.toString() == "Last weather update Wed, Oct 28, 2026 • 12:00" })
                } else {
                    session.awaitText("Stale weather")
                }
                f.settings.value = f.settings.value.copy(temperatureUnit = TemperatureUnit.FAHRENHEIT, themeMode = ThemeMode.DARK)
                val dark = session.awaitText("73°F")
                val temperature = texts(dark).first { it.text.toString() == "73°F" }
                assertEquals(hluMaterialColorScheme(true).onSurface.toArgb(), temperature.currentTextColor)
                val renamed = f.first.copy(name = "Renamed")
                f.locations.value = listOf(renamed, f.second)
                session.awaitText("Renamed")
                f.active.value = ActiveLocation.Saved(f.second)
                session.awaitText("Second", absent = "73°F")
                f.put(f.second, 10.0)
                session.awaitText("50°F")
                f.settings.value = f.settings.value.copy(provider = WeatherProvider.MET_NO)
                session.awaitText("No weather available", absent = "50°F")
                f.put(f.second, 30.0, WeatherProvider.MET_NO)
                session.awaitText("86°F")
                assertEquals("one provideGlance call for the entire session", 1, widget.calls)
                assertEquals(0, f.networkCalls)
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    @Test
    fun same_sessions_relayout_font_scale_and_follow_system_theme_changes_without_weather_requests() = runBlocking {
        for (medium in listOf(false, true)) {
            val f = Fixture()
            val initialConfiguration = configuration(
                Configuration(f.context.resources.configuration),
                fontScale = 1f,
                night = false
            )
            var widgetUpdates = 0
            val configurationHandler = WidgetSystemThemeChangeHandler(
                initialConfiguration = initialConfiguration,
                refreshWidgetInventory = { true },
                updateAllWidgets = { widgetUpdates++ }
            )
            val size = if (medium) DpSize(320.dp, 144.dp) else DpSize(200.dp, 160.dp)
            val widget = TestWidget(
                f.widgets,
                f.widgets.loadCachedState(),
                medium,
                configurationHandler.renderConfiguration,
                size
            )
            val session = RecordingSession(widget, f.context, medium, size)
            val job = launch(Dispatchers.Default) { session.runSession(f.context) }
            try {
                f.put(f.first, 23.0)
                val initialView = session.awaitView(contextFor(f.context, initialConfiguration)) {
                    texts(it).any { text -> text.text.toString() == "23°C" }
                }
                assertConfigurationRender(initialView, medium, size, 1f, dark = false)

                val largeFont = configuration(initialConfiguration, fontScale = 1.3f, night = false)
                configurationHandler.onConfigurationChanged(largeFont)
                val largeFontView = session.awaitView(contextFor(f.context, largeFont)) {
                    texts(it).any { text -> text.text.toString() == "23°C" } &&
                        it.resources.configuration.fontScale == 1.3f
                }
                assertConfigurationRender(largeFontView, medium, size, 1.3f, dark = false)

                val largeFontDark = configuration(largeFont, fontScale = 1.3f, night = true)
                configurationHandler.onConfigurationChanged(largeFontDark)
                val darkView = session.awaitView(contextFor(f.context, largeFontDark)) {
                    texts(it).any { text -> text.text.toString() == "23°C" } &&
                        (it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
                }
                assertConfigurationRender(darkView, medium, size, 1.3f, dark = true)

                val normalFontDark = configuration(largeFontDark, fontScale = 1f, night = true)
                configurationHandler.onConfigurationChanged(normalFontDark)
                val normalFontDarkView = session.awaitView(contextFor(f.context, normalFontDark)) {
                    texts(it).any { text -> text.text.toString() == "23°C" } &&
                        it.resources.configuration.fontScale == 1f
                }
                assertConfigurationRender(normalFontDarkView, medium, size, 1f, dark = true)

                val normalFontLight = configuration(normalFontDark, fontScale = 1f, night = false)
                configurationHandler.onConfigurationChanged(normalFontLight)
                val lightView = session.awaitView(contextFor(f.context, normalFontLight)) {
                    texts(it).any { text -> text.text.toString() == "23°C" } &&
                        (it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_NO
                }
                assertConfigurationRender(lightView, medium, size, 1f, dark = false)

                f.settings.value = f.settings.value.copy(themeMode = ThemeMode.LIGHT)
                val explicitLight = session.awaitView(contextFor(f.context, normalFontLight)) {
                    hasTemperatureColor(it, dark = false)
                }
                assertConfigurationRender(explicitLight, medium, size, 1f, dark = false)
                val explicitLightDarkSystem = configuration(normalFontLight, fontScale = 1f, night = true)
                configurationHandler.onConfigurationChanged(explicitLightDarkSystem)
                val explicitLightUnderDarkSystem = session.awaitView(contextFor(f.context, explicitLightDarkSystem)) {
                    hasTemperatureColor(it, dark = false)
                }
                assertConfigurationRender(explicitLightUnderDarkSystem, medium, size, 1f, dark = false)

                f.settings.value = f.settings.value.copy(themeMode = ThemeMode.DARK)
                val explicitDark = session.awaitView(contextFor(f.context, explicitLightDarkSystem)) {
                    hasTemperatureColor(it, dark = true)
                }
                assertConfigurationRender(explicitDark, medium, size, 1f, dark = true)
                val explicitDarkLightSystem = configuration(explicitLightDarkSystem, fontScale = 1f, night = false)
                configurationHandler.onConfigurationChanged(explicitDarkLightSystem)
                val explicitDarkUnderLightSystem = session.awaitView(contextFor(f.context, explicitDarkLightSystem)) {
                    hasTemperatureColor(it, dark = true)
                }
                assertConfigurationRender(explicitDarkUnderLightSystem, medium, size, 1f, dark = true)

                val higherDensity = Configuration(explicitDarkLightSystem).apply {
                    densityDpi = (initialConfiguration.densityDpi + 40).coerceAtLeast(200)
                }
                configurationHandler.onConfigurationChanged(higherDensity)
                val higherDensityView = session.awaitView(contextFor(f.context, higherDensity)) {
                    hasTemperatureColor(it, dark = true) &&
                        it.resources.displayMetrics.densityDpi == higherDensity.densityDpi
                }
                assertConfigurationRender(higherDensityView, medium, size, 1f, dark = true)

                assertEquals("configuration changes do not restart provideGlance", 1, widget.calls)
                assertEquals("render-only configuration changes do not request weather", 0, f.networkCalls)
                assertTrue("relevant configuration callbacks invalidate installed widgets", widgetUpdates >= 6)
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    @Test
    fun cached_session_remote_views_reapply_without_composition_or_network() = runBlocking {
        val f = Fixture()
        f.put(f.first, 23.0)
        val configuration = configuration(Configuration(f.context.resources.configuration), 1f, false)
        val host = contextFor(f.context, configuration)
        val size = DpSize(320.dp, 144.dp)
        val widget = TestWidget(
            f.widgets, f.widgets.loadCachedState(), true,
            MutableStateFlow(WidgetRenderConfiguration.from(configuration)), size
        )
        val session = RecordingSession(widget, f.context, true, size)
        val job = launch(Dispatchers.Default) { session.runSession(f.context) }
        try {
            val (remote, view) = session.awaitRemoteView(host) {
                texts(it).any { text -> text.text.toString() == "23°C" }
            }
            val translations = session.translations
            val density = host.resources.displayMetrics.density
            val width = (320f * density).roundToInt()
            val height = (144f * density).roundToInt()
            listOf(1.3f, 1f).forEach { scale ->
                val changed = Configuration(configuration).apply { fontScale = scale }
                host.resources.updateConfiguration(changed, host.resources.displayMetrics)
                view.dispatchConfigurationChanged(changed)
                remote.reapply(host, view)
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                view.layout(0, 0, width, height)
                assertEquals(scale, view.resources.configuration.fontScale, 0f)
                assertRenderedContentFitsWithoutOverlap(view)
                assertHourlyProbabilityInkFits(view)
                texts(view).filter { it.contentDescription?.toString()?.startsWith("Rain probability") == true }
                    .forEach { assertEquals("cached probability is not ellipsized", 0, it.layout.getEllipsisCount(0)) }
                assertEquals("host reapply does not translate a new composition", translations, session.translations)
                assertEquals("host reapply does not restart the widget", 1, widget.calls)
                assertEquals("host reapply does not request weather", 0, f.networkCalls)
            }
        } finally {
            job.cancelAndJoin()
        }
    }

    private class TestWidget(
        val repo: WeatherWidgetRepository,
        val initial: WeatherWidgetSnapshot,
        val medium: Boolean,
        private val renderConfigurations: kotlinx.coroutines.flow.StateFlow<WidgetRenderConfiguration>,
        val size: DpSize = if (medium) DpSize(400.dp, 160.dp) else DpSize(200.dp, 160.dp)
    ) : GlanceAppWidget() {
        var calls = 0
        override val stateDefinition = null
        override val sizeMode = SizeMode.Responsive(setOf(size))
        override suspend fun provideGlance(context: Context, id: GlanceId) {
            calls++
            provideContent {
                if (medium) ObservedMediumWeatherWidget(repo, initial, renderConfigurations)
                else ObservedSmallWeatherWidget(repo, initial, renderConfigurations)
            }
        }
    }

    private class RecordingSession(
        widget: TestWidget,
        private val context: Context,
        medium: Boolean,
        private val size: DpSize
    ) : AppWidgetSession(
        widget = widget,
        id = AppWidgetId(if (medium) -2002 else -2001),
        lambdaReceiver = ComponentName(context, SmallWeatherWidgetReceiver::class.java),
        shouldPublish = false
    ) {
        private val updates = Channel<RemoteViews>(Channel.UNLIMITED)
        @Volatile var translations = 0
            private set
        override suspend fun processEmittableTree(context: Context, root: EmittableWithChildren): Boolean {
            val result = super.processEmittableTree(context, root)
            val field = AppWidgetSession::class.java.getDeclaredField("lastRemoteViews").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val remote = (field.get(this) as MutableStateFlow<RemoteViews?>).value
            if (result && remote != null) {
                translations++
                updates.send(remote)
            }
            return result
        }
        suspend fun awaitRemoteView(renderContext: Context = context, matches: (View) -> Boolean): Pair<RemoteViews, View> = withTimeout(10_000) {
            while (true) {
                val density = renderContext.resources.displayMetrics.density
                val width = (size.width.value * density).roundToInt()
                val height = (size.height.value * density).roundToInt()
                val remote = updates.receive()
                val view = remote.apply(renderContext, FrameLayout(renderContext))
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                view.layout(0, 0, width, height)
                if (matches(view)) return@withTimeout remote to view
            }
            error("unreachable")
        }

        suspend fun awaitView(renderContext: Context = context, matches: (View) -> Boolean): View =
            awaitRemoteView(renderContext, matches).second

        suspend fun awaitText(expected: String, absent: String? = null): View = awaitView { view ->
            val text = texts(view).map { it.text.toString() }
            text.any { it.contains(expected) } && (absent == null || text.none { it.contains(absent) })
        }

        suspend fun awaitAccessibleDescription(description: String, present: Boolean): View = awaitView { view ->
            val descriptions = buildList {
                fun collect(current: View) {
                    current.contentDescription?.toString()?.let { add(it) }
                    if (current is ViewGroup) {
                        for (index in 0 until current.childCount) collect(current.getChildAt(index))
                    }
                }
                collect(view)
            }
            descriptions.any { it.contains(description) } == present
        }
    }

    private fun configuration(
        base: Configuration,
        fontScale: Float,
        night: Boolean
    ) = Configuration(base).apply {
        this.fontScale = fontScale
        val nightMode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
    }

    private fun contextFor(context: Context, configuration: Configuration): Context =
        WidgetRenderConfiguration.from(configuration).contextFrom(context)

    private fun assertConfigurationRender(
        root: View,
        medium: Boolean,
        size: DpSize,
        fontScale: Float,
        dark: Boolean
    ) {
        assertEquals(fontScale, root.resources.configuration.fontScale, 0f)
        assertTemperatureTheme(root, dark)
        assertRenderedContentFitsWithoutOverlap(root)

        val density = root.resources.displayMetrics.density
        val refresh = allViews(root).firstOrNull { it.contentDescription == "Refresh weather" }
        if (medium) {
            assertNull("medium widget has no manual refresh action", refresh)
        } else {
            assertNotNull("small widget keeps its refresh action", refresh)
            assertTrue(
                "small refresh target remains at least 48dp",
                requireNotNull(refresh).width >= 48f * density - 1f
            )
        }
        val expectedIconSizeDp = if (medium) {
            WeatherWidgetSizePolicy.medium(
                widthDp = size.width.value,
                heightDp = size.height.value,
                fontScale = fontScale,
                formattedTemperatureLength = "23°C".length,
                apparentTemperatureLength = "24°C".length
            ).currentIconSizeDp
        } else {
            WeatherWidgetSizePolicy.small(
                widthDp = size.width.value,
                heightDp = size.height.value,
                fontScale = fontScale,
                formattedTemperatureLength = "23°C".length,
                apparentTemperatureLength = "24°C".length
            ).iconSizeDp
        }
        val largestIconBitmapWidth = allViews(root).filterIsInstance<ImageView>()
            .mapNotNull { (it.drawable as? BitmapDrawable)?.bitmap?.width }
            .maxOrNull()
        assertEquals(
            "weather vectors are rasterized at the current resource density",
            (expectedIconSizeDp * density).toInt(),
            largestIconBitmapWidth
        )

        if (!medium) return

        val expectedHours = listOf("13:00", "14:00", "15:00", "16:00", "17:00")
        val expectedColumns = WeatherWidgetSizePolicy.medium(
            widthDp = size.width.value,
            heightDp = size.height.value,
            fontScale = fontScale,
            formattedTemperatureLength = "23°C".length,
            apparentTemperatureLength = "24°C".length
        ).hourlyColumnCount
        val viewTexts = texts(root).map { it.text.toString() }
        val visibleHours = viewTexts.filter { it in expectedHours }
        assertEquals(expectedHours.take(expectedColumns), visibleHours)
        assertEquals(
            (20 downTo 20 - expectedColumns + 1).map { "$it°C" },
            viewTexts.filter { it in listOf("20°C", "19°C", "18°C", "17°C", "16°C") }
        )
        val expectedProbabilities = listOf("0%", "0%", "10%", "20%", "60%").take(expectedColumns)
        val probabilityTexts = viewTexts.filter { it in listOf("0%", "10%", "20%", "60%") }
        assertEquals(expectedProbabilities, probabilityTexts)
        assertEquals(
            expectedHours.take(expectedColumns).map { "$it, Partly cloudy" },
            allViews(root).mapNotNull { it.contentDescription?.toString() }
                .filter { description -> expectedHours.any { description == "$it, Partly cloudy" } }
        )
        assertHourlyProbabilityInkFits(root)
    }

    private fun assertTemperatureTheme(root: View, dark: Boolean) {
        val expectedColor = hluMaterialColorScheme(dark).onSurface.toArgb()
        val temperature = texts(root).first { it.text.toString() == "23°C" }
        assertEquals("current temperature follows the selected theme", expectedColor, temperature.currentTextColor)
    }

    private fun hasTemperatureColor(root: View, dark: Boolean): Boolean = runCatching {
        assertTemperatureTheme(root, dark)
    }.isSuccess

    private fun assertHourlyProbabilityInkFits(root: View) {
        texts(root).filter { it.text.toString() in setOf("0%", "10%", "20%", "60%") }
            .forEach { text ->
                val contentTop = text.compoundPaddingTop
                val contentBottom = text.height - text.compoundPaddingBottom
                val lineBottom = contentTop + text.layout.getLineBottom(0)
                assertTrue(
                    "${text.text} line box fits its translated TextView " +
                        "(lineBottom=$lineBottom, contentBottom=$contentBottom, " +
                        "height=${text.height}, textSizePx=${text.textSize}, " +
                        "fontScale=${text.resources.configuration.fontScale})",
                    lineBottom <= contentBottom
                )

                val ink = Rect()
                text.paint.getTextBounds(text.text, 0, text.text.length, ink)
                assertTrue("${text.text} glyph ink stays inside its translated TextView", text.baseline + ink.bottom <= contentBottom)
            }
    }

    private fun allViews(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) addAll(allViews(root.getChildAt(index)))
        }
    }

    private class Fixture {
        val context: Context = ApplicationProvider.getApplicationContext()
        val first = WeatherLocation("first", "First", 43.0, 20.0)
        val second = WeatherLocation("second", "Second", 44.0, 21.0)
        val settings = MutableStateFlow(PersistedSettings())
        val locations = MutableStateFlow(listOf(first, second))
        val active = MutableStateFlow<ActiveLocation?>(ActiveLocation.Saved(first))
        val data = object : DataStore<Preferences> {
            override val data = MutableStateFlow<Preferences>(emptyPreferences())
            val mutex = Mutex()
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
                transform(data.value).also { data.value = it }
            }
        }
        val cache = ForecastCache(data)
        var now = Instant.parse("2026-10-28T12:00:00Z")
        val clock = object : Clock { override fun now() = now }
        var networkCalls = 0
        val source = object : WeatherSource {
            override val provider = WeatherProvider.OPEN_METEO
            override suspend fun getForecast(location: WeatherLocation): WeatherForecast {
                networkCalls++
                error("observation must not request weather")
            }
        }
        val weather = CachingWeatherRepository(mapOf(WeatherProvider.OPEN_METEO to source), cache, clock::now)
        val widgets = WeatherWidgetRepository(object : SettingsRepository {
            override val settings = this@Fixture.settings
            override suspend fun save(settings: PersistedSettings) { this@Fixture.settings.value = settings }
        }, object : LocationRepository {
            override val locations = this@Fixture.locations
            override val activeLocation = active
            override val locationMode = MutableStateFlow(LocationMode.SAVED_LOCATION)
            override val lastKnownCurrentLocation = MutableStateFlow<ActiveLocation.Current?>(null)
            override suspend fun add(location: WeatherLocation) = Unit
            override suspend fun update(location: WeatherLocation) = Unit
            override suspend fun delete(id: String) = Unit
            override suspend fun selectSaved(id: String) = Unit
            override suspend fun setTrackMe(enabled: Boolean) = Unit
        }, weather, clock, DataStoreWidgetRefreshMetadataStore(data))
        fun forecast(location: WeatherLocation, temperature: Double): WeatherForecast {
            val probabilities = listOf(0, 0, 10, 20, 60)
            val hours = (0..4).map { index ->
                HourForecast(
                    time = now + (index + 1).hours,
                    temperature = 20.0 - index,
                    apparentTemperature = null,
                    humidity = null,
                    dewPoint = null,
                    precipitation = null,
                    precipitationProbability = probabilities[index],
                    condition = WeatherCondition.PARTLY_CLOUDY,
                    isDay = true
                )
            }
            return WeatherForecast(
                location,
                WeatherProvider.OPEN_METEO,
                now,
                CurrentWeather(temperature, 24.0, null, null, null, WeatherCondition.CLEAR, true),
                hours,
                emptyList(),
                0.0,
                "UTC"
            )
        }
        suspend fun put(location: WeatherLocation, temperature: Double, provider: WeatherProvider = WeatherProvider.OPEN_METEO) = cache.put(
            ForecastCacheKey(provider, "saved:${location.id}"), forecast(location, temperature).copy(provider = provider)
        )
    }

    companion object {
        private fun texts(view: View): List<TextView> = buildList {
            if (view is TextView) add(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) addAll(texts(view.getChildAt(i)))
        }
    }
}
