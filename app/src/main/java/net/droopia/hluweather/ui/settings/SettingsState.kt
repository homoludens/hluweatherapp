package net.droopia.hluweather.ui.settings

import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.PlaceSearchProvider
import kotlinx.datetime.LocalTime

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

enum class HourlyTableColumn {
    TIME,
    WEATHER_ICON,
    WEATHER_TEXT,
    TEMPERATURE,
    DEW_POINT,
    RELATIVE_HUMIDITY,
    PRECIPITATION,
    WIND_SPEED,
    WIND_DIRECTION,
    EVAPOTRANSPIRATION
}

val defaultHourlyTableColumns = setOf(
    HourlyTableColumn.WEATHER_ICON,
    HourlyTableColumn.TEMPERATURE,
    HourlyTableColumn.DEW_POINT,
    HourlyTableColumn.RELATIVE_HUMIDITY,
    HourlyTableColumn.PRECIPITATION
)

data class SettingsUiState(
    val provider: WeatherProvider = WeatherProvider.OPEN_METEO,
    val placeSearchProvider: PlaceSearchProvider = PlaceSearchProvider.PHOTON,
    val isInitialized: Boolean = false,
    val locations: List<WeatherLocation> = emptyList(),
    val selectedLocationId: String? = null,
    val trackMeEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windUnit: WindUnit = WindUnit.KMH,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val precipitationUnit: PrecipitationUnit = PrecipitationUnit.MM,
    val weatherAlerts: Boolean = false,
    val dailySummary: Boolean = false,
    val dailySummaryTime: LocalTime = LocalTime(8, 0),
    val hourlyTableColumns: Set<HourlyTableColumn> = defaultHourlyTableColumns
)
