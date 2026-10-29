package net.droopia.hluweather.widget

import android.content.Context
import android.view.LayoutInflater
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
            previewImage = "weather_widget_preview_medium_image",
            previewLayout = "weather_widget_preview_medium"
        )
    }

    @Test
    fun static_picker_previews_use_the_production_card_surface_and_shared_weather_glyph_assets() {
        val small = parse(sourceFile("app/src/main/res/layout/weather_widget_preview_small.xml"))
        val medium = parse(sourceFile("app/src/main/res/layout/weather_widget_preview_medium.xml"))
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
        listOf(small, medium).forEach { layout ->
            assertEquals("@drawable/weather_widget_card_surface", layout.documentElement.androidAttribute("background"))
            assertTrue(layout.getElementsByTagName("ImageView").asElements().any {
                it.androidAttribute("src") == "@drawable/weather_widget_preview_sun"
            })
            assertTrue(layout.documentElement.getElementsByTagName("TextView").length > 0)
        }
        assertTrue(medium.getElementsByTagName("ImageView").asElements().any {
            it.androidAttribute("src") == "@drawable/weather_widget_preview_drop"
        })
    }

    @Test
    fun picker_preview_layouts_and_legacy_preview_images_inflate_on_api_28() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val inflater = LayoutInflater.from(context)
        val small = inflater.inflate(R.layout.weather_widget_preview_small, null)
        val medium = inflater.inflate(R.layout.weather_widget_preview_medium, null)

        assertTrue(small is ViewGroup)
        assertTrue(medium is ViewGroup)
        assertTrue(findViews(small).filterIsInstance<TextView>().any {
            it.text == context.getString(R.string.weather_widget_preview_condition)
        })
        assertTrue(findViews(medium).filterIsInstance<TextView>().any {
            it.text == context.getString(R.string.weather_widget_preview_hour_00)
        })
        assertNotNull(context.getDrawable(R.drawable.weather_widget_preview_small_image))
        assertNotNull(context.getDrawable(R.drawable.weather_widget_preview_medium_image))
    }

    private fun assertProvider(
        resourceName: String,
        width: String,
        height: String,
        cellsWide: String,
        cellsTall: String,
        label: String,
        description: String,
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
        assertEquals("@layout/weather_widget_initial_state", provider.androidAttribute("initialLayout"))
        assertEquals("@drawable/$previewImage", provider.androidAttribute("previewImage"))
        assertEquals("@layout/$previewLayout", provider.androidAttribute("previewLayout"))
        assertFalse("widgets must not require configuration", provider.hasAttributeNS(ANDROID_NS, "configure"))

        listOf(
            "layout/weather_widget_initial_state.xml",
            "layout/$previewLayout.xml",
            "drawable/$previewImage.xml",
            "drawable/weather_widget_card_surface.xml",
            "drawable/weather_widget_preview_sun.xml"
        ).forEach { assertTrue("missing referenced resource $it", sourceFile("app/src/main/res/$it").isFile) }
        assertTrue(sourceFile("app/src/main/res/values/strings.xml").isFile)
    }

    private fun Element.androidAttribute(name: String): String = getAttributeNS(ANDROID_NS, name)

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
