package net.droopia.hluweather.widget

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.ForecastLoad
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.data.repository.currentLocationCacheKey
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.SettingsRepository

/** A provider and location selected for one widget weather request. */
data class WeatherWidgetRequest(
    val provider: WeatherProvider,
    val location: ActiveLocation,
    val locationName: String
) {
    val identity: WeatherWidgetRequestIdentity = location.toRequestIdentity(provider)
}

/** Stable request identity; presentation labels intentionally are not part of it. */
data class WeatherWidgetRequestIdentity(
    val provider: WeatherProvider,
    val location: WeatherWidgetLocationIdentity
)

/** A cache-backed render snapshot that also exposes the selection for later refresh work. */
sealed interface WeatherWidgetSnapshot {
    val settings: PersistedSettings

    data class NoLocation(
        override val settings: PersistedSettings
    ) : WeatherWidgetSnapshot

    data class NoWeather(
        override val settings: PersistedSettings,
        val request: WeatherWidgetRequest,
        val refresh: WidgetRefreshStatus = WidgetRefreshStatus()
    ) : WeatherWidgetSnapshot

    data class Available(
        override val settings: PersistedSettings,
        val request: WeatherWidgetRequest,
        val weather: WeatherWidgetState,
        val refresh: WidgetRefreshStatus = WidgetRefreshStatus()
    ) : WeatherWidgetSnapshot
}

sealed interface WeatherWidgetLocationIdentity {
    data class SavedLocation(
        val id: String,
        val latitude: Double,
        val longitude: Double,
        val altitude: Int?
    ) : WeatherWidgetLocationIdentity

    data class CurrentLocation(
        /** Same three-decimal key used by [net.droopia.hluweather.data.repository.CachingWeatherRepository]. */
        val cacheLocationKey: String,
        val altitude: Int?
    ) : WeatherWidgetLocationIdentity
}

/**
 * Resolves the app's current provider and location without acquiring or naming device locations.
 * Production callers use the application-owned instance, which supplies durable refresh metadata.
 */
