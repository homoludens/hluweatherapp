package net.droopia.hluweather.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.time.Instant
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.WeatherLocation
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.overlay.include
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.minutes

const val OPEN_FREE_MAP_LIBERTY_STYLE = "https://tiles.openfreemap.org/styles/liberty"
const val OPEN_FREE_MAP_DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"
private const val MAP_DEFAULT_LATITUDE = 44.2380
private const val MAP_DEFAULT_LONGITUDE = 21.1970
private const val MAP_MINIMUM_SPAN_DEGREES = 0.005
private const val MAP_PADDING_FACTOR = 1.2
private const val MAP_MAX_ZOOM = 15.0
private const val MAP_FIT_ZOOM_OUT_STEPS = 1.0

data class WeatherMapMarker(
    val location: WeatherLocation,
    val isActive: Boolean
)

data class WeatherMapViewport(
    val center: GeoPoint,
    val zoom: Double
)

private data class WeatherMapLongitudeBounds(
    val west: Double,
    val east: Double,
    val center: Double,
    val span: Double
)

fun mapStyleUrl(darkTheme: Boolean): String =
    if (darkTheme) OPEN_FREE_MAP_DARK_STYLE else OPEN_FREE_MAP_LIBERTY_STYLE

fun weatherMapMarkers(
    locations: List<WeatherLocation>,
    activeLocationId: String?
): List<WeatherMapMarker> = locations.map { location ->
    WeatherMapMarker(location, location.id == activeLocationId)
}

fun weatherMapViewport(
    locations: List<WeatherLocation>,
    fallbackCenter: GeoPoint = GeoPoint(MAP_DEFAULT_LATITUDE, MAP_DEFAULT_LONGITUDE),
    includeFallbackInBounds: Boolean = false
): WeatherMapViewport {
    if (locations.isEmpty()) return WeatherMapViewport(fallbackCenter, zoom = 11.0)

    val points = locations.map { it.toGeoPoint() } +
        if (includeFallbackInBounds) listOf(fallbackCenter) else emptyList()
    val minLatitude = points.minOf(GeoPoint::latitude)
    val maxLatitude = points.maxOf(GeoPoint::latitude)
    val latitudeSpan = (maxLatitude - minLatitude).coerceAtLeast(MAP_MINIMUM_SPAN_DEGREES)
    val longitudeBounds = weatherMapLongitudeBounds(points)
    val longitudeSpan = longitudeBounds.span.coerceAtLeast(MAP_MINIMUM_SPAN_DEGREES)
    val span = maxOf(latitudeSpan, longitudeSpan) * MAP_PADDING_FACTOR
    val zoom = (ln(360.0 / span) / ln(2.0)).coerceIn(0.0, MAP_MAX_ZOOM)

    return WeatherMapViewport(
        center = GeoPoint(
            latitude = (minLatitude + maxLatitude) / 2.0,
            longitude = longitudeBounds.center
        ),
        zoom = zoom
    )
}

internal fun weatherMapBounds(points: List<GeoPoint>): BoundingBox {
    require(points.isNotEmpty())
    val minLatitude = points.minOf(GeoPoint::latitude)
    val maxLatitude = points.maxOf(GeoPoint::latitude)
    val longitudeBounds = weatherMapLongitudeBounds(points)
    val latitudePadding = if (minLatitude == maxLatitude) {
        MAP_MINIMUM_SPAN_DEGREES / 2.0
    } else {
        0.0
    }
    val longitudePadding = if (longitudeBounds.span == 0.0) {
        MAP_MINIMUM_SPAN_DEGREES / 2.0
    } else {
        0.0
    }
    return BoundingBox(
        west = longitudeFrom360(longitudeBounds.west - longitudePadding),
        south = minLatitude - latitudePadding,
        east = longitudeFrom360(longitudeBounds.east + longitudePadding),
        north = maxLatitude + latitudePadding
    )
}

