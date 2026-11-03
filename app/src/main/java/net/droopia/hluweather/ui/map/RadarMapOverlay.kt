package net.droopia.hluweather.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.IOException
import java.net.URI
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarProviderDescriptors
import net.droopia.hluweather.data.network.RadarApiException
import net.droopia.hluweather.data.repository.RadarMetadataState

private val RadarObservedColor = Color(0xFF27835A)

@Composable
internal fun RadarMapOverlay(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    selectedProvider: RadarProvider,
    onProviderChange: (RadarProvider) -> Unit,
    playbackState: RadarPlaybackState,
    metadataState: RadarMetadataState,
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    onFrameSelected: (Long) -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        RadarLayerControls(
            enabled = enabled,
            onEnabledChange = onEnabledChange,
            selectedProvider = selectedProvider,
            onProviderChange = onProviderChange,
            opacity = opacity,
            onOpacityChange = onOpacityChange,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        )

        if (enabled) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                RadarAttribution(
                    provider = playbackState.displayedFrame?.provider ?: selectedProvider
                )
                RadarTimeline(
                    playbackState = playbackState,
                    selectedProvider = selectedProvider,
                    metadataState = metadataState,
                    onFrameSelected = onFrameSelected,
                    onPlayPause = onPlayPause,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RadarLayerControls(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    selectedProvider: RadarProvider,
    onProviderChange: (RadarProvider) -> Unit,
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 3.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconToggleButton(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = "Radar visibility"
                            stateDescription = if (enabled) "On" else "Off"
                        }
                        .testTag("radar_visibility_toggle")
                ) {
                    Icon(
                        imageVector = if (enabled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Radar layer options" }
                        .testTag("radar_layer_options")
                ) {
                    Icon(Icons.Outlined.Layers, contentDescription = null)
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Radar layers", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Radar visibility", modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = onEnabledChange,
                        modifier = Modifier
                            .semantics {
                                contentDescription = "Radar visibility in layer menu"
                                stateDescription = if (enabled) "On" else "Off"
                            }
                            .testTag("radar_layer_visibility")
                    )
                }
                HorizontalDivider()
                Text(
                    text = "Radar provider",
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelLarge
                )
                RadarProviderOption(
                    provider = RadarProvider.LIBREWXR,
                    selectedProvider = selectedProvider,
                    onSelected = onProviderChange,
                    tag = "radar_layer_provider_librewxr"
                )
                RadarProviderOption(
                    provider = RadarProvider.RAINVIEWER,
                    selectedProvider = selectedProvider,
                    onSelected = onProviderChange,
                    tag = "radar_layer_provider_rainviewer"
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Opacity", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${(opacity.coerceIn(0f, 1f) * 100).toInt()}%",
                        modifier = Modifier.testTag("radar_opacity_value"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = opacity.coerceIn(0f, 1f),
                    onValueChange = onOpacityChange,
                    valueRange = 0f..1f,
                    steps = 99,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Radar opacity"
                            stateDescription = "${(opacity.coerceIn(0f, 1f) * 100).toInt()} percent"
                        }
                        .testTag("radar_opacity_slider")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0%", style = MaterialTheme.typography.labelSmall)
                    Text("100%", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun RadarProviderOption(
    provider: RadarProvider,
    selectedProvider: RadarProvider,
    onSelected: (RadarProvider) -> Unit,
    tag: String
) {
    val selected = provider == selectedProvider
    val label = when (provider) {
        RadarProvider.LIBREWXR -> "LibreWXR"
        RadarProvider.RAINVIEWER -> "RainViewer"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onSelected(provider) }
            )
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RadarTimeline(
    playbackState: RadarPlaybackState,
    selectedProvider: RadarProvider,
    metadataState: RadarMetadataState,
    onFrameSelected: (Long) -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val frames = playbackState.frames
    val selectedIndex = frames.indexOfFirst {
        it.timestampSeconds == playbackState.selectedFrame?.timestampSeconds
    }.coerceAtLeast(0)
    val selectedType = playbackState.selectedFrameType
    val badgeColor = when (selectedType) {
        RadarFrameType.OBSERVED -> MaterialTheme.colorScheme.tertiaryContainer
        RadarFrameType.FORECAST -> MaterialTheme.colorScheme.primaryContainer
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    val badgeTextColor = when (selectedType) {
        RadarFrameType.OBSERVED -> MaterialTheme.colorScheme.onTertiaryContainer
        RadarFrameType.FORECAST -> MaterialTheme.colorScheme.onPrimaryContainer
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val displayFailure = playbackState.displayError?.let(::radarDisplayErrorLabel)
    val metadataFailure = metadataState.error?.let(::radarMetadataErrorLabel)
    val status = when {
        displayFailure != null -> displayFailure
        metadataFailure != null -> metadataFailure
        metadataState.isLoading && frames.isEmpty() -> "Loading radar…"
        frames.isEmpty() -> "No radar frames"
        else -> null
    }

    Surface(
        modifier = modifier.testTag("radar_timeline"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (status != null) {
                Text(
                    text = status,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
                        .testTag("radar_status"),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (displayFailure != null || metadataFailure != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    if (selectedType != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor
                        ) {
                            Text(
                                text = buildString {
                                    append(playbackState.selectedFrameTypeLabel.orEmpty())
                                    playbackState.forecastOffsetLabel?.let { append(" $it") }
                                },
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeTextColor
                            )
                        }
                    } else {
                        Text("Radar timeline", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        text = playbackState.requestedFrameTimeLabel ?: "Waiting for radar frames",
                        modifier = Modifier.testTag("radar_requested_time"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onPlayPause,
                    enabled = frames.isNotEmpty(),
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = if (playbackState.isPlaying) {
                                "Pause radar playback"
                            } else {
                                "Play radar playback"
                            }
                        }
                        .testTag("radar_play_pause")
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) {
                            Icons.Outlined.Pause
                        } else {
                            Icons.Outlined.PlayArrow
                        },
                        contentDescription = null
                    )
                }
            }

            if (frames.isNotEmpty()) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    val sliderRange = (frames.lastIndex).coerceAtLeast(1).toFloat()
                    Slider(
                        value = selectedIndex.toFloat().coerceAtMost(sliderRange),
                        onValueChange = { position ->
                            frames.getOrNull(position.toInt())?.let { onFrameSelected(it.timestampSeconds) }
                        },
                        valueRange = 0f..sliderRange,
                        steps = (frames.size - 2).coerceAtLeast(0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Radar timeline"
                                stateDescription = buildString {
                                    append(playbackState.selectedFrameTypeLabel ?: "No frame selected")
                                    playbackState.selectedFrameTimeLabel?.let { append(", $it") }
                                    playbackState.forecastOffsetLabel?.let { append(", $it from latest observation") }
                                }
                            }
                            .testTag("radar_timeline_slider")
                    )

                    val latestObservedIndex = frames.indexOfLast {
                        it.type == RadarFrameType.OBSERVED
                    }
                    if (latestObservedIndex >= 0) {
                        val markerFraction = if (frames.size <= 1) {
                            0f
                        } else {
                            latestObservedIndex.toFloat() / frames.lastIndex.toFloat()
                        }
                        val markerX = 12.dp + (maxWidth - 24.dp) * markerFraction
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = markerX - 1.dp)
                                .width(2.dp)
                                .height(12.dp)
                                .clip(CircleShape)
                                .background(RadarObservedColor)
                                .semantics {
                                    contentDescription = "Latest observed radar frame"
                                    stateDescription = playbackState.latestObservedTimestampSeconds
                                        ?.let { "Frame ${it}" }.orEmpty()
                                }
                                .testTag("radar_latest_observed_marker")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarAttribution(provider: RadarProvider, modifier: Modifier = Modifier) {
    val descriptor = RadarProviderDescriptors[provider]
    val sourceLink = descriptor.sourceLink
    val uriHandler = LocalUriHandler.current
    val safeSourceUrl = safeRadarSourceUrl(sourceLink.url)
    val sourceLabel = buildAnnotatedString {
        if (safeSourceUrl != null) {
            pushLink(
                LinkAnnotation.Url(
                    url = safeSourceUrl,
                    linkInteractionListener = LinkInteractionListener {
                        runCatching { uriHandler.openUri(safeSourceUrl) }
                    }
                )
            )
        }
        append(descriptor.displayName)
        if (safeSourceUrl != null) pop()
    }

    Surface(
        modifier = modifier.testTag("radar_attribution"),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp
    ) {
        Text(
            text = sourceLabel,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics { contentDescription = "Open ${descriptor.displayName} source link" }
                .testTag("radar_attribution_source"),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                textDecoration = TextDecoration.Underline
            ),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun safeRadarSourceUrl(url: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    return url.takeIf {
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }?.let { uri.toASCIIString() }
}

private fun radarMetadataErrorLabel(error: Throwable): String = when {
    error is RadarApiException && error.statusCode == 429 -> "Radar rate limit reached"
    error is IOException -> "Radar network unavailable"
    else -> "Radar unavailable"
}

private fun radarDisplayErrorLabel(message: String): String = when {
    message.contains("429") || message.contains("rate limit", ignoreCase = true) ->
        "Radar rate limit reached"
    message.contains("timed out", ignoreCase = true) || message.contains("timeout", ignoreCase = true) ->
        "Radar loading timed out"
    message.contains("network", ignoreCase = true) || message.contains("connection", ignoreCase = true) ||
        message.contains("http", ignoreCase = true) -> "Radar network unavailable"
    else -> "Radar unavailable"
}