class WeatherWidgetRepository(
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    private val clock: Clock = Clock.System,
    private val refreshMetadata: WidgetRefreshMetadataStore = NoWidgetRefreshMetadataStore
) {
    suspend fun resolveRequest(): WeatherWidgetRequest? = loadResolutionSnapshot().resolveRequest()

    /** Called by refresh work; the caller owns networking and cancellation cleanup. */
    suspend fun beginRefresh(request: WeatherWidgetRequest): WidgetRefreshAttempt =
        refreshMetadata.start(request.identity, clock.now())

    /** Null, stale, or mismatched results are failures, never fresh successes.
     * On cancellation, the caller should finish with null in a non-cancellable cleanup block;
     * an interrupted process is recovered by the metadata loading timeout instead.
     */
    suspend fun finishRefresh(request: WeatherWidgetRequest, attempt: WidgetRefreshAttempt, result: ForecastLoad?) {
        val failed = result == null || result.isStale || !request.matchesIdentity(result.forecast)
        refreshMetadata.finish(request.identity, attempt, clock.now(), failed)
    }

    fun observeRefreshStatus(request: WeatherWidgetRequest) =
        refreshMetadata.observe(request.identity, clock::now)

    /** Reads the existing repository cache only; refreshes are owned by asynchronous work. */
    suspend fun loadCachedState(): WeatherWidgetSnapshot {
        val selection = loadResolutionSnapshot()
        val request = selection.resolveRequest()
            ?: return WeatherWidgetSnapshot.NoLocation(selection.settings)
        val refresh = refreshMetadata.read(request.identity, clock::now)
        val cached = weatherRepository.getLastCachedForecast(request.provider, request.location)
            ?.takeIf { request.matchesIdentity(it.forecast) }

        // Selection may change while storage is being read. Never label an obsolete result as current.
        val latest = loadResolutionSnapshot()
        if (latest != selection) {
            val current = latest.resolveRequest() ?: return WeatherWidgetSnapshot.NoLocation(latest.settings)
            return WeatherWidgetSnapshot.NoWeather(latest.settings, current)
        }
        if (cached == null) return WeatherWidgetSnapshot.NoWeather(selection.settings, request, refresh)

        return WeatherWidgetSnapshot.Available(
            settings = selection.settings,
            request = request,
            weather = cached.copy(isStale = cached.isStale ||
                (refresh.lastAttemptFailed && refresh.endedAt?.let { cached.forecast.fetchedAt <= it } == true)
            ).toWeatherWidgetState(
                settings = selection.settings,
                locationName = request.locationName,
                clock = clock
            ),
            refresh = refresh
        )
    }

    private suspend fun loadResolutionSnapshot(): ResolutionSnapshot {
        return combine(
            settingsRepository.settings,
            locationRepository.locationMode,
            locationRepository.activeLocation,
            locationRepository.lastKnownCurrentLocation,
            locationRepository.locations
        ) { settings, mode, active, passiveCurrent, savedLocations ->
            ResolutionSnapshot(settings, mode, active, passiveCurrent, savedLocations)
        }.first()
    }

    private fun ResolutionSnapshot.resolveRequest(): WeatherWidgetRequest? {
        val location = when (mode) {
            LocationMode.SAVED_LOCATION -> resolveSavedLocation()?.let(ActiveLocation::Saved)
            LocationMode.TRACK_ME -> resolveCurrentLocation()
        } ?: return null

        val name = when (location) {
            is ActiveLocation.Saved -> location.location.name
            is ActiveLocation.Current -> CURRENT_LOCATION_NAME
        }
        return WeatherWidgetRequest(
            provider = settings.provider,
            location = location,
            locationName = name
        )
    }

    private data class ResolutionSnapshot(
        val settings: PersistedSettings,
        val mode: LocationMode,
        val activeLocation: ActiveLocation?,
        val passiveCurrentLocation: ActiveLocation.Current?,
        val savedLocations: List<WeatherLocation>
    ) {
        fun resolveSavedLocation(): WeatherLocation? {
            val canonicalId = (activeLocation as? ActiveLocation.Saved)?.location?.id
            return canonicalId?.findValidSavedLocation()
                ?: savedLocations.firstOrNull(WeatherLocation::hasValidCoordinates)
        }

        fun resolveCurrentLocation(): ActiveLocation? {
            val live = (activeLocation as? ActiveLocation.Current)
                ?.takeIf(ActiveLocation.Current::hasValidCoordinates)
            if (live != null) return live

            val passive = passiveCurrentLocation
                ?.takeIf(ActiveLocation.Current::hasValidCoordinates)
            if (passive != null) return passive

            val selected = settings.selectedLocationId?.findValidSavedLocation()
            val fallback = selected ?: savedLocations.firstOrNull(WeatherLocation::hasValidCoordinates)
            return fallback?.let(ActiveLocation::Saved)
        }

        private fun String.findValidSavedLocation(): WeatherLocation? =
            savedLocations.firstOrNull { it.id == this && it.hasValidCoordinates() }
    }
}

internal fun WeatherWidgetRequest.matchesIdentity(
    forecast: WeatherForecast
): Boolean {
    if (forecast.provider != identity.provider) return false

    val forecastLocation = forecast.location
    return when (val requestedLocation = identity.location) {
        is WeatherWidgetLocationIdentity.SavedLocation ->
            forecastLocation.id == requestedLocation.id &&
                forecastLocation.latitude == requestedLocation.latitude &&
                forecastLocation.longitude == requestedLocation.longitude &&
                forecastLocation.altitude == requestedLocation.altitude

        is WeatherWidgetLocationIdentity.CurrentLocation ->
            forecastLocation.id == "current" &&
                GeoPoint(forecastLocation.latitude, forecastLocation.longitude)
                    .currentLocationCacheKey() == requestedLocation.cacheLocationKey &&
                forecastLocation.altitude == requestedLocation.altitude
    }
}

private fun ActiveLocation.toRequestIdentity(provider: WeatherProvider): WeatherWidgetRequestIdentity {
    val locationIdentity = when (this) {
        is ActiveLocation.Saved -> WeatherWidgetLocationIdentity.SavedLocation(
            id = location.id,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude
        )
        is ActiveLocation.Current -> WeatherWidgetLocationIdentity.CurrentLocation(
            cacheLocationKey = point.currentLocationCacheKey(),
            altitude = altitude
        )
    }
    return WeatherWidgetRequestIdentity(provider, locationIdentity)
}

private fun ActiveLocation.Current.hasValidCoordinates(): Boolean =
    point.hasValidCoordinates()

private fun WeatherLocation.hasValidCoordinates(): Boolean =
    latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

private fun GeoPoint.hasValidCoordinates(): Boolean =
    latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

private const val CURRENT_LOCATION_NAME = "Current location"
