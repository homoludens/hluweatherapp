package net.droopia.hluweather.data.device

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class GoogleFusedLocationGateway(
    private val client: FusedLocationProviderClient,
    private val looper: Looper = Looper.getMainLooper()
) : FusedLocationGateway {

    @SuppressLint("MissingPermission")
    override suspend fun lastLocation(): Location? = suspendCancellableCoroutine { continuation ->
        client.lastLocation
            .addOnSuccessListener { location ->
                if (continuation.isActive) continuation.resume(location)
            }
            .addOnFailureListener { exception ->
                if (continuation.isActive) continuation.resumeWithException(exception)
            }
    }

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(priority: Int): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()
            continuation.invokeOnCancellation { cancellationTokenSource.cancel() }
            client.getCurrentLocation(priority, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
                .addOnFailureListener { exception ->
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
        }

    @SuppressLint("MissingPermission")
    override suspend fun requestLocationUpdates(
        priority: Int,
        listener: (Location) -> Unit
    ): LocationUpdateRegistration = suspendCancellableCoroutine { continuation ->
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(listener)
            }
        }
        val request = LocationRequest.Builder(priority, 5_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .setMinUpdateDistanceMeters(10f)
            .build()

        continuation.invokeOnCancellation {
            client.removeLocationUpdates(callback)
        }
        client.requestLocationUpdates(request, callback, looper)
            .addOnSuccessListener {
                if (continuation.isActive) {
                    continuation.resume(LocationUpdateRegistration {
                        client.removeLocationUpdates(callback)
                    })
                }
            }
            .addOnFailureListener { exception ->
                client.removeLocationUpdates(callback)
                if (continuation.isActive) continuation.resumeWithException(exception)
            }
    }
}

internal fun GoogleFusedLocationGateway(context: android.content.Context): GoogleFusedLocationGateway =
    GoogleFusedLocationGateway(LocationServices.getFusedLocationProviderClient(context))
