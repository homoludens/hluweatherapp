package net.droopia.hluweather.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import net.droopia.hluweather.HluWeatherApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.LocationRepository

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    private var hasUserMutation = false

    init {
        viewModelScope.launch {
            runCatching {
                combine(repository.settings, locationRepository.locations) { persisted, locations ->
                    persisted to locations
                }.collect { (persisted, locations) ->
                    _state.value = if (hasUserMutation) {
                        _state.value.copy(
                            locations = locations,
                            selectedLocationId = _state.value.selectedLocationId
                                ?.takeIf { id -> locations.any { it.id == id } }
                                ?: locations.firstOrNull()?.id
                        )
                    } else {
                        persisted.toUiState(locations)
                    }
                }
            }
        }
    }

    fun setProvider(provider: WeatherProvider) {
        updateSettings { it.copy(provider = provider) }
    }

    fun setTrackMe(enabled: Boolean) {
        updateSettings { it.copy(trackMeEnabled = enabled) }
        viewModelScope.launch {
            runCatching { locationRepository.setTrackMe(enabled) }
        }
    }

    fun selectLocation(location: WeatherLocation) {
        updateSettings {
            it.copy(
                selectedLocationId = location.id,
                trackMeEnabled = false
            )
        }
        viewModelScope.launch {
            runCatching { locationRepository.selectSaved(location.id) }
        }
    }

    fun onLocationMenuClick(location: WeatherLocation) = Unit

    fun onAddLocationClick() = Unit

    fun onProviderInfoClick(provider: WeatherProvider) = Unit

    fun setTheme(themeMode: ThemeMode) {
        updateSettings { it.copy(themeMode = themeMode) }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        updateSettings { it.copy(temperatureUnit = unit) }
    }

    fun setWindUnit(unit: WindUnit) {
        updateSettings { it.copy(windUnit = unit) }
    }

    fun setDistanceUnit(unit: DistanceUnit) {
        updateSettings { it.copy(distanceUnit = unit) }
    }

    fun setPrecipitationUnit(unit: PrecipitationUnit) {
        updateSettings { it.copy(precipitationUnit = unit) }
    }

    fun setWeatherAlerts(enabled: Boolean) {
        updateSettings { it.copy(weatherAlerts = enabled) }
    }

    fun setDailySummary(enabled: Boolean) {
        updateSettings { it.copy(dailySummary = enabled) }
    }

    fun setTripAlerts(enabled: Boolean) {
        updateSettings { it.copy(tripAlerts = enabled) }
    }

    fun clearCache() = Unit

    private fun updateSettings(transform: (SettingsUiState) -> SettingsUiState) {
        hasUserMutation = true
        val next = transform(_state.value)
        _state.value = next
        viewModelScope.launch {
            runCatching { repository.save(next.toPersistedSettings()) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[
                    ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
                ] as HluWeatherApplication
                SettingsViewModel(
                    settingsRepository(application),
                    application.locationRepository
                )
            }
        }
    }
}

private fun PersistedSettings.toUiState(locations: List<WeatherLocation>): SettingsUiState =
    SettingsUiState(
        provider = provider,
        locations = locations,
        selectedLocationId = selectedLocationId
            ?.takeIf { id -> locations.any { location -> location.id == id } }
            ?: locations.firstOrNull()?.id,
        trackMeEnabled = trackMeEnabled,
        themeMode = themeMode,
        temperatureUnit = temperatureUnit,
        windUnit = windUnit,
        distanceUnit = distanceUnit,
        precipitationUnit = precipitationUnit,
        weatherAlerts = weatherAlerts,
        dailySummary = dailySummary,
        tripAlerts = tripAlerts
    )

private fun SettingsUiState.toPersistedSettings() = PersistedSettings(
    provider = provider,
    selectedLocationId = selectedLocationId,
    trackMeEnabled = trackMeEnabled,
    themeMode = themeMode,
    temperatureUnit = temperatureUnit,
    windUnit = windUnit,
    distanceUnit = distanceUnit,
    precipitationUnit = precipitationUnit,
    weatherAlerts = weatherAlerts,
    dailySummary = dailySummary,
    tripAlerts = tripAlerts
)
