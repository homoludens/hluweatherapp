package net.droopia.hluweather.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import net.droopia.hluweather.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.w3c.dom.Element
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WeatherWidgetRegistrationTest {

    @Test
    fun both_receivers_are_exported_widget_providers_with_their_matching_metadata() {
        val manifest = parse(sourceFile("app/src/main/AndroidManifest.xml"))
        val receivers = manifest.getElementsByTagName("receiver").asElements().associateBy {
            it.androidAttribute("name")
        }

        val expected = mapOf(
            "net.droopia.hluweather.widget.SmallWeatherWidgetReceiver" to "@xml/small_weather_widget_provider",
            "net.droopia.hluweather.widget.MediumWeatherWidgetReceiver" to "@xml/medium_weather_widget_provider"
        )
        expected.forEach { (receiverName, providerResource) ->
            val receiver = requireNotNull(receivers[receiverName])
            assertEquals("true", receiver.androidAttribute("exported"))
            assertTrue(receiver.getElementsByTagName("action").asElements().any {
                it.androidAttribute("name") == "android.appwidget.action.APPWIDGET_UPDATE"
            })
            val providerMetadata = receiver.getElementsByTagName("meta-data").asElements().single {
                it.androidAttribute("name") == "android.appwidget.provider"
            }
            assertEquals(providerResource, providerMetadata.androidAttribute("resource"))
        }
        assertFalse(manifest.getElementsByTagName("activity").asElements().any {
            it.androidAttribute("name").contains("widget", ignoreCase = true)
        })

        val permissionNames = manifest.getElementsByTagName("uses-permission").asElements()
            .map { it.androidAttribute("name") }
            .toSet()
        assertEquals(
            setOf(
                "android.permission.INTERNET",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.POST_NOTIFICATIONS"
            ),
            permissionNames
        )
    }

    @Test
    fun provider_metadata_matches_responsive_layout_minimums_and_disables_xml_refreshes() {
        assertProvider(
            resourceName = "small_weather_widget_provider",
            width = "${WeatherWidgetSizePolicy.SMALL_MIN_WIDTH_DP.toInt()}dp",
            height = "${WeatherWidgetSizePolicy.SMALL_MIN_HEIGHT_DP.toInt()}dp",
            cellsWide = "2",
            cellsTall = "2",
            label = "small_weather_widget_name",
            description = "small_weather_widget_description",
            initialLayout = "weather_widget_initial_state",
            previewImage = "weather_widget_preview_small_image",
            previewLayout = "weather_widget_preview_small"
        )
        assertProvider(
            resourceName = "medium_weather_widget_provider",
            width = "${WeatherWidgetSizePolicy.MEDIUM_MIN_WIDTH_DP.toInt()}dp",
            height = "${WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP.toInt()}dp",
            cellsWide = "4",
            cellsTall = "2",
            label = "medium_weather_widget_name",
            description = "medium_weather_widget_description",
            initialLayout = "weather_widget_initial_state_medium",
            previewImage = "weather_widget_preview_medium_image",
            previewLayout = "weather_widget_preview_medium"
        )
    }

    @Test
    fun medium_picker_preview_is_transparent_and_small_preview_keeps_its_surface() {
        val small = parse(sourceFile("app/src/main/res/layout/weather_widget_preview_small.xml"))
        val medium = parse(sourceFile("app/src/main/res/layout/weather_widget_preview_medium.xml"))
        val smallInitial = parse(sourceFile("app/src/main/res/layout/weather_widget_initial_state.xml"))
        val mediumInitial = parse(sourceFile("app/src/main/res/layout/weather_widget_initial_state_medium.xml"))
        assertEquals(
            "${WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP.toInt()}dp",
            medium.documentElement.androidAttribute("layout_height")
        )
        assertEquals(
            "40dp",
            (medium.getElementsByTagName("LinearLayout").item(1) as Element)
                .androidAttribute("layout_height")
        )
        assertTrue(
            DpSize(
                WeatherWidgetSizePolicy.MEDIUM_MIN_WIDTH_DP.dp,
                WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP.dp
            ) in MEDIUM_WEATHER_WIDGET_RESPONSIVE_SIZES
        )
        assertEquals(
            "@drawable/weather_widget_card_surface",
            small.documentElement.androidAttribute("background")
        )
        assertEquals(
            "@drawable/weather_widget_card_surface",
            smallInitial.documentElement.androidAttribute("background")
        )
        assertFalse("medium initial layout has no card background", mediumInitial.documentElement.hasAttributeNS(ANDROID_NS, "background"))
        assertFalse("medium picker layout does not draw a card background", medium.documentElement.hasAttributeNS(ANDROID_NS, "background"))
        assertEquals("4dp", medium.documentElement.androidAttribute("paddingStart"))
        assertEquals("4dp", medium.documentElement.androidAttribute("paddingEnd"))
        assertEquals("2dp", medium.documentElement.androidAttribute("paddingTop"))
        assertEquals("2dp", medium.documentElement.androidAttribute("paddingBottom"))
        listOf(small, medium).forEach { layout ->
            assertTrue(layout.getElementsByTagName("ImageView").asElements().any {
                it.androidAttribute("src") == "@drawable/weather_widget_preview_sun"
            })
            assertTrue(layout.documentElement.getElementsByTagName("TextView").length > 0)
        }
        assertTrue(medium.getElementsByTagName("ImageView").asElements().any {
            it.androidAttribute("src") == "@drawable/weather_widget_preview_drop"
        })
        assertFalse("medium fallback image does not reference the shared card surface", parse(
            sourceFile("app/src/main/res/drawable/weather_widget_preview_medium_image.xml")
        ).getElementsByTagName("item").asElements().any {
            it.androidAttribute("drawable") == "@drawable/weather_widget_card_surface"
        })
        assertTrue("small fallback image keeps its shared card surface", parse(
            sourceFile("app/src/main/res/drawable/weather_widget_preview_small_image.xml")
        ).getElementsByTagName("item").asElements().any {
            it.androidAttribute("drawable") == "@drawable/weather_widget_card_surface"
        })
    }

    @Test
    fun picker_preview_layouts_and_legacy_preview_images_inflate_on_api_28() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val inflater = LayoutInflater.from(context)
        val small = inflater.inflate(R.layout.weather_widget_preview_small, null)
        val medium = inflater.inflate(R.layout.weather_widget_preview_medium, null)
        val smallInitial = inflater.inflate(R.layout.weather_widget_initial_state, null)
        val mediumInitial = inflater.inflate(R.layout.weather_widget_initial_state_medium, null)

        assertTrue(small is ViewGroup)
        assertTrue(medium is ViewGroup)
        assertTrue(findViews(small).filterIsInstance<TextView>().any {
            it.text == context.getString(R.string.weather_widget_preview_condition)
        })
        assertTrue(findViews(medium).filterIsInstance<TextView>().any {
            it.text == context.getString(R.string.weather_widget_preview_hour_00)
        })
        assertTrue(smallInitial.background != null)
        assertTrue("medium initial layout has no card background", mediumInitial.background == null)
        assertPixelAlpha(mediumInitial, widthDp = 260, heightDp = 110, xDp = 2, yDp = 55)
        assertEquals(0, drawDrawablePixelAlpha(
            context.getDrawable(R.drawable.weather_widget_preview_medium_image)!!,
            widthDp = 440,
            heightDp = 220,
            xDp = 2,
            yDp = 110
        ))
        assertNotNull(context.getDrawable(R.drawable.weather_widget_preview_small_image))
        assertNotNull(context.getDrawable(R.drawable.weather_widget_preview_medium_image))
        assertTrue("small picker layout keeps its root surface", small.background != null)
        assertPixelAlpha(medium, widthDp = 260, heightDp = 110, xDp = 2, yDp = 55)
    }

    private fun assertProvider(
        resourceName: String,
        width: String,
        height: String,
        cellsWide: String,
        cellsTall: String,
        label: String,
        description: String,
        initialLayout: String,
        previewImage: String,
        previewLayout: String
    ) {
        val provider = parse(sourceFile("app/src/main/res/xml/$resourceName.xml")).documentElement
        assertEquals(width, provider.androidAttribute("minWidth"))
        assertEquals(height, provider.androidAttribute("minHeight"))
        assertEquals(width, provider.androidAttribute("minResizeWidth"))
        assertEquals(height, provider.androidAttribute("minResizeHeight"))
        assertEquals("horizontal|vertical", provider.androidAttribute("resizeMode"))
        assertEquals(cellsWide, provider.androidAttribute("targetCellWidth"))
        assertEquals(cellsTall, provider.androidAttribute("targetCellHeight"))
        assertEquals("0", provider.androidAttribute("updatePeriodMillis"))
        assertEquals("home_screen", provider.androidAttribute("widgetCategory"))
        assertEquals("@string/$label", provider.androidAttribute("label"))
        assertEquals("@string/$description", provider.androidAttribute("description"))
        assertEquals("@layout/$initialLayout", provider.androidAttribute("initialLayout"))
        assertEquals("@drawable/$previewImage", provider.androidAttribute("previewImage"))
        assertEquals("@layout/$previewLayout", provider.androidAttribute("previewLayout"))
        assertFalse("widgets must not require configuration", provider.hasAttributeNS(ANDROID_NS, "configure"))

        listOf(
            "layout/$initialLayout.xml",
            "layout/$previewLayout.xml",
            "drawable/$previewImage.xml",
            "drawable/weather_widget_card_surface.xml",
            "drawable/weather_widget_preview_sun.xml"
        ).forEach { assertTrue("missing referenced resource $it", sourceFile("app/src/main/res/$it").isFile) }
        assertTrue(sourceFile("app/src/main/res/values/strings.xml").isFile)
    }

    private fun Element.androidAttribute(name: String): String = getAttributeNS(ANDROID_NS, name)

    private fun assertPixelAlpha(
        view: View,
        widthDp: Int,
        heightDp: Int,
        xDp: Int,
        yDp: Int,
        expectedAlpha: Int = 0
    ) {
        val density = view.resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt()
        val heightPx = (heightDp * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val pixel = bitmap.getPixel((xDp * density).toInt(), (yDp * density).toInt())
        assertEquals("pixel alpha at ($xDp,$yDp) dp", expectedAlpha, pixel ushr 24)
    }

    private fun drawDrawablePixelAlpha(
        drawable: android.graphics.drawable.Drawable,
        widthDp: Int,
        heightDp: Int,
        xDp: Int,
        yDp: Int
    ): Int {
        val density = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt()
        val heightPx = (heightDp * density).toInt()
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, widthPx, heightPx)
        drawable.draw(Canvas(bitmap))
        return bitmap.getPixel((xDp * density).toInt(), (yDp * density).toInt()) ushr 24
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> =
        (0 until length).map { item(it) as Element }

    private fun parse(file: File) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(file)

    private fun findViews(root: android.view.View): List<android.view.View> = buildList {
        add(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) addAll(findViews(root.getChildAt(index)))
        }
    }

    private fun sourceFile(path: String): File = File(path).takeIf { it.isFile }
        ?: File(path.removePrefix("app/"))

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
