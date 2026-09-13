package net.droopia.hluweather.data.device

import android.location.Location

internal interface FusedLocationGateway {
    suspend fun lastLocation(): Location?

    suspend fun currentLocation(priority: Int): Location?

    suspend fun requestLocationUpdates(
        priority: Int,
        listener: (Location) -> Unit
    ): LocationUpdateRegistration
}

internal fun interface LocationUpdateRegistration {
    fun remove()
}
