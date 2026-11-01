package net.droopia.hluweather.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.droopia.hluweather.HluWeatherApplication
import net.droopia.hluweather.data.dateTimeText
import net.droopia.hluweather.data.network.LibreWxrRadarFrame
import net.droopia.hluweather.data.network.LibreWxrRadarFrameType
import net.droopia.hluweather.data.network.LibreWxrRadarMetadata
import net.droopia.hluweather.data.repository.RadarMetadataState
import net.droopia.hluweather.data.repository.RadarRepository
import kotlin.time.Instant

internal const val RADAR_PLAYBACK_INTERVAL_MILLIS = 600L

data class RadarPlaybackState(
    val isVisible: Boolean = false,
    val metadataState: RadarMetadataState = RadarMetadataState(),
    val metadata: LibreWxrRadarMetadata? = null,
    val frames: List<LibreWxrRadarFrame> = emptyList(),
    val selectedFrame: LibreWxrRadarFrame? = null,
    val latestObservedTimestampSeconds: Long? = null,
    val selectedFrameTimeLabel: String? = null,
    val selectedFrameType: LibreWxrRadarFrameType? = null,
    val selectedFrameTypeLabel: String? = null,
    val forecastOffsetSeconds: Long? = null,
    val forecastOffsetLabel: String? = null,
    val isPlaying: Boolean = false
) {
    val observedFrames: List<LibreWxrRadarFrame>
        get() = frames.filter { it.type == LibreWxrRadarFrameType.OBSERVED }

    val forecastFrames: List<LibreWxrRadarFrame>
        get() = frames.filter { it.type == LibreWxrRadarFrameType.FORECAST }

    val hasForecastFrames: Boolean
        get() = forecastFrames.isNotEmpty()
}

/**
 * Owns the main Map radar timeline. The UI must set visibility only while the main Map and radar
 * are displayed (and the screen lifecycle is started); hidden screens do not collect metadata.
 */
class RadarPlaybackViewModel(
    private val repository: RadarRepository,
    private val timeZone: () -> ZoneId = { ZoneId.systemDefault() }
) : ViewModel() {
    private val _state = MutableStateFlow(RadarPlaybackState())
    val state: StateFlow<RadarPlaybackState> = _state.asStateFlow()

    private var metadataJob: Job? = null
    private var playbackJob: Job? = null

    /** Start or stop all radar state work as the main Map/radar becomes visible or hidden. */
    fun setVisible(visible: Boolean) {
        if (_state.value.isVisible == visible) return
        _state.update { it.copy(isVisible = visible) }

        if (visible) {
            metadataJob = viewModelScope.launch {
                repository.state.collect(::acceptMetadataState)
            }
        } else {
            pause()
            metadataJob?.cancel()
            metadataJob = null
        }
    }

    fun selectFrame(timestampSeconds: Long) {
        val current = _state.value
        val frame = current.frames.firstOrNull { it.timestampSeconds == timestampSeconds } ?: return
        _state.value = current.withSelectedFrame(frame)
    }

    fun play() {
        val current = _state.value
        if (!current.isVisible || current.frames.isEmpty() || current.isPlaying) return

        _state.update { it.copy(isPlaying = true) }
        playbackJob = viewModelScope.launch {
            while (true) {
                delay(RADAR_PLAYBACK_INTERVAL_MILLIS)
                advanceFrame()
            }
        }
    }

    fun pause() {
        playbackJob?.cancel()
        playbackJob = null
        _state.update { if (it.isPlaying) it.copy(isPlaying = false) else it }
    }

    override fun onCleared() {
        metadataJob?.cancel()
        playbackJob?.cancel()
        super.onCleared()
    }

    private fun acceptMetadataState(metadataState: RadarMetadataState) {
        val current = _state.value
        val metadata = metadataState.metadata
        if (metadata == null) {
            // A fresh subscription briefly replays the repository's empty initial state. Keep a
            // previous frame selection until cached or refreshed metadata is delivered.
            _state.value = current.copy(metadataState = metadataState)
            return
        }

        val frames = metadata.frames.sortedBy(LibreWxrRadarFrame::timestampSeconds)
        val latestObserved = frames
            .asSequence()
            .filter { it.type == LibreWxrRadarFrameType.OBSERVED }
            .maxByOrNull(LibreWxrRadarFrame::timestampSeconds)
        val retainedSelection = current.selectedFrame?.timestampSeconds?.let { timestamp ->
            frames.firstOrNull { it.timestampSeconds == timestamp }
        }
        val selectedFrame = retainedSelection ?: latestObserved

        _state.value = current.copy(
            metadataState = metadataState,
            metadata = metadata,
            frames = frames,
            latestObservedTimestampSeconds = latestObserved?.timestampSeconds
        ).withSelectedFrame(selectedFrame)

        if (frames.isEmpty()) pause()
    }

    private fun advanceFrame() {
        val current = _state.value
        if (!current.isVisible || !current.isPlaying || current.frames.isEmpty()) {
            pause()
            return
        }

        val currentIndex = current.frames.indexOfFirst {
            it.timestampSeconds == current.selectedFrame?.timestampSeconds
        }
        val nextIndex = if (currentIndex < 0 || currentIndex == current.frames.lastIndex) {
            0
        } else {
            currentIndex + 1
        }
        _state.value = current.withSelectedFrame(current.frames[nextIndex])
    }

    private fun RadarPlaybackState.withSelectedFrame(
        frame: LibreWxrRadarFrame?
    ): RadarPlaybackState {
        val offsetSeconds = if (
            frame?.type == LibreWxrRadarFrameType.FORECAST && latestObservedTimestampSeconds != null
        ) {
            frame.timestampSeconds - latestObservedTimestampSeconds
        } else {
            null
        }
        return copy(
            selectedFrame = frame,
            selectedFrameTimeLabel = frame?.let {
                Instant.fromEpochSeconds(it.timestampSeconds).dateTimeText(timeZone())
            },
            selectedFrameType = frame?.type,
            selectedFrameTypeLabel = when (frame?.type) {
                LibreWxrRadarFrameType.OBSERVED -> "Observed"
                LibreWxrRadarFrameType.FORECAST -> "Forecast"
                null -> null
            },
            forecastOffsetSeconds = offsetSeconds,
            forecastOffsetLabel = offsetSeconds?.formatForecastOffset()
        )
    }

    private fun Long.formatForecastOffset(): String {
        val sign = if (this < 0) "−" else "+"
        val seconds = kotlin.math.abs(this)
        val hours = seconds / 3_600
        val minutes = (seconds % 3_600) / 60
        val remainingSeconds = seconds % 60
        val duration = buildList {
            if (hours > 0) add("$hours hr")
            if (minutes > 0) add("$minutes min")
            if (hours == 0L && minutes == 0L || remainingSeconds > 0) {
                add("$remainingSeconds sec")
            }
        }.joinToString(" ")
        return "$sign$duration"
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[
                    ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
                ] as HluWeatherApplication
                RadarPlaybackViewModel(application.radarRepository)
            }
        }
    }
}
