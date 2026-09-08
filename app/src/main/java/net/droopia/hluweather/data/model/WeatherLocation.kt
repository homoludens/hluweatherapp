package net.droopia.hluweather.data.model

data class WeatherLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Int? = null
)
