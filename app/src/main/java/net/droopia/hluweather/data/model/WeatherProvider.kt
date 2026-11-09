package net.droopia.hluweather.data.model

enum class WeatherProvider(
    val title: String
) {
    OPEN_METEO("Open-Meteo"),
    MET_NO("MET Norway"),
    VISUAL_CROSSING("Visual Crossing"),
    BRIGHT_SKY("Bright Sky")
}
