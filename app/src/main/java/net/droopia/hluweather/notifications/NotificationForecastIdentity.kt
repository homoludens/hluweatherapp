package net.droopia.hluweather.notifications

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.WeatherProvider

internal suspend fun NotificationWorkerDependencies.isCurrentForecastSelection(
    provider: WeatherProvider,
    location: ActiveLocation.Saved,
    enabled: (net.droopia.hluweather.ui.settings.PersistedSettings) -> Boolean
): Boolean {
    currentCoroutineContext().ensureActive()
    val settings = settingsRepository.settings.first()
    val current = locationRepository.activeLocation.first() as? ActiveLocation.Saved ?: return false
    val a = location.location
    val b = current.location
    return enabled(settings) && settings.provider == provider && a.id == b.id &&
        a.latitude == b.latitude && a.longitude == b.longitude && a.altitude == b.altitude
}
