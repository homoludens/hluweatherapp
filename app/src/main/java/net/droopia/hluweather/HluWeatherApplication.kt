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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
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
import net.droopia.hluweather.data.network.KtorOpenMeteoGeocodingApi
import net.droopia.hluweather.data.network.KtorOpenMeteoRouteWeatherApi
import net.droopia.hluweather.data.network.KtorOsrmApi
import net.droopia.hluweather.data.network.KtorPhotonApi
import net.droopia.hluweather.data.repository.NominatimReverseGeocoder
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.MetNoWeatherRepository
import net.droopia.hluweather.data.repository.OpenMeteoPlaceSearchSource
import net.droopia.hluweather.data.repository.OpenMeteoRouteWeatherSource
import net.droopia.hluweather.data.repository.OpenMeteoWeatherRepository
import net.droopia.hluweather.data.repository.OsrmRoutingSource
import net.droopia.hluweather.data.repository.PhotonPlaceSearchSource
import net.droopia.hluweather.data.repository.PlaceSearchProvider
import net.droopia.hluweather.data.repository.PlaceSearchSource
import net.droopia.hluweather.data.repository.ReverseGeocoder
import net.droopia.hluweather.data.repository.RouteWeatherSource
import net.droopia.hluweather.data.repository.RoutingSource
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.data.repository.WeatherSource
import net.droopia.hluweather.data.repository.applicationDataStore
import net.droopia.hluweather.data.repository.locationRepository
import net.droopia.hluweather.notifications.AndroidWeatherNotificationPublisher
import net.droopia.hluweather.notifications.AppWorkerFactory
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.notifications.DataStoreNotificationStateRepository
import net.droopia.hluweather.notifications.NotificationChannels
import net.droopia.hluweather.notifications.NotificationScheduler
import net.droopia.hluweather.notifications.NotificationStateRepository
import net.droopia.hluweather.notifications.WeatherNotificationPublisher
import net.droopia.hluweather.notifications.WorkManagerNotificationScheduler
import net.droopia.hluweather.notifications.notificationDataStore
import net.droopia.hluweather.ui.settings.SettingsRepository
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.settingsRepository

class HluWeatherApplication : Application(), Configuration.Provider {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(AppWorkerFactory(this))
            .build()

    override fun onCreate() {
        super.onCreate()
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(this, workManagerConfiguration)
        }
        NotificationChannels.create(this)
        startNotificationReconciliation(
            scope = applicationScope,
            settings = settingsRepository.settings,
            activeLocation = locationRepository.activeLocation,
            scheduler = notificationScheduler
        )
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

    val routingSource: RoutingSource by lazy {
        OsrmRoutingSource(KtorOsrmApi(httpClient))
    }

    val routePlaceSearchSources: Map<PlaceSearchProvider, PlaceSearchSource> by lazy {
        mapOf(
            PlaceSearchProvider.PHOTON to PhotonPlaceSearchSource(KtorPhotonApi(httpClient)),
            PlaceSearchProvider.OPEN_METEO to OpenMeteoPlaceSearchSource(
                KtorOpenMeteoGeocodingApi(httpClient)
            )
        )
    }

    val routeWeatherSource: RouteWeatherSource by lazy {
        OpenMeteoRouteWeatherSource(KtorOpenMeteoRouteWeatherApi(httpClient))
    }

    val notificationStateRepository: NotificationStateRepository by lazy {
        DataStoreNotificationStateRepository(notificationDataStore)
    }

    val notificationPublisher: WeatherNotificationPublisher by lazy {
        AndroidWeatherNotificationPublisher(this)
    }

    val notificationScheduler: NotificationScheduler by lazy {
        WorkManagerNotificationScheduler(
            workManager = WorkManager.getInstance(this),
            alertWorkerClass = net.droopia.hluweather.notifications.WeatherAlertWorker::class.java,
            summaryWorkerClass = net.droopia.hluweather.notifications.DailySummaryWorker::class.java
        )
    }

    val reverseGeocoder: ReverseGeocoder by lazy {
        NominatimReverseGeocoder(KtorNominatimApi(httpClient))
    }
}

internal fun startNotificationReconciliation(
    scope: CoroutineScope,
    settings: Flow<PersistedSettings>,
    activeLocation: Flow<ActiveLocation?>,
    scheduler: NotificationScheduler
): Job = scope.launch {
    combine(settings, activeLocation) { persistedSettings, canonicalLocation ->
        persistedSettings to canonicalLocation
    }.collect { (persistedSettings, canonicalLocation) ->
        scheduler.reconcile(persistedSettings, canonicalLocation)
    }
}
