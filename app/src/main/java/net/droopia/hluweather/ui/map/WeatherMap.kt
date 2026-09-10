package net.droopia.hluweather.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.WeatherLocation
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.map.MapEvent
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.overlay.include
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.minutes

const val OPEN_FREE_MAP_LIBERTY_STYLE = "https://tiles.openfreemap.org/styles/liberty"
const val OPEN_FREE_MAP_DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"
private const val MAP_DEFAULT_LATITUDE = 44.2380
private const val MAP_DEFAULT_LONGITUDE = 21.1970

data class WeatherMapMarker(
    val location: WeatherLocation,
    val isActive: Boolean
)

fun mapStyleUrl(darkTheme: Boolean): String =
    if (darkTheme) OPEN_FREE_MAP_DARK_STYLE else OPEN_FREE_MAP_LIBERTY_STYLE

fun weatherMapMarkers(
    locations: List<WeatherLocation>,
    activeLocationId: String?
): List<WeatherMapMarker> = locations.map { location ->
    WeatherMapMarker(location, location.id == activeLocationId)
}

fun selectWeatherMapLocation(
    locations: List<WeatherLocation>,
    locationId: String,
    onSelected: (WeatherLocation) -> Unit
) {
    locations.firstOrNull { it.id == locationId }?.let(onSelected)
}

fun recenterWeatherMap(onRecenter: () -> Unit) {
    onRecenter()
}

fun shouldAnimateWeatherMapCenter(current: GeoPoint, requested: GeoPoint): Boolean =
    current != requested

fun shouldRefresh(
    lastPoint: GeoPoint?,
    currentPoint: GeoPoint,
    lastFetch: Instant?,
    now: Instant
): Boolean = lastPoint == null ||
    distanceMeters(lastPoint, currentPoint) >= 5_000.0 ||
    lastFetch == null ||
    now - lastFetch >= 30.minutes

@Composable
fun WeatherMap(
    locations: List<WeatherLocation> = emptyList(),
    activeLocationId: String? = null,
    center: GeoPoint? = null,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    onLocationClick: (WeatherLocation) -> Unit = {},
    onMapClick: (GeoPoint) -> Unit = {},
    onCameraIdle: (GeoPoint) -> Unit = {},
    onRecenterClick: () -> Unit = {}
) {
    val initialCenter = center
        ?: locations.firstOrNull { it.id == activeLocationId }?.toGeoPoint()
        ?: locations.firstOrNull()?.toGeoPoint()
        ?: GeoPoint(MAP_DEFAULT_LATITUDE, MAP_DEFAULT_LONGITUDE)
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(mapStyleUrl(darkTheme)),
        initialCameraPosition = CameraPosition(
            target = initialCenter.toPosition(),
            zoom = 11.0
        )
    )
    val scope = rememberCoroutineScope()
    val markers = remember(locations, activeLocationId) {
        weatherMapMarkers(locations, activeLocationId)
    }

    LaunchedEffect(mapState) {
        mapState.events
            .filterIsInstance<MapEvent.Idle>()
            .collect {
                val position = mapState.cameraPosition.target
                onCameraIdle(GeoPoint(position.latitude, position.longitude))
            }
    }

    LaunchedEffect(center) {
        center?.let { point ->
            val current = mapState.cameraPosition.target
            val currentPoint = GeoPoint(current.latitude, current.longitude)
            if (shouldAnimateWeatherMapCenter(currentPoint, point)) {
                mapState.animateCameraPosition(
                    CameraPosition(
                        target = point.toPosition(),
                        zoom = mapState.cameraPosition.zoom
                    )
                )
            }
        }
    }

    Box(modifier = modifier.testTag("weather_map")) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            state = mapState,
            interactions = MapInteractions {
                callbacks {
                    click {
                        onEvent { event ->
                            event.position?.let { position ->
                                onMapClick(GeoPoint(position.latitude, position.longitude))
                            }
                            ClickResult.Consume
                        }
                    }
                }
            }
        ) {
            include(MapOverlay.Default)
            markers.forEach { marker ->
                Surface(
                    modifier = Modifier
                        .placedAt(marker.location.toGeoPoint().toPosition(), Alignment.BottomCenter)
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                            onClickLabel = "Show weather for ${marker.location.name}",
                            role = Role.Button
                        ) {
                            selectWeatherMapLocation(locations, marker.location.id, onLocationClick)
                        }
                        .semantics {
                            contentDescription = "Weather for ${marker.location.name}"
                        }
                        .testTag("weather_map_marker_${marker.location.id}"),
                    shape = CircleShape,
                    color = if (marker.isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                ) {
                    Text(
                        text = marker.location.name.take(1).uppercase(),
                        modifier = Modifier.align(Alignment.Center),
                        color = if (marker.isActive) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
            }
            WeatherMapRecenterButton(
                onClick = {
                    recenterWeatherMap {
                        scope.launch {
                            mapState.animateCameraPosition(
                                CameraPosition(
                                    target = initialCenter.toPosition(),
                                    zoom = mapState.cameraPosition.zoom
                                )
                            )
                        }
                        onRecenterClick()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
            )
        }

        val loadState = mapState.style.loadState
        if (loadState is StyleLoadState.Failed) {
            Text(
                text = "Map tiles unavailable",
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(12.dp)
                    .testTag("weather_map_error"),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
internal fun WeatherMapRecenterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                contentDescription = "Recenter map"
            }
            .testTag("weather_map_recenter")
    ) {
        Icon(Icons.Outlined.MyLocation, contentDescription = null)
    }
}

private fun WeatherLocation.toGeoPoint() = GeoPoint(latitude, longitude)

private fun GeoPoint.toPosition() = Position(longitude = longitude, latitude = latitude)

private fun distanceMeters(first: GeoPoint, second: GeoPoint): Double {
    val earthRadiusMeters = 6_371_000.0
    val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
    val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
    val firstLatitude = Math.toRadians(first.latitude)
    val secondLatitude = Math.toRadians(second.latitude)
    val haversine = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
        cos(firstLatitude) * cos(secondLatitude) *
        sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
    return earthRadiusMeters * 2 * asin(sqrt(haversine))
}
