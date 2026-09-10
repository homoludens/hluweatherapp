package net.droopia.hluweather.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import net.droopia.hluweather.MainActivity

class AndroidWeatherNotificationPublisher(
    private val context: Context,
    private val weatherAlertsChannelId: String = NotificationChannels.WEATHER_ALERTS_CHANNEL_ID,
    private val dailySummaryChannelId: String = NotificationChannels.DAILY_SUMMARY_CHANNEL_ID,
    private val dailySummaryNotificationId: Int = DAILY_SUMMARY_NOTIFICATION_ID
) : WeatherNotificationPublisher {

    override fun publishAlert(event: WeatherAlertEvent, title: String, body: String) {
        publish(
            channelId = weatherAlertsChannelId,
            notificationId = event.key.hashCode(),
            title = title,
            body = body
        )
    }

    override fun publishDailySummary(title: String, body: String) {
        publish(
            channelId = dailySummaryChannelId,
            notificationId = dailySummaryNotificationId,
            title = title,
            body = body
        )
    }

    private fun publish(channelId: String, notificationId: Int, title: String, body: String) {
        NotificationChannels.create(context, weatherAlertsChannelId, dailySummaryChannelId)
        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    private companion object {
        const val DAILY_SUMMARY_NOTIFICATION_ID = 1001
    }
}
