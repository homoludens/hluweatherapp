package net.droopia.hluweather.ui.locationpicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.swipeUp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.data.device.DeviceLocationSource
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.GpsResult
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.ReverseGeocoder
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocationPickerScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun shows_coordinates_name_and_save_action() {
        render()

        composeRule.onNodeWithText("Location picker").assertIsDisplayed()
        composeRule.onNodeWithTag("location_picker_map").assertIsDisplayed()
        composeRule.onNodeWithTag("location_picker_center_marker").assertIsDisplayed()
        composeRule.onNodeWithTag("location_picker_name").assertIsDisplayed()
    }

    @Test
    fun name_and_navigation_actions_report_callbacks() {
        var backClicks = 0
        var saved = 0
        val repository = TestLocationRepository()
        val viewModel = viewModel(repository)
        render(viewModel, onBackClick = { backClicks++ }, onSaved = { saved++ })

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithTag("location_picker_name").performTextInput("Belgrade")
        composeRule.onNodeWithTag("location_picker_scroll").performTouchInput { swipeUp() }
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        assertEquals("BelgradeNew location", repository.added.single().name)
        assertEquals(1, backClicks)
        assertEquals(1, saved)
    }

    @Test
    fun location_picker_actions_are_exposed_to_accessibility_services() {
        render()

        composeRule.onNodeWithContentDescription("Back").assertHasClickAction()
        composeRule.onNodeWithText("Use my location").assertHasClickAction()
        composeRule.onNodeWithText("Save").assertHasClickAction()
    }

    private fun render(
        viewModel: LocationPickerViewModel = viewModel(TestLocationRepository()),
        onBackClick: () -> Unit = {},
        onSaved: () -> Unit = {}
    ) {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                LocationPickerScreen(
                    viewModel = viewModel,
                    onBackClick = onBackClick,
                    onSaved = onSaved,
                    mapContent = { _, _, _ ->
                        Box(Modifier.fillMaxSize().testTag("location_picker_map"))
                    }
                )
            }
        }
    }

    private fun viewModel(repository: TestLocationRepository) = LocationPickerViewModel(
        locationRepository = repository,
        deviceLocationSource = object : DeviceLocationSource {
            override suspend fun currentLocation() = GpsResult.Unavailable
        },
        reverseGeocoder = object : ReverseGeocoder {
            override suspend fun reverse(point: net.droopia.hluweather.data.model.GeoPoint): String? = null
        }
    )

    private class TestLocationRepository : LocationRepository {
        override val locations = MutableStateFlow(emptyList<WeatherLocation>())
        override val activeLocation = MutableStateFlow<ActiveLocation?>(null)
        override val locationMode = MutableStateFlow(LocationMode.SAVED_LOCATION)
        val added = mutableListOf<WeatherLocation>()

        override suspend fun add(location: WeatherLocation) {
            added += location
        }

        override suspend fun update(location: WeatherLocation) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun selectSaved(id: String) = Unit
        override suspend fun setTrackMe(enabled: Boolean) = Unit
    }
}
