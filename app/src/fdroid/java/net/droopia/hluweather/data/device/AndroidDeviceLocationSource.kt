package net.droopia.hluweather.data.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import net.droopia.hluweather.data.model.GpsResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

@SuppressLint("MissingPermission")
class AndroidDeviceLocationSource(
    private val context: Context,
    private val locationManager: LocationManager =
        context.getSystemService(LocationManager::class.java),
    private val mainLooper: Looper = Looper.getMainLooper(),
    private val hasPermission: (String) -> Boolean = { permission ->
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    },
    private val isLocationEnabled: () -> Boolean = { locationManager.isLocationEnabled },
    private val isProviderEnabled: (String) -> Boolean = locationManager::isProviderEnabled,
    private val timeoutMillis: Long = LOCATION_TIMEOUT_MILLIS,
    private val requestLocationUpdates: (String, Long, Float, LocationListener) -> Unit =
        { provider, intervalMillis, minDistanceMeters, listener ->
            locationManager.requestLocationUpdates(
                provider,
                intervalMillis,
                minDistanceMeters,
                listener,
                mainLooper
            )
        },
    private val requestSingleUpdate: (String, LocationListener) -> Unit =
        { provider, listener -> locationManager.requestSingleUpdate(provider, listener, mainLooper) },
    private val removeLocationUpdates: (LocationListener) -> Unit = locationManager::removeUpdates
) : DeviceLocationSource {

    override fun foregroundLocations(): Flow<GpsResult> = callbackFlow {
        if (!hasRequiredPermission()) {
            trySend(GpsResult.PermissionRequired)
            close()
            return@callbackFlow
        }

        if (!isLocationEnabled()) {
            trySend(GpsResult.LocationDisabled)
            close()
            return@callbackFlow
        }

        val provider = selectLocationProvider(
            hasFinePermission = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
            isProviderEnabled = isProviderEnabled
        )
        if (provider == null) {
            trySend(GpsResult.Unavailable)
            close()
            return@callbackFlow
        }

        val firstFix = CompletableDeferred<Unit>()
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                firstFix.complete(Unit)
                trySend(location.toGpsResult())
            }
        }

        try {
            requestLocationUpdates(provider, 5_000L, 10f, listener)
        } catch (_: SecurityException) {
            trySend(GpsResult.PermissionRequired)
            close()
            return@callbackFlow
        } catch (_: RuntimeException) {
            trySend(GpsResult.Unavailable)
            close()
            return@callbackFlow
        }

        if (withTimeoutOrNull(timeoutMillis) { firstFix.await() } == null) {
            trySend(GpsResult.Unavailable)
            close()
        }

        awaitClose {
            removeLocationUpdates(listener)
        }
    }

    override suspend fun currentLocation(): GpsResult {
        if (!hasRequiredPermission()) return GpsResult.PermissionRequired
        if (!isLocationEnabled()) return GpsResult.LocationDisabled

        val provider = selectLocationProvider(
            hasFinePermission = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
            isProviderEnabled = isProviderEnabled
        ) ?: return GpsResult.Unavailable

        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                lateinit var listener: LocationListener
                val removed = AtomicBoolean(false)
                fun removeListener() {
                    if (removed.compareAndSet(false, true)) {
                        removeLocationUpdates(listener)
                    }
                }
                listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (continuation.isActive) {
                            removeListener()
                            continuation.resume(location.toGpsResult())
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    removeListener()
                }

                try {
                    requestSingleUpdate(provider, listener)
                } catch (_: SecurityException) {
                    removeListener()
                    if (continuation.isActive) continuation.resume(GpsResult.PermissionRequired)
                } catch (_: RuntimeException) {
                    removeListener()
                    if (continuation.isActive) continuation.resume(GpsResult.Unavailable)
                }
            }
        } ?: GpsResult.Unavailable
    }

    private fun hasRequiredPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
}

internal fun selectLocationProvider(
    hasFinePermission: Boolean,
    isProviderEnabled: (String) -> Boolean
): String? {
    val providers = if (hasFinePermission) {
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
    } else {
        listOf(LocationManager.NETWORK_PROVIDER)
    }
    return providers.firstOrNull(isProviderEnabled)
}
