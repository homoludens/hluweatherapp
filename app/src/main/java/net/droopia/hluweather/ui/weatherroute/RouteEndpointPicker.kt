package net.droopia.hluweather.ui.weatherroute

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
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
import net.droopia.hluweather.data.repository.PlaceSearchResult
import net.droopia.hluweather.ui.map.WeatherMap

private val DEFAULT_MAP_POINT = GeoPoint(44.2380, 21.1970)

@Composable
fun RouteEndpointPicker(
    state: WeatherRouteUiState,
    savedLocations: List<WeatherLocation>,
    slot: RouteEndpointSlot,
    onSearchQueryChanged: (RouteEndpointSlot, String) -> Unit,
    onSearchResultSelected: (PlaceSearchResult) -> Unit,
    onSavedLocationSelected: (RouteEndpointSlot, WeatherLocation) -> Unit,
    onCurrentLocationSelected: (RouteEndpointSlot) -> Unit,
    onMapEndpointSelected: (RouteEndpointSlot, RouteEndpoint) -> Unit,
    darkTheme: Boolean = false,
    showCard: Boolean = true,
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("route_endpoint_picker")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EndpointCard(
                slot = slot,
                endpoint = if (slot == RouteEndpointSlot.START) state.start else state.end,
                state = state,
                onSearchQueryChanged = onSearchQueryChanged,
                onSearchResultSelected = onSearchResultSelected,
                onSavedLocationsClick = { savedSlot = slot },
                onMapPickerClick = {
                    mapPoint = (if (slot == RouteEndpointSlot.START) state.start else state.end)
                        ?.point ?: DEFAULT_MAP_POINT
                    mapSlot = slot
                },
                onCurrentLocationClick = {
                    if (slot == RouteEndpointSlot.START) {
                        onCurrentLocationSelected(slot)
                    }
                },
                showCard = showCard,
                modifier = if (showCard) Modifier.padding(horizontal = 16.dp) else Modifier
            )
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
@OptIn(ExperimentalLayoutApi::class)
private fun EndpointCard(
    slot: RouteEndpointSlot,
    endpoint: RouteEndpoint?,
    state: WeatherRouteUiState,
    onSearchQueryChanged: (RouteEndpointSlot, String) -> Unit,
    onSearchResultSelected: (PlaceSearchResult) -> Unit,
    onSavedLocationsClick: () -> Unit,
    onMapPickerClick: () -> Unit,
    onCurrentLocationClick: () -> Unit,
    showCard: Boolean,
    modifier: Modifier = Modifier
) {
    val prefix = if (slot == RouteEndpointSlot.START) "route_start" else "route_end"
    val isActiveSearch = state.activeSearchSlot == slot

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.testTag("${prefix}_endpoint"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (slot == RouteEndpointSlot.START) "Start" else "Destination",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = endpoint?.label ?: "Not selected",
                        modifier = Modifier.testTag("${prefix}_label"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onSavedLocationsClick,
                    label = { Text("Saved locations") },
                    leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.primary,
                        leadingIconContentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("${prefix}_saved_locations")
                )
                AssistChip(
                    onClick = onMapPickerClick,
                    label = { Text("Pick on map") },
                    leadingIcon = { Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.primary,
                        leadingIconContentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("${prefix}_map_picker")
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
                    state.searchResults.take(5).forEach { result ->
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
                AssistChip(
                    onClick = onCurrentLocationClick,
                    label = { Text("Use my location") },
                    leadingIcon = { Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.primary,
                        leadingIconContentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("route_start_current_location")
                )
            }
        }
    }
    if (showCard) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) { content() }
        }
    } else {
        content()
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
