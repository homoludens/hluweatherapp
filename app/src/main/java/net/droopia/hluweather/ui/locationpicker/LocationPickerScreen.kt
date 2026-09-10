package net.droopia.hluweather.ui.locationpicker

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.filterNotNull
import net.droopia.hluweather.data.model.GeoPoint
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    viewModel: LocationPickerViewModel,
    onBackClick: () -> Unit,
    onSaved: () -> Unit = onBackClick,
    onDeleted: () -> Unit = onBackClick,
    modifier: Modifier = Modifier,
    mapContent: @Composable (GeoPoint, (GeoPoint) -> Unit) -> Unit = { point, _ ->
        LocationPickerMap(point)
    }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.onPermissionResult(
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        )
    }

    LaunchedEffect(state.gpsStatus) {
        if (state.gpsStatus == GpsStatus.PermissionRequired) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.completion.filterNotNull().collect { completion ->
            when (completion) {
                LocationPickerEvent.Saved -> onSaved()
                LocationPickerEvent.Deleted -> onDeleted()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Location picker") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .navigationBarsPadding()
                .testTag("location_picker_scroll")
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                mapContent(state.point, viewModel::onCameraIdle)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::onNameChanged,
                        label = { Text("Location name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("location_picker_name"),
                        singleLine = true
                    )
                    Text(
                        text = coordinateText(state.point),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("location_picker_coordinates")
                    )
                    state.altitude?.let { altitude ->
                        Text("Altitude: ${altitude} m", style = MaterialTheme.typography.bodyMedium)
                    }
                    when (state.initialization) {
                        LocationPickerInitialization.Ready -> Unit
                        LocationPickerInitialization.Loading ->
                            Text("Loading location...", style = MaterialTheme.typography.bodySmall)
                        LocationPickerInitialization.MissingEditLocation ->
                            Text(
                                "Location not found",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                    }
                    gpsStatusText(state.gpsStatus)?.let { status ->
                        Text(
                            text = status,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = viewModel::onGpsClick) {
                            Icon(Icons.Outlined.MyLocation, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Use my location")
                        }
                        Spacer(Modifier.weight(1f))
                        if (viewModel.isEditMode) {
                            TextButton(onClick = viewModel::delete) {
                                Text("Delete")
                            }
                        }
                        Button(
                            enabled = state.initialization == LocationPickerInitialization.Ready,
                            onClick = viewModel::save
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun LocationPickerMap(
    point: GeoPoint,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("location_picker_map"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Map preview", style = MaterialTheme.typography.titleMedium)
            Text(
                coordinateText(point),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun coordinateText(point: GeoPoint): String =
    "%.4f, %.4f".format(Locale.US, point.latitude, point.longitude)

private fun gpsStatusText(status: GpsStatus): String? = when (status) {
    GpsStatus.Idle -> null
    GpsStatus.Locating -> "Finding your location..."
    GpsStatus.Success -> "Location found"
    GpsStatus.PermissionRequired -> "Location permission is required"
    GpsStatus.LocationDisabled -> "Location is disabled"
    GpsStatus.Unavailable -> "Location unavailable"
}
