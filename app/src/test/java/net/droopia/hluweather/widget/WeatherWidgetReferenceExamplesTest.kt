package net.droopia.hluweather.widget

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.composeForPreview
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.CurrentWeather
import net.droopia.hluweather.data.model.DayForecast
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.model.label
import net.droopia.hluweather.data.repository.ForecastLoad
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.ui.components.weatherIconSpec
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.SettingsRepository
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherWidgetReferenceExamplesTest {

    @Test
    fun renders_offline_reference_examples_from_normalized_forecasts_with_production_remote_views() = runBlocking {
        val lightSmall = loadCachedExample(
            forecast = clearDayForecast(ZAJECAR),
            settings = PersistedSettings(themeMode = ThemeMode.LIGHT),
            now = Instant.parse("2026-10-28T10:30:00Z")
        )
        val lightSmallView = renderSmall(lightSmall.snapshot, darkSystem = false)
        val lightState = lightSmall.snapshot.weather
        assertEquals("Zaječar", lightState.locationName)
        assertEquals("23°C", lightState.temperatureText)
        assertEquals("24°C", lightState.apparentTemperatureText)
        assertEquals("Clear sky", lightState.conditionLabel)
        assertText(lightSmallView.root, "Zaječar")
        assertText(lightSmallView.root, "23°C")
        assertText(lightSmallView.root, "Clear sky")
        assertText(lightSmallView.root, "Feels like 24°C")
        assertContainsSharedIcon(lightSmallView.root, lightState.condition, lightState.isDay, lightStatePalette(lightSmall))

        val darkSmall = loadCachedExample(
            forecast = partlyCloudyNightForecast(TRIESTE),
            settings = PersistedSettings(themeMode = ThemeMode.DARK),
            now = Instant.parse("2026-10-28T20:30:00Z")
        )
        val darkSmallView = renderSmall(darkSmall.snapshot, darkSystem = false)
        val darkState = darkSmall.snapshot.weather
        assertEquals("Trieste", darkState.locationName)
        assertEquals("18°C", darkState.temperatureText)
        assertEquals("17°C", darkState.apparentTemperatureText)
        assertEquals("Partly cloudy", darkState.conditionLabel)
        assertEquals(false, darkState.isDay)
        assertText(darkSmallView.root, "Trieste")
        assertText(darkSmallView.root, "18°C")
        assertText(darkSmallView.root, "Partly cloudy")
        assertText(darkSmallView.root, "Feels like 17°C")
        assertContainsSharedIcon(darkSmallView.root, darkState.condition, darkState.isDay, lightStatePalette(darkSmall))

        val medium = loadCachedExample(
            forecast = mediumReferenceForecast(),
            settings = PersistedSettings(themeMode = ThemeMode.LIGHT),
            now = Instant.parse("2026-10-28T20:30:00Z")
        )
        val mediumView = renderMedium(medium.snapshot, darkSystem = false)
        val mediumState = medium.snapshot.weather
        assertEquals("Zaječar", mediumState.locationName)
        assertEquals("Clear sky", mediumState.conditionLabel)
        assertEquals(true, mediumState.isDay)
        assertEquals(listOf("22:00", "23:00", "00:00", "01:00", "02:00"), mediumState.hours.map { it.timeText })
        assertEquals(listOf("20°C", "18°C", "17°C", "16°C", "15°C"), mediumState.hours.map { it.temperatureText })
        assertEquals(listOf(0, 0, 10, 20, 60), mediumState.hours.map { it.precipitationProbability })
        assertEquals(listOf(false, false, false, false, false), mediumState.hours.map { it.isDay })
        assertEquals(
            listOf(
                WeatherCondition.CLEAR,
                WeatherCondition.PARTLY_CLOUDY,
                WeatherCondition.CLOUDY,
                WeatherCondition.PARTLY_CLOUDY,
                WeatherCondition.CLEAR
            ).map(WeatherCondition::label),
            mediumState.hours.map { it.conditionLabel }
        )
        assertText(mediumView.root, "23°C")
        assertText(mediumView.root, "Clear sky")
        assertText(mediumView.root, "Feels like 24°C")
        assertText(mediumView.root, "21:17")
        assertTrue("medium reference has no visible date/update label", findViews(mediumView.root)
            .filterIsInstance<TextView>()
            .none { it.text.contains("Updated", ignoreCase = true) || it.text.contains("Oct") })
        assertEquals("medium reference background remains transparent", 0,
            mediumView.bitmap.getPixel(0, 0) ushr 24)
        assertEquals(
            listOf("22:00", "23:00", "00:00", "01:00", "02:00"),
            findViews(mediumView.root).filterIsInstance<TextView>().map { it.text.toString() }
                .filter { it in REFERENCE_HOURS }
        )
        assertEquals(
            listOf("20°C", "18°C", "17°C", "16°C", "15°C"),
            findViews(mediumView.root).filterIsInstance<TextView>().map { it.text.toString() }
                .filter { it in REFERENCE_TEMPERATURES }
        )
        assertEquals(
            listOf("0%", "0%", "10%", "20%", "60%"),
            findViews(mediumView.root).filterIsInstance<TextView>().map { it.text.toString() }
                .filter { it in REFERENCE_PROBABILITIES }
        )
        val mediumPalette = lightStatePalette(medium)
        assertContainsSharedIcon(mediumView.root, mediumState.condition, mediumState.isDay, mediumPalette)
        mediumState.hours.forEach { hour ->
            val description = "${hour.timeText}, ${hour.conditionLabel}"
            val image = findViews(mediumView.root).filterIsInstance<ImageView>()
                .single { it.contentDescription == description }
            assertImageUsesSharedIcon(image, hour.condition, hour.isDay, mediumPalette)
        }

        assertEquals("the light example never makes a live weather request", 0, lightSmall.weather.liveRequests)
        assertEquals("the dark example never makes a live weather request", 0, darkSmall.weather.liveRequests)
        assertEquals("the medium example never makes a live weather request", 0, medium.weather.liveRequests)
        assertEquals(1, lightSmall.weather.cacheReads)
        assertEquals(1, darkSmall.weather.cacheReads)
        assertEquals(1, medium.weather.cacheReads)

        writeExample("small-light-zajecar.png", lightSmallView)
        writeExample("small-dark-trieste.png", darkSmallView)
        writeExample("medium-zajecar-five-hours.png", mediumView)
    }

    private suspend fun loadCachedExample(
        forecast: WeatherForecast,
        settings: PersistedSettings,
        now: Instant
    ): CachedExample {
        val weather = OfflineFixtureWeatherRepository(ForecastLoad(forecast))
        val repository = WeatherWidgetRepository(
            settingsRepository = FixtureSettingsRepository(settings),
            locationRepository = FixtureLocationRepository(forecast.location),
            weatherRepository = weather,
            clock = FixedClock(now)
        )
        val snapshot = repository.loadCachedState()
        assertTrue("normalized cache fixture should resolve a matching weather card", snapshot is WeatherWidgetSnapshot.Available)
        return CachedExample(snapshot as WeatherWidgetSnapshot.Available, weather)
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun renderSmall(snapshot: WeatherWidgetSnapshot, darkSystem: Boolean): RenderedExample =
        render(
            widget = SnapshotSmallWidget(snapshot),
            widthDp = 200f,
            heightDp = 160f,
            darkSystem = darkSystem
        )

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun renderMedium(snapshot: WeatherWidgetSnapshot, darkSystem: Boolean): RenderedExample =
        render(
            widget = SnapshotMediumWidget(snapshot),
            widthDp = 400f,
            heightDp = 160f,
            darkSystem = darkSystem
        )

    private fun render(
        widget: GlanceAppWidget,
        widthDp: Float,
        heightDp: Float,
        darkSystem: Boolean
    ): RenderedExample {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(baseContext.resources.configuration).apply {
            val nightMode = if (darkSystem) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        val context = baseContext.createConfigurationContext(configuration)
        val density = context.resources.displayMetrics.density
        val widthPx = (widthDp * density).roundToInt()
        val heightPx = (heightDp * density).roundToInt()
        val remoteViews = runBlocking {
            widget.composeForPreview(
                context,
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                AppWidgetProviderInfo().apply {
                    minWidth = widthPx
                    minHeight = heightPx
                }
            )
        }
        val root = remoteViews.apply(context, FrameLayout(context))
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        return RenderedExample(root, bitmap)
    }

    private fun lightStatePalette(example: CachedExample): WeatherWidgetPalette =
        weatherWidgetPalette(example.snapshot.settings, isSystemDark = false)

    private fun assertContainsSharedIcon(
        root: View,
        condition: WeatherCondition,
        isDay: Boolean?,
        palette: WeatherWidgetPalette
    ) {
        val actualImages = findViews(root).filterIsInstance<ImageView>()
        assertTrue(
            "rendered RemoteViews should contain the shared ${condition.name} icon for isDay=$isDay",
            actualImages.any { image -> image.usesSharedIcon(condition, isDay, palette) }
        )
    }

    private fun assertImageUsesSharedIcon(
        image: ImageView,
        condition: WeatherCondition,
        isDay: Boolean?,
        palette: WeatherWidgetPalette
    ) {
        assertTrue(
            "${image.contentDescription} should use the shared ${condition.name} icon for isDay=$isDay",
            image.usesSharedIcon(condition, isDay, palette)
        )
    }

    private fun ImageView.usesSharedIcon(
        condition: WeatherCondition,
        isDay: Boolean?,
        palette: WeatherWidgetPalette
    ): Boolean {
        val actual = (drawable as? BitmapDrawable)?.bitmap ?: return false
        val spec = weatherIconSpec(condition, isDay, palette.weatherColors)
        val expected = WeatherIconBitmapAdapter.bitmap(
            imageVector = spec.imageVector,
            tint = spec.tint,
            widthPx = actual.width,
            heightPx = actual.height,
            density = resources.displayMetrics.density
        )
        return actual.sameAs(expected)
    }

    private fun writeExample(name: String, rendered: RenderedExample) {
        val directoryPath = System.getenv(EXAMPLE_OUTPUT_ENV) ?: return
        val directory = File(directoryPath)
        assertTrue("example output directory can be created", directory.isDirectory || directory.mkdirs())
        val output = File(directory, name)
        val encoded = ByteArrayOutputStream().use { stream ->
            assertTrue("PNG encoder should succeed", rendered.bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            stream.toByteArray()
        }
        if (output.exists()) {
            assertArrayEquals("re-rendered example must match the checked-in artifact", encoded, output.readBytes())
        } else {
            output.writeBytes(encoded)
        }
    }

    private fun assertText(root: View, expected: String) {
        assertTrue("expected production RemoteViews text '$expected'", findViews(root)
            .filterIsInstance<TextView>().any { it.text.toString() == expected })
    }

    private fun findViews(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) addAll(findViews(root.getChildAt(index)))
        }
    }

    private fun clearDayForecast(location: WeatherLocation) = forecast(
        location = location,
        temperature = 23.0,
        apparentTemperature = 24.0,
        condition = WeatherCondition.CLEAR,
        isDay = true,
        fetchedAt = Instant.parse("2026-10-28T10:17:00Z")
    )

    private fun partlyCloudyNightForecast(location: WeatherLocation) = forecast(
        location = location,
        temperature = 18.0,
        apparentTemperature = 17.0,
        condition = WeatherCondition.PARTLY_CLOUDY,
        isDay = false,
        fetchedAt = Instant.parse("2026-10-28T20:17:00Z"),
        timezone = "Europe/Rome"
    )

    private fun mediumReferenceForecast(): WeatherForecast {
        val localInstants = listOf(
            "2026-10-28T21:00:00Z",
            "2026-10-28T22:00:00Z",
            "2026-10-28T23:00:00Z",
            "2026-10-29T00:00:00Z",
            "2026-10-29T01:00:00Z"
        )
        val temperatures = listOf(20.0, 18.0, 17.0, 16.0, 15.0)
        val probabilities = listOf(0, 0, 10, 20, 60)
        val conditions = listOf(
            WeatherCondition.CLEAR,
            WeatherCondition.PARTLY_CLOUDY,
            WeatherCondition.CLOUDY,
            WeatherCondition.PARTLY_CLOUDY,
            WeatherCondition.CLEAR
        )
        return forecast(
            location = ZAJECAR,
            temperature = 23.0,
            apparentTemperature = 24.0,
            condition = WeatherCondition.CLEAR,
            isDay = true,
            fetchedAt = Instant.parse("2026-10-28T20:17:00Z"),
            hourly = localInstants.indices.map { index ->
                HourForecast(
                    time = Instant.parse(localInstants[index]),
                    temperature = temperatures[index],
                    apparentTemperature = null,
                    humidity = null,
                    dewPoint = null,
                    precipitation = null,
                    precipitationProbability = probabilities[index],
                    condition = conditions[index],
                    isDay = false
                )
            }
        )
    }

    private fun forecast(
        location: WeatherLocation,
        temperature: Double,
        apparentTemperature: Double,
        condition: WeatherCondition,
        isDay: Boolean,
        fetchedAt: Instant,
        hourly: List<HourForecast> = emptyList(),
        timezone: String = "Europe/Belgrade"
    ) = WeatherForecast(
        location = location,
        provider = WeatherProvider.OPEN_METEO,
        fetchedAt = fetchedAt,
        current = CurrentWeather(
            temperature = temperature,
            apparentTemperature = apparentTemperature,
            humidity = 52,
            dewPoint = null,
            precipitation = null,
            condition = condition,
            isDay = isDay
        ),
        hourly = hourly,
        daily = emptyList<DayForecast>(),
        moonPhase = 0.5,
        timezone = timezone
    )

    private class SnapshotSmallWidget(
        private val snapshot: WeatherWidgetSnapshot
    ) : GlanceAppWidget() {
        override val sizeMode: SizeMode = SizeMode.Responsive(
            setOf(DpSize(200.dp, 160.dp))
        )

        override suspend fun provideGlance(context: Context, id: GlanceId) = provideSnapshot()
        override suspend fun providePreview(context: Context, widgetCategory: Int) = provideSnapshot()

        private suspend fun provideSnapshot() {
            provideContent { SmallWeatherWidgetContent(snapshot) }
        }
    }

    private class SnapshotMediumWidget(
        private val snapshot: WeatherWidgetSnapshot
    ) : GlanceAppWidget() {
        override val sizeMode: SizeMode = SizeMode.Responsive(
            setOf(DpSize(400.dp, 160.dp))
        )

        override suspend fun provideGlance(context: Context, id: GlanceId) = provideSnapshot()
        override suspend fun providePreview(context: Context, widgetCategory: Int) = provideSnapshot()

        private suspend fun provideSnapshot() {
            provideContent { MediumWeatherWidgetContent(snapshot) }
        }
    }

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private class FixtureSettingsRepository(
        settingsValue: PersistedSettings
    ) : SettingsRepository {
        override val settings: Flow<PersistedSettings> = flowOf(settingsValue)
        override suspend fun save(settings: PersistedSettings) = Unit
    }

    private class FixtureLocationRepository(
        private val location: WeatherLocation
    ) : LocationRepository {
        override val locations: Flow<List<WeatherLocation>> = flowOf(listOf(location))
        override val activeLocation: Flow<ActiveLocation?> = flowOf(ActiveLocation.Saved(location))
        override val locationMode: Flow<LocationMode> = flowOf(LocationMode.SAVED_LOCATION)

        override suspend fun add(location: WeatherLocation) = Unit
        override suspend fun update(location: WeatherLocation) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun selectSaved(id: String) = Unit
        override suspend fun setTrackMe(enabled: Boolean) = Unit
    }

    private class OfflineFixtureWeatherRepository(
        private val cached: ForecastLoad
    ) : WeatherRepository {
        var cacheReads = 0
            private set
        var liveRequests = 0
            private set

        override suspend fun getForecast(
            provider: WeatherProvider,
            location: ActiveLocation
        ): ForecastLoad {
            liveRequests++
            error("Reference previews must use fixtures and never request live weather")
        }

        override suspend fun getLastCachedForecast(
            provider: WeatherProvider,
            location: ActiveLocation
        ): ForecastLoad? {
            cacheReads++
            return cached.takeIf {
                provider == cached.forecast.provider &&
                    location == ActiveLocation.Saved(cached.forecast.location)
            }
        }

        override suspend fun clearCache() = Unit
    }

    private data class CachedExample(
        val snapshot: WeatherWidgetSnapshot.Available,
        val weather: OfflineFixtureWeatherRepository
    )

    private data class RenderedExample(val root: View, val bitmap: Bitmap)

    private companion object {
        const val EXAMPLE_OUTPUT_ENV = "WEATHER_WIDGET_EXAMPLES_DIR"
        val ZAJECAR = WeatherLocation("zajecar", "Zaječar", 43.9, 20.3)
        val TRIESTE = WeatherLocation("trieste", "Trieste", 45.65, 13.77)
        val REFERENCE_HOURS = listOf("22:00", "23:00", "00:00", "01:00", "02:00")
        val REFERENCE_TEMPERATURES = listOf("20°C", "18°C", "17°C", "16°C", "15°C")
        val REFERENCE_PROBABILITIES = listOf("0%", "10%", "20%", "60%")
    }
}
