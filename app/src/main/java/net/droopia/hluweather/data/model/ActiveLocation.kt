package net.droopia.hluweather.data.model

enum class LocationMode {
    SAVED_LOCATION,
    TRACK_ME
}

sealed interface ActiveLocation {
    data class Saved(val location: WeatherLocation) : ActiveLocation
    data class Current(val point: GeoPoint, val altitude: Int? = null) : ActiveLocation
}
