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
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.PlaceSearchProvider
import net.droopia.hluweather.data.repository.WeatherRepository

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository? = null
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    private var hasUserMutation = false
    private val mutatedSettings = mutableSetOf<PersistedSetting>()
    private var pendingTrackMe: Boolean? = null
    private var pendingSettingsSave = false

    init {
        viewModelScope.launch {
            runCatching {
                combine(
                    repository.settings,
                    locationRepository.locations,
                    locationRepository.activeLocation,
                    locationRepository.locationMode
                ) { persisted, locations, activeLocation, locationMode ->
                    SettingsSnapshot(persisted, locations, activeLocation, locationMode)
                }.collect { snapshot ->
                    val persisted = snapshot.persisted
                    val locations = snapshot.locations
                    val activeLocation = snapshot.activeLocation
                    val locationMode = snapshot.locationMode
                    val nextState = if (hasUserMutation) {
                        val current = _state.value
                        val activeSavedId = (activeLocation as? ActiveLocation.Saved)?.location?.id
                        val requestedTrackMe = pendingTrackMe
                        if (activeSavedId != null) {
                            pendingTrackMe = null
                        } else if (requestedTrackMe == (locationMode == LocationMode.TRACK_ME)) {
                            pendingTrackMe = null
                        }
                        persisted.toUiState(locations, activeLocation, locationMode).copy(
                            provider = current.value(PersistedSetting.PROVIDER, persisted.provider),
                            placeSearchProvider = current.value(
                                PersistedSetting.PLACE_SEARCH_PROVIDER,
                                persisted.placeSearchProvider
                            ),
                            themeMode = current.value(PersistedSetting.THEME_MODE, persisted.themeMode),
                            temperatureUnit = current.value(
                                PersistedSetting.TEMPERATURE_UNIT,
                                persisted.temperatureUnit
                            ),
                            windUnit = current.value(PersistedSetting.WIND_UNIT, persisted.windUnit),
                            distanceUnit = current.value(PersistedSetting.DISTANCE_UNIT, persisted.distanceUnit),
                            precipitationUnit = current.value(
                                PersistedSetting.PRECIPITATION_UNIT,
                                persisted.precipitationUnit
                            ),
                            weatherAlerts = current.value(
                                PersistedSetting.WEATHER_ALERTS,
                                persisted.weatherAlerts
                            ),
                            dailySummary = current.value(PersistedSetting.DAILY_SUMMARY, persisted.dailySummary),
                            dailySummaryTime = current.value(
                                PersistedSetting.DAILY_SUMMARY_TIME,
                                persisted.dailySummaryTime
                            ),
                            hourlyTableColumns = current.value(
                                PersistedSetting.HOURLY_TABLE_COLUMNS,
                                persisted.hourlyTableColumns
                            ),
                            isInitialized = true,
                            locations = locations,
                            selectedLocationId = activeSavedId
                                ?: _state.value.selectedLocationId?.takeIf { id ->
                                    locations.any { it.id == id }
                                }
                                ?: locations.firstOrNull()?.id,
                            trackMeEnabled = if (activeSavedId == null) {
                                requestedTrackMe ?: locationMode == LocationMode.TRACK_ME
                            } else {
                                false
                            }
                        )
                    } else {
                        persisted.toUiState(locations, activeLocation, locationMode)
                    }
                    _state.value = nextState
                    if (pendingSettingsSave) {
                        pendingSettingsSave = false
                        viewModelScope.launch {
                            runCatching { repository.save(nextState.toPersistedSettings()) }
                        }
                    }
                }
            }
        }
    }

    fun setProvider(provider: WeatherProvider) {
        updateSettings(PersistedSetting.PROVIDER) { it.copy(provider = provider) }
    }

    fun setPlaceSearchProvider(provider: PlaceSearchProvider) {
        updateSettings(PersistedSetting.PLACE_SEARCH_PROVIDER) { it.copy(placeSearchProvider = provider) }
    }

    fun setTrackMe(enabled: Boolean) {
        pendingTrackMe = enabled
        updateSettings { it.copy(trackMeEnabled = enabled) }
        viewModelScope.launch {
            runCatching { locationRepository.setTrackMe(enabled) }
        }
    }

    fun selectLocation(location: WeatherLocation) {
        pendingTrackMe = false
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
        updateSettings(PersistedSetting.THEME_MODE) { it.copy(themeMode = themeMode) }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        updateSettings(PersistedSetting.TEMPERATURE_UNIT) { it.copy(temperatureUnit = unit) }
    }

    fun setWindUnit(unit: WindUnit) {
        updateSettings(PersistedSetting.WIND_UNIT) { it.copy(windUnit = unit) }
    }

    fun setDistanceUnit(unit: DistanceUnit) {
        updateSettings(PersistedSetting.DISTANCE_UNIT) { it.copy(distanceUnit = unit) }
    }

    fun setPrecipitationUnit(unit: PrecipitationUnit) {
        updateSettings(PersistedSetting.PRECIPITATION_UNIT) { it.copy(precipitationUnit = unit) }
    }

    fun setHourlyTableColumn(column: HourlyTableColumn, enabled: Boolean) {
        updateSettings(PersistedSetting.HOURLY_TABLE_COLUMNS) { state ->
            state.copy(
                hourlyTableColumns = if (enabled) {
                    state.hourlyTableColumns + column
                } else {
                    state.hourlyTableColumns - column
                }
            )
        }
    }

    fun setWeatherAlerts(enabled: Boolean) {
        updateSettings(PersistedSetting.WEATHER_ALERTS) { it.copy(weatherAlerts = enabled) }
    }

    fun setDailySummary(enabled: Boolean) {
        updateSettings(PersistedSetting.DAILY_SUMMARY) { it.copy(dailySummary = enabled) }
    }

    fun setDailySummaryTime(time: kotlinx.datetime.LocalTime) {
        updateSettings(PersistedSetting.DAILY_SUMMARY_TIME) { it.copy(dailySummaryTime = time) }
    }

    fun clearCache() {
        viewModelScope.launch {
            runCatching { weatherRepository?.clearCache() }
        }
    }

    private fun updateSettings(transform: (SettingsUiState) -> SettingsUiState) {
        hasUserMutation = true
        val current = _state.value
        val next = transform(current)
        _state.value = next
        if (current.isInitialized) {
            viewModelScope.launch {
                runCatching { repository.save(next.toPersistedSettings()) }
            }
        } else {
            pendingSettingsSave = true
        }
    }

    private fun updateSettings(
        setting: PersistedSetting,
        transform: (SettingsUiState) -> SettingsUiState
    ) {
        mutatedSettings += setting
        updateSettings(transform)
    }

    private fun <T> SettingsUiState.value(
        setting: PersistedSetting,
        persistedValue: T
    ): T = if (setting in mutatedSettings) {
        @Suppress("UNCHECKED_CAST")
        when (setting) {
            PersistedSetting.PROVIDER -> provider
            PersistedSetting.PLACE_SEARCH_PROVIDER -> placeSearchProvider
            PersistedSetting.THEME_MODE -> themeMode
            PersistedSetting.TEMPERATURE_UNIT -> temperatureUnit
            PersistedSetting.WIND_UNIT -> windUnit
            PersistedSetting.DISTANCE_UNIT -> distanceUnit
            PersistedSetting.PRECIPITATION_UNIT -> precipitationUnit
            PersistedSetting.WEATHER_ALERTS -> weatherAlerts
            PersistedSetting.DAILY_SUMMARY -> dailySummary
            PersistedSetting.DAILY_SUMMARY_TIME -> dailySummaryTime
            PersistedSetting.HOURLY_TABLE_COLUMNS -> hourlyTableColumns
        } as T
    } else {
        persistedValue
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[
                    ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
                ] as HluWeatherApplication
                SettingsViewModel(
                    settingsRepository(application),
                    application.locationRepository,
                    application.weatherRepository
                )
            }
        }
    }
}

