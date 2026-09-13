package net.droopia.hluweather.data.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeoutOrNull
import net.droopia.hluweather.data.model.GpsResult

@SuppressLint("MissingPermission")
class AndroidDeviceLocationSource internal constructor(
    private val context: Context,
    private val fusedLocationGateway: FusedLocationGateway = GoogleFusedLocationGateway(context),
    private val isLocationEnabled: () -> Boolean = {
        context.getSystemService(LocationManager::class.java).isLocationEnabled
    },
    private val hasPermission: (String) -> Boolean = { permission ->
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    },
    private val timeoutMillis: Long = LOCATION_TIMEOUT_MILLIS
) : DeviceLocationSource {

    override suspend fun currentLocation(): GpsResult {
        val validation = validateAccess() ?: return validationResult()
        log("requesting current location with priority=$validation")
        return try {
            val location = withTimeoutOrNull(timeoutMillis) {
                fusedLocationGateway.lastLocation()
                    ?: fusedLocationGateway.currentLocation(validation)
            }
            location?.toGpsResult() ?: GpsResult.Unavailable
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: SecurityException) {
            GpsResult.PermissionRequired
        } catch (_: Exception) {
            GpsResult.Unavailable
        }
    }

    override fun foregroundLocations(): Flow<GpsResult> = callbackFlow {
        val priority = validateAccess()
        if (priority == null) {
            trySend(validationResult())
            close()
            return@callbackFlow
        }

        val firstFix = CompletableDeferred<Unit>()
        val registrationLock = Any()
        var registration: LocationUpdateRegistration? = null
        var flowClosed = false
        var cachedFixReceived = false
        fun removeRegistration() {
            val registrationToRemove = synchronized(registrationLock) {
                flowClosed = true
                registration.also { registration = null }
            }
            registrationToRemove?.remove()
        }
        try {
            val cached = fusedLocationGateway.lastLocation()
            if (cached != null) {
                cachedFixReceived = true
                firstFix.complete(Unit)
                trySend(cached.toGpsResult())
            }

            val newRegistration = fusedLocationGateway.requestLocationUpdates(priority) { location ->
                firstFix.complete(Unit)
                trySend(location.toGpsResult())
            }
            val removeNewRegistration = synchronized(registrationLock) {
                if (flowClosed) {
                    true
                } else {
                    registration = newRegistration
                    false
                }
            }
            if (removeNewRegistration) newRegistration.remove()
        } catch (_: SecurityException) {
            trySend(GpsResult.PermissionRequired)
            close()
            return@callbackFlow
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            trySend(GpsResult.Unavailable)
            close()
            return@callbackFlow
        }

        if (!cachedFixReceived && withTimeoutOrNull(timeoutMillis) { firstFix.await() } == null) {
            trySend(GpsResult.Unavailable)
            close()
        }

        awaitClose {
            removeRegistration()
        }
    }

    private fun validateAccess(): Int? {
        if (!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) &&
            !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            return null
        }
        if (!isLocationEnabled()) return null
        return if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
    }

    private fun validationResult(): GpsResult = when {
        !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) &&
            !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) -> GpsResult.PermissionRequired
        !isLocationEnabled() -> GpsResult.LocationDisabled
        else -> GpsResult.Unavailable
    }

    private fun log(message: String) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d(TAG, message)
        }
    }

    private companion object {
        const val TAG = "DeviceLocationSource"
    }
}
