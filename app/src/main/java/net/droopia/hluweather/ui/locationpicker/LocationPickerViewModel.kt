package net.droopia.hluweather.ui.locationpicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.droopia.hluweather.HluWeatherApplication
import net.droopia.hluweather.data.device.DeviceLocationSource
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.GpsResult
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.ReverseGeocoder
import java.util.UUID

const val NEW_LOCATION_NAME = "New location"
private const val DEFAULT_LATITUDE = 44.2380
private const val DEFAULT_LONGITUDE = 21.1970

sealed interface GpsStatus {
    data object Idle : GpsStatus
    data object Locating : GpsStatus
    data object Success : GpsStatus
    data object PermissionRequired : GpsStatus
    data object LocationDisabled : GpsStatus
    data object Unavailable : GpsStatus
}

sealed interface LocationPickerInitialization {
    data object Ready : LocationPickerInitialization
    data object Loading : LocationPickerInitialization
    data object MissingEditLocation : LocationPickerInitialization
}

data class LocationPickerUiState(
    val latitude: Double = DEFAULT_LATITUDE,
    val longitude: Double = DEFAULT_LONGITUDE,
    val altitude: Int? = null,
    val name: String = NEW_LOCATION_NAME,
    val isNameEditing: Boolean = false,
    val gpsStatus: GpsStatus = GpsStatus.Idle,
    val initialization: LocationPickerInitialization = LocationPickerInitialization.Ready
) {
    val point: GeoPoint
        get() = GeoPoint(latitude, longitude)
}

sealed interface LocationPickerEvent {
    data object Saved : LocationPickerEvent
    data object Deleted : LocationPickerEvent
}

class LocationPickerViewModel(
    private val locationRepository: LocationRepository,
    private val deviceLocationSource: DeviceLocationSource,
    private val reverseGeocoder: ReverseGeocoder,
    private val locationId: String? = null,
    initialLocation: WeatherLocation? = null
) : ViewModel() {
    private val _state = MutableStateFlow(initialLocation.toPickerState())
    val state: StateFlow<LocationPickerUiState> = _state

    private val _events = MutableSharedFlow<LocationPickerEvent>(replay = 1, extraBufferCapacity = 1)
    val events: SharedFlow<LocationPickerEvent> = _events.asSharedFlow()
    private val _completion = MutableStateFlow<LocationPickerEvent?>(null)
    val completion: StateFlow<LocationPickerEvent?> = _completion.asStateFlow()

    private var reverseGeocodingJob: Job? = null
    private val editingLocationId = initialLocation?.id ?: locationId
    val isEditMode: Boolean
        get() = editingLocationId != null && _state.value.initialization == LocationPickerInitialization.Ready

    init {
        if (initialLocation == null && locationId != null) {
            _state.update { it.copy(initialization = LocationPickerInitialization.Loading) }
            viewModelScope.launch {
                val location = locationRepository.locations.first()
                    .firstOrNull { it.id == locationId }
                if (location == null) {
                    _state.update {
                        it.copy(initialization = LocationPickerInitialization.MissingEditLocation)
                    }
                } else {
                    _state.value = location.toPickerState()
                }
            }
        }
    }

    fun onCameraIdle(point: GeoPoint) {
        updatePoint(point, altitude = null)
        requestReverseGeocode(point)
    }

    fun onNameChanged(name: String) {
        _state.update { it.copy(name = name, isNameEditing = true) }
    }

    fun onReverseGeocoded(name: String?) {
        val generatedName = name?.trim()?.takeIf(String::isNotEmpty) ?: NEW_LOCATION_NAME
        _state.update { state ->
            if (state.isNameEditing) state else state.copy(name = generatedName)
        }
    }

    fun onGpsClick() {
        _state.update { it.copy(gpsStatus = GpsStatus.Locating) }
        viewModelScope.launch {
            val result = try {
                deviceLocationSource.currentLocation()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                GpsResult.Unavailable
            }
            onGpsResult(result)
        }
    }

    fun onGpsResult(result: GpsResult) {
        when (result) {
            is GpsResult.Success -> {
                updatePoint(result.point, result.altitude)
                _state.update { it.copy(gpsStatus = GpsStatus.Success) }
                requestReverseGeocode(result.point)
            }

            GpsResult.PermissionRequired ->
                _state.update { it.copy(gpsStatus = GpsStatus.PermissionRequired) }

            GpsResult.LocationDisabled ->
                _state.update { it.copy(gpsStatus = GpsStatus.LocationDisabled) }

            GpsResult.Unavailable ->
                _state.update { it.copy(gpsStatus = GpsStatus.Unavailable) }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            onGpsClick()
        } else {
            _state.update { it.copy(gpsStatus = GpsStatus.PermissionRequired) }
        }
    }

    fun save() {
        if (_state.value.initialization != LocationPickerInitialization.Ready) return
        val current = _state.value
        val location = WeatherLocation(
            id = editingLocationId ?: UUID.randomUUID().toString(),
            name = current.name.trim().ifEmpty { NEW_LOCATION_NAME },
            latitude = current.latitude,
            longitude = current.longitude,
            altitude = current.altitude
        )
        viewModelScope.launch {
            runCatching {
                if (editingLocationId == null) {
                    locationRepository.add(location)
                } else {
                    locationRepository.update(location)
                }
            }.onSuccess {
                val event = LocationPickerEvent.Saved
                _events.tryEmit(event)
                _completion.value = event
            }
        }
    }

    fun delete() {
        if (!isEditMode) return
        val id = editingLocationId ?: return
        viewModelScope.launch {
            runCatching { locationRepository.delete(id) }
                .onSuccess {
                    val event = LocationPickerEvent.Deleted
                    _events.tryEmit(event)
                    _completion.value = event
                }
        }
    }

    private fun updatePoint(point: GeoPoint, altitude: Int?) {
        _state.update {
            it.copy(
                latitude = point.latitude,
                longitude = point.longitude,
                altitude = altitude,
                name = if (it.isNameEditing) it.name else NEW_LOCATION_NAME
            )
        }
    }

    private fun requestReverseGeocode(point: GeoPoint) {
        reverseGeocodingJob?.cancel()
        reverseGeocodingJob = viewModelScope.launch {
            try {
                onReverseGeocoded(reverseGeocoder.reverse(point))
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                onReverseGeocoded(null)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = factory(null)

        fun factory(locationId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[
                    ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
                ] as HluWeatherApplication
                LocationPickerViewModel(
                    locationRepository = application.locationRepository,
                    deviceLocationSource = application.deviceLocationSource,
                    reverseGeocoder = application.reverseGeocoder,
                    locationId = locationId
                )
            }
        }
    }
}

private fun WeatherLocation?.toPickerState(): LocationPickerUiState = this?.let { location ->
    LocationPickerUiState(
        latitude = location.latitude,
        longitude = location.longitude,
        altitude = location.altitude,
        name = location.name,
        isNameEditing = true
    )
} ?: LocationPickerUiState()
