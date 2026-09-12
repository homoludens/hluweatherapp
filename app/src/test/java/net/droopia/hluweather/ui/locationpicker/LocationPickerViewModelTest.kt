package net.droopia.hluweather.ui.locationpicker

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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
import org.junit.Assert.assertNull
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
    fun reverse_geocoding_exposes_name_loading_state() {
        val viewModel = picker(geocoder = PendingReverseGeocoder())

        viewModel.onCameraIdle(viewModelPoint)

        assertTrue(viewModel.state.value.isNameLoading)
    }

    @Test
    fun new_location_starts_waiting_for_its_name() {
        val viewModel = picker(
            geocoder = PendingReverseGeocoder(),
            reverseGeocodeDebounceMillis = 300L
        )

        assertTrue(viewModel.state.value.isNameLoading)
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
    fun route_edit_save_waits_for_hydration_and_then_updates_the_existing_location() = runTest {
        val existing = WeatherLocation("belgrade", "Belgrade", 44.8176, 20.4633)
        val locations = MutableSharedFlow<List<WeatherLocation>>()
        val repository = FakeLocationRepository(locationsFlow = locations)
        val viewModel = picker(repository = repository, locationId = existing.id)

        assertEquals(LocationPickerInitialization.Loading, viewModel.state.value.initialization)
        viewModel.save()
        advanceUntilIdle()
        assertTrue(repository.updated.isEmpty())

        locations.emit(listOf(existing))
        advanceUntilIdle()

        assertEquals(LocationPickerInitialization.Ready, viewModel.state.value.initialization)
        assertEquals(existing.name, viewModel.state.value.name)
        viewModel.onNameChanged("Stari Grad")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(existing.copy(name = "Stari Grad"), repository.updated.single())
    }

    @Test
    fun missing_route_location_cannot_be_saved_or_deleted() = runTest {
        val locations = MutableSharedFlow<List<WeatherLocation>>()
        val repository = FakeLocationRepository(locationsFlow = locations)
        val viewModel = picker(repository = repository, locationId = "missing")

        locations.emit(emptyList())
        advanceUntilIdle()

        assertEquals(
            LocationPickerInitialization.MissingEditLocation,
            viewModel.state.value.initialization
        )
        assertFalse(viewModel.isEditMode)
        viewModel.save()
        viewModel.delete()
        advanceUntilIdle()

        assertTrue(repository.updated.isEmpty())
        assertTrue(repository.deleted.isEmpty())
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
    fun denied_permission_preserves_permission_required_status() {
        val viewModel = picker()

        viewModel.onPermissionResult(granted = false)

        assertEquals(GpsStatus.PermissionRequired, viewModel.state.value.gpsStatus)
    }

    @Test
    fun granted_permission_retries_gps() = runTest {
        val point = GeoPoint(45.6495, 13.7768)
        val gps = RecordingDeviceLocationSource(GpsResult.Success(point, altitude = null))
        val viewModel = picker(gps = gps)

        viewModel.onPermissionResult(granted = true)
        advanceUntilIdle()

        assertEquals(1, gps.calls)
        assertEquals(point, viewModel.state.value.point)
        assertEquals(GpsStatus.Success, viewModel.state.value.gpsStatus)
    }

    @Test
    fun reverse_geocoding_exception_uses_fallback_name() = runTest {
        val viewModel = picker(geocoder = ThrowingReverseGeocoder())

        viewModel.onCameraIdle(viewModelPoint)
        advanceUntilIdle()

        assertEquals(NEW_LOCATION_NAME, viewModel.state.value.name)
    }

    @Test
    fun reverse_geocoding_is_debounced_to_the_latest_camera_idle_point() = runTest {
        val geocoder = RecordingReverseGeocoder()
        val viewModel = picker(geocoder = geocoder, reverseGeocodeDebounceMillis = 300L)

        viewModel.onCameraIdle(GeoPoint(44.8176, 20.4633))
        advanceTimeBy(200L)
        viewModel.onCameraIdle(GeoPoint(45.6495, 13.7768))
        advanceTimeBy(299L)

        assertTrue(geocoder.points.isEmpty())
        advanceTimeBy(1L)
        advanceUntilIdle()

        assertEquals(listOf(GeoPoint(45.6495, 13.7768)), geocoder.points)
    }

    @Test
    fun gps_cancellation_does_not_become_unavailable() = runTest {
        val viewModel = picker(gps = CancellingDeviceLocationSource())

        viewModel.onGpsClick()
        advanceUntilIdle()

        assertEquals(GpsStatus.Locating, viewModel.state.value.gpsStatus)
    }

    @Test
    fun stale_gps_result_cannot_replace_a_newer_request() = runTest {
        val oldPoint = GeoPoint(44.8176, 20.4633)
        val newPoint = GeoPoint(45.6495, 13.7768)
        val gps = DeferredDeviceLocationSource()
        val viewModel = picker(gps = gps)

        viewModel.onGpsClick()
        viewModel.onGpsClick()
        gps.second.complete(GpsResult.Success(newPoint, null))
        advanceUntilIdle()
        gps.first.complete(GpsResult.Success(oldPoint, null))
        advanceUntilIdle()

        assertEquals(newPoint, viewModel.state.value.point)
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

    @Test
    fun save_cancellation_cancels_the_operation_job() = runTest {
        val viewModel = picker(repository = CancellingLocationRepository(), initialLocation = existingLocation)

        val job = viewModel.save()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertNull(viewModel.completion.value)
    }

    @Test
    fun delete_cancellation_cancels_the_operation_job() = runTest {
        val viewModel = picker(repository = CancellingLocationRepository(), initialLocation = existingLocation)

        val job = viewModel.delete()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertNull(viewModel.completion.value)
    }

    private fun picker(
        repository: FakeLocationRepository = FakeLocationRepository(),
        gps: DeviceLocationSource = FakeDeviceLocationSource(GpsResult.Unavailable),
        geocoder: ReverseGeocoder = FakeReverseGeocoder(),
        initialLocation: WeatherLocation? = null,
        locationId: String? = null,
        reverseGeocodeDebounceMillis: Long = 0L
    ) = LocationPickerViewModel(
        locationRepository = repository,
        deviceLocationSource = gps,
        reverseGeocoder = geocoder,
        initialLocation = initialLocation,
        locationId = locationId,
        reverseGeocodeDebounceMillis = reverseGeocodeDebounceMillis
    )

    private class FakeDeviceLocationSource(
        private val result: GpsResult
    ) : DeviceLocationSource {
        override suspend fun currentLocation(): GpsResult = result
    }

    private class RecordingDeviceLocationSource(
        private val result: GpsResult
    ) : DeviceLocationSource {
        var calls = 0

        override suspend fun currentLocation(): GpsResult {
            calls++
            return result
        }
    }

    private class CancellingDeviceLocationSource : DeviceLocationSource {
        override suspend fun currentLocation(): GpsResult {
            throw CancellationException("cancelled")
        }
    }

    private class DeferredDeviceLocationSource : DeviceLocationSource {
        val first = CompletableDeferred<GpsResult>()
        val second = CompletableDeferred<GpsResult>()
        private var calls = 0

        override suspend fun currentLocation(): GpsResult =
            if (calls++ == 0) first.await() else second.await()
    }

    private class FakeReverseGeocoder : ReverseGeocoder {
        override suspend fun reverse(point: GeoPoint): String? = null
    }

    private class PendingReverseGeocoder : ReverseGeocoder {
        override suspend fun reverse(point: GeoPoint): String? {
            kotlinx.coroutines.awaitCancellation()
        }
    }

    private class ThrowingReverseGeocoder : ReverseGeocoder {
        override suspend fun reverse(point: GeoPoint): String? {
            error("geocoding failed")
        }
    }

    private class RecordingReverseGeocoder : ReverseGeocoder {
        val points = mutableListOf<GeoPoint>()

        override suspend fun reverse(point: GeoPoint): String? {
            points += point
            return null
        }
    }

    private open class FakeLocationRepository(
        initialLocations: List<WeatherLocation> = emptyList(),
        locationsFlow: Flow<List<WeatherLocation>> = MutableStateFlow(initialLocations)
    ) : LocationRepository {
        override val locations = locationsFlow
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

    private class CancellingLocationRepository : FakeLocationRepository() {
        override suspend fun add(location: WeatherLocation): Unit = throw CancellationException("cancelled")
        override suspend fun update(location: WeatherLocation): Unit = throw CancellationException("cancelled")
        override suspend fun delete(id: String): Unit = throw CancellationException("cancelled")
    }

    private companion object {
        val viewModelPoint = GeoPoint(44.8176, 20.4633)
        val existingLocation = WeatherLocation("belgrade", "Belgrade", 44.8176, 20.4633)
    }
}
