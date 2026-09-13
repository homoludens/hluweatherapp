package net.droopia.hluweather.data.device

import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.GpsResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceLocationSourceTest {
    @Test
    fun coarse_permission_uses_network_provider() {
        assertEquals(
            LocationManager.NETWORK_PROVIDER,
            selectLocationProvider(hasFinePermission = false) { it == LocationManager.NETWORK_PROVIDER }
        )
    }

    @Test
    fun fine_permission_prefers_network_provider_when_available() {
        assertEquals(
            LocationManager.NETWORK_PROVIDER,
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
        var removeCalls = 0
        val source = AndroidDeviceLocationSource(
            context = ApplicationProvider.getApplicationContext(),
            mainLooper = Looper.getMainLooper(),
            hasPermission = { true },
            isLocationEnabled = { true },
            isProviderEnabled = { it == LocationManager.NETWORK_PROVIDER },
            timeoutMillis = 1_000L,
            requestLocationUpdates = { _, _, _, _ -> },
            requestSingleUpdate = { _, _ -> },
            removeLocationUpdates = { removeCalls++ }
        )

        assertEquals(GpsResult.Unavailable, source.foregroundLocations().first())
        assertEquals(1, removeCalls)
    }

    @Test
    fun current_location_removes_listener_when_request_fails() = runTest {
        var removeCalls = 0
        val source = AndroidDeviceLocationSource(
            context = ApplicationProvider.getApplicationContext(),
            mainLooper = Looper.getMainLooper(),
            hasPermission = { true },
            isLocationEnabled = { true },
            isProviderEnabled = { it == LocationManager.NETWORK_PROVIDER },
            requestLocationUpdates = { _, _, _, _ -> },
            requestSingleUpdate = { _, _ -> throw IllegalStateException("provider failure") },
            removeLocationUpdates = { removeCalls++ }
        )

        assertEquals(GpsResult.Unavailable, source.currentLocation())
        assertEquals(1, removeCalls)
    }
}
