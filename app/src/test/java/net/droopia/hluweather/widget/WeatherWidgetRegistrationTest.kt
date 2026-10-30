package net.droopia.hluweather.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
            "24dp",
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
        val mediumHeader = medium.getElementsByTagName("LinearLayout").item(1) as Element
        val headerText = mediumHeader.getElementsByTagName("TextView").asElements()
            .map { it.androidAttribute("text") }
        assertEquals(
            listOf(
                "@string/weather_widget_preview_location",
                "@string/weather_widget_preview_update_time"
            ),
            headerText
        )
        assertFalse(mediumHeader.getElementsByTagName("ImageView").length > 0)
        val hourLabels = medium.getElementsByTagName("TextView").asElements().filter {
            it.androidAttribute("text").startsWith("@string/weather_widget_preview_hour_")
        }
        assertEquals(4, hourLabels.size)
        hourLabels.forEach { label ->
            assertEquals("match_parent", label.androidAttribute("layout_width"))
            assertEquals("center_horizontal", label.androidAttribute("gravity"))
        }
        val precipitationLabels = medium.getElementsByTagName("TextView").asElements().filter {
            it.androidAttribute("text").startsWith("@string/weather_widget_preview_probability_")
        }
        assertEquals(4, precipitationLabels.size)
        precipitationLabels.forEach { label ->
            assertEquals("10sp", label.androidAttribute("textSize"))
        }
        assertFalse(medium.getElementsByTagName("ImageView").asElements().any {
            it.androidAttribute("src") == "@drawable/weather_widget_preview_refresh"
        })
        val mediumFallback = parse(sourceFile("app/src/main/res/drawable/weather_widget_preview_medium_image.xml"))
        val fallbackItems = mediumFallback.getElementsByTagName("item").asElements()
        assertFalse("medium fallback image does not reference the shared card surface", fallbackItems.any {
            it.androidAttribute("drawable") == "@drawable/weather_widget_card_surface"
        })
        assertFalse("medium fallback image omits the small-widget refresh action", fallbackItems.any {
            it.androidAttribute("drawable") == "@drawable/weather_widget_preview_refresh"
        })
        val fallbackDividers = fallbackItems.filter {
            it.androidAttribute("width") == "1dp" && it.androidAttribute("height") == "128dp"
        }.map { it.dpAttribute("left") }
        assertEquals(4, fallbackDividers.size)
        val fallbackHourMarks = fallbackItems.filter {
            it.androidAttribute("width") == "24dp" && it.androidAttribute("height") == "6dp"
        }
        val fallbackProbabilityMarks = fallbackItems.filter {
            it.androidAttribute("width") == "32dp" && it.androidAttribute("height") == "8dp"
        }
        assertEquals(4, fallbackHourMarks.size)
        assertEquals(4, fallbackProbabilityMarks.size)
        val hourlyCenters = fallbackDividers.indices.map { index ->
            val columnWidth = fallbackDividers.getOrNull(index + 1)?.let { it - fallbackDividers[index] }
                ?: (fallbackDividers[index] - fallbackDividers[index - 1])
            fallbackDividers[index] + columnWidth / 2f
        }
        listOf(fallbackHourMarks, fallbackProbabilityMarks).forEach { marks ->
            marks.forEachIndexed { index, mark ->
                val markCenter = mark.dpAttribute("left") + mark.dpAttribute("width") / 2f
                assertEquals(hourlyCenters[index], markCenter, 0.1f)
            }
        }
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
        assertTrue("small picker keeps its refresh control", findViews(small).any { view ->
            (view as? android.widget.ImageView)?.drawable != null &&
                view.contentDescription == context.getString(R.string.weather_widget_preview_refresh)
        })
        assertPixelAlpha(medium, widthDp = 260, heightDp = 110, xDp = 2, yDp = 55)
    }

    @Test
    fun rendered_medium_picker_preview_fits_at_minimum_size_at_normal_and_130_percent_font_scale() {
        assertRenderedMediumPreview(fontScale = 1f)
        assertRenderedMediumPreview(fontScale = 1.3f)

        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val small = LayoutInflater.from(baseContext)
            .inflate(R.layout.weather_widget_preview_small, null)
        val density = baseContext.resources.displayMetrics.density
        measureAndLayout(small, widthPx = (200 * density).toInt(), heightPx = (160 * density).toInt())
        val smallBitmap = draw(small)
        assertEquals(255, smallBitmap.getPixel((100 * density).toInt(), (80 * density).toInt()) ushr 24)
        assertTrue(findViews(small).filterIsInstance<ImageView>().any {
            it.contentDescription == baseContext.getString(R.string.weather_widget_preview_refresh)
        })
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

    private fun Element.dpAttribute(name: String): Float =
        androidAttribute(name).removeSuffix("dp").toFloat()

    private fun assertRenderedMediumPreview(fontScale: Float) {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(baseContext.resources.configuration).apply {
            this.fontScale = fontScale
        }
        val context = baseContext.createConfigurationContext(configuration)
        val density = context.resources.displayMetrics.density
        val widthPx = (260 * density).toInt()
        val heightPx = (110 * density).toInt()
        val root = LayoutInflater.from(context).inflate(R.layout.weather_widget_preview_medium, null)
        measureAndLayout(root, widthPx, heightPx)
        val bitmap = draw(root)
        val scaleLabel = "${(fontScale * 100).toInt()}%"
        assertEquals(
            "$scaleLabel preview is transparent outside its content",
            0,
            bitmap.getPixel((2 * density).toInt(), (55 * density).toInt()) ushr 24
        )

        val views = findViews(root)
        val textViews = views.filterIsInstance<TextView>()
        fun textView(text: String): TextView = textViews.single { it.text.toString() == text }

        val location = textView(context.getString(R.string.weather_widget_preview_location))
        val updateTime = textView(context.getString(R.string.weather_widget_preview_update_time))
        val hourTexts = listOf(
            R.string.weather_widget_preview_hour_22,
            R.string.weather_widget_preview_hour_23,
            R.string.weather_widget_preview_hour_00,
            R.string.weather_widget_preview_hour_01
        ).map(context::getString).map(::textView)
        val temperatures = listOf("20°", "18°", "17°", "16°").map(::textView)
        val expectedProbabilities = listOf("0%", "0%", "10%", "20%")
        val probabilities = textViews.filter { it.text.toString() in expectedProbabilities }
        assertEquals(expectedProbabilities, probabilities.map { it.text.toString() })
        val droplets = views.filterIsInstance<ImageView>().filter {
            it.contentDescription == null && it.drawable != null
        }
        assertEquals("$scaleLabel preview has four probability droplets", 4, droplets.size)
        assertTrue("$scaleLabel medium preview has no refresh control", views.filterIsInstance<ImageView>().none {
            it.contentDescription == context.getString(R.string.weather_widget_preview_refresh)
        })

        val locationInk = assertTextInkFits(location, location.parent as View, root, bitmap, fontScale, 14f)
        val timeInk = assertTextInkFits(updateTime, updateTime.parent as View, root, bitmap, fontScale, 9f)
        assertFalse("$scaleLabel location and time glyphs overlap", RectF.intersects(locationInk, timeInk))
        assertEquals("Zaječar", location.text.toString())
        assertEquals("21:17", updateTime.text.toString())

        val hourlyColumns = hourTexts.map { it.parent as View }
        assertEquals("$scaleLabel has four distinct hourly columns", 4, hourlyColumns.distinct().size)
        hourlyColumns.forEachIndexed { index, column ->
            val time = hourTexts[index]
            val temperature = temperatures[index]
            val probability = probabilities[index]
            val probabilityRow = probability.parent as View
            val droplet = droplets.single { it.parent === probabilityRow }
            assertTrue(time.parent === column)
            assertTrue(temperature.parent === column)
            assertTrue(probabilityRow.parent === column)
            assertEquals(8f, droplet.measuredWidth / density, 0.1f)
            assertEquals(8f, droplet.measuredHeight / density, 0.1f)
            assertViewFits(droplet, probabilityRow, root, "$scaleLabel ${probability.text} droplet")
            assertViewFits(probabilityRow, column, root, "$scaleLabel ${probability.text} row")
            assertViewFits(column, column.parent as View, root, "$scaleLabel ${time.text} column")

            val timeInk = assertTextInkFits(time, column, root, bitmap, fontScale, 8f, centered = true)
            val temperatureInk = assertTextInkFits(temperature, column, root, bitmap, fontScale, 11f)
            val probabilityInk = assertTextInkFits(probability, probabilityRow, root, bitmap, fontScale, 10f)
            val dropletInk = renderedInkBounds(droplet, root, bitmap, "$scaleLabel ${probability.text} droplet")
            assertRectContains(
                "$scaleLabel ${probability.text} droplet ink fits its row",
                boundsInRoot(probabilityRow, root),
                dropletInk
            )
            assertRectContains("$scaleLabel ${probability.text} droplet ink fits the preview root",
                boundsInRoot(root, root), dropletInk)
            val columnBounds = boundsInRoot(column, root)
            val hourCenter = (timeInk.left + timeInk.right) / 2f
            assertEquals("$scaleLabel ${time.text} is centered", columnBounds.centerX(), hourCenter, 1f)
            assertEquals(
                "$scaleLabel ${temperature.text} is centered",
                columnBounds.centerX(),
                (temperatureInk.left + temperatureInk.right) / 2f,
                1f
            )
            assertTrue(
                "$scaleLabel ${probability.text} droplet and text do not overlap",
                dropletInk.right <= probabilityInk.left + 1f
            )
            assertEquals(
                "$scaleLabel probability row is centered",
                columnBounds.centerX(),
                boundsInRoot(probabilityRow, root).centerX(),
                1f
            )
            assertTrue("$scaleLabel ${time.text} and temperature do not overlap",
                timeInk.bottom <= temperatureInk.top)
            assertTrue("$scaleLabel ${temperature.text} and probability do not overlap",
                temperatureInk.bottom <= probabilityInk.top)
        }
        assertNoOverlappingSiblings(root, root, scaleLabel)
    }

    private fun assertTextInkFits(
        textView: TextView,
        parent: View,
        root: View,
        bitmap: Bitmap,
        fontScale: Float,
        expectedSp: Float,
        centered: Boolean = false
    ): RectF {
        val text = textView.text.toString()
        val layout = requireNotNull(textView.layout)
        assertEquals("$text has one visible line", 1, layout.lineCount)
        assertEquals("$text has no ellipsized characters", 0, layout.getEllipsisCount(0))
        assertEquals(
            "$text uses its intended preview text size",
            expectedSp * fontScale,
            textView.textSize / textView.resources.displayMetrics.density,
            0.5f
        )
        if (centered) {
            assertEquals(
                "$text is center aligned in its hourly cell",
                Gravity.CENTER_HORIZONTAL,
                textView.gravity and Gravity.HORIZONTAL_GRAVITY_MASK
            )
        }

        val glyphInk = renderedInkBounds(textView, root, bitmap, text)
        val viewBounds = boundsInRoot(textView, root)
        assertRectContains("$text glyph ink fits its TextView", viewBounds, glyphInk,
            textView.compoundPaddingLeft.toFloat(), textView.compoundPaddingTop.toFloat(),
            textView.compoundPaddingRight.toFloat(), textView.compoundPaddingBottom.toFloat())
        assertRectContains("$text glyph ink fits its parent", boundsInRoot(parent, root), glyphInk,
            parent.paddingLeft.toFloat(), parent.paddingTop.toFloat(),
            parent.paddingRight.toFloat(), parent.paddingBottom.toFloat())
        assertRectContains("$text glyph ink fits the preview root", boundsInRoot(root, root), glyphInk,
            root.paddingLeft.toFloat(), root.paddingTop.toFloat(),
            root.paddingRight.toFloat(), root.paddingBottom.toFloat())
        return glyphInk
    }

    private fun renderedInkBounds(view: View, root: View, bitmap: Bitmap, label: String): RectF {
        val viewBounds = boundsInRoot(view, root)
        val left = viewBounds.left.toInt().coerceIn(0, bitmap.width)
        val top = viewBounds.top.toInt().coerceIn(0, bitmap.height)
        val right = viewBounds.right.toInt().coerceIn(0, bitmap.width)
        val bottom = viewBounds.bottom.toInt().coerceIn(0, bitmap.height)
        var minX = right
        var minY = bottom
        var maxX = left - 1
        var maxY = top - 1
        for (y in top until bottom) {
            for (x in left until right) {
                if (bitmap.getPixel(x, y) ushr 24 > 0) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        assertTrue("$label has rendered glyph/icon ink", maxX >= minX && maxY >= minY)
        return RectF(minX.toFloat(), minY.toFloat(), maxX + 1f, maxY + 1f)
    }

    private fun assertRectContains(
        message: String,
        container: RectF,
        content: RectF,
        insetLeft: Float = 0f,
        insetTop: Float = 0f,
        insetRight: Float = 0f,
        insetBottom: Float = 0f
    ) {
        assertTrue(message, content.left >= container.left + insetLeft - 1f)
        assertTrue(message, content.top >= container.top + insetTop - 1f)
        assertTrue(message, content.right <= container.right - insetRight + 1f)
        assertTrue(message, content.bottom <= container.bottom - insetBottom + 1f)
    }

    private fun assertViewFits(view: View, parent: View, root: View, label: String) {
        val bounds = boundsInRoot(view, root)
        assertRectContains(label, boundsInRoot(parent, root), bounds,
            parent.paddingLeft.toFloat(), parent.paddingTop.toFloat(),
            parent.paddingRight.toFloat(), parent.paddingBottom.toFloat())
        assertRectContains(label, boundsInRoot(root, root), bounds,
            root.paddingLeft.toFloat(), root.paddingTop.toFloat(),
            root.paddingRight.toFloat(), root.paddingBottom.toFloat())
    }

    private fun boundsInRoot(view: View, root: View): RectF {
        var left = 0f
        var top = 0f
        var current = view
        while (current !== root) {
            val parent = current.parent as? View
                ?: throw AssertionError("$view is not a descendant of the preview root")
            left += current.left - parent.scrollX
            top += current.top - parent.scrollY
            current = parent
        }
        return RectF(left, top, left + view.width, top + view.height)
    }

    private fun assertNoOverlappingSiblings(view: View, root: View, scaleLabel: String) {
        val group = view as? ViewGroup ?: return
        val children = (0 until group.childCount).map(group::getChildAt).filter {
            it.visibility == View.VISIBLE && it.width > 0 && it.height > 0
        }
        children.forEachIndexed { index, first ->
            val firstBounds = boundsInRoot(first, root)
            children.drop(index + 1).forEach { second ->
                assertFalse(
                    "$scaleLabel preview siblings ${first.javaClass.simpleName} and " +
                        "${second.javaClass.simpleName} overlap",
                    RectF.intersects(firstBounds, boundsInRoot(second, root))
                )
            }
        }
        children.forEach { assertNoOverlappingSiblings(it, root, scaleLabel) }
    }

    private fun measureAndLayout(view: View, widthPx: Int, heightPx: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, widthPx, heightPx)
    }

    private fun draw(view: View): Bitmap = Bitmap.createBitmap(
        view.width,
        view.height,
        Bitmap.Config.ARGB_8888
    ).also { bitmap -> view.draw(Canvas(bitmap)) }

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
