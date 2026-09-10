package net.droopia.hluweather

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseResourceSourceTest {
    @Test
    fun manifest_uses_project_owned_launcher_icons() {
        val manifest = File("app/src/main/AndroidManifest.xml").takeIf { it.isFile }
            ?: File("src/main/AndroidManifest.xml")
        val source = manifest.readText()

        assertTrue(source.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(source.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
        assertTrue(!source.contains("@android:drawable/sym_def_app_icon"))
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

        sources.map(File::readText).forEach { source ->
            assertTrue(source.contains("M28,67c0,-8 6,-14 14,-14"))
            assertTrue(source.contains("M68,37m-10,0a10,10 0,1 1,20 0"))
        }
    }
}
