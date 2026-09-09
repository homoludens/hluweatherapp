package net.droopia.hluweather.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.droopia.hluweather.HluWeatherApplication
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.WeatherRepository

data class WeatherUiState(
    val activeLocation: WeatherLocation? = null,
    val forecast: WeatherForecast? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val forecastMode: ForecastMode = ForecastMode.HOURLY,
    val selectedDayIndex: Int = 0
)

class WeatherViewModel(
    private val repository: WeatherRepository,
    private val location: WeatherLocation = Svilajnac
) : ViewModel() {

    private val _state = MutableStateFlow(
        WeatherUiState(
            activeLocation = location,
            isLoading = true
        )
    )

    val state = _state.asStateFlow()

    init {
        load()
    }

    fun refresh() {
        load()
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

    private fun load() {
        val currentLocation = location
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    activeLocation = currentLocation,
                    error = null
                )
            }
            try {
                val forecast = repository.getForecast(currentLocation)
                _state.update {
                    it.copy(
                        isLoading = false,
                        forecast = forecast,
                        error = null
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Weather request failed"
                    )
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[
                    ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
                ] as HluWeatherApplication
                WeatherViewModel(application.weatherRepository)
            }
        }
    }
}
