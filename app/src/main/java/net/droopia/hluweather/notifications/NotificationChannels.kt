package net.droopia.hluweather.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val WEATHER_ALERTS_CHANNEL_ID = "weather_alerts"
    const val DAILY_SUMMARY_CHANNEL_ID = "daily_summary"

    fun create(
        context: Context,
        weatherAlertsChannelId: String = WEATHER_ALERTS_CHANNEL_ID,
        dailySummaryChannelId: String = DAILY_SUMMARY_CHANNEL_ID
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    weatherAlertsChannelId,
                    "Weather alerts",
                    NotificationManager.IMPORTANCE_LOW
                ),
                NotificationChannel(
                    dailySummaryChannelId,
                    "Daily summary",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        )
    }
}
