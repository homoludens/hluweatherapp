package net.droopia.hluweather.widget

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.LocationRepository
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
    private val locationRepository: LocationRepository
) {
    suspend fun resolveRequest(): WeatherWidgetRequest? {
        val snapshot = combine(
            settingsRepository.settings,
            locationRepository.locationMode,
            locationRepository.activeLocation,
            locationRepository.lastKnownCurrentLocation,
            locationRepository.locations
        ) { settings, mode, active, passiveCurrent, savedLocations ->
            ResolutionSnapshot(settings, mode, active, passiveCurrent, savedLocations)
        }.first()

        val location = when (snapshot.mode) {
            LocationMode.SAVED_LOCATION -> snapshot.resolveSavedLocation()
                ?.let(ActiveLocation::Saved)
            LocationMode.TRACK_ME -> snapshot.resolveCurrentLocation()
        } ?: return null

        val name = when (location) {
            is ActiveLocation.Saved -> location.location.name
            is ActiveLocation.Current -> CURRENT_LOCATION_NAME
        }
        return WeatherWidgetRequest(
            provider = snapshot.settings.provider,
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
