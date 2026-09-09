package net.droopia.hluweather.data.model

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

sealed interface GpsResult {
    data class Success(val point: GeoPoint, val altitude: Int?) : GpsResult
    data object PermissionRequired : GpsResult
    data object LocationDisabled : GpsResult
    data object Unavailable : GpsResult
}
