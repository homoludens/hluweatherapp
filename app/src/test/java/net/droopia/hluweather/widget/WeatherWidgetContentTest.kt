package net.droopia.hluweather.widget

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.graphics.toArgb
import androidx.glance.GlanceId
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.composeForPreview
import androidx.glance.appwidget.provideContent
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.theme.DarkHluColors
import net.droopia.hluweather.ui.theme.LightHluColors
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
class WeatherWidgetContentTest {

    @Test
    fun translated_light_system_card_uses_shared_palette_weather_icon_and_accessible_text() {
        val state = availableSnapshot(settings = PersistedSettings(themeMode = ThemeMode.SYSTEM))
        val view = render(state, systemDark = false)

        assertText(view, "Zaječar")
        assertText(view, "23°C")
        assertText(view, "Clear sky")
        assertText(view, "Feels like 24°C")
        assertEquals(
            "23 degrees C",
            findTextView(view, "23°C")?.contentDescription?.toString()
        )
        assertEquals(
            "Weather for Zaječar",
            findViews(view).filterIsInstance<TextView>()
                .first { it.text.toString() == "Zaječar" }.contentDescription
        )
        assertEquals(
            hluMaterialColorScheme(darkTheme = false).onSurface.toArgb(),
            findTextView(view, "23°C")?.currentTextColor
        )
        assertHasWeatherIconTint(view, LightHluColors, WeatherCondition.CLEAR, isDay = true)
        val surface = findRoundedSurface(view)
        assertTrue((surface.drawable as GradientDrawable).cornerRadius > 0f)
        assertSurfaceColor(surface, LightHluColors.weatherCard.toArgb())
        assertNoDescribedDecorativeImages(view)
        assertNoEmoji(view)
    }

    @Test
    fun translated_dark_explicit_theme_overrides_light_system_configuration() {
        val state = availableSnapshot(settings = PersistedSettings(themeMode = ThemeMode.DARK))
        val view = render(state, systemDark = false)

        assertEquals(
            hluMaterialColorScheme(darkTheme = true).onSurface.toArgb(),
            findTextView(view, "23°C")?.currentTextColor
        )
        val surface = findRoundedSurface(view)
        assertTrue((surface.drawable as GradientDrawable).cornerRadius > 0f)
        assertSurfaceColor(surface, DarkHluColors.weatherCard.toArgb())
        assertHasWeatherIconTint(view, DarkHluColors, WeatherCondition.CLEAR, isDay = true)
        assertText(view, "Clear sky")
        assertNoEmoji(view)
    }

    @Test
    fun translated_system_theme_follows_dark_configuration() {
        val state = availableSnapshot(settings = PersistedSettings(themeMode = ThemeMode.SYSTEM))
        val view = render(state, systemDark = true)

        assertEquals(
            hluMaterialColorScheme(darkTheme = true).onSurface.toArgb(),
            findTextView(view, "23°C")?.currentTextColor
        )
        assertHasWeatherIconTint(view, DarkHluColors, WeatherCondition.CLEAR, isDay = true)
    }

    @Test
    @Config(sdk = [28], qualifiers = "xxxhdpi")
    fun translated_icons_stay_bounded_on_dense_displays() {
        val view = render(availableSnapshot(), systemDark = false, weatherIconSizeDp = 80f)
        val bitmapSizes = findViews(view)
            .filterIsInstance<ImageView>()
            .mapNotNull { (it.drawable as? BitmapDrawable)?.bitmap?.width }

        assertTrue(bitmapSizes.isNotEmpty())
        assertTrue(bitmapSizes.all { it <= WeatherIconBitmapAdapter.MAX_ICON_DIMENSION_PX })
        assertTrue(WeatherIconBitmapAdapter.MAX_ICON_DIMENSION_PX in bitmapSizes)
    }

    @Test
    fun translated_empty_location_and_no_weather_states_are_actionable_and_do_not_invent_weather() {
        val noLocation = render(
            WeatherWidgetSnapshot.NoLocation(PersistedSettings()),
            systemDark = false
        )
        assertText(noLocation, "Open HluWeather to choose a location")
        assertFalse(findViews(noLocation).filterIsInstance<TextView>().any { it.text.contains("°") })

        val noWeather = render(noWeatherSnapshot(), systemDark = false)
        assertText(noWeather, "No weather available for Trieste")
        assertFalse(findViews(noWeather).filterIsInstance<TextView>().any { it.text.contains("°") })
        assertNoEmoji(noLocation)
        assertNoEmoji(noWeather)
    }

