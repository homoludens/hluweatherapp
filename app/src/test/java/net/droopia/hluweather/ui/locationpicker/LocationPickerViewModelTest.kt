package net.droopia.hluweather.ui.locationpicker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.droopia.hluweather.data.device.DeviceLocationSource
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.GpsResult
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.ReverseGeocoder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationPickerViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun manual_coordinate_uses_fallback_until_geocoding_and_preserves_edited_name() = runTest {
        val viewModel = picker()

        viewModel.onCameraIdle(GeoPoint(44.8176, 20.4633))

        assertEquals("New location", viewModel.state.value.name)
        viewModel.onNameChanged("Belgrade")
        viewModel.onReverseGeocoded("Stari Grad")

        assertEquals("Belgrade", viewModel.state.value.name)
    }

    @Test
    fun reverse_geocoding_fills_an_unedited_name() {
        val viewModel = picker()

        viewModel.onCameraIdle(GeoPoint(44.8176, 20.4633))
        viewModel.onReverseGeocoded("Stari Grad")

        assertEquals("Stari Grad", viewModel.state.value.name)
        assertFalse(viewModel.state.value.isNameEditing)
    }

    @Test
    fun add_save_persists_without_waiting_for_reverse_geocoding() = runTest {
        val repository = FakeLocationRepository()
        val viewModel = picker(repository = repository, geocoder = PendingReverseGeocoder())
        val point = GeoPoint(44.8176, 20.4633)

        viewModel.onCameraIdle(point)
        viewModel.onNameChanged("Belgrade")
        viewModel.save()

        val saved = repository.added.single()
        assertEquals("Belgrade", saved.name)
        assertEquals(point.latitude, saved.latitude, 0.0)
        assertEquals(point.longitude, saved.longitude, 0.0)
        assertTrue(saved.id.isNotBlank())
    }

    @Test
    fun edit_save_updates_the_existing_location() = runTest {
        val existing = WeatherLocation("belgrade", "Belgrade", 44.8176, 20.4633)
        val repository = FakeLocationRepository(listOf(existing))
        val viewModel = picker(repository = repository, initialLocation = existing)

        viewModel.onNameChanged("Stari Grad")
        viewModel.save()

        assertEquals(existing.copy(name = "Stari Grad"), repository.updated.single())
        assertTrue(repository.added.isEmpty())
    }

    @Test
    fun gps_success_updates_coordinates_altitude_and_status() = runTest {
        val point = GeoPoint(45.6495, 13.7768)
        val viewModel = picker(
            gps = FakeDeviceLocationSource(GpsResult.Success(point, 2))
        )

        viewModel.onGpsClick()

        assertEquals(point, viewModel.state.value.point)
        assertEquals(2, viewModel.state.value.altitude)
        assertEquals(GpsStatus.Success, viewModel.state.value.gpsStatus)
    }

    @Test
    fun gps_result_statuses_are_exposed_without_changing_manual_coordinates() = runTest {
        val point = viewModelPoint
        for ((result, expected) in listOf(
            GpsResult.PermissionRequired to GpsStatus.PermissionRequired,
            GpsResult.LocationDisabled to GpsStatus.LocationDisabled,
            GpsResult.Unavailable to GpsStatus.Unavailable
        )) {
            val viewModel = picker(gps = FakeDeviceLocationSource(result))
            viewModel.onCameraIdle(point)
            viewModel.onGpsClick()

            assertEquals(expected, viewModel.state.value.gpsStatus)
            assertEquals(point, viewModel.state.value.point)
        }
    }

    @Test
    fun delete_only_deletes_in_edit_mode() = runTest {
        val existing = WeatherLocation("belgrade", "Belgrade", 44.8176, 20.4633)
        val repository = FakeLocationRepository(listOf(existing))
        val editViewModel = picker(repository = repository, initialLocation = existing)

        editViewModel.delete()

        assertEquals(listOf("belgrade"), repository.deleted)

        val addViewModel = picker(repository = repository)
        addViewModel.delete()

        assertEquals(listOf("belgrade"), repository.deleted)
    }

    private fun picker(
        repository: FakeLocationRepository = FakeLocationRepository(),
        gps: DeviceLocationSource = FakeDeviceLocationSource(GpsResult.Unavailable),
        geocoder: ReverseGeocoder = FakeReverseGeocoder(),
        initialLocation: WeatherLocation? = null
    ) = LocationPickerViewModel(
        locationRepository = repository,
        deviceLocationSource = gps,
        reverseGeocoder = geocoder,
        initialLocation = initialLocation
    )

    private class FakeDeviceLocationSource(
        private val result: GpsResult
    ) : DeviceLocationSource {
        override suspend fun currentLocation(): GpsResult = result
    }

    private class FakeReverseGeocoder : ReverseGeocoder {
        override suspend fun reverse(point: GeoPoint): String? = null
    }

    private class PendingReverseGeocoder : ReverseGeocoder {
        override suspend fun reverse(point: GeoPoint): String? {
            kotlinx.coroutines.awaitCancellation()
        }
    }

    private class FakeLocationRepository(
        initialLocations: List<WeatherLocation> = emptyList()
    ) : LocationRepository {
        override val locations = MutableStateFlow(initialLocations)
        override val activeLocation = MutableStateFlow<ActiveLocation?>(null)
        override val locationMode = MutableStateFlow(LocationMode.SAVED_LOCATION)
        val added = mutableListOf<WeatherLocation>()
        val updated = mutableListOf<WeatherLocation>()
        val deleted = mutableListOf<String>()

        override suspend fun add(location: WeatherLocation) {
            added += location
        }

        override suspend fun update(location: WeatherLocation) {
            updated += location
        }

        override suspend fun delete(id: String) {
            deleted += id
        }

        override suspend fun selectSaved(id: String) = Unit

        override suspend fun setTrackMe(enabled: Boolean) = Unit
    }

    private companion object {
        val viewModelPoint = GeoPoint(44.8176, 20.4633)
    }
}
