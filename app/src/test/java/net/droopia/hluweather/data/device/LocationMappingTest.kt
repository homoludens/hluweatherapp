package net.droopia.hluweather.data.device

import android.location.Location
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.GpsResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationMappingTest {
    @Test
    fun location_without_altitude_preserves_a_null_altitude() {
        val location = Location("gps").apply {
            latitude = 44.8176
            longitude = 20.4633
        }

        assertEquals(
            GpsResult.Success(GeoPoint(44.8176, 20.4633), altitude = null),
            location.toGpsResult()
        )
    }
}
