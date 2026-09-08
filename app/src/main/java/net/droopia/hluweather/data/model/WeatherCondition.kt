package net.droopia.hluweather.data.model

enum class WeatherCondition {
    CLEAR,
    MOSTLY_CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    THUNDERSTORM,
    UNKNOWN
}

fun WeatherCondition.label(): String = when (this) {
    WeatherCondition.CLEAR -> "Clear sky"
    WeatherCondition.MOSTLY_CLEAR -> "Fair"
    WeatherCondition.PARTLY_CLOUDY -> "Partly cloudy"
    WeatherCondition.CLOUDY -> "Cloudy"
    WeatherCondition.FOG -> "Fog"
    WeatherCondition.DRIZZLE -> "Drizzle"
    WeatherCondition.RAIN -> "Rain"
    WeatherCondition.SNOW -> "Snow"
    WeatherCondition.THUNDERSTORM -> "Thunderstorm"
    WeatherCondition.UNKNOWN -> "Unknown"
}
