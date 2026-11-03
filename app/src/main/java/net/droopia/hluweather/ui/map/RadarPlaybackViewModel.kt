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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.droopia.hluweather.HluWeatherApplication
import net.droopia.hluweather.data.dateTimeText
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameIdentity
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarMetadata
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarTimelineNeighborhoodPlanner
import net.droopia.hluweather.data.repository.RadarMetadataState
import net.droopia.hluweather.data.repository.RadarRepository
import kotlin.time.Instant

/** Display time after accepted promotion; preparation and renderer events are not clock inputs. */
internal const val RADAR_PLAYBACK_INTERVAL_MILLIS = 600L

data class RadarPlaybackState(
    val isVisible: Boolean = false,
    val selectedProvider: RadarProvider = RadarProvider.LIBREWXR,
    val metadataState: RadarMetadataState = RadarMetadataState(),
    val metadata: RadarMetadata? = null,
    val frames: List<RadarFrame> = emptyList(),
    val requestedFrame: RadarFrame? = null,
    val requestedFrameIdentity: RadarFrameIdentity? = null,
    /** Accepted/submitted by the renderer, not confirmation that native pixels were drawn. */
    val displayedFrame: RadarFrame? = null,
    val displayedFrameIdentity: RadarFrameIdentity? = null,
    val displayedFrameTimeLabel: String? = null,
    val latestObservedTimestampSeconds: Long? = null,
    val requestedFrameTimeLabel: String? = null,
    val requestedFrameType: RadarFrameType? = null,
    val requestedFrameTypeLabel: String? = null,
    val forecastOffsetSeconds: Long? = null,
    val forecastOffsetLabel: String? = null,
    val isPending: Boolean = false,
    val displayError: String? = null,
    /** Changes when the current failed display request is explicitly retried. */
    val displayRetryToken: Long = 0,
    val isPlaying: Boolean = false
) {
    // Compatibility aliases for the existing timeline UI; task 5.1 will wire the distinct state.
    val selectedFrame: RadarFrame? get() = requestedFrame
    val selectedFrameTimeLabel: String? get() = requestedFrameTimeLabel
    val selectedFrameType: RadarFrameType? get() = requestedFrameType
    val selectedFrameTypeLabel: String? get() = requestedFrameTypeLabel

    val observedFrames: List<RadarFrame>
        get() = frames.filter { it.type == RadarFrameType.OBSERVED }

    val forecastFrames: List<RadarFrame>
        get() = frames.filter { it.type == RadarFrameType.FORECAST }

    val hasForecastFrames: Boolean
        get() = forecastFrames.isNotEmpty()
}

/**
 * Owns the main Map radar timeline. The UI sets visibility only while the main Map and radar are
 * displayed; hidden screens do not collect provider metadata.
 */
