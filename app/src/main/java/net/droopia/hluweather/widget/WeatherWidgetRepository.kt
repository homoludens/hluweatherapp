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
        val request: WeatherWidgetRequest
    ) : WeatherWidgetSnapshot

    data class Available(
        override val settings: PersistedSettings,
        val request: WeatherWidgetRequest,
        val weather: WeatherWidgetState
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

/** Resolves the app's current provider and location without acquiring or naming device locations. */
class WeatherWidgetRepository(
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    private val clock: Clock = Clock.System
) {
    suspend fun resolveRequest(): WeatherWidgetRequest? = loadResolutionSnapshot().resolveRequest()

    /** Reads the existing repository cache only; refreshes are owned by asynchronous work. */
    suspend fun loadCachedState(): WeatherWidgetSnapshot {
        val selection = loadResolutionSnapshot()
        val request = selection.resolveRequest()
            ?: return WeatherWidgetSnapshot.NoLocation(selection.settings)
        val cached = weatherRepository.getLastCachedForecast(request.provider, request.location)
            ?.takeIf { request.matchesIdentity(it.forecast) }
            ?: return WeatherWidgetSnapshot.NoWeather(selection.settings, request)

        return WeatherWidgetSnapshot.Available(
            settings = selection.settings,
            request = request,
            weather = cached.toWeatherWidgetState(
                settings = selection.settings,
                locationName = request.locationName,
                clock = clock
            )
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

private fun WeatherWidgetRequest.matchesIdentity(
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
