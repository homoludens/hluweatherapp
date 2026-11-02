package net.droopia.hluweather.data.model

/** Plans up to two adjacent frames on either side of the requested timeline frame. */
object RadarTimelineNeighborhoodPlanner {
    fun plan(
        metadata: RadarMetadata,
        requestedFrame: RadarFrameIdentity
    ): List<RadarFrame> {
        if (requestedFrame.provider != metadata.provider) return emptyList()

        val timeline = metadata.frames.asSequence()
            .filter { it.provider == metadata.provider }
            .distinctBy { it.identity(metadata.host) }
            .sortedWith(
                compareBy<RadarFrame> { it.timestampSeconds }
                    .thenBy { it.type.ordinal }
                    .thenBy { it.path }
            )
            .toList()
        val requestedIndex = timeline.indexOfFirst {
            it.identity(metadata.host) == requestedFrame
        }
        if (requestedIndex < 0) return emptyList()

        return buildList {
            for (distance in 1..2) {
                val before = requestedIndex - distance
                if (before >= 0) add(timeline[before])

                val after = requestedIndex + distance
                if (after < timeline.size) add(timeline[after])
            }
        }.filterNot { it.identity(metadata.host) == requestedFrame }
    }
}