    @Test
    fun translated_refreshing_state_is_readable_and_stale_state_keeps_forecast_timestamp() {
        val refreshing = render(
            availableSnapshot(refresh = WidgetRefreshStatus(isRefreshing = true)),
            systemDark = false
        )
        assertText(refreshing, "Refreshing weather")
        assertEquals("Refresh weather", findDescription(refreshing, "Refresh weather"))

        val stale = render(
            availableSnapshot(
                stale = true,
                refresh = WidgetRefreshStatus(lastAttemptFailed = true)
            ),
            systemDark = false
        )
        assertText(stale, "Stale · updated Wed, Oct 28, 2026 • 11:17")
        assertFalse(findViews(stale).filterIsInstance<TextView>().any {
            it.text.toString().contains("Oct 29")
        })
        assertNoEmoji(refreshing)
        assertNoEmoji(stale)
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun render(
        snapshot: WeatherWidgetSnapshot,
        systemDark: Boolean,
        weatherIconSizeDp: Float = 36f
    ): View {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(baseContext.resources.configuration).apply {
            val nightMode = if (systemDark) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        val context = baseContext.createConfigurationContext(configuration)
        val remoteViews = runBlocking {
            PreviewWeatherWidget(snapshot, weatherIconSizeDp).composeForPreview(
                context = context,
                widgetCategory = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
            )
        }
        return remoteViews.apply(context, FrameLayout(context))
    }

    private fun assertHasWeatherIconTint(
        root: View,
        colors: net.droopia.hluweather.ui.theme.HluColors,
        condition: WeatherCondition,
        isDay: Boolean
    ) {
        val expectedTint = net.droopia.hluweather.ui.components
            .weatherIconSpec(condition, isDay, colors).tint.toArgb()
        val bitmaps = findViews(root)
            .filterIsInstance<ImageView>()
            .mapNotNull { (it.drawable as? BitmapDrawable)?.bitmap }
        assertTrue("translated RemoteViews should contain the shared weather bitmap", bitmaps.isNotEmpty())
        assertTrue(
            "weather bitmap should use shared icon tint #${expectedTint.toUInt().toString(16)}",
            bitmaps.any { it.hasPixelColor(expectedTint) }
        )
    }

    private fun Bitmap.hasPixelColor(expected: Int): Boolean {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = getPixel(x, y)
                if (pixel ushr 24 > 32 &&
                    (pixel and 0x00FFFFFF) == (expected and 0x00FFFFFF)
                ) return true
            }
        }
        return false
    }

    private fun assertText(root: View, text: String) {
        assertTrue("expected translated TextView '$text'", findTextView(root, text) != null)
    }

    private fun findRoundedSurface(root: View): ImageView =
        findViews(root).filterIsInstance<ImageView>().first { it.drawable is GradientDrawable }

    private fun assertSurfaceColor(surface: ImageView, expectedColor: Int) {
        val width = 120
        val height = 80
        surface.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        surface.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        surface.draw(Canvas(bitmap))
        assertEquals(expectedColor, bitmap.getPixel(width / 2, height / 2))
        assertEquals(0, bitmap.getPixel(0, 0) ushr 24)
    }

    private fun findTextView(root: View, text: String): TextView? =
        findViews(root).filterIsInstance<TextView>().firstOrNull { it.text.toString() == text }

    private fun findDescription(root: View, description: String): String? =
        findViews(root).firstOrNull { it.contentDescription == description }?.contentDescription?.toString()

    private fun assertNoDescribedDecorativeImages(root: View) {
        assertFalse(
            "condition, location, and refresh glyphs should not be spoken as duplicate icons",
            findViews(root).filterIsInstance<ImageView>().any { !it.contentDescription.isNullOrBlank() }
        )
    }

    private fun assertNoEmoji(root: View) {
        val visibleText = findViews(root).filterIsInstance<TextView>().joinToString(" ") { it.text }
        assertFalse(
            "widget content must not use emoji",
            visibleText.codePoints().anyMatch { codePoint ->
                codePoint in 0x1F000..0x1FAFF || codePoint in 0x2600..0x27BF
            }
        )
    }

    private fun findViews(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) addAll(findViews(root.getChildAt(index)))
        }
    }

    private fun availableSnapshot(
        settings: PersistedSettings = PersistedSettings(),
        stale: Boolean = false,
        refresh: WidgetRefreshStatus = WidgetRefreshStatus()
    ) = WeatherWidgetSnapshot.Available(
        settings = settings,
        request = widgetRequest("Zaječar"),
        weather = WeatherWidgetState(
            locationName = "Zaječar",
            temperatureText = "23°C",
            temperatureContentDescription = "23 degrees C",
            apparentTemperatureText = "24°C",
            apparentTemperatureContentDescription = "24 degrees C",
            condition = WeatherCondition.CLEAR,
            conditionLabel = "Clear sky",
            isDay = true,
            hours = emptyList(),
            lastWeatherUpdate = Instant.parse("2026-10-28T10:17:00Z"),
            lastWeatherUpdateText = "Wed, Oct 28, 2026 • 11:17",
            lastWeatherUpdateCompactText = "Oct 28 11:17",
            isStale = stale
        ),
        refresh = refresh
    )

    private fun noWeatherSnapshot() = WeatherWidgetSnapshot.NoWeather(
        settings = PersistedSettings(),
        request = widgetRequest("Trieste"),
        refresh = WidgetRefreshStatus(lastAttemptFailed = true)
    )

    private fun widgetRequest(locationName: String) = WeatherWidgetRequest(
        provider = WeatherProvider.OPEN_METEO,
        location = ActiveLocation.Saved(
            WeatherLocation(
                id = locationName.lowercase(),
                name = locationName,
                latitude = 45.65,
                longitude = 13.77
            )
        ),
        locationName = locationName
    )

    private class PreviewWeatherWidget(
        private val snapshot: WeatherWidgetSnapshot,
        private val weatherIconSizeDp: Float
    ) : GlanceAppWidget() {
        override suspend fun provideGlance(context: Context, id: GlanceId) = providePreviewContent()

        override suspend fun providePreview(context: Context, widgetCategory: Int) =
            providePreviewContent()

        private suspend fun providePreviewContent() {
            provideContent {
                WeatherWidgetCard(snapshot) { palette ->
                    when (snapshot) {
                        is WeatherWidgetSnapshot.Available -> {
                            WeatherWidgetCurrentWeather(
                                weather = snapshot.weather,
                                palette = palette,
                                iconSizeDp = weatherIconSizeDp,
                                temperatureFontSizeSp = 30f
                            )
                            WeatherWidgetStatus(snapshot, palette, showUpdateTime = true)
                        }
                        is WeatherWidgetSnapshot.NoLocation,
                        is WeatherWidgetSnapshot.NoWeather -> {
                            WeatherWidgetEmptyContent(snapshot, palette)
                            WeatherWidgetStatus(snapshot, palette)
                        }
                    }
                }
            }
        }
    }
}