private fun PersistedSettings.toUiState(
    locations: List<WeatherLocation>,
    activeLocation: ActiveLocation?,
    locationMode: LocationMode
): SettingsUiState =
    SettingsUiState(
        provider = provider,
        placeSearchProvider = placeSearchProvider,
        isInitialized = true,
        locations = locations,
        selectedLocationId = (activeLocation as? ActiveLocation.Saved)?.location?.id
            ?: selectedLocationId?.takeIf { id -> locations.any { location -> location.id == id } }
            ?: locations.firstOrNull()?.id,
        trackMeEnabled = if (activeLocation is ActiveLocation.Saved) {
            false
        } else {
            locationMode == LocationMode.TRACK_ME
        },
        themeMode = themeMode,
        temperatureUnit = temperatureUnit,
        windUnit = windUnit,
        distanceUnit = distanceUnit,
        precipitationUnit = precipitationUnit,
        weatherAlerts = weatherAlerts,
        dailySummary = dailySummary,
        dailySummaryTime = dailySummaryTime,
        hourlyTableColumns = hourlyTableColumns
    )

private fun SettingsUiState.toPersistedSettings() = PersistedSettings(
    provider = provider,
    placeSearchProvider = placeSearchProvider,
    selectedLocationId = selectedLocationId,
    trackMeEnabled = trackMeEnabled,
    themeMode = themeMode,
    temperatureUnit = temperatureUnit,
    windUnit = windUnit,
    distanceUnit = distanceUnit,
    precipitationUnit = precipitationUnit,
    weatherAlerts = weatherAlerts,
    dailySummary = dailySummary,
    dailySummaryTime = dailySummaryTime,
    hourlyTableColumns = hourlyTableColumns
)

private data class SettingsSnapshot(
    val persisted: PersistedSettings,
    val locations: List<WeatherLocation>,
    val activeLocation: ActiveLocation?,
    val locationMode: LocationMode
)

private enum class PersistedSetting {
    PROVIDER,
    PLACE_SEARCH_PROVIDER,
    THEME_MODE,
    TEMPERATURE_UNIT,
    WIND_UNIT,
    DISTANCE_UNIT,
    PRECIPITATION_UNIT,
    WEATHER_ALERTS,
    DAILY_SUMMARY,
    DAILY_SUMMARY_TIME,
    HOURLY_TABLE_COLUMNS
}
