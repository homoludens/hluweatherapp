package net.droopia.hluweather.data.device

import android.location.Location
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.GpsResult

internal fun Location.toGpsResult(): GpsResult.Success = GpsResult.Success(
    point = GeoPoint(latitude, longitude),
    altitude = altitude.takeIf { hasAltitude() }?.toInt()
)