private fun weatherMapLongitudeBounds(points: List<GeoPoint>): WeatherMapLongitudeBounds {
    val longitudes = points.map { longitude ->
        (longitude.longitude % 360.0 + 360.0) % 360.0
    }.sorted()
    var largestGap = -1.0
    var largestGapIndex = 0
    longitudes.indices.forEach { index ->
        val next = if (index == longitudes.lastIndex) {
            longitudes.first() + 360.0
        } else {
            longitudes[index + 1]
        }
        val gap = next - longitudes[index]
        if (gap > largestGap) {
            largestGap = gap
            largestGapIndex = index
        }
    }
    val west360 = longitudes[(largestGapIndex + 1) % longitudes.size]
    val east360 = longitudes[largestGapIndex]
    val span = (east360 - west360 + 360.0) % 360.0
    val center360 = (west360 + span / 2.0) % 360.0
    return WeatherMapLongitudeBounds(
        west = longitudeFrom360(west360),
        east = longitudeFrom360(east360),
        center = longitudeFrom360(center360),
        span = span
    )
}

private fun longitudeFrom360(longitude: Double): Double {
    val normalized = (longitude % 360.0 + 360.0) % 360.0
    return if (normalized > 180.0) normalized - 360.0 else normalized
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

internal fun zoomOutWeatherMapFit(zoom: Double): Double =
    (zoom - MAP_FIT_ZOOM_OUT_STEPS).coerceAtLeast(0.0)

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
    fitLocations: Boolean = false,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    onLocationClick: (WeatherLocation) -> Unit = {},
    onMapClick: (GeoPoint) -> Unit = {},
    onCameraIdle: (GeoPoint) -> Unit = {},
    onRecenterClick: () -> Unit = {}
) {
    val viewport = if (fitLocations) {
        weatherMapViewport(
            locations,
            center ?: GeoPoint(MAP_DEFAULT_LATITUDE, MAP_DEFAULT_LONGITUDE),
            includeFallbackInBounds = activeLocationId == null && center != null
        )
    } else {
        WeatherMapViewport(
            center = center
                ?: locations.firstOrNull { it.id == activeLocationId }?.toGeoPoint()
                ?: locations.firstOrNull()?.toGeoPoint()
                ?: GeoPoint(MAP_DEFAULT_LATITUDE, MAP_DEFAULT_LONGITUDE),
            zoom = 11.0
        )
    }
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(mapStyleUrl(darkTheme)),
        initialCameraPosition = CameraPosition(
            target = viewport.center.toPosition(),
            zoom = viewport.zoom
        )
    )
    val scope = rememberCoroutineScope()
    val markers = remember(locations, activeLocationId) {
        weatherMapMarkers(locations, activeLocationId)
    }

    LaunchedEffect(mapState) {
        snapshotFlow { mapState.isCameraMoving to mapState.cameraMoveReason }.collect { (isMoving, reason) ->
            if (shouldReportWeatherMapCameraIdle(reason, isMoving)) {
                val position = mapState.cameraPosition.target
                onCameraIdle(GeoPoint(position.latitude, position.longitude))
            }
        }
    }

    LaunchedEffect(center) {
        if (fitLocations) return@LaunchedEffect
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

    LaunchedEffect(locations, fitLocations, center, activeLocationId) {
        if (fitLocations) {
            val points = locations.map { it.toGeoPoint() } +
                if (activeLocationId == null && center != null) listOf(center) else emptyList()
            if (points.isNotEmpty()) {
                mapState.animateCameraToBounds(
                    weatherMapBounds(points),
                    padding = PaddingValues(32.dp)
                )
                val fittedCamera = mapState.cameraPosition
                mapState.animateCameraPosition(
                    CameraPosition(
                        target = fittedCamera.target,
                        zoom = zoomOutWeatherMapFit(fittedCamera.zoom)
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
                ) { }
            }
            WeatherMapRecenterButton(
                onClick = {
                    recenterWeatherMap {
                        scope.launch {
                            mapState.animateCameraPosition(
                                CameraPosition(
                                    target = viewport.center.toPosition(),
                                    zoom = viewport.zoom
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

internal fun shouldReportWeatherMapCameraIdle(
    reason: CameraMoveReason,
    cameraIsMoving: Boolean = false
): Boolean = reason == CameraMoveReason.GESTURE && !cameraIsMoving

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
