package net.droopia.hluweather.notifications

import kotlinx.datetime.Instant
import net.droopia.hluweather.data.model.WeatherProvider

data class WeatherAlertEvent(
    val provider: WeatherProvider,
    val locationId: String,
    val periodStart: Instant
) {
    val key: String
        get() = "weather-alert:${provider.name}:$locationId:${periodStart.epochSeconds}"
}

interface WeatherNotificationPublisher {
    fun publishAlert(event: WeatherAlertEvent, title: String, body: String)

    fun publishDailySummary(title: String, body: String)
}
