package net.droopia.hluweather

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.droopia.hluweather.data.cache.ForecastCache
import net.droopia.hluweather.data.repository.CachingWeatherRepository
import net.droopia.hluweather.data.device.AndroidDeviceLocationSource
import net.droopia.hluweather.data.device.DeviceLocationSource
import net.droopia.hluweather.data.network.KtorMetNoApi
import net.droopia.hluweather.data.network.KtorNominatimApi
import net.droopia.hluweather.data.network.KtorOpenMeteoApi
import net.droopia.hluweather.data.repository.NominatimReverseGeocoder
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.MetNoWeatherRepository
import net.droopia.hluweather.data.repository.OpenMeteoWeatherRepository
import net.droopia.hluweather.data.repository.ReverseGeocoder
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.data.repository.WeatherSource
import net.droopia.hluweather.data.repository.applicationDataStore
import net.droopia.hluweather.data.repository.locationRepository
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.notifications.DataStoreNotificationStateRepository
import net.droopia.hluweather.notifications.NotificationBoundaryWorker
import net.droopia.hluweather.notifications.NotificationChannels
import net.droopia.hluweather.notifications.NotificationScheduler
import net.droopia.hluweather.notifications.NotificationStateRepository
import net.droopia.hluweather.notifications.WorkManagerNotificationScheduler
import net.droopia.hluweather.notifications.notificationDataStore
import net.droopia.hluweather.ui.settings.SettingsRepository
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.settingsRepository

class HluWeatherApplication : Application(), Configuration.Provider {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        applicationScope.launch {
            combine(
                settingsRepository.settings,
                locationRepository.activeLocation,
                locationRepository.locationMode
            ) { settings, activeLocation, locationMode ->
                notificationSettingsForReconciliation(settings, activeLocation, locationMode)
            }.collect(notificationScheduler::reconcile)
        }
    }

    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
            }
        }
    }

    val weatherRepository: WeatherRepository by lazy {
        CachingWeatherRepository(
            openMeteo = OpenMeteoWeatherRepository(KtorOpenMeteoApi(httpClient)),
            metNo = MetNoWeatherRepository(KtorMetNoApi(httpClient)),
            cache = ForecastCache(applicationDataStore)
        )
    }

    val metNoWeatherRepository: WeatherSource by lazy {
        MetNoWeatherRepository(KtorMetNoApi(httpClient))
    }

    val settingsRepository: SettingsRepository by lazy {
        settingsRepository(this)
    }

    val locationRepository: LocationRepository by lazy {
        locationRepository(this)
    }

    val deviceLocationSource: DeviceLocationSource by lazy {
        AndroidDeviceLocationSource(this)
    }

    val notificationStateRepository: NotificationStateRepository by lazy {
        DataStoreNotificationStateRepository(notificationDataStore)
    }

    val notificationScheduler: NotificationScheduler by lazy {
        WorkManagerNotificationScheduler(
            workManager = WorkManager.getInstance(this),
            alertWorkerClass = NotificationBoundaryWorker::class.java,
            summaryWorkerClass = NotificationBoundaryWorker::class.java
        )
    }

    val reverseGeocoder: ReverseGeocoder by lazy {
        NominatimReverseGeocoder(KtorNominatimApi(httpClient))
    }
}

internal fun notificationSettingsForReconciliation(
    settings: PersistedSettings,
    activeLocation: ActiveLocation?,
    locationMode: LocationMode
): PersistedSettings = settings.copy(
    selectedLocationId = (activeLocation as? ActiveLocation.Saved)?.location?.id,
    trackMeEnabled = locationMode == LocationMode.TRACK_ME
)