class RadarPlaybackViewModel(
    private val repositories: Map<RadarProvider, RadarRepository>,
    private val timeZone: () -> ZoneId = { ZoneId.systemDefault() }
) : ViewModel() {
    /** Compatibility for existing callers that only configure the default LibreWXR provider. */
    constructor(
        repository: RadarRepository,
        timeZone: () -> ZoneId = { ZoneId.systemDefault() }
    ) : this(mapOf(RadarProvider.LIBREWXR to repository), timeZone)

    private val _state = MutableStateFlow(RadarPlaybackState())
    val state: StateFlow<RadarPlaybackState> = _state.asStateFlow()

    private var metadataJob: Job? = null
    private var playbackJob: Job? = null
    private var metadataGeneration = 0L

    /** Start or stop all radar state work as the main Map/radar becomes visible or hidden. */
    fun setVisible(visible: Boolean) {
        val current = _state.value
        if (current.isVisible == visible) return

        if (visible) {
            _state.value = current.copy(
                isVisible = true,
                isPending = current.requestedFrameIdentity != null &&
                    current.requestedFrameIdentity != current.displayedFrameIdentity
            )
            startMetadataCollection()
        } else {
            pause()
            cancelMetadataCollection()
            _state.update {
                it.copy(
                    isVisible = false,
                    displayedFrame = null,
                    displayedFrameIdentity = null,
                    displayedFrameTimeLabel = null,
                    isPending = false,
                    displayError = null
                )
            }
        }
    }

    /** Selects a provider and immediately invalidates all state and callbacks owned by the old one. */
    fun selectProvider(provider: RadarProvider) {
        val current = _state.value
        if (current.selectedProvider == provider) return

        pause()
        cancelMetadataCollection()
        _state.value = RadarPlaybackState(
            isVisible = current.isVisible,
            selectedProvider = provider
        )
        if (current.isVisible) startMetadataCollection()
    }

    /** Manual timeline selection always pauses playback, even when reselecting the same frame. */
    fun selectFrame(timestampSeconds: Long) {
        pause()
        val current = _state.value
        val frame = current.frames.firstOrNull { it.timestampSeconds == timestampSeconds } ?: return
        setRequestedFrame(frame, retrySameRequest = true)
    }

    /** Identity-based selection for callers that already hold a provider/frame identity. */
    fun selectFrame(identity: RadarFrameIdentity) {
        pause()
        val current = _state.value
        if (identity.provider != current.selectedProvider) return
        val frame = current.frames.firstOrNull {
            current.metadata?.let { metadata -> it.identity(metadata.host) == identity } == true
        } ?: return
        setRequestedFrame(frame, retrySameRequest = true)
    }

    /**
     * Accept renderer state only for the exact currently requested provider/frame identity. The
     * renderer may report the old same-provider front while a replacement is pending. In the
     * default renderer, displayed means the guarded promotion was accepted, not that pixels drew.
     */
    fun onDisplayChanged(display: RadarDisplayState) {
        val current = _state.value
        val requested = current.requestedFrameIdentity ?: return
        if (!current.isVisible || requested.provider != current.selectedProvider ||
            display.requested != requested
        ) return

        val displayedIdentity = display.displayed?.takeIf {
            it.provider == current.selectedProvider
        }
        val displayedFrame = displayedIdentity?.let { identity ->
            current.frames.firstOrNull { frame ->
                current.metadata?.let { metadata -> frame.identity(metadata.host) == identity } == true
            } ?: current.displayedFrame?.takeIf { current.displayedFrameIdentity == identity }
                ?: identity.toFrame()
        }
        val error = display.error
        _state.value = current.copy(
            displayedFrame = displayedFrame,
            displayedFrameIdentity = displayedIdentity,
            displayedFrameTimeLabel = displayedFrame?.let {
                Instant.fromEpochSeconds(it.timestampSeconds).dateTimeText(timeZone())
            },
            isPending = error == null && (display.pending || display.displayed != requested),
            displayError = error
        )
        if (error != null) pause()
    }

    fun play() {
        val current = _state.value
        if (!current.isVisible || current.requestedFrameIdentity == null || current.isPlaying) return

        _state.value = current.copy(
            isPlaying = true,
            isPending = current.isPending || current.requestedFrameIdentity != current.displayedFrameIdentity ||
                current.displayError != null,
            displayError = null,
            displayRetryToken = current.displayRetryToken + if (current.displayError != null) 1 else 0
        )
        playbackJob = viewModelScope.launch { playbackLoop() }
    }

    fun pause() {
        playbackJob?.cancel()
        playbackJob = null
        _state.update { if (it.isPlaying) it.copy(isPlaying = false) else it }
    }

    override fun onCleared() {
        metadataGeneration++
        metadataJob?.cancel()
        playbackJob?.cancel()
        super.onCleared()
    }

    private fun startMetadataCollection() {
        cancelMetadataCollection()
        val provider = _state.value.selectedProvider
        val generation = metadataGeneration
        val repository = repositories[provider] ?: return
        metadataJob = viewModelScope.launch {
            repository.state.collect { metadataState ->
                acceptMetadataState(provider, generation, metadataState)
            }
        }
    }

    private fun cancelMetadataCollection() {
        metadataGeneration++
        metadataJob?.cancel()
        metadataJob = null
    }

    private fun acceptMetadataState(
        provider: RadarProvider,
        generation: Long,
        metadataState: RadarMetadataState
    ) {
        val current = _state.value
        if (!current.isVisible || current.selectedProvider != provider ||
            metadataGeneration != generation
        ) return

        val metadata = metadataState.metadata
        if (metadata == null) {
            _state.value = current.copy(metadataState = metadataState)
            return
        }
        if (metadata.provider != provider || metadata.frames.any { it.provider != provider }) return

        val frames = RadarTimelineNeighborhoodPlanner.orderedFrames(metadata)
        val latestObserved = frames
            .asSequence()
            .filter { it.type == RadarFrameType.OBSERVED }
            .maxByOrNull(RadarFrame::timestampSeconds)
        val retainedSelection = current.requestedFrame?.let { previous ->
            frames.firstOrNull {
                it.timestampSeconds == previous.timestampSeconds && it.type == previous.type
            }
        }
        val requestedFrame = retainedSelection ?: latestObserved
        val requestedIdentity = requestedFrame?.identity(metadata.host)
        val requestChanged = requestedIdentity != current.requestedFrameIdentity

        _state.value = current.copy(
            metadataState = metadataState,
            metadata = metadata,
            frames = frames,
            latestObservedTimestampSeconds = latestObserved?.timestampSeconds
        ).withRequestedFrame(
            requestedFrame,
            clearError = requestChanged,
            forcePending = requestChanged
        )

        if (frames.isEmpty()) pause()
    }

    private suspend fun playbackLoop() {
        var requestedIdentity = _state.value.requestedFrameIdentity ?: return
        while (true) {
            val readyState = awaitDisplayed(requestedIdentity) ?: return
            val readyIdentity = readyState.requestedFrameIdentity ?: return
            if (readyIdentity != requestedIdentity || readyState.isPending ||
                readyState.displayedFrameIdentity != readyIdentity
            ) {
                requestedIdentity = readyIdentity
                continue
            }
            requestedIdentity = readyIdentity
            // Only an exact accepted display starts this single interval. Optional preparation
            // and native render diagnostics deliberately do not restart or postpone it.
            delay(RADAR_PLAYBACK_INTERVAL_MILLIS)

            val current = _state.value
            if (!current.isVisible || !current.isPlaying) return
            val next = current.requestedFrameIdentity ?: return
            if (next != requestedIdentity || current.displayError != null || current.isPending) {
                continue
            }

            val currentIndex = current.frames.indexOfFirst { frame ->
                current.metadata?.let { metadata -> frame.identity(metadata.host) == requestedIdentity } == true
            }
            val nextIndex = if (currentIndex < 0 || currentIndex == current.frames.lastIndex) {
                0
            } else {
                currentIndex + 1
            }
            val nextFrame = current.frames.getOrNull(nextIndex) ?: run {
                pause()
                return
            }
            setRequestedFrame(nextFrame)
            requestedIdentity = _state.value.requestedFrameIdentity ?: return
        }
    }

    /** Returns only when this exact request is accepted or the wait must stop/rebind. */
    private suspend fun awaitDisplayed(identity: RadarFrameIdentity): RadarPlaybackState? {
        val result = state.first { current ->
            !current.isVisible || !current.isPlaying ||
                current.requestedFrameIdentity != identity || current.displayError != null ||
                (current.displayedFrameIdentity == identity && !current.isPending)
        }
        if (!result.isVisible || !result.isPlaying || result.displayError != null) return null
        if (result.requestedFrameIdentity == identity &&
            result.displayedFrameIdentity == identity && !result.isPending
        ) return result
        return result.takeIf { it.requestedFrameIdentity != null }
    }

    private fun setRequestedFrame(
        frame: RadarFrame?,
        retrySameRequest: Boolean = false
    ) {
        val current = _state.value
        val identity = frame?.let { current.metadata?.let { metadata -> it.identity(metadata.host) } }
        val requestChanged = identity != current.requestedFrameIdentity
        val retry = retrySameRequest && !requestChanged && current.displayError != null
        _state.value = current.withRequestedFrame(
            frame,
            clearError = requestChanged || retrySameRequest,
            forcePending = requestChanged || retrySameRequest,
            retryToken = if (retry) current.displayRetryToken + 1 else current.displayRetryToken
        )
    }

    private fun RadarPlaybackState.withRequestedFrame(
        frame: RadarFrame?,
        clearError: Boolean,
        forcePending: Boolean,
        retryToken: Long = displayRetryToken
    ): RadarPlaybackState {
        val identity = frame?.let { metadata?.let { metadata -> it.identity(metadata.host) } }
        val offsetSeconds = if (
            frame?.type == RadarFrameType.FORECAST && latestObservedTimestampSeconds != null
        ) {
            frame.timestampSeconds - latestObservedTimestampSeconds
        } else {
            null
        }
        return copy(
            requestedFrame = frame,
            requestedFrameIdentity = identity,
            requestedFrameTimeLabel = frame?.let {
                Instant.fromEpochSeconds(it.timestampSeconds).dateTimeText(timeZone())
            },
            requestedFrameType = frame?.type,
            requestedFrameTypeLabel = when (frame?.type) {
                RadarFrameType.OBSERVED -> "Observed"
                RadarFrameType.FORECAST -> "Forecast"
                null -> null
            },
            forecastOffsetSeconds = offsetSeconds,
            forecastOffsetLabel = offsetSeconds?.formatForecastOffset(),
            isPending = if (forcePending) {
                isVisible && identity != null &&
                    (identity != requestedFrameIdentity || identity != displayedFrameIdentity ||
                        isPending || retryToken != displayRetryToken)
            } else {
                isPending
            },
            displayError = if (clearError) null else displayError,
            displayRetryToken = retryToken
        )
    }

    private fun RadarFrameIdentity.toFrame() = RadarFrame(
        timestampSeconds = timestampSeconds,
        path = path,
        type = type,
        provider = provider
    )

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
                RadarPlaybackViewModel(application.radarRepositories)
            }
        }
    }
}
