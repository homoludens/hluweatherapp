package net.droopia.hluweather.ui.weatherroute

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherRouteResult
import net.droopia.hluweather.data.weatherroute.WeatherSeverity
import net.droopia.hluweather.data.weatherroute.displayRouteSampleIndices
import net.droopia.hluweather.data.weatherroute.weatherSeverity
import net.droopia.hluweather.ui.map.mapStyleUrl
import net.droopia.hluweather.ui.components.HluWeatherIcon
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.overlay.include
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.geojson.toJson

data class WeatherRouteMarker(
    val sampleIndex: Int,
    val point: GeoPoint,
    val severity: WeatherSeverity,
    val color: Color,
    val isSelected: Boolean,
    val condition: WeatherCondition? = null,
    val isDay: Boolean? = null
)

fun weatherRouteMarkers(
    result: WeatherRouteResult,
    selectedSampleIndex: Int?
): List<WeatherRouteMarker> = displayRouteSampleIndices(result.samples, result.departure).map { index ->
    val sample = result.samples[index]
    val severity = weatherSeverity(sample.condition)
    WeatherRouteMarker(
        sampleIndex = index,
        point = sample.point,
        condition = sample.condition,
        isDay = sample.isDay,
        severity = severity,
        color = weatherRouteMarkerColor(severity),
        isSelected = index == selectedSampleIndex
    )
}

fun weatherRouteMarkerColor(severity: WeatherSeverity): Color = when (severity) {
    WeatherSeverity.FAVORABLE -> Color(0xFF2E7D32)
    WeatherSeverity.CAUTION -> Color(0xFFF9A825)
    WeatherSeverity.ADVERSE -> Color(0xFFEF6C00)
    WeatherSeverity.SEVERE -> Color(0xFFC62828)
    WeatherSeverity.UNAVAILABLE -> Color(0xFF7A808A)
}

fun routeLineGeoJson(polyline: List<GeoPoint>): String =
    LineString(polyline.map { Position(longitude = it.longitude, latitude = it.latitude) }).toJson()

internal fun routeMapBounds(polyline: List<GeoPoint>): BoundingBox = BoundingBox(
    west = polyline.minOf { it.longitude },
    south = polyline.minOf { it.latitude },
    east = polyline.maxOf { it.longitude },
    north = polyline.maxOf { it.latitude }
)

internal fun routeMapSelectedPoint(
    result: WeatherRouteResult,
    selectedSampleIndex: Int?
): GeoPoint? = result.samples.getOrNull(selectedSampleIndex ?: -1)?.point

@Composable
fun WeatherRouteMap(
    result: WeatherRouteResult,
    selectedSampleIndex: Int? = null,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    onSampleSelected: (Int) -> Unit = {}
) {
    if (LocalInspectionMode.current) {
        Box(modifier = modifier.fillMaxSize().testTag("weather_route_map"))
        return
    }

    val initialPoint = result.route.polyline.firstOrNull() ?: result.start.point
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(
            mapStyleUrl(darkTheme)
        ),
        initialCameraPosition = CameraPosition(
            target = initialPoint.toPosition(),
            zoom = 10.0
        )
    ) {
        val routeSource = rememberGeoJsonSource(
            GeoJsonData.JsonString(routeLineGeoJson(result.route.polyline))
        )
        LineLayer(
            id = "weather_route_line",
            source = routeSource,
            color = const(MaterialTheme.colorScheme.primary),
            width = const(5.dp),
            cap = const(LineCap.Round),
            join = const(LineJoin.Round)
        )
    }
    LaunchedEffect(mapState, result.route.polyline) {
        if (result.route.polyline.size >= 2) {
            mapState.fitCameraToBounds(
                routeMapBounds(result.route.polyline),
                padding = androidx.compose.foundation.layout.PaddingValues(48.dp)
            )
        }
    }

    LaunchedEffect(selectedSampleIndex) {
        routeMapSelectedPoint(result, selectedSampleIndex)?.let { point ->
            mapState.animateCameraPosition(
                CameraPosition(
                    target = point.toPosition(),
                    zoom = mapState.cameraPosition.zoom.coerceAtLeast(12.0)
                )
            )
        }
    }

    Box(modifier = modifier.testTag("weather_route_map")) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            state = mapState
        ) {
            include(MapOverlay.Default)

            RouteEndpointMarker(
                label = "Start",
                text = "S",
                modifier = Modifier
                    .placedAt(result.start.point.toPosition(), Alignment.BottomCenter)
                    .testTag("route_weather_start_marker")
            )
            RouteEndpointMarker(
                label = result.end.label,
                text = "D",
                modifier = Modifier
                    .placedAt(result.end.point.toPosition(), Alignment.BottomCenter)
                    .testTag("route_weather_end_marker")
            )
            weatherRouteMarkers(result, selectedSampleIndex).forEach { marker ->
                WeatherRouteSampleMarker(
                    marker = marker,
                    onSelected = onSampleSelected,
                    modifier = Modifier.placedAt(
                        marker.point.toPosition(),
                        Alignment.BottomCenter
                    )
                )
            }
        }

        WeatherRouteMapError(
            showError = mapState.style.loadState is StyleLoadState.Failed,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
internal fun WeatherRouteMapMarkers(
    result: WeatherRouteResult,
    selectedSampleIndex: Int?,
    onSampleSelected: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        weatherRouteMarkers(result, selectedSampleIndex).forEach { marker ->
            WeatherRouteSampleMarker(marker, onSampleSelected)
        }
    }
}

@Composable
private fun WeatherRouteSampleMarker(
    marker: WeatherRouteMarker,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(if (marker.isSelected) 56.dp else 48.dp)
            .border(
                width = if (marker.isSelected) 3.dp else 0.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape
            )
            .clickable(
                role = Role.Button,
                onClickLabel = "Show weather sample ${marker.sampleIndex + 1}"
            ) {
                onSelected(marker.sampleIndex)
            }
            .semantics {
                contentDescription = buildString {
                    append("Weather sample ${marker.sampleIndex + 1}, ${marker.severity}")
                    if (weatherSeverity(marker.condition) == WeatherSeverity.UNAVAILABLE) {
                        append(", Weather unavailable")
                    }
                }
            }
            .testTag("route_weather_marker_${marker.sampleIndex}"),
        shape = CircleShape,
        color = marker.color
    ) {
        Box(contentAlignment = Alignment.Center) {
            HluWeatherIcon(
                condition = marker.condition ?: WeatherCondition.UNKNOWN,
                isDay = marker.isDay,
                modifier = Modifier.size(28.dp).testTag("route_weather_icon_${marker.sampleIndex}")
            )
        }
    }
}

@Composable
private fun RouteEndpointMarker(
    label: String,
    text: String,
    modifier: Modifier
) {
    Surface(
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = label },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
internal fun WeatherRouteMapError(showError: Boolean, modifier: Modifier = Modifier) {
    if (showError) {
        Text(
            text = "Map tiles unavailable",
            modifier = modifier
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .padding(12.dp)
                .testTag("weather_route_map_error"),
            color = MaterialTheme.colorScheme.error
        )
    }
}

private fun GeoPoint.toPosition() = Position(longitude = longitude, latitude = latitude)
