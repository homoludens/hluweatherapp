package net.droopia.hluweather.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider

class SettingsViewModel : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun setProvider(provider: WeatherProvider) {
        _state.update { it.copy(provider = provider) }
    }

    fun setTrackMe(enabled: Boolean) {
        _state.update { it.copy(trackMeEnabled = enabled) }
    }

    fun selectLocation(location: WeatherLocation) {
        _state.update {
            it.copy(
                selectedLocationId = location.id,
                trackMeEnabled = false
            )
        }
    }

    fun setTheme(themeMode: ThemeMode) {
        _state.update { it.copy(themeMode = themeMode) }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        _state.update { it.copy(temperatureUnit = unit) }
    }

    fun setWindUnit(unit: WindUnit) {
        _state.update { it.copy(windUnit = unit) }
    }

    fun setDistanceUnit(unit: DistanceUnit) {
        _state.update { it.copy(distanceUnit = unit) }
    }

    fun setPrecipitationUnit(unit: PrecipitationUnit) {
        _state.update { it.copy(precipitationUnit = unit) }
    }

    fun setWeatherAlerts(enabled: Boolean) {
        _state.update { it.copy(weatherAlerts = enabled) }
    }

    fun setDailySummary(enabled: Boolean) {
        _state.update { it.copy(dailySummary = enabled) }
    }

    fun setTripAlerts(enabled: Boolean) {
        _state.update { it.copy(tripAlerts = enabled) }
    }

    fun clearCache() = Unit
}
