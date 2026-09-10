package net.droopia.hluweather.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import net.droopia.hluweather.HluWeatherApplication
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherForecast
import java.io.IOException
import kotlin.time.Duration.Companion.hours

class WeatherAlertWorker(
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
            if (!settings.weatherAlerts || !dependencies.permissionChecker.isGranted(applicationContext)) {
                return Result.success()
            }

            val location = dependencies.savedLocation(settings.selectedLocationId)
                ?: return Result.success()
            val forecast = try {
                dependencies.weatherRepository.getForecast(
                    settings.provider,
                    ActiveLocation.Saved(location)
                )
            } catch (error: IOException) {
                return Result.retry()
            }

            val now = dependencies.clock.now()
            forecast.forecast.thunderstormEvents(now).forEach { event ->
                if (dependencies.stateRepository.wasDelivered(event.key)) return@forEach
                try {
                    dependencies.publisher.publishAlert(
                        event = event,
                        title = "Thunderstorm alert",
                        body = "${location.name}: thunderstorm possible at ${event.periodStart}."
                    )
                    dependencies.stateRepository.markDelivered(event.key)
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    return Result.failure()
                }
            }
            Result.success()
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (error is IOException) Result.retry() else Result.failure()
        }
    }
}

internal fun WeatherForecast.thunderstormEvents(now: Instant): List<WeatherAlertEvent> {
    val end = now + 24.hours
    return hourly
        .asSequence()
        .filter { it.time >= now && it.time < end && it.condition == WeatherCondition.THUNDERSTORM }
        .map { hour ->
            WeatherAlertEvent(
                provider = provider,
                locationId = location.id,
                periodStart = hour.time
            )
        }
        .toList()
}
