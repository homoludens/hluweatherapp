package net.droopia.hluweather.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider

class SettingsViewModel(
    private val repository: SettingsRepository = DefaultSettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.settings.first() }
                .getOrNull()
                ?.let { persisted ->
                    _state.value = persisted.toUiState(_state.value)
                }
        }
    }

    fun setProvider(provider: WeatherProvider) {
        updateSettings { it.copy(provider = provider) }
    }

    fun setTrackMe(enabled: Boolean) {
        updateSettings { it.copy(trackMeEnabled = enabled) }
    }

    fun selectLocation(location: WeatherLocation) {
        updateSettings {
            it.copy(
                selectedLocationId = location.id,
                trackMeEnabled = false
            )
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
        val next = transform(_state.value)
        _state.value = next
        viewModelScope.launch {
            runCatching { repository.save(next.toPersistedSettings()) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                )
                SettingsViewModel(settingsRepository(application))
            }
        }
    }
}

private fun PersistedSettings.toUiState(current: SettingsUiState): SettingsUiState =
    current.copy(
        provider = provider,
        selectedLocationId = selectedLocationId
            ?.takeIf { id -> current.locations.any { location -> location.id == id } }
            ?: current.selectedLocationId,
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

private object DefaultSettingsRepository : SettingsRepository {
    override val settings: Flow<PersistedSettings> = flowOf(PersistedSettings())

    override suspend fun save(settings: PersistedSettings) = Unit
}
