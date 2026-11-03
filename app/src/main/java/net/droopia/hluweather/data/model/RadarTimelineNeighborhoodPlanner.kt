package net.droopia.hluweather.data.model

/** Plans the immediately adjacent frame on either side of the requested timeline frame. */
object RadarTimelineNeighborhoodPlanner {
    /** Canonical order shared by playback and adjacent-frame planning. */
    fun orderedFrames(metadata: RadarMetadata): List<RadarFrame> = metadata.frames.asSequence()
        .filter { it.provider == metadata.provider }
        .distinctBy { it.identity(metadata.host) }
        .sortedWith(
            compareBy<RadarFrame> { it.timestampSeconds }
                .thenBy { it.type.ordinal }
                .thenBy { it.path }
        )
        .toList()

    /** The next chronological frame, without wrapping at the end of the timeline. */
    fun nextFrame(
        metadata: RadarMetadata,
        requestedFrame: RadarFrameIdentity
    ): RadarFrame? {
        if (requestedFrame.provider != metadata.provider) return null
        val timeline = orderedFrames(metadata)
        val requestedIndex = timeline.indexOfFirst { it.identity(metadata.host) == requestedFrame }
        if (requestedIndex < 0) return null
        return timeline.getOrNull(requestedIndex + 1)
    }

    fun plan(
        metadata: RadarMetadata,
        requestedFrame: RadarFrameIdentity
    ): List<RadarFrame> {
        if (requestedFrame.provider != metadata.provider) return emptyList()

        val timeline = orderedFrames(metadata)
        val requestedIndex = timeline.indexOfFirst {
            it.identity(metadata.host) == requestedFrame
        }
        if (requestedIndex < 0) return emptyList()

        return buildList {
            timeline.getOrNull(requestedIndex - 1)?.let(::add)
            timeline.getOrNull(requestedIndex + 1)?.let(::add)
        }.filterNot { it.identity(metadata.host) == requestedFrame }
    }
}
