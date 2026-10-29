package net.droopia.hluweather.widget

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.glance.GlanceId
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.composeForPreview
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ApplicationProvider
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.theme.hluMaterialColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediumWeatherWidgetTest {

    @Test
    fun production_widget_uses_finite_responsive_sizes_and_advertises_probability_safe_minimum() {
        val widget = MediumWeatherWidget()

        assertTrue(widget.sizeMode is SizeMode.Responsive)
        assertEquals(
            MEDIUM_WEATHER_WIDGET_RESPONSIVE_SIZES,
            (widget.sizeMode as SizeMode.Responsive).sizes
        )
        assertTrue(
            MEDIUM_WEATHER_WIDGET_RESPONSIVE_SIZES.all {
                it.height.value >= WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP
            }
        )
        assertEquals(
            setOf(
                260f to 110f,
                320f to 110f,
                400f to 110f,
                260f to 144f,
                320f to 144f,
                400f to 160f
            ),
            MEDIUM_WEATHER_WIDGET_RESPONSIVE_SIZES.map { it.width.value to it.height.value }.toSet()
        )
    }

    @Test
    fun medium_surface_is_transparent_for_weather_and_empty_states_in_both_themes() {
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { theme ->
            val settings = PersistedSettings(themeMode = theme)
            val states = listOf(
                availableSnapshot(settings = settings),
                WeatherWidgetSnapshot.NoWeather(
                    settings = settings,
                    request = widgetRequest("Trieste"),
                    refresh = WidgetRefreshStatus(lastAttemptFailed = true)
                ),
                WeatherWidgetSnapshot.NoLocation(settings)
            )

            states.forEach { snapshot ->
                val view = render(
                    snapshot,
                    widthDp = WeatherWidgetSizePolicy.MEDIUM_MIN_WIDTH_DP,
                    heightDp = WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP,
                    systemDark = theme == ThemeMode.LIGHT
                )

                when (snapshot) {
                    is WeatherWidgetSnapshot.Available -> assertText(view, "23°C")
                    is WeatherWidgetSnapshot.NoWeather -> assertText(view, "No weather available for Trieste")
                    is WeatherWidgetSnapshot.NoLocation -> assertText(view, "Open HluWeather to choose a location")
                }
                if (snapshot !is WeatherWidgetSnapshot.Available) {
                    assertFalse("empty state does not invent a temperature", findViews(view)
                        .filterIsInstance<TextView>().any { it.text.toString().contains("°") })
                }
                assertTransparentMediumSurface(view, 260f, 110f)
                assertMediumContentStaysWithinOuterInsets(view, 4f)
                assertLayoutFits(view, 260f, 110f)
            }
        }

        val available = render(availableSnapshot(), widthDp = 320f, heightDp = 144f)
        assertTransparentMediumSurface(available, 320f, 144f)
        assertMediumContentStaysWithinOuterInsets(available, 4f)
        assertMediumInternalGapsRemainVisible(available)
        assertLayoutFits(available, 320f, 144f)
    }

    @Test
    fun translated_production_layout_renders_three_four_and_five_hour_columns_without_overflow() {
        val cases = listOf(
            MediumCase(260f, 144f, columns = 3, theme = ThemeMode.LIGHT),
            MediumCase(320f, 144f, columns = 4, theme = ThemeMode.SYSTEM),
            MediumCase(400f, 160f, columns = 5, theme = ThemeMode.DARK)
        )

        cases.forEach { case ->
            val view = render(
                availableSnapshot(settings = PersistedSettings(themeMode = case.theme)),
                case.widthDp,
                case.heightDp,
                fontScale = 1f,
                systemDark = case.theme == ThemeMode.SYSTEM
            )
            val visibleHours = findViews(view).filterIsInstance<TextView>()
                .map { it.text.toString() }
                .filter { it in REFERENCE_HOURS }

            assertEquals(
                "hour branch for $case; visible=$visibleHours, allText=" +
                    findViews(view).filterIsInstance<TextView>().joinToString { it.text.toString() },
                REFERENCE_HOURS.take(case.columns),
                visibleHours
            )
            assertText(view, "23°C")
            assertText(view, "Clear sky")
            assertText(view, "Feels like 24°C")
            assertText(view, "Zaječar")
            assertText(view, "Updated Oct 28 11:17")
            assertEquals("Last weather update Wed, Oct 28, 2026 • 11:17", findDescription(view, "Last weather update Wed, Oct 28, 2026 • 11:17"))
            assertCurrentTemperatureColor(view, dark = case.theme == ThemeMode.DARK || case.theme == ThemeMode.SYSTEM)
            assertHeaderMetadataDoesNotOverlap(view)
            assertRefreshTarget(view)
            assertDividers(
                view,
                expectedCount = case.columns,
                expectedColor = hluMaterialColorScheme(
                    darkTheme = case.theme == ThemeMode.DARK || case.theme == ThemeMode.SYSTEM
                ).outlineVariant.toArgb()
            )
            assertLayoutFits(view, case.widthDp, case.heightDp)
        }
    }

    @Test
    fun compact_110dp_production_layout_keeps_current_weather_and_probability_for_three_to_five_hours() {
        val cases = listOf(
            MediumCase(260f, 110f, columns = 3, theme = ThemeMode.LIGHT),
            MediumCase(320f, 110f, columns = 4, theme = ThemeMode.SYSTEM),
            MediumCase(400f, 110f, columns = 5, theme = ThemeMode.DARK)
        )

        cases.forEach { case ->
            val view = render(
                availableSnapshot(settings = PersistedSettings(themeMode = case.theme)),
                widthDp = case.widthDp,
                heightDp = case.heightDp,
                fontScale = 1f,
                systemDark = case.theme == ThemeMode.SYSTEM
            )
            val text = findViews(view).filterIsInstance<TextView>().map { it.text.toString() }
            val visibleHours = text.filter { it in REFERENCE_HOURS }
            val visibleProbabilities = text.filter { it in listOf("0%", "10%", "20%", "60%") }

            assertEquals("hour branch at compact minimum for $case", REFERENCE_HOURS.take(case.columns), visibleHours)
            assertEquals(
                "every compact hourly column retains its probability for $case",
                listOf("0%", "0%", "10%", "20%", "60%").take(case.columns),
                visibleProbabilities
            )
            assertText(view, "23°C")
            assertText(view, "Clear sky")
            assertText(view, "Zaječar")
            assertRefreshTarget(view)
            assertDividers(
                view,
                expectedCount = case.columns,
                expectedColor = hluMaterialColorScheme(
                    darkTheme = case.theme == ThemeMode.DARK || case.theme == ThemeMode.SYSTEM
                ).outlineVariant.toArgb()
            )
            assertLayoutFits(view, case.widthDp, case.heightDp)
        }
    }

    @Test
    fun translated_medium_text_ink_fits_at_normal_and_130_percent_font_scale() {
        val snapshot = availableSnapshot(
            temperature = "73°F",
            apparentTemperature = "124°F",
            hours = referenceHours(
                temperatures = listOf("−123°F", "123°F", "99°F", "88°F", "77°F"),
                probabilities = listOf(null, 0, 5, 100, 60)
            ),
            settings = PersistedSettings(temperatureUnit = TemperatureUnit.FAHRENHEIT)
        )
        val sizes = listOf(
            293f to 184f,
            320f to 144f,
            400f to 160f,
            260f to 110f
        )

        sizes.forEach { (widthDp, heightDp) ->
            listOf(1f, 1.3f).forEach { fontScale ->
                val view = render(snapshot, widthDp, heightDp, fontScale)
                val expectedColumns = WeatherWidgetSizePolicy.medium(
                    widthDp = widthDp,
                    heightDp = heightDp,
                    fontScale = fontScale,
                    formattedTemperatureLength = "−123°F".length,
                    currentConditionLabelLength = "Clear sky".length,
                    apparentTemperatureLength = "124°F".length,
                    headerTimestampTextLength = "Updated Oct 28 11:17".length
                ).hourlyColumnCount
                val textViews = findViews(view).filterIsInstance<TextView>()

                assertEquals(fontScale.toString(), fontScale, view.resources.configuration.fontScale, 0f)
                assertText(view, "73°F")
                assertEquals(
                    "hour labels remain present at ${widthDp}x${heightDp}dp, fontScale=$fontScale",
                    REFERENCE_HOURS.take(expectedColumns),
                    textViews.map { it.text.toString() }.filter { it in REFERENCE_HOURS }
                )
                assertEquals(
                    "hourly temperatures remain present at ${widthDp}x${heightDp}dp, fontScale=$fontScale",
                    listOf("−123°F", "123°F", "99°F", "88°F", "77°F").take(expectedColumns),
                    textViews.map { it.text.toString() }
                        .filter { it in listOf("−123°F", "123°F", "99°F", "88°F", "77°F") }
                )
                assertEquals(
                    "all hourly probabilities remain present at ${widthDp}x${heightDp}dp, fontScale=$fontScale",
                    listOf("—", "0%", "5%", "100%", "60%").take(expectedColumns),
                    textViews.map { it.text.toString() }.filter { it in listOf("—", "0%", "5%", "100%", "60%") }
                )
                val hourlyIconDescriptions = findViews(view)
                    .mapNotNull { it.contentDescription?.toString() }
                    .filter { description -> REFERENCE_HOURS.any { description == "$it, Partly cloudy" } }
                assertEquals(
                    "hour icons remain present at ${widthDp}x${heightDp}dp, fontScale=$fontScale",
                    REFERENCE_HOURS.take(expectedColumns).map { "$it, Partly cloudy" },
                    hourlyIconDescriptions
                )
                assertLayoutFits(view, widthDp, heightDp)
                assertTextInkFits(view, "${widthDp}x${heightDp}dp at fontScale=$fontScale")
            }
        }
    }

    @Config(sdk = [34])
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    @Test
    fun cached_production_remote_views_fit_when_host_reapplies_them_at_changed_font_scale() {
        val snapshot = availableSnapshot(
            locationName = "A very long saved location name for adaptive header text",
            temperature = "−123°F",
            apparentTemperature = "124°F",
            hours = referenceHours(
                temperatures = listOf("−123°F", "123°F", "99°F", "88°F", "77°F"),
                probabilities = listOf(null, 0, 5, 100, 60)
            ),
            settings = PersistedSettings(temperatureUnit = TemperatureUnit.FAHRENHEIT)
        )
        val sizes = listOf(
            260f to 110f,
            293f to 184f,
            320f to 144f,
            400f to 160f
        )

        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { theme ->
            val themedSnapshot = snapshot.copy(settings = snapshot.settings.copy(themeMode = theme))
            sizes.forEach { (widthDp, heightDp) ->
                val baseContext = ApplicationProvider.getApplicationContext<Context>()
                val initialConfiguration = Configuration(baseContext.resources.configuration).apply {
                    fontScale = 1f
                    val nightMode = if (theme == ThemeMode.DARK) {
                        Configuration.UI_MODE_NIGHT_YES
                    } else {
                        Configuration.UI_MODE_NIGHT_NO
                    }
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
                }
                val hostContext = baseContext.createConfigurationContext(initialConfiguration)
                val density = hostContext.resources.displayMetrics.density
                val providerInfo = AppWidgetProviderInfo().apply {
                    minWidth = (widthDp * density).roundToInt()
                    minHeight = (heightDp * density).roundToInt()
                }
                val productionRemoteViews = runBlocking {
                    PreviewMediumWeatherWidget(themedSnapshot).composeForPreview(
                        hostContext,
                        AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                        providerInfo
                    )
                }
                val widthPx = (widthDp * density).roundToInt()
                val heightPx = (heightDp * density).roundToInt()
                val expectedColumns = WeatherWidgetSizePolicy.medium(
                    widthDp = widthDp,
                    heightDp = heightDp,
                    fontScale = 1f,
                    formattedTemperatureLength = "−123°F".length,
                    apparentTemperatureLength = "124°F".length
                ).hourlyColumnCount
                val expectedProbabilities = listOf("—", "0%", "5%", "100%", "60%")
                    .take(expectedColumns)
                val hostView = productionRemoteViews.apply(hostContext, FrameLayout(hostContext))
                hostView.measure(
                    View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
                )
                hostView.layout(0, 0, widthPx, heightPx)
                assertText(hostView, "−123°F")
                assertEquals(expectedProbabilities, visibleProbabilities(hostView))
                assertNativeAutoSizing(hostView)
                assertLayoutFits(hostView, widthDp, heightDp)
                assertTextInkFits(hostView, "$widthDp×$heightDp dp at host font scale 1.0")
                val probabilityBeforeScale = findText(hostView, "5%")
                val initialMinTextSize = probabilityBeforeScale.autoSizeMinTextSize

                // Simulate the launcher updating its resources and reapplying its cached RemoteViews.
                // No Glance composition, app callback, or weather worker runs between these applies.
                val scaledConfiguration = Configuration(initialConfiguration).apply { fontScale = 1.3f }
                hostContext.resources.updateConfiguration(
                    scaledConfiguration,
                    hostContext.resources.displayMetrics
                )
                hostView.dispatchConfigurationChanged(scaledConfiguration)
                productionRemoteViews.reapply(hostContext, hostView)
                hostView.measure(
                    View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
                )
                hostView.layout(0, 0, widthPx, heightPx)
                assertEquals(1.3f, hostView.resources.configuration.fontScale, 0f)
                assertText(hostView, "−123°F")
                assertEquals(expectedProbabilities, visibleProbabilities(hostView))
                assertNativeAutoSizing(hostView)
                val probabilityAfterScale = findText(hostView, "5%")
                assertEquals(initialMinTextSize, probabilityAfterScale.autoSizeMinTextSize)
                assertTrue(
                    "host font fitting respects the readable physical minimum",
                    probabilityAfterScale.textSize >= probabilityAfterScale.autoSizeMinTextSize - 1f
                )
                assertLayoutFits(hostView, widthDp, heightDp)
                assertTextInkFits(hostView, "$widthDp×$heightDp dp at host font scale 1.3")

                val restoredConfiguration = Configuration(scaledConfiguration).apply { fontScale = 1f }
                hostContext.resources.updateConfiguration(
                    restoredConfiguration,
                    hostContext.resources.displayMetrics
                )
                hostView.dispatchConfigurationChanged(restoredConfiguration)
                productionRemoteViews.reapply(hostContext, hostView)
                hostView.measure(
                    View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
                )
                hostView.layout(0, 0, widthPx, heightPx)
                assertEquals(1f, hostView.resources.configuration.fontScale, 0f)
                assertNativeAutoSizing(hostView)
                assertEquals(initialMinTextSize, findText(hostView, "5%").autoSizeMinTextSize)
                assertLayoutFits(hostView, widthDp, heightDp)
                assertTextInkFits(hostView, "$widthDp×$heightDp dp after host font scale restore")
            }
        }
    }

    @Config(sdk = [28, 34])
    @Test
    fun system_configuration_change_rethemes_medium_widget_but_explicit_preferences_win() {
        val system = availableSnapshot(settings = PersistedSettings(themeMode = ThemeMode.SYSTEM))
        assertRenderedTheme(render(system, 400f, 160f, systemDark = false), dark = false)
        assertRenderedTheme(render(system, 400f, 160f, systemDark = true), dark = true)

        val light = availableSnapshot(settings = PersistedSettings(themeMode = ThemeMode.LIGHT))
        assertRenderedTheme(render(light, 400f, 160f, systemDark = true), dark = false)

        val dark = availableSnapshot(settings = PersistedSettings(themeMode = ThemeMode.DARK))
        assertRenderedTheme(render(dark, 400f, 160f, systemDark = false), dark = true)
    }

    @Test
    fun long_location_ellipsizes_without_hiding_medium_refresh_or_update_metadata() {
        val longName = "A very long location name that must leave room for controls"
        val view = render(
            availableSnapshot(locationName = longName),
            widthDp = WeatherWidgetSizePolicy.MEDIUM_MIN_WIDTH_DP,
            heightDp = WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP
        )
        val location = findViews(view).filterIsInstance<TextView>()
            .first { it.contentDescription == "Weather for $longName" }

        assertTrue("long location ends with an ellipsis", location.text.endsWith("…"))
        assertTrue("update time remains visible when policy reserves its space", hasText(view, "Updated Oct 28 11:17"))
        assertHeaderMetadataDoesNotOverlap(view, location)
        assertRefreshTarget(view)
        assertLayoutFits(view, WeatherWidgetSizePolicy.MEDIUM_MIN_WIDTH_DP, WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP)
    }

    @Test
    fun every_hour_shows_accessible_probability_and_distinguishes_missing_from_zero_in_both_themes() {
        listOf(
            ThemeMode.LIGHT to false,
            ThemeMode.DARK to true
        ).forEach { (theme, dark) ->
            val view = render(
                availableSnapshot(
                    hours = referenceHours(probabilities = listOf(null, 0, 10, 20, 60)),
                    settings = PersistedSettings(themeMode = theme)
                ),
                widthDp = 260f,
                heightDp = WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP,
                systemDark = !dark
            )

            assertText(view, "—")
            assertText(view, "0%")
            assertEquals("Rain probability unavailable", findDescription(view, "Rain probability unavailable"))
            assertEquals("Rain probability 0 percent", findDescription(view, "Rain probability 0 percent"))
            assertTrue("droplet uses the theme's blue primary accent", hasSmallTintedBitmap(
                view,
                hluMaterialColorScheme(darkTheme = dark).primary.toArgb()
            ))
            assertLayoutFits(view, 260f, WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP)
        }
    }

    @Test
    fun partial_and_empty_future_hours_are_not_repeated_or_fabricated_and_keep_current_weather() {
        val partial = render(
            availableSnapshot(hours = referenceHours(probabilities = listOf(0, 20)).take(2)),
            widthDp = 400f,
            heightDp = 160f
        )
        val expired = render(
            availableSnapshot(
                hours = emptyList(),
                stale = true,
                refresh = WidgetRefreshStatus(
                    endedAt = Instant.parse("2026-10-29T12:42:00Z"),
                    lastAttemptFailed = true
                )
            ),
            widthDp = 260f,
            heightDp = WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP
        )

        assertEquals(
            listOf("22:00", "23:00"),
            findViews(partial).filterIsInstance<TextView>().map { it.text.toString() }
                .filter { it in REFERENCE_HOURS }
        )
        assertText(partial, "20°C")
        assertText(partial, "18°C")
        assertText(expired, "23°C")
        assertText(expired, "Clear sky")
        assertText(expired, "Stale · Oct 28 11:17")
        assertFalse("expired forecast contributes no hourly label", REFERENCE_HOURS.any { hasText(expired, it) })
        assertFalse("no future hours does not invent a probability", hasText(expired, "60%"))
        assertLayoutFits(partial, 400f, 160f)
        assertLayoutFits(expired, 260f, WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP)
    }

    @Test
    fun failed_refresh_keeps_cached_current_and_hourly_weather_with_its_original_update_time() {
        val view = render(
            availableSnapshot(
                temperature = "73°F",
                apparentTemperature = null,
                hours = referenceHours(
                    temperatures = listOf("68°F", "64°F", "63°F", "61°F", "59°F"),
                    probabilities = listOf(null, 0, 10, 20, 60)
                ),
                settings = PersistedSettings(temperatureUnit = TemperatureUnit.FAHRENHEIT),
                refresh = WidgetRefreshStatus(
                    endedAt = Instant.parse("2026-10-28T12:00:00Z"),
                    lastAttemptFailed = true
                )
            ),
            widthDp = 400f,
            heightDp = 160f
        )

        assertText(view, "73°F")
        assertText(view, "Clear sky")
        assertFalse("missing apparent temperature stays absent", findViews(view)
            .filterIsInstance<TextView>().any { it.text.toString().startsWith("Feels like") })
        assertEquals(
            listOf("22:00", "23:00", "00:00", "01:00", "02:00"),
            findViews(view).filterIsInstance<TextView>().map { it.text.toString() }
                .filter { it in REFERENCE_HOURS }
        )
        listOf("68°F", "64°F", "63°F", "61°F", "59°F", "—", "0%").forEach { assertText(view, it) }
        assertText(view, "Stale · Oct 28 11:17")
        assertEquals(
            "Stale weather. Last weather update Wed, Oct 28, 2026 • 11:17",
            findDescription(view, "Stale weather. Last weather update Wed, Oct 28, 2026 • 11:17")
        )
        assertLayoutFits(view, 400f, 160f)
    }

    @Test
    fun signed_and_three_digit_fahrenheit_fit_at_the_supported_minimum_with_large_font_scale() {
        listOf(
            "−123°F" to ThemeMode.LIGHT,
            "123°F" to ThemeMode.DARK
        ).forEach { (temperature, theme) ->
            val view = render(
                availableSnapshot(
                    temperature = temperature,
                    apparentTemperature = "124°F",
                    hours = referenceHours(
                        temperatures = listOf("−123°F", "123°F", "99°F", "88°F", "77°F"),
                        probabilities = listOf(null, 100, 10, 20, 60)
                    ),
                    settings = PersistedSettings(
                        temperatureUnit = TemperatureUnit.FAHRENHEIT,
                        themeMode = theme
                    )
                ),
                widthDp = WeatherWidgetSizePolicy.MEDIUM_MIN_WIDTH_DP,
                heightDp = WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP,
                fontScale = 2f,
                systemDark = theme == ThemeMode.LIGHT
            )

            assertText(view, temperature)
            assertText(view, "—")
            assertText(view, "100%")
            assertEquals("Rain probability unavailable", findDescription(view, "Rain probability unavailable"))
            assertFalse(
                "long update metadata yields at larger font scales",
                findViews(view).any { it.contentDescription?.toString()?.startsWith("Last weather update") == true }
            )
            assertLayoutFits(
                view,
                WeatherWidgetSizePolicy.MEDIUM_MIN_WIDTH_DP,
                WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP
            )
        }
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun render(
        snapshot: WeatherWidgetSnapshot,
        widthDp: Float,
        heightDp: Float,
        fontScale: Float = 1f,
        systemDark: Boolean = false
    ): View {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(baseContext.resources.configuration).apply {
            this.fontScale = fontScale
            val nightMode = if (systemDark) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        val context = baseContext.createConfigurationContext(configuration)
        val density = context.resources.displayMetrics.density
        val providerInfo = AppWidgetProviderInfo().apply {
            minWidth = (widthDp * density).roundToInt()
            minHeight = (heightDp * density).roundToInt()
        }
        val remoteViews = runBlocking {
            PreviewMediumWeatherWidget(snapshot).composeForPreview(
                context,
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                providerInfo
            )
        }
        val view = remoteViews.apply(context, FrameLayout(context))
        val widthPx = (widthDp * density).roundToInt()
        val heightPx = (heightDp * density).roundToInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, widthPx, heightPx)
        return view
    }

    private fun assertLayoutFits(root: View, widthDp: Float, heightDp: Float) {
        val density = root.resources.displayMetrics.density
        val widthPx = (widthDp * density).roundToInt()
        val heightPx = (heightDp * density).roundToInt()
        val views = findViews(root)

        assertEquals(widthPx, root.width)
        assertEquals(heightPx, root.height)
        assertFalse("medium layout must never use horizontal scrolling", views.any { it is HorizontalScrollView })
        views.filterIsInstance<TextView>().forEach { text ->
            val bounds = absoluteBounds(text, root)
            assertTrue("text left edge remains inside the card: ${text.text}", bounds.left >= 0)
            assertTrue("text right edge remains inside the card: ${text.text}", bounds.right <= widthPx)
            assertTrue("text top edge remains inside the card: ${text.text}", bounds.top >= 0)
            assertTrue("text bottom edge remains inside the card: ${text.text}", bounds.bottom <= heightPx)
            assertEquals("widget text stays on one line: ${text.text}", 1, text.lineCount)
        }
        views.filterIsInstance<TextView>().filter { it.text.toString().contains("°") }.forEach { temperature ->
            val textWidth = temperature.width - temperature.compoundPaddingLeft - temperature.compoundPaddingRight
            assertTrue(
                "temperature '${temperature.text}' has a usable width " +
                    "(width=${temperature.width}, padding=${temperature.compoundPaddingLeft}/" +
                    "${temperature.compoundPaddingRight}, bounds=${absoluteBounds(temperature, root)}, " +
                    "views=${views.filterIsInstance<TextView>().joinToString { "${it.text}:${it.width}@${absoluteBounds(it, root).left}" }})",
                textWidth > 0
            )
            assertTrue(
                "temperature '${temperature.text}' fits its translated TextView: " +
                    "${temperature.paint.measureText(temperature.text.toString())} > $textWidth",
                temperature.paint.measureText(temperature.text.toString()) <= textWidth + 1f
            )
        }
        views.filterIsInstance<TextView>()
            .filter { it.contentDescription?.toString()?.startsWith("Rain probability") == true }
            .forEach { probability ->
                val width = probability.width - probability.compoundPaddingLeft - probability.compoundPaddingRight
                assertTrue("precipitation probability remains readable", width > 0)
                assertTrue(
                    "probability '${probability.text}' fits its translated cell " +
                        "(width=$width, measured=${probability.paint.measureText(probability.text.toString())}, " +
                        "size=${probability.textSize}, bounds=${absoluteBounds(probability, root)})",
                    probability.paint.measureText(probability.text.toString()) <= width + 1f
                )
                assertTrue(
                    "precipitation probability retains readable text size " +
                        "(size=${probability.textSize}, min=${7f * density}, text=${probability.text}, " +
                        "bounds=${absoluteBounds(probability, root)})",
                    probability.textSize >= 7f * density
                )
            }
        assertRenderedContentFitsWithoutOverlap(root)
    }

    private fun assertTransparentMediumSurface(root: View, widthDp: Float, heightDp: Float) {
        val density = root.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))

        val edgeX = (2f * density).roundToInt()
        val midY = root.height / 2
        assertEquals("transparent left wallpaper strip", 0, bitmap.getPixel(edgeX, midY) ushr 24)
        assertEquals(
            "transparent right wallpaper strip",
            0,
            bitmap.getPixel(root.width - edgeX - 1, midY) ushr 24
        )

        val policy = WeatherWidgetSizePolicy.medium(
            widthDp = widthDp,
            heightDp = heightDp,
            fontScale = root.resources.configuration.fontScale,
            formattedTemperatureLength = 4
        )
        val refresh = findViews(root).first { it.contentDescription == "Refresh weather" }
        val refreshBounds = absoluteBounds(refresh, root)
        val expectedTopInsetPx = (policy.cardVerticalPaddingDp * density).roundToInt()
        assertTrue(
            "medium header begins at its reduced vertical outer inset",
            kotlin.math.abs(refreshBounds.top - expectedTopInsetPx) <= 1
        )
        assertTrue(
            "medium header retains the policy's 48dp height inside the outer insets",
            refreshBounds.bottom - refreshBounds.top >=
                WeatherWidgetSizePolicy.REFRESH_TOUCH_TARGET_DP * density - 1f
        )
        val betweenHeaderAndBodyY = (
            policy.cardVerticalPaddingDp + WeatherWidgetSizePolicy.REFRESH_TOUCH_TARGET_DP +
                policy.headerToBodyGapDp / 2f
            ) * density
        assertEquals(
            "transparent gap between header and weather body",
            0,
            bitmap.getPixel(root.width / 2, betweenHeaderAndBodyY.roundToInt()) ushr 24
        )

        val fullSizeBackgrounds = findViews(root).filterIsInstance<ImageView>().filter {
            it.width >= root.width - 1 && it.height >= root.height - 1 && it.drawable !is BitmapDrawable
        }
        assertTrue("medium does not attach a card-sized background image", fullSizeBackgrounds.isEmpty())
    }

    private fun assertMediumContentStaysWithinOuterInsets(root: View, insetDp: Float) {
        val density = root.resources.displayMetrics.density
        val insetPx = insetDp * density
        val renderedContent = findViews(root).filter { view ->
            view.visibility == View.VISIBLE && when (view) {
                is TextView -> view.text.isNotBlank()
                is ImageView -> view.drawable is BitmapDrawable
                else -> false
            }
        }
        renderedContent.forEach { view ->
            val bounds = absoluteBounds(view, root)
            assertTrue(
                "${view.javaClass.simpleName} begins after the ${insetDp}dp medium inset: $bounds",
                bounds.left >= insetPx - 1f
            )
            assertTrue(
                "${view.javaClass.simpleName} ends before the ${insetDp}dp medium inset: $bounds",
                bounds.right <= root.width - insetPx + 1f
            )
        }
    }

    private fun assertMediumInternalGapsRemainVisible(root: View) {
        val density = root.resources.displayMetrics.density
        val time = findText(root, "22:00")
        val icon = findViews(root).filterIsInstance<ImageView>()
            .first { it.contentDescription == "22:00, Partly cloudy" }
        val temperature = findText(root, "20°C")
        val probability = findViews(root).filterIsInstance<TextView>()
            .first { it.contentDescription == "Rain probability 0 percent" }
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val timeInk = paintedBounds(bitmap, absoluteBounds(time, root))
        val iconInk = paintedBounds(bitmap, absoluteBounds(icon, root))
        val temperatureInk = paintedBounds(bitmap, absoluteBounds(temperature, root))
        val probabilityInk = paintedBounds(bitmap, absoluteBounds(probability, root))

        assertTrue(
            "rendered hour label keeps a 2dp gap before its icon " +
                "(gap=${iconInk.top - timeInk.bottom}px, time=$timeInk, icon=$iconInk)",
            iconInk.top - timeInk.bottom >= 2f * density - 1f
        )
        assertTrue(
            "rendered hour icon keeps a 2dp gap before its temperature " +
                "(gap=${temperatureInk.top - iconInk.bottom}px, icon=$iconInk, temperature=$temperatureInk)",
            temperatureInk.top - iconInk.bottom >= 2f * density - 1f
        )
        assertTrue(
            "rendered temperature keeps a 2dp gap before precipitation " +
                "(gap=${probabilityInk.top - temperatureInk.bottom}px, temperature=$temperatureInk, probability=$probabilityInk)",
            probabilityInk.top - temperatureInk.bottom >= 2f * density - 1f
        )

        val divider = findViews(root).first { view ->
            view.width <= 2f * density && view.height >= 30f * density &&
                (view.background as? ColorDrawable)?.color == hluMaterialColorScheme(false).outlineVariant.toArgb()
        }
        val dividerBounds = absoluteBounds(divider, root)
        val timeBounds = absoluteBounds(time, root)
        assertTrue(
            "hour cell retains horizontal padding after its divider",
            timeBounds.left - dividerBounds.right >= 3f * density - 1f
        )
    }

    private fun paintedBounds(bitmap: Bitmap, region: Bounds): Bounds {
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        for (y in region.top.coerceAtLeast(0) until region.bottom.coerceAtMost(bitmap.height)) {
            for (x in region.left.coerceAtLeast(0) until region.right.coerceAtMost(bitmap.width)) {
                if (bitmap.getPixel(x, y) ushr 24 > 32) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x + 1)
                    bottom = maxOf(bottom, y + 1)
                }
            }
        }
        assertTrue("rendered content has visible pixels inside $region", right >= 0)
        return Bounds(left, top, right, bottom)
    }

    private fun assertTextInkFits(root: View, sizeDescription: String) {
        findViews(root).filterIsInstance<TextView>().filter { it.text.isNotBlank() }.forEach { text ->
            val layout = text.layout
            assertEquals("text remains on one measured layout line: ${text.text} ($sizeDescription)", 1, layout.lineCount)

            val contentTop = text.compoundPaddingTop
            val contentBottom = text.height - text.compoundPaddingBottom
            val lineBottom = contentTop + layout.getLineBottom(0)
            assertTrue(
                "measured text line stays within its TextView: '${text.text}' ($sizeDescription) " +
                    "(lineBottom=$lineBottom, contentBottom=$contentBottom, " +
                    "compoundPadding=${text.compoundPaddingTop}/${text.compoundPaddingBottom}, " +
                    "layoutLineBottom=${layout.getLineBottom(0)}, layoutBaseline=${layout.getLineBaseline(0)}, " +
                    "includeFontPadding=${text.includeFontPadding}, size=${text.textSize}, " +
                    "bounds=${absoluteBounds(text, root)}, parents=${viewAncestors(text)})",
                lineBottom <= contentBottom
            )

            val baseline = text.baseline
            val metrics = text.paint.fontMetricsInt
            val metricTop = baseline + if (text.includeFontPadding) metrics.top else metrics.ascent
            val metricBottom = baseline + if (text.includeFontPadding) metrics.bottom else metrics.descent
            assertTrue(
                "font metrics ink starts inside TextView content: '${text.text}' ($sizeDescription) " +
                    "(top=$metricTop, contentTop=$contentTop, baseline=$baseline, " +
                    "metrics=$metrics, includeFontPadding=${text.includeFontPadding})",
                metricTop >= contentTop
            )
            assertTrue(
                "font metrics ink ends inside TextView content: '${text.text}' ($sizeDescription) " +
                    "(bottom=$metricBottom, contentBottom=$contentBottom, baseline=$baseline, " +
                    "metrics=$metrics, includeFontPadding=${text.includeFontPadding})",
                metricBottom <= contentBottom
            )

            val ink = Rect()
            text.paint.getTextBounds(text.text, 0, text.text.length, ink)
            assertTrue(
                "actual glyph ink stays inside TextView: '${text.text}' ($sizeDescription) " +
                    "(ink=$ink, baseline=$baseline, height=${text.height}, " +
                    "compoundPadding=${text.compoundPaddingTop}/${text.compoundPaddingBottom})",
                baseline + ink.bottom <= contentBottom
            )
            assertTrue(
                "actual glyph ink stays below TextView top: '${text.text}' ($sizeDescription) " +
                    "(ink=$ink, baseline=$baseline, contentTop=$contentTop)",
                baseline + ink.top >= contentTop
            )
        }
    }

    private fun assertDividers(root: View, expectedCount: Int, expectedColor: Int) {
        val density = root.resources.displayMetrics.density
        val dividers = findViews(root).filter { view ->
            val backgroundColor = (view.background as? ColorDrawable)?.color
            view.width <= 2f * density && view.height >= 30f * density &&
                backgroundColor == expectedColor
        }
        assertEquals("one subtle vertical divider per visible hour", expectedCount, dividers.size)
    }

    private fun assertHeaderMetadataDoesNotOverlap(
        root: View,
        location: TextView = findViews(root).filterIsInstance<TextView>()
            .first { it.contentDescription == "Weather for Zaječar" }
    ) {
        val update = findViews(root).filterIsInstance<TextView>()
            .first { it.contentDescription?.toString()?.startsWith("Last weather update") == true }
        val refresh = findViews(root).first { it.contentDescription == "Refresh weather" }

        assertTrue("location and update time do not overlap", absoluteBounds(location, root).right <= absoluteBounds(update, root).left)
        assertTrue("update time does not overlap the refresh control", absoluteBounds(update, root).right <= absoluteBounds(refresh, root).left)
    }

    private fun assertRefreshTarget(root: View) {
        val density = root.resources.displayMetrics.density
        val refresh = findViews(root).first { it.contentDescription == "Refresh weather" }
        val bounds = absoluteBounds(refresh, root)
        assertTrue("medium refresh target is at least 48 dp", refresh.width >= 48f * density - 1f)
        assertTrue("medium refresh stays inside the right card edge", bounds.right <= root.width)
        assertTrue("medium refresh remains in the header", bounds.top < 60f * density)
    }

    private fun assertCurrentTemperatureColor(root: View, dark: Boolean) {
        val expected = hluMaterialColorScheme(darkTheme = dark).onSurface.toArgb()
        val actual = findViews(root).filterIsInstance<TextView>()
            .first { it.text.toString() == "23°C" }.currentTextColor
        assertEquals(expected, actual)
    }

    private fun assertRenderedTheme(root: View, dark: Boolean) {
        assertCurrentTemperatureColor(root, dark)
        assertDividers(
            root,
            expectedCount = 5,
            expectedColor = hluMaterialColorScheme(darkTheme = dark).outlineVariant.toArgb()
        )
    }

    private fun hasSmallTintedBitmap(root: View, expectedColor: Int): Boolean {
        val density = root.resources.displayMetrics.density
        return findViews(root).filterIsInstance<ImageView>().any { image ->
            val bitmap = (image.drawable as? BitmapDrawable)?.bitmap ?: return@any false
            image.width <= 10f * density + 1f && bitmap.hasPixelColor(expectedColor)
        }
    }

    private fun android.graphics.Bitmap.hasPixelColor(expectedColor: Int): Boolean {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = getPixel(x, y)
                if (color ushr 24 > 32 && (color and 0x00FFFFFF) == (expectedColor and 0x00FFFFFF)) return true
            }
        }
        return false
    }

    private fun assertText(root: View, expected: String) {
        assertTrue("expected translated TextView '$expected'", hasText(root, expected))
    }

    private fun assertNativeAutoSizing(root: View) {
        val density = root.resources.displayMetrics.density
        findViews(root).filterIsInstance<TextView>().filter { it.text.isNotBlank() }.forEach { text ->
            assertEquals(
                "${text.text} uses platform uniform auto-sizing",
                TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM,
                text.autoSizeTextType
            )
            assertTrue("${text.text} has a physical minimum size", text.autoSizeMinTextSize >= 7f * density - 1f)
            assertTrue("${text.text} has a role maximum above its minimum", text.autoSizeMaxTextSize > text.autoSizeMinTextSize)
        }
    }

    private fun findText(root: View, expected: String): TextView = findViews(root)
        .filterIsInstance<TextView>()
        .first { it.text.toString() == expected }

    private fun hasText(root: View, expected: String): Boolean = findViews(root)
        .filterIsInstance<TextView>()
        .any { it.text.toString() == expected }

    private fun visibleProbabilities(root: View): List<String> = findViews(root)
        .filterIsInstance<TextView>()
        .map { it.text.toString() }
        .filter { it in listOf("—", "0%", "5%", "10%", "20%", "60%", "100%") }

    private fun findDescription(root: View, description: String): String? = findViews(root)
        .firstOrNull { it.contentDescription == description }
        ?.contentDescription
        ?.toString()

    private fun absoluteBounds(view: View, root: View): Bounds {
        var left = view.left
        var top = view.top
        var parent = view.parent
        while (parent is View && parent !== root) {
            left += parent.left
            top += parent.top
            parent = parent.parent
        }
        return Bounds(left, top, left + view.width, top + view.height)
    }

    private fun viewAncestors(view: View): String = buildList {
        var current: View? = view
        while (current != null) {
            add(
                "${current.javaClass.simpleName}(${current.width}x${current.height}, " +
                    "lp=${current.layoutParams?.width}x${current.layoutParams?.height})"
            )
            current = current.parent as? View
        }
    }.joinToString(" <- ")

    private fun findViews(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) addAll(findViews(root.getChildAt(index)))
        }
    }

    private fun availableSnapshot(
        locationName: String = "Zaječar",
        temperature: String = "23°C",
        apparentTemperature: String? = "24°C",
        hours: List<WidgetHour> = referenceHours(),
        settings: PersistedSettings = PersistedSettings(),
        stale: Boolean = false,
        refresh: WidgetRefreshStatus = WidgetRefreshStatus()
    ) = WeatherWidgetSnapshot.Available(
        settings = settings,
        request = widgetRequest(locationName),
        weather = WeatherWidgetState(
            locationName = locationName,
            temperatureText = temperature,
            temperatureContentDescription = temperature.toSpokenTemperature(),
            apparentTemperatureText = apparentTemperature,
            apparentTemperatureContentDescription = apparentTemperature?.toSpokenTemperature(),
            condition = WeatherCondition.CLEAR,
            conditionLabel = "Clear sky",
            isDay = true,
            hours = hours,
            lastWeatherUpdate = Instant.parse("2026-10-28T10:17:00Z"),
            lastWeatherUpdateText = "Wed, Oct 28, 2026 • 11:17",
            lastWeatherUpdateLocalTimeText = "11:17",
            lastWeatherUpdateCompactText = "Oct 28 11:17",
            isStale = stale
        ),
        refresh = refresh
    )

    private fun referenceHours(
        temperatures: List<String> = listOf("20°C", "18°C", "17°C", "16°C", "15°C"),
        probabilities: List<Int?> = listOf(0, 0, 10, 20, 60)
    ): List<WidgetHour> = REFERENCE_HOURS.mapIndexed { index, time ->
        val probability = probabilities.getOrNull(index)
        val temperature = temperatures.getOrElse(index) { "${20 - index}°C" }
        WidgetHour(
            time = Instant.parse(REFERENCE_INSTANTS[index]),
            timeText = time,
            temperatureText = temperature,
            temperatureContentDescription = temperature.toSpokenTemperature(),
            condition = WeatherCondition.PARTLY_CLOUDY,
            conditionLabel = "Partly cloudy",
            isDay = index < 2,
            precipitationProbability = probability,
            precipitationProbabilityText = probability?.let { "$it%" } ?: "—",
            precipitationProbabilityContentDescription = probability
                ?.let { "Rain probability $it percent" }
                ?: "Rain probability unavailable"
        )
    }

    private fun String.toSpokenTemperature(): String = replace("−", "minus ")
        .replace("°F", " degrees F")
        .replace("°C", " degrees C")

    private fun widgetRequest(locationName: String) = WeatherWidgetRequest(
        provider = WeatherProvider.OPEN_METEO,
        location = ActiveLocation.Saved(
            WeatherLocation(
                id = locationName.lowercase(),
                name = locationName,
                latitude = 43.9,
                longitude = 20.3
            )
        ),
        locationName = locationName
    )

    private data class MediumCase(
        val widthDp: Float,
        val heightDp: Float,
        val columns: Int,
        val theme: ThemeMode
    )

    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private class PreviewMediumWeatherWidget(
        private val snapshot: WeatherWidgetSnapshot
    ) : GlanceAppWidget() {
        override val sizeMode: SizeMode = SizeMode.Responsive(MEDIUM_WEATHER_WIDGET_RESPONSIVE_SIZES)

        override suspend fun provideGlance(context: Context, id: GlanceId) = provideSnapshot()

        override suspend fun providePreview(context: Context, widgetCategory: Int) = provideSnapshot()

        private suspend fun provideSnapshot() {
            provideContent { MediumWeatherWidgetContent(snapshot) }
        }
    }

    private companion object {
        val REFERENCE_HOURS = listOf("22:00", "23:00", "00:00", "01:00", "02:00")
        val REFERENCE_INSTANTS = listOf(
            "2026-10-28T20:00:00Z",
            "2026-10-28T21:00:00Z",
            "2026-10-28T22:00:00Z",
            "2026-10-28T23:00:00Z",
            "2026-10-29T00:00:00Z"
        )
    }
}
