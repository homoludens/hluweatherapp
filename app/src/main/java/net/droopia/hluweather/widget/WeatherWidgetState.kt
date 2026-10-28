package net.droopia.hluweather.widget

import kotlin.time.Instant
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.label
import net.droopia.hluweather.data.percentText
import net.droopia.hluweather.data.repository.ForecastLoad
import net.droopia.hluweather.data.temperatureText
import net.droopia.hluweather.data.temperatureValueText
import net.droopia.hluweather.ui.settings.PersistedSettings

/** Display-ready weather values shared by the small and medium widgets. */
data class WeatherWidgetState(
    val locationName: String,
    val temperatureText: String,
    val temperatureContentDescription: String,
    val apparentTemperatureText: String?,
    val apparentTemperatureContentDescription: String?,
    val condition: WeatherCondition,
    val conditionLabel: String,
    val isDay: Boolean?,
    val hours: List<WidgetHour>,
    /** The forecast's fetch time, not a widget refresh-attempt or render time. */
    val lastWeatherUpdate: Instant,
    val isStale: Boolean
)

/** Display-ready hourly values while retaining shared weather semantics and the source instant. */
data class WidgetHour(
    val time: Instant,
    val temperatureText: String,
    val temperatureContentDescription: String,
    val condition: WeatherCondition,
    val conditionLabel: String,
    val isDay: Boolean?,
    val precipitationProbability: Int?,
    val precipitationProbabilityText: String,
    val precipitationProbabilityContentDescription: String
)

/** Maps normalized repository weather into widget display values without changing its identity. */
fun ForecastLoad.toWeatherWidgetState(
    settings: PersistedSettings,
    locationName: String = forecast.location.name
): WeatherWidgetState {
    val weather = forecast.current
    val unit = settings.temperatureUnit

    return WeatherWidgetState(
        locationName = locationName,
        temperatureText = weather.temperature.temperatureValueText(unit),
        temperatureContentDescription = weather.temperature.temperatureText(unit),
        apparentTemperatureText = weather.apparentTemperature?.temperatureValueText(unit),
        apparentTemperatureContentDescription = weather.apparentTemperature?.temperatureText(unit),
        condition = weather.condition,
        conditionLabel = weather.condition.label(),
        isDay = weather.isDay,
        hours = forecast.hourly.map { hour ->
            WidgetHour(
                time = hour.time,
                temperatureText = hour.temperature.temperatureValueText(unit),
                temperatureContentDescription = hour.temperature.temperatureText(unit),
                condition = hour.condition,
                conditionLabel = hour.condition.label(),
                isDay = hour.isDay,
                precipitationProbability = hour.precipitationProbability,
                precipitationProbabilityText = hour.precipitationProbability.percentText(),
                precipitationProbabilityContentDescription = hour.precipitationProbability
                    ?.let { "Rain probability $it percent" }
                    ?: "Rain probability unavailable"
            )
        },
        lastWeatherUpdate = forecast.fetchedAt,
        isStale = isStale
    )
}
