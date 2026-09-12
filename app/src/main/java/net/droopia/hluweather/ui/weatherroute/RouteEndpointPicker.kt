package net.droopia.hluweather.ui.weatherroute

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.RouteEndpoint
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.repository.PlaceSearchProvider
import net.droopia.hluweather.data.repository.PlaceSearchResult
import net.droopia.hluweather.ui.map.WeatherMap

private val DEFAULT_MAP_POINT = GeoPoint(44.2380, 21.1970)

@Composable
fun RouteEndpointPicker(
    state: WeatherRouteUiState,
    savedLocations: List<WeatherLocation>,
    onSearchQueryChanged: (RouteEndpointSlot, String) -> Unit,
    onSearchProviderChanged: (PlaceSearchProvider) -> Unit,
    onSearchResultSelected: (PlaceSearchResult) -> Unit,
    onSavedLocationSelected: (RouteEndpointSlot, WeatherLocation) -> Unit,
    onCurrentLocationSelected: (RouteEndpointSlot) -> Unit,
    onMapEndpointSelected: (RouteEndpointSlot, RouteEndpoint) -> Unit,
    darkTheme: Boolean = false,
    modifier: Modifier = Modifier,
    mapContent: @Composable (GeoPoint, Boolean, (GeoPoint) -> Unit) -> Unit = { point,
        mapDarkTheme,
        onCameraIdle ->
        WeatherMap(
            center = point,
            darkTheme = mapDarkTheme,
            onCameraIdle = onCameraIdle
        )
    }
) {
    var savedSlot by remember { mutableStateOf<RouteEndpointSlot?>(null) }
    var mapSlot by remember { mutableStateOf<RouteEndpointSlot?>(null) }
    var mapPoint by remember { mutableStateOf(DEFAULT_MAP_POINT) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SearchProviderSelector(
                provider = state.searchProvider,
                onProviderChanged = onSearchProviderChanged,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            EndpointCard(
                slot = RouteEndpointSlot.START,
                endpoint = state.start,
                state = state,
                onSearchQueryChanged = onSearchQueryChanged,
                onSearchResultSelected = onSearchResultSelected,
                onSavedLocationsClick = { savedSlot = RouteEndpointSlot.START },
                onMapPickerClick = {
                    mapPoint = state.start?.point ?: DEFAULT_MAP_POINT
                    mapSlot = RouteEndpointSlot.START
                },
                onCurrentLocationClick = { onCurrentLocationSelected(RouteEndpointSlot.START) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            EndpointCard(
                slot = RouteEndpointSlot.END,
                endpoint = state.end,
                state = state,
                onSearchQueryChanged = onSearchQueryChanged,
                onSearchResultSelected = onSearchResultSelected,
                onSavedLocationsClick = { savedSlot = RouteEndpointSlot.END },
                onMapPickerClick = {
                    mapPoint = state.end?.point ?: DEFAULT_MAP_POINT
                    mapSlot = RouteEndpointSlot.END
                },
                onCurrentLocationClick = {},
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
        }

        savedSlot?.let { slot ->
            SavedLocationOverlay(
                locations = savedLocations,
                onDismiss = { savedSlot = null },
                onLocationSelected = { location ->
                    onSavedLocationSelected(slot, location)
                    savedSlot = null
                }
            )
        }

        mapSlot?.let { slot ->
            MapPickerOverlay(
                point = mapPoint,
                darkTheme = darkTheme,
                mapContent = mapContent,
                onPointChanged = { mapPoint = it },
                onDismiss = { mapSlot = null },
                onConfirm = {
                    onMapEndpointSelected(slot, RouteEndpoint("Selected map point", mapPoint))
                    mapSlot = null
                }
            )
        }
    }
}

@Composable
private fun SearchProviderSelector(
    provider: PlaceSearchProvider,
    onProviderChanged: (PlaceSearchProvider) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Search provider", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Photon",
                modifier = Modifier
                    .testTag("route_search_provider_photon")
                    .clickable { onProviderChanged(PlaceSearchProvider.PHOTON) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = if (provider == PlaceSearchProvider.PHOTON) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = "Open-Meteo",
                modifier = Modifier
                    .testTag("route_search_provider_open_meteo")
                    .clickable { onProviderChanged(PlaceSearchProvider.OPEN_METEO) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = if (provider == PlaceSearchProvider.OPEN_METEO) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun EndpointCard(
    slot: RouteEndpointSlot,
    endpoint: RouteEndpoint?,
    state: WeatherRouteUiState,
    onSearchQueryChanged: (RouteEndpointSlot, String) -> Unit,
    onSearchResultSelected: (PlaceSearchResult) -> Unit,
    onSavedLocationsClick: () -> Unit,
    onMapPickerClick: () -> Unit,
    onCurrentLocationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefix = if (slot == RouteEndpointSlot.START) "route_start" else "route_end"
    val isActiveSearch = state.activeSearchSlot == slot

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (slot == RouteEndpointSlot.START) "Start" else "Destination",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = endpoint?.label ?: "Not selected",
                modifier = Modifier.testTag("${prefix}_label"),
                style = MaterialTheme.typography.bodyLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Saved locations",
                    modifier = Modifier
                        .testTag("${prefix}_saved_locations")
                        .clickable(onClick = onSavedLocationsClick)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Pick on map",
                    modifier = Modifier
                        .testTag("${prefix}_map_picker")
                        .clickable(onClick = onMapPickerClick)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            OutlinedTextField(
                value = if (isActiveSearch) state.searchQuery else "",
                onValueChange = { query -> onSearchQueryChanged(slot, query) },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${prefix}_search")
            )
            if (isActiveSearch && state.searchResults.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.searchResults.forEach { result ->
                        TextButton(
                            onClick = { onSearchResultSelected(result) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(result.label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            if (slot == RouteEndpointSlot.START) {
                Text(
                    text = "Use my location",
                    modifier = Modifier
                        .testTag("route_start_current_location")
                        .clickable(onClick = onCurrentLocationClick)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SavedLocationOverlay(
    locations: List<WeatherLocation>,
    onDismiss: () -> Unit,
    onLocationSelected: (WeatherLocation) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .testTag("route_saved_locations_scrim")
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {})
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                tonalElevation = 6.dp,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Saved locations", style = MaterialTheme.typography.titleLarge)
                    if (locations.isEmpty()) {
                        Text("No saved locations", modifier = Modifier.padding(top = 16.dp))
                    } else {
                        locations.forEach { location ->
                            TextButton(
                                onClick = { onLocationSelected(location) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("route_saved_location_${location.id}")
                            ) {
                                Text(location.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun MapPickerOverlay(
    point: GeoPoint,
    darkTheme: Boolean,
    mapContent: @Composable (GeoPoint, Boolean, (GeoPoint) -> Unit) -> Unit,
    onPointChanged: (GeoPoint) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize().testTag("route_map_picker_overlay")) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    mapContent(point, darkTheme, onPointChanged)
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .testTag("route_map_picker_center_marker"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Text(
                        text = "Use this point",
                        modifier = Modifier
                            .testTag("route_map_picker_confirm")
                            .clickable(onClick = onConfirm)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
