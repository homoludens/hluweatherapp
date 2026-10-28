package net.droopia.hluweather

import android.app.Application
import android.content.res.Configuration as AndroidConfiguration
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
import net.droopia.hluweather.data.network.KtorOpenMeteoAirQualityApi
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
import net.droopia.hluweather.widget.DataStoreWidgetRefreshMetadataStore
import net.droopia.hluweather.widget.AndroidWeatherWidgetDispatcher
import net.droopia.hluweather.widget.WeatherWidgetRepository
import net.droopia.hluweather.widget.WeatherWidgetUpdater
import net.droopia.hluweather.widget.WeatherWidgetUpdaterOwner
import net.droopia.hluweather.widget.WeatherWidgetWorker
import net.droopia.hluweather.widget.WeatherWidgetWorkerDependencies
import net.droopia.hluweather.widget.WeatherWidgetRefreshCoordinator
import net.droopia.hluweather.widget.WidgetSystemThemeChangeHandler
import net.droopia.hluweather.widget.startWidgetReconciliation

class HluWeatherApplication : Application(), Configuration.Provider, WeatherWidgetUpdaterOwner {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var widgetSystemThemeChangeHandler: WidgetSystemThemeChangeHandler? = null

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(AppWorkerFactory(this))
            .build()

    override fun onCreate() {
        super.onCreate()
        widgetSystemThemeChangeHandler = WidgetSystemThemeChangeHandler(
            initialConfiguration = resources.configuration,
            refreshWidgetInventory = weatherWidgetDispatcher::refreshInventory,
            updateAllWidgets = weatherWidgetDispatcher::updateAllWidgets
        )
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
        weatherWidgetUpdater.refreshInventory()
        startWidgetReconciliation(
            scope = applicationScope,
            installed = weatherWidgetDispatcher.installedWidgets,
            widgets = weatherWidgetRepository,
            enqueueSelectionRefresh = weatherWidgetUpdater::enqueueSelectionRefresh,
            updateAllWidgets = { weatherWidgetDispatcher.updateAllWidgets() }
        )
    }

    override fun onConfigurationChanged(newConfig: AndroidConfiguration) {
        super.onConfigurationChanged(newConfig)
        widgetSystemThemeChangeHandler?.onConfigurationChanged(newConfig)
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
            openMeteo = OpenMeteoWeatherRepository(
                api = KtorOpenMeteoApi(httpClient),
                airQualityApi = KtorOpenMeteoAirQualityApi(httpClient)
            ),
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

    val weatherWidgetRepository: WeatherWidgetRepository by lazy {
        WeatherWidgetRepository(
            settingsRepository, locationRepository, weatherRepository,
            refreshMetadata = DataStoreWidgetRefreshMetadataStore(applicationDataStore)
        )
    }

    private val weatherWidgetDispatcher: AndroidWeatherWidgetDispatcher by lazy {
        AndroidWeatherWidgetDispatcher(this)
    }

    override val weatherWidgetUpdater: WeatherWidgetUpdater by lazy {
        WeatherWidgetUpdater(
            workManager = WorkManager.getInstance(this),
            workerClass = WeatherWidgetWorker::class.java,
            hasInstalledWidgets = weatherWidgetDispatcher::hasInstalledWidgets,
            refreshInstalledWidgetInventory = { weatherWidgetDispatcher.refreshInventory() },
            recordDeletedWidgetIds = weatherWidgetDispatcher::recordDeletedIds
        )
    }

    internal val weatherWidgetRefreshCoordinator: WeatherWidgetRefreshCoordinator by lazy {
        WeatherWidgetRefreshCoordinator(
            widgets = weatherWidgetRepository,
            weather = weatherRepository,
            publish = { weatherWidgetDispatcher.updateAllWidgets() }
        )
    }

    internal val weatherWidgetWorkerDependencies: WeatherWidgetWorkerDependencies by lazy {
        object : WeatherWidgetWorkerDependencies {
            override val coordinator: WeatherWidgetRefreshCoordinator
                get() = weatherWidgetRefreshCoordinator

            override suspend fun updateAllWidgets() {
                weatherWidgetDispatcher.updateAllWidgets()
            }

            override suspend fun deferSelectionReconciliation() {
                weatherWidgetUpdater.enqueueSelectionRefresh()
            }
        }
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
