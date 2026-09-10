package net.droopia.hluweather.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.droopia.hluweather.HluWeatherApplication
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.DayForecast
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.label
import java.io.IOException
import java.util.Locale

class DailySummaryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private var injectedDependencies: NotificationWorkerDependencies? = null

    internal constructor(
        appContext: Context,
        workerParams: WorkerParameters,
        dependencies: NotificationWorkerDependencies
    ) : this(appContext, workerParams) {
        injectedDependencies = dependencies
    }

    override suspend fun doWork(): ListenableWorker.Result {
        val dependencies = injectedDependencies ?: (applicationContext as HluWeatherApplication).notificationWorkerDependencies()
        return try {
            val settings = dependencies.settingsRepository.settings.first()
            if (!settings.dailySummary) return Result.success()

            val location = dependencies.savedLocation(settings.selectedLocationId)
                ?: return Result.success()
            if (!dependencies.permissionChecker.isGranted(applicationContext)) {
                dependencies.scheduler.enqueueNextDailySummary(settings)
                return Result.success()
            }

            val forecast = try {
                dependencies.weatherRepository.getForecast(
                    settings.provider,
                    ActiveLocation.Saved(location)
                ).forecast
            } catch (error: IOException) {
                return Result.retry()
            }

            try {
                dependencies.publisher.publishDailySummary(
                    title = "Daily weather summary",
                    body = forecast.summaryBody()
                )
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                return Result.failure()
            }
            dependencies.scheduler.enqueueNextDailySummary(settings)
            Result.success()
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (error is IOException) Result.retry() else Result.failure()
        }
    }
}

internal fun WeatherForecast.summaryBody(): String {
    val day = dayForSummary()
    val precipitation = day?.precipitation ?: 0.0
    return buildString {
        append(location.name)
        append(": ")
        append(current.condition.label())
        append(", current ")
        append(current.temperature.asNotificationNumber())
        append("°C. ")
        if (day != null) {
            append("High ")
            append(day.temperatureMax.asNotificationNumber())
            append("°C, low ")
            append(day.temperatureMin.asNotificationNumber())
            append("°C, precipitation ")
            append(precipitation.asNotificationNumber())
            append(" mm.")
        } else {
            append("No daily forecast available.")
        }
    }
}

private fun WeatherForecast.dayForSummary(): DayForecast? {
    val date = runCatching {
        fetchedAt.toLocalDateTime(TimeZone.of(timezone)).date
    }.getOrNull()
    return daily.firstOrNull { it.date == date } ?: daily.firstOrNull()
}

private fun Double.asNotificationNumber(): String =
    String.format(Locale.US, "%.1f", this)
