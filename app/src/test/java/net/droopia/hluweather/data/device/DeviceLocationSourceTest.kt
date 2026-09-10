package net.droopia.hluweather.data.device

import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.GpsResult
import org.junit.Assert.assertEquals
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceLocationSourceTest {

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

    @Test
    fun coarse_permission_uses_network_even_when_gps_is_enabled() {
        assertEquals(
            LocationManager.NETWORK_PROVIDER,
            selectLocationProvider(hasFinePermission = false) { provider ->
                provider == LocationManager.GPS_PROVIDER || provider == LocationManager.NETWORK_PROVIDER
            }
        )
    }

    @Test
    fun fine_permission_prefers_gps_when_it_is_enabled() {
        assertEquals(
            LocationManager.GPS_PROVIDER,
            selectLocationProvider(hasFinePermission = true) { provider ->
                provider == LocationManager.GPS_PROVIDER || provider == LocationManager.NETWORK_PROVIDER
            }
        )
    }

    @Test
    fun current_location_returns_unavailable_and_removes_listener_after_timeout() = runTest {
        var removeCalls = 0
        val source = AndroidDeviceLocationSource(
            context = ApplicationProvider.getApplicationContext(),
            locationManager = ApplicationProvider.getApplicationContext<android.content.Context>()
                .getSystemService(LocationManager::class.java),
            mainLooper = Looper.getMainLooper(),
            hasPermission = { true },
            isLocationEnabled = { true },
            isProviderEnabled = { it == LocationManager.NETWORK_PROVIDER },
            timeoutMillis = 1_000L,
            requestLocationUpdates = { _, _, _, _ -> },
            requestSingleUpdate = { _, _ -> },
            removeLocationUpdates = { removeCalls++ }
        )

        assertEquals(GpsResult.Unavailable, source.currentLocation())
        assertEquals(1, removeCalls)
    }

    @Test
    fun foreground_locations_report_unavailable_after_timeout_without_a_fix() = runTest {
        val source = AndroidDeviceLocationSource(
            context = ApplicationProvider.getApplicationContext(),
            locationManager = ApplicationProvider.getApplicationContext<android.content.Context>()
                .getSystemService(LocationManager::class.java),
            mainLooper = Looper.getMainLooper(),
            hasPermission = { true },
            isLocationEnabled = { true },
            isProviderEnabled = { it == LocationManager.NETWORK_PROVIDER },
            timeoutMillis = 1_000L,
            requestLocationUpdates = { _, _, _, _ -> },
            requestSingleUpdate = { _, _ -> },
            removeLocationUpdates = {}
        )

        assertEquals(GpsResult.Unavailable, source.foregroundLocations().first())
    }
}
