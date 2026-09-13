package net.droopia.hluweather.data.device

import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.Priority
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.GpsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceLocationSourceTest {
    @Test
    fun current_location_returns_cached_location_without_requesting_fresh_fix() = runTest {
        val cached = location()
        val gateway = RecordingFusedLocationGateway(last = cached)

        assertEquals(
            cached.toGpsResult(),
            source(gateway, hasFinePermission = true).currentLocation()
        )
        assertEquals(0, gateway.currentLocationCalls)
    }

    @Test
    fun missing_cached_location_uses_high_accuracy_fresh_fix_for_fine_permission() = runTest {
        val gateway = RecordingFusedLocationGateway(current = location())

        source(gateway, hasFinePermission = true).currentLocation()

        assertEquals(Priority.PRIORITY_HIGH_ACCURACY, gateway.lastPriority)
    }

    @Test
    fun missing_cached_location_uses_balanced_accuracy_for_coarse_permission() = runTest {
        val gateway = RecordingFusedLocationGateway(current = location())

        source(gateway, hasFinePermission = false).currentLocation()

        assertEquals(Priority.PRIORITY_BALANCED_POWER_ACCURACY, gateway.lastPriority)
    }

    @Test
    fun missing_permission_returns_permission_required_without_gateway_access() = runTest {
        val gateway = RecordingFusedLocationGateway()

        assertEquals(
            GpsResult.PermissionRequired,
            source(gateway, hasFinePermission = false, hasCoarsePermission = false).currentLocation()
        )
        assertEquals(0, gateway.lastLocationCalls)
    }

    @Test
    fun disabled_location_returns_location_disabled_without_gateway_access() = runTest {
        val gateway = RecordingFusedLocationGateway()

        assertEquals(
            GpsResult.LocationDisabled,
            source(gateway, isLocationEnabled = false).currentLocation()
        )
        assertEquals(0, gateway.lastLocationCalls)
    }

    @Test
    fun checked_provider_failure_returns_unavailable() = runTest {
        val gateway = RecordingFusedLocationGateway(failure = Exception("provider failure"))

        assertEquals(GpsResult.Unavailable, source(gateway).currentLocation())
    }

    @Test
    fun checked_foreground_provider_failure_returns_unavailable() = runTest {
        val gateway = RecordingFusedLocationGateway(failure = Exception("provider failure"))

        assertEquals(GpsResult.Unavailable, source(gateway).foregroundLocations().first())
    }

    @Test
    fun foreground_flow_removes_fused_registration_when_cancelled() = runTest {
        val gateway = RecordingFusedLocationGateway()
        val result = async {
            source(gateway).foregroundLocations().first()
        }
        runCurrent()
        gateway.emit(location())

        assertTrue(result.await() is GpsResult.Success)

        assertEquals(1, gateway.removeCalls)
    }

    @Test
    fun foreground_flow_emits_a_fresh_callback_when_cache_is_empty() = runTest {
        val gateway = RecordingFusedLocationGateway()
        val source = source(gateway)

        val result = async {
            source.foregroundLocations().first()
        }
        runCurrent()
        gateway.emit(location())

        assertTrue(result.await() is GpsResult.Success)
    }

    private fun source(
        gateway: RecordingFusedLocationGateway,
        hasFinePermission: Boolean = true,
        hasCoarsePermission: Boolean = true,
        isLocationEnabled: Boolean = true
    ) = AndroidDeviceLocationSource(
        context = ApplicationProvider.getApplicationContext(),
        fusedLocationGateway = gateway,
        hasPermission = { permission ->
            permission.endsWith("FINE_LOCATION") && hasFinePermission ||
                permission.endsWith("COARSE_LOCATION") && hasCoarsePermission
        },
        isLocationEnabled = { isLocationEnabled },
        timeoutMillis = 1_000L
    )

    private fun location() = Location("fused").apply {
        latitude = 44.8176
        longitude = 20.4633
    }

    private class RecordingFusedLocationGateway(
        private val last: Location? = null,
        private val current: Location? = null,
        private val failure: Exception? = null
    ) : FusedLocationGateway {
        var lastLocationCalls = 0
        var currentLocationCalls = 0
        var lastPriority: Int? = null
        var removeCalls = 0
        private var listener: ((Location) -> Unit)? = null

        override suspend fun lastLocation(): Location? {
            lastLocationCalls++
            failure?.let { throw it }
            return last
        }

        override suspend fun currentLocation(priority: Int): Location? {
            currentLocationCalls++
            lastPriority = priority
            return current
        }

        override suspend fun requestLocationUpdates(
            priority: Int,
            listener: (Location) -> Unit
        ): LocationUpdateRegistration {
            lastPriority = priority
            this.listener = listener
            return LocationUpdateRegistration { removeCalls++ }
        }

        fun emit(location: Location) {
            listener?.invoke(location)
        }
    }
}
