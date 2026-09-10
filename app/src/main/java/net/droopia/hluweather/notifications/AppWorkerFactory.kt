package net.droopia.hluweather.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import net.droopia.hluweather.HluWeatherApplication
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.ui.settings.SettingsRepository
import kotlin.time.Clock

fun interface NotificationPermissionChecker {
    fun isGranted(context: Context): Boolean
}

object AndroidNotificationPermissionChecker : NotificationPermissionChecker {
    override fun isGranted(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
}

data class NotificationWorkerDependencies(
    val settingsRepository: SettingsRepository,
    val locationRepository: LocationRepository,
    val weatherRepository: WeatherRepository,
    val publisher: WeatherNotificationPublisher,
    val stateRepository: NotificationStateRepository,
    val scheduler: NotificationScheduler,
    val permissionChecker: NotificationPermissionChecker = AndroidNotificationPermissionChecker,
    val clock: Clock = Clock.System
)

class AppWorkerFactory private constructor(
    private val dependenciesProvider: () -> NotificationWorkerDependencies
) : WorkerFactory() {

    constructor(application: HluWeatherApplication) : this({ application.notificationWorkerDependencies() })

    constructor(dependencies: NotificationWorkerDependencies) : this({ dependencies })

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = when (workerClassName) {
        WeatherAlertWorker::class.java.name -> WeatherAlertWorker(
            appContext,
            workerParameters,
            dependenciesProvider()
        )
        DailySummaryWorker::class.java.name -> DailySummaryWorker(
            appContext,
            workerParameters,
            dependenciesProvider()
        )
        else -> null
    }
}

internal fun HluWeatherApplication.notificationWorkerDependencies() = NotificationWorkerDependencies(
    settingsRepository = settingsRepository,
    locationRepository = locationRepository,
    weatherRepository = weatherRepository,
    publisher = notificationPublisher,
    stateRepository = notificationStateRepository,
    scheduler = notificationScheduler
)
