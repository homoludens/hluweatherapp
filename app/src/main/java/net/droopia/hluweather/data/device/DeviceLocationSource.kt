package net.droopia.hluweather.data.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.GpsResult
import kotlin.coroutines.resume

interface DeviceLocationSource {
    suspend fun currentLocation(): GpsResult
}

class AndroidDeviceLocationSource(
    private val context: Context,
    private val locationManager: LocationManager =
        context.getSystemService(LocationManager::class.java),
    private val mainLooper: Looper = Looper.getMainLooper(),
    private val hasPermission: (String) -> Boolean = { permission ->
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
) : DeviceLocationSource {

    override suspend fun currentLocation(): GpsResult {
        if (!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) &&
            !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            return GpsResult.PermissionRequired
        }

        if (!locationManager.isLocationEnabled) {
            return GpsResult.LocationDisabled
        }

        val provider = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).firstOrNull(locationManager::isProviderEnabled)
            ?: return GpsResult.Unavailable

        return suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) {
                        continuation.resume(
                            location.toGpsResult()
                        )
                    }
                }
            }

            continuation.invokeOnCancellation {
                locationManager.removeUpdates(listener)
            }

            try {
                locationManager.requestSingleUpdate(provider, listener, mainLooper)
            } catch (_: SecurityException) {
                if (continuation.isActive) {
                    continuation.resume(GpsResult.PermissionRequired)
                }
            } catch (_: RuntimeException) {
                if (continuation.isActive) {
                    continuation.resume(GpsResult.Unavailable)
                }
            }
        }
    }
}

internal fun Location.toGpsResult(): GpsResult.Success = GpsResult.Success(
    point = GeoPoint(latitude, longitude),
    altitude = altitude.takeIf { hasAltitude() }?.toInt()
)
