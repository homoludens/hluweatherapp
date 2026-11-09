package net.droopia.hluweather.data.timezone

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineTimeZoneResolverAndroidTest {
    @Test
    fun bundledArchiveAndNativeDecompressionResolveBerlin() = runBlocking {
        val resolver = OfflineTimeZoneResolver()
        assertEquals(LocationTimeZone("Europe/Berlin", false), resolver.resolve(52.52, 13.40))
    }
}
