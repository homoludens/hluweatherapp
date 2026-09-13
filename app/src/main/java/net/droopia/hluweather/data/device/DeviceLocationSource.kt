package net.droopia.hluweather.data.device

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.droopia.hluweather.data.model.GpsResult

interface DeviceLocationSource {
    suspend fun currentLocation(): GpsResult

    fun foregroundLocations(): Flow<GpsResult> = flow {
        emit(currentLocation())
    }
}

internal const val LOCATION_TIMEOUT_MILLIS = 10_000L
