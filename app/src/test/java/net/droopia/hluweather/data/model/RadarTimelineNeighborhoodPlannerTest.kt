package net.droopia.hluweather.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RadarTimelineNeighborhoodPlannerTest {
    @Test
    fun empty_timeline_and_missing_requested_frame_have_no_neighbors() {
        val requested = frame(200)

        assertEquals(emptyList<RadarFrame>(), plan(emptyList(), requested))
        assertEquals(emptyList<RadarFrame>(), plan(listOf(frame(100), frame(300)), requested))
    }

    @Test
    fun single_frame_timeline_has_no_neighbors() {
        val requested = frame(100)

        assertEquals(emptyList<RadarFrame>(), plan(listOf(requested), requested))
    }

    @Test
    fun short_timelines_return_only_available_adjacent_frames() {
        val twoFrames = listOf(frame(100), frame(200))
        assertEquals(listOf(twoFrames[1]), plan(twoFrames, twoFrames[0]))
        assertEquals(listOf(twoFrames[0]), plan(twoFrames, twoFrames[1]))

        val threeFrames = listOf(frame(100), frame(200), frame(300))
        assertEquals(
            listOf(threeFrames[0], threeFrames[2]),
            plan(threeFrames, threeFrames[1])
        )
    }

    @Test
    fun endpoints_clamp_without_wrapping_and_middle_orders_by_step_distance() {
        val frames = (1L..5L).map { frame(it * 100) }

        assertEquals(listOf(frames[1], frames[2]), plan(frames, frames.first()))
        assertEquals(
            listOf(frames[3], frames[2]),
            plan(frames, frames.last())
        )
        assertEquals(
            listOf(frames[1], frames[3], frames[0], frames[4]),
            plan(frames, frames[2])
        )
    }

    @Test
    fun irregular_timestamps_use_chronological_indices_not_time_offsets() {
        val chronological = listOf(10L, 11L, 100L, 1_000L, 10_000L).map(::frame)
        val unorderedMetadata = listOf(
            chronological[4], chronological[2], chronological[0], chronological[3], chronological[1]
        )

        assertEquals(
            listOf(chronological[1], chronological[3], chronological[0], chronological[4]),
            plan(unorderedMetadata, chronological[2])
        )
    }

    @Test
    fun duplicate_frames_are_deduplicated_and_provider_identity_avoids_timestamp_collisions() {
        val before = frame(100, provider = RadarProvider.RAINVIEWER)
        val requested = frame(200, provider = RadarProvider.RAINVIEWER)
        val after = frame(300, provider = RadarProvider.RAINVIEWER)
        val otherProviderSameTimestamp = requested.copy(provider = RadarProvider.LIBREWXR)
        val metadata = metadata(
            listOf(before, requested, requested, after, before, otherProviderSameTimestamp),
            provider = RadarProvider.RAINVIEWER
        )

        assertEquals(
            listOf(before, after),
            RadarTimelineNeighborhoodPlanner.plan(metadata, requested.identity(host))
        )
        assertEquals(
            emptyList<RadarFrame>(),
            RadarTimelineNeighborhoodPlanner.plan(metadata, otherProviderSameTimestamp.identity(host))
        )
    }

    @Test
    fun metadata_window_refresh_replans_around_the_requested_identity() {
        val requested = frame(300)
        val initial = metadata(listOf(frame(100), frame(200), requested))
        val refreshed = metadata(
            listOf(frame(0), frame(100), frame(200), requested, frame(400), frame(500))
        )

        assertEquals(
            listOf(frame(200), frame(100)),
            RadarTimelineNeighborhoodPlanner.plan(initial, requested.identity(host))
        )
        assertEquals(
            listOf(frame(200), frame(400), frame(100), frame(500)),
            RadarTimelineNeighborhoodPlanner.plan(refreshed, requested.identity(host))
        )
    }

    private fun plan(frames: List<RadarFrame>, requested: RadarFrame) =
        RadarTimelineNeighborhoodPlanner.plan(metadata(frames), requested.identity(host))

    private fun metadata(
        frames: List<RadarFrame>,
        provider: RadarProvider = RadarProvider.LIBREWXR
    ) = RadarMetadata(host = host, frames = frames, provider = provider)

    private fun frame(
        timestamp: Long,
        provider: RadarProvider = RadarProvider.LIBREWXR
    ) = RadarFrame(
        timestampSeconds = timestamp,
        path = "/radar/$timestamp",
        type = RadarFrameType.OBSERVED,
        provider = provider
    )

    private companion object {
        const val host = "https://tiles.example.test"
    }
}
