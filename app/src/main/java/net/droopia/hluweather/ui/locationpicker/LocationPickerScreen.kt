package net.droopia.hluweather.ui.locationpicker

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.filterNotNull
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.ui.map.WeatherMap
import java.util.Locale

private const val NAME_SPINNER_DURATION_MILLIS = 900

internal fun nameSpinnerRotation(elapsedMillis: Long): Float =
    (elapsedMillis % NAME_SPINNER_DURATION_MILLIS) * 360f / NAME_SPINNER_DURATION_MILLIS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    viewModel: LocationPickerViewModel,
    onBackClick: () -> Unit,
    onSaved: () -> Unit = onBackClick,
    onDeleted: () -> Unit = onBackClick,
    darkTheme: Boolean = false,
    modifier: Modifier = Modifier,
    mapContent: @Composable (GeoPoint, Boolean, (GeoPoint) -> Unit) -> Unit = { point, mapDarkTheme, onCameraIdle ->
        WeatherMap(
            center = point,
            darkTheme = mapDarkTheme,
            onCameraIdle = onCameraIdle,
            onRecenterClick = viewModel::onGpsClick
        )
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
                mapContent(state.point, darkTheme, viewModel::onCameraIdle)
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .testTag("location_picker_center_marker"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = MaterialTheme.colorScheme.onPrimary)
                }
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
                    if (state.isNameLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LocationNameSpinner(
                                modifier = Modifier
                                    .size(18.dp)
                                    .testTag("location_name_loading")
                            )
                            Text("Finding location name...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
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
                            TextButton(onClick = { viewModel.delete() }) {
                                Text("Delete")
                            }
                        }
                        Button(
                            enabled = state.initialization == LocationPickerInitialization.Ready &&
                                !state.isNameLoading,
                            onClick = { viewModel.save() }
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
private fun LocationNameSpinner(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "location_name_spinner")
    val rotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(NAME_SPINNER_DURATION_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "location_name_spinner_rotation"
    )
    val color = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier.semantics {
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }
    ) {
        drawArc(
            color = color,
            startAngle = rotation.value,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(size.minDimension * 0.12f, cap = StrokeCap.Round)
        )
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
