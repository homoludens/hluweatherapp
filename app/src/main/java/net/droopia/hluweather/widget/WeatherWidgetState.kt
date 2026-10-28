package net.droopia.hluweather.widget

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant
import net.droopia.hluweather.data.dateTimeText
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
    /** The fetch time formatted in the forecast's location time zone. */
    val lastWeatherUpdateText: String,
    val isStale: Boolean
)

/** Display-ready hourly values while retaining shared weather semantics and the source instant. */
data class WidgetHour(
    val time: Instant,
    val timeText: String,
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
    locationName: String = forecast.location.name,
    clock: Clock = Clock.System
): WeatherWidgetState {
    val weather = forecast.current
    val unit = settings.temperatureUnit
    val zone = forecast.timezone.toSafeZoneId()
    val now = clock.now()

    return WeatherWidgetState(
        locationName = locationName,
        temperatureText = weather.temperature.temperatureValueText(unit),
        temperatureContentDescription = weather.temperature.temperatureText(unit),
        apparentTemperatureText = weather.apparentTemperature?.temperatureValueText(unit),
        apparentTemperatureContentDescription = weather.apparentTemperature?.temperatureText(unit),
        condition = weather.condition,
        conditionLabel = weather.condition.label(),
        isDay = weather.isDay,
        hours = forecast.hourly.asSequence()
            .filter { hour -> hour.time > now }
            .sortedBy { hour -> hour.time }
            .take(MAX_WIDGET_HOURS)
            .map { hour ->
                WidgetHour(
                    time = hour.time,
                    timeText = hour.time.toWidgetTimeText(zone),
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
            }
            .toList(),
        lastWeatherUpdate = forecast.fetchedAt,
        lastWeatherUpdateText = forecast.fetchedAt.dateTimeText(zone),
        isStale = isStale
    )
}

private const val MAX_WIDGET_HOURS = 5

private val widgetHourFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

private fun String.toSafeZoneId(): ZoneId =
    runCatching { ZoneId.of(this) }.getOrElse { ZoneId.systemDefault() }

private fun Instant.toWidgetTimeText(zone: ZoneId): String =
    java.time.Instant.ofEpochMilli(toEpochMilliseconds())
        .atZone(zone)
        .format(widgetHourFormatter)
