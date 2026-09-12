package net.droopia.hluweather.notifications

import android.content.Context
import android.util.Log
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
import net.droopia.hluweather.ui.settings.PrecipitationUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
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

            val location = dependencies.locationRepository.activeLocation.first() as? ActiveLocation.Saved
                ?: return Result.success()
            if (!dependencies.permissionChecker.isGranted(applicationContext)) {
                dependencies.scheduler.enqueueNextDailySummary(settings, location)
                return Result.success()
            }

            val forecast = try {
                dependencies.weatherRepository.getForecast(
                    settings.provider,
                    location
                ).forecast
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.e(TAG, "Unable to load summary forecast for ${location.location.id}/${settings.provider}", error)
                return if (error.isRetryableNotificationFailure()) Result.retry() else Result.failure()
            }

            val body = forecast.summaryBody(
                deliveryTime = dependencies.clock.now(),
                temperatureUnit = settings.temperatureUnit,
                precipitationUnit = settings.precipitationUnit
            ) ?: run {
                Log.w(TAG, "No forecast day for summary delivery in ${forecast.timezone}")
                dependencies.scheduler.enqueueNextDailySummary(settings, location)
                return Result.success()
            }

            try {
                dependencies.publisher.publishDailySummary(
                    title = "Daily weather summary",
                    body = body
                )
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.e(TAG, "Unable to post daily summary for ${location.location.id}", error)
                return Result.failure()
            }
            dependencies.scheduler.enqueueNextDailySummary(settings, location)
            Result.success()
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.e(
                TAG,
                "Terminal daily summary worker failure for " +
                    "${inputData.getString(LOCATION_ID_INPUT)}/${inputData.getString(PROVIDER_INPUT)}",
                error
            )
            if (error.isRetryableNotificationFailure()) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val TAG = "DailySummaryWorker"
    }
}

internal fun WeatherForecast.summaryBody(
    deliveryTime: kotlinx.datetime.Instant,
    temperatureUnit: TemperatureUnit,
    precipitationUnit: PrecipitationUnit
): String? {
    val day = dayForSummary(deliveryTime) ?: return null
    return buildString {
        append(location.name)
        append(": ")
        append(current.condition.label())
        append(", current ")
        append(current.temperature.asNotificationTemperature(temperatureUnit))
        append(". High ")
        append(day.temperatureMax.asNotificationTemperature(temperatureUnit))
        append(", low ")
        append(day.temperatureMin.asNotificationTemperature(temperatureUnit))
        val rainWindow = rainWindow(day)
        if (day.precipitation != null && day.precipitation > 0.0 && rainWindow != null) {
            append(", Rain ")
            append(rainWindow)
            append(", ")
            append(day.precipitation.asNotificationPrecipitation(precipitationUnit))
            append(".")
        } else {
            append(", precipitation ")
            if (day.precipitation == null) {
                append("unavailable.")
            } else {
                append(day.precipitation.asNotificationPrecipitation(precipitationUnit))
                append(".")
            }
        }
    }
}

private fun WeatherForecast.rainWindow(day: DayForecast): String? {
    val timeZone = runCatching { TimeZone.of(timezone) }.getOrNull() ?: return null
    val rainHours = hourly.filter { hour ->
        hour.precipitation > 0.0 && hour.time.toLocalDateTime(timeZone).date == day.date
    }
    if (rainHours.isEmpty()) return null

    val first = rainHours.first().time.toLocalDateTime(timeZone).time.toString()
    val last = rainHours.last().time.toLocalDateTime(timeZone).time.toString()
    return if (first == last) first else "$first-$last"
}

private fun WeatherForecast.dayForSummary(deliveryTime: kotlinx.datetime.Instant): DayForecast? {
    val date = runCatching {
        deliveryTime.toLocalDateTime(TimeZone.of(timezone)).date
    }.getOrNull()
    return daily.firstOrNull { it.date == date }
}

private fun Double.asNotificationTemperature(unit: TemperatureUnit): String {
    val converted = if (unit == TemperatureUnit.FAHRENHEIT) this * 9 / 5 + 32 else this
    val symbol = if (unit == TemperatureUnit.FAHRENHEIT) "F" else "C"
    return "${converted.asNotificationNumber(1)}°$symbol"
}

private fun Double.asNotificationPrecipitation(unit: PrecipitationUnit): String {
    val converted = if (unit == PrecipitationUnit.INCH) this / 25.4 else this
    val decimals = if (unit == PrecipitationUnit.INCH) 2 else 1
    val symbol = if (unit == PrecipitationUnit.INCH) "in" else "mm"
    return "${converted.asNotificationNumber(decimals)} $symbol"
}

private fun Double.asNotificationNumber(decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", this)
