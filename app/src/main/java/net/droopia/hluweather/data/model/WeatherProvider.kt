package net.droopia.hluweather.data.model

data class WeatherProviderAttribution(
    val widgetLabel: String,
    val accessibilityDescription: String,
    val notificationText: String
)

enum class WeatherProvider(
    val title: String,
    val attribution: WeatherProviderAttribution? = null
) {
    OPEN_METEO("Open-Meteo"),
    MET_NO("MET Norway"),
    VISUAL_CROSSING(
        "Visual Crossing",
        WeatherProviderAttribution(
            widgetLabel = "Visual Crossing Weather",
            accessibilityDescription = "Weather data provided by Visual Crossing Weather",
            notificationText = "Weather data provided by Visual Crossing Weather"
        )
    ),
    BRIGHT_SKY(
        "Bright Sky",
        WeatherProviderAttribution(
            widgetLabel = "Bright Sky · DWD",
            accessibilityDescription = "Weather data via Bright Sky, sourced from Deutscher Wetterdienst (DWD)",
            notificationText = "Bright Sky · Source: Deutscher Wetterdienst (DWD)"
        )
    )
}
