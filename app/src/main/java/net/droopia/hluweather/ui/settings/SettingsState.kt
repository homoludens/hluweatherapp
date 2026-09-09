package net.droopia.hluweather.ui.settings

import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.Svilajnac

enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT
}

enum class WindUnit {
    KMH,
    MPH
}

enum class DistanceUnit {
    KM,
    MILES
}

enum class PrecipitationUnit {
    MM,
    INCH
}

data class SettingsUiState(
    val provider: WeatherProvider = WeatherProvider.OPEN_METEO,
    val locations: List<WeatherLocation> = defaultSettingsLocations(),
    val selectedLocationId: String? = "svilajnac",
    val trackMeEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windUnit: WindUnit = WindUnit.KMH,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val precipitationUnit: PrecipitationUnit = PrecipitationUnit.MM,
    val weatherAlerts: Boolean = true,
    val dailySummary: Boolean = false,
    val tripAlerts: Boolean = false
)

fun defaultSettingsLocations(): List<WeatherLocation> = listOf(
    Svilajnac,
    WeatherLocation(
        id = "belgrade",
        name = "Belgrade",
        latitude = 44.8176,
        longitude = 20.4633
    ),
    WeatherLocation(
        id = "trieste",
        name = "Trieste",
        latitude = 45.6495,
        longitude = 13.7768
    )
)
