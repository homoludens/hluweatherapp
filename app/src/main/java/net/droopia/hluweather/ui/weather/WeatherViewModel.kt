package net.droopia.hluweather.ui.weather

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.droopia.hluweather.HluWeatherApplication
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.SettingsRepository

data class WeatherUiState(
    val activeLocation: WeatherLocation? = null,
    val locations: List<WeatherLocation> = emptyList(),
    val forecast: WeatherForecast? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val forecastMode: ForecastMode = ForecastMode.HOURLY,
    val selectedDayIndex: Int = 0
)

class WeatherViewModel(
    private val repository: WeatherRepository,
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    constructor(repository: WeatherRepository, location: WeatherLocation) : this(
        repository = repository,
        settingsRepository = FixedSettingsRepository,
        locationRepository = FixedLocationRepository(location)
    )

    private val refreshes = MutableStateFlow(0)
    private val _state = MutableStateFlow(WeatherUiState(isLoading = true))
    private var requestGeneration = 0L

    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.settings,
                locationRepository.activeLocation,
                locationRepository.locations,
                refreshes
            ) { settings, activeLocation, locations, _ ->
                WeatherLoadRequest(
                    generation = ++requestGeneration,
                    provider = settings.provider,
                    location = (activeLocation as? ActiveLocation.Saved)?.location,
                    locations = locations
                )
            }.collectLatest { request ->
                load(request)
            }
        }
    }

    fun refresh() {
        refreshes.update { it + 1 }
    }

    fun selectLocation(location: WeatherLocation) {
        viewModelScope.launch {
            locationRepository.selectSaved(location.id)
        }
    }

    fun onForecastModeSelected(mode: ForecastMode) {
        _state.update { it.copy(forecastMode = mode) }
    }

    fun onDaySelected(index: Int) {
        val maxDayIndex = state.value.forecast?.daily?.lastIndex ?: 0
        _state.update {
            it.copy(
                forecastMode = ForecastMode.HOURLY,
                selectedDayIndex = index.coerceIn(0, maxDayIndex)
            )
        }
    }

    private suspend fun load(request: WeatherLoadRequest) {
        val currentLocation = request.location
        if (currentLocation == null) {
            if (requestGeneration != request.generation) return
            _state.update {
                it.copy(
                    activeLocation = null,
                    locations = request.locations,
                    forecast = null,
                    isLoading = false,
                    error = null,
                    selectedDayIndex = 0
                )
            }
            return
        }

        _state.update {
            it.copy(
                isLoading = true,
                activeLocation = currentLocation,
                locations = request.locations,
                forecast = null,
                error = null,
                selectedDayIndex = 0
            )
        }
        try {
            val forecast = repository.getForecast(request.provider, currentLocation)
            if (requestGeneration == request.generation &&
                _state.value.activeLocation == currentLocation
            ) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        forecast = forecast,
                        error = null
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (requestGeneration != request.generation) return
            Log.e("WeatherViewModel", "Weather request failed", error)
            _state.update {
                it.copy(
                    isLoading = false,
                    error = error.message ?: "Weather request failed"
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[
                    ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
                ] as HluWeatherApplication
                WeatherViewModel(
                    application.weatherRepository,
                    application.settingsRepository,
                    application.locationRepository
                )
            }
        }
    }
}

private data class WeatherLoadRequest(
    val generation: Long,
    val provider: WeatherProvider,
    val location: WeatherLocation?,
    val locations: List<WeatherLocation>
)

private object FixedSettingsRepository : SettingsRepository {
    override val settings = flowOf(PersistedSettings())

    override suspend fun save(settings: PersistedSettings) = Unit
}

private class FixedLocationRepository(
    location: WeatherLocation
) : LocationRepository {
    override val locations = flowOf(listOf(location))
    override val activeLocation = flowOf<ActiveLocation?>(ActiveLocation.Saved(location))
    override val locationMode = flowOf(LocationMode.SAVED_LOCATION)

    override suspend fun add(location: WeatherLocation) = Unit
    override suspend fun update(location: WeatherLocation) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun selectSaved(id: String) = Unit
    override suspend fun setTrackMe(enabled: Boolean) = Unit
}
