package net.droopia.hluweather

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseResourceSourceTest {
    @Test
    fun manifest_uses_project_owned_launcher_icons() {
        val manifest = File("app/src/main/AndroidManifest.xml").takeIf { it.isFile }
            ?: File("src/main/AndroidManifest.xml")
        val source = manifest.readText()

        assertTrue(source.contains("android:icon=\"@drawable/hluweatherapp_icon\""))
        assertTrue(source.contains("android:roundIcon=\"@drawable/hluweatherapp_icon\""))
        assertTrue(!source.contains("@android:drawable/sym_def_app_icon"))
    }

    @Test
    fun launcher_icon_matches_the_supplied_design_asset() {
        val resourceDirectory = File("app/src/main/res").takeIf { it.isDirectory }
            ?: File("src/main/res")
        val icon = resourceDirectory.resolve("drawable-nodpi/hluweatherapp_icon.png")
        val source = File("docs/settings_design/hluweatherapp_icon.png").takeIf { it.isFile }
            ?: File("../docs/settings_design/hluweatherapp_icon.png")

        assertTrue(icon.isFile)
        assertTrue(source.isFile)
        assertArrayEquals(source.readBytes(), icon.readBytes())
    }

    @Test
    fun adaptive_foreground_artwork_stays_inside_the_safe_zone() {
        val resourceDirectory = File("app/src/main/res").takeIf { it.isDirectory }
            ?: File("src/main/res")
        val sources = listOf(
            resourceDirectory.resolve("drawable/ic_launcher_foreground.xml"),
            resourceDirectory.resolve("drawable/ic_launcher_monochrome.xml"),
            resourceDirectory.resolve("mipmap-anydpi/ic_launcher.xml"),
            resourceDirectory.resolve("mipmap-anydpi/ic_launcher_round.xml")
        )

        sources.forEach { resource -> assertTrue(resource.isFile) }

        sources.forEach { resource ->
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val document = factory.newDocumentBuilder().parse(resource)
            val vector = document.documentElement
            assertTrue(vector.tagName == "vector")
            assertTrue(vector.getAttributeNS(ANDROID_NS, "viewportWidth") == "108")
            assertTrue(vector.getAttributeNS(ANDROID_NS, "viewportHeight") == "108")

            val paths = vector.getElementsByTagName("path")
            assertTrue(paths.length >= 2)
            for (index in 0 until paths.length) {
                val path = paths.item(index) as org.w3c.dom.Element
                if (path.getAttributeNS(ANDROID_NS, "fillColor") == "@color/ic_launcher_background") {
                    continue
                }
                val bounds = pathBounds(path.getAttributeNS(ANDROID_NS, "pathData"))
                assertTrue("${resource.name} artwork starts inside safe zone", bounds.left >= SAFE_ZONE_LEFT)
                assertTrue("${resource.name} artwork ends inside safe zone", bounds.right <= SAFE_ZONE_RIGHT)
                assertTrue("${resource.name} artwork starts inside safe zone", bounds.top >= SAFE_ZONE_TOP)
                assertTrue("${resource.name} artwork ends inside safe zone", bounds.bottom <= SAFE_ZONE_BOTTOM)
            }
        }
    }

    private data class Bounds(
        var left: Float = Float.POSITIVE_INFINITY,
        var top: Float = Float.POSITIVE_INFINITY,
        var right: Float = Float.NEGATIVE_INFINITY,
        var bottom: Float = Float.NEGATIVE_INFINITY
    ) {
        fun include(x: Float, y: Float) {
            left = minOf(left, x)
            top = minOf(top, y)
            right = maxOf(right, x)
            bottom = maxOf(bottom, y)
        }
    }

    private fun pathBounds(pathData: String): Bounds {
        val tokenPattern = Regex("""[AaCcHhLlMmQqSsTtVvZz]|-?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?""")
        val tokens = tokenPattern.findAll(pathData).map { it.value }.toList()
        val bounds = Bounds()
        var command = '\u0000'
        var currentX = 0f
        var currentY = 0f
        var startX = 0f
        var startY = 0f
        var index = 0

        fun nextNumber(): Float {
            val token = tokens[index++]
            require(token.length != 1 || token[0] !in PATH_COMMANDS) {
                "Path command $token appeared where a number was expected in: $pathData"
            }
            return token.toFloat()
        }
        fun point(x: Float, y: Float) {
            currentX = x
            currentY = y
            bounds.include(x, y)
        }

        while (index < tokens.size) {
            val token = tokens[index]
            if (token.length == 1 && token[0] in PATH_COMMANDS) {
                command = token[0]
                index++
                if (command == 'z' || command == 'Z') {
                    point(startX, startY)
                    command = '\u0000'
                    continue
                }
            }
            require(command != '\u0000') { "Path data has a number without a command: $pathData" }
            val relative = command.isLowerCase()
            when (command.lowercaseChar()) {
                'm', 'l', 't' -> {
                    val x = nextNumber()
                    val y = nextNumber()
                    val nextX = if (relative) currentX + x else x
                    val nextY = if (relative) currentY + y else y
                    point(nextX, nextY)
                    if (command.lowercaseChar() == 'm') {
                        startX = currentX
                        startY = currentY
                        command = if (relative) 'l' else 'L'
                    }
                }
                'h' -> point(if (relative) currentX + nextNumber() else nextNumber(), currentY)
                'v' -> point(currentX, if (relative) currentY + nextNumber() else nextNumber())
                'c' -> {
                    val values = FloatArray(6) { nextNumber() }
                    val x1 = if (relative) currentX + values[0] else values[0]
                    val y1 = if (relative) currentY + values[1] else values[1]
                    val x2 = if (relative) currentX + values[2] else values[2]
                    val y2 = if (relative) currentY + values[3] else values[3]
                    val endX = if (relative) currentX + values[4] else values[4]
                    val endY = if (relative) currentY + values[5] else values[5]
                    bounds.include(x1, y1)
                    bounds.include(x2, y2)
                    point(endX, endY)
                }
                's', 'q' -> {
                    val values = FloatArray(4) { nextNumber() }
                    val x1 = if (relative) currentX + values[0] else values[0]
                    val y1 = if (relative) currentY + values[1] else values[1]
                    val endX = if (relative) currentX + values[2] else values[2]
                    val endY = if (relative) currentY + values[3] else values[3]
                    bounds.include(x1, y1)
                    point(endX, endY)
                }
                'a' -> {
                    val values = FloatArray(7) { nextNumber() }
                    val radiusX = values[0]
                    val radiusY = values[1]
                    val endX = if (relative) currentX + values[5] else values[5]
                    val endY = if (relative) currentY + values[6] else values[6]
                    bounds.include(currentX - radiusX, currentY - radiusY)
                    bounds.include(currentX + radiusX, currentY + radiusY)
                    bounds.include(endX - radiusX, endY - radiusY)
                    bounds.include(endX + radiusX, endY + radiusY)
                    point(endX, endY)
                }
                else -> error("Unsupported path command: $command")
            }
        }
        return bounds
    }

    companion object {
        private const val PATH_COMMANDS = "AaCcHhLlMmQqSsTtVvZz"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val SAFE_ZONE_LEFT = 21f
        private const val SAFE_ZONE_TOP = 21f
        private const val SAFE_ZONE_RIGHT = 87f
        private const val SAFE_ZONE_BOTTOM = 87f
    }
}
