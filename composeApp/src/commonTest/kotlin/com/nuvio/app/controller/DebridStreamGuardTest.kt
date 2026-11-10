package com.nuvio.app.controller

import com.nuvio.app.features.streams.StreamItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebridStreamGuardTest {

    @Test
    fun `parseRuntimeMinutes parses various duration formats`() {
        assertEquals(120, DebridStreamGuard.parseRuntimeMinutes("120 min"))
        assertEquals(105, DebridStreamGuard.parseRuntimeMinutes("1h 45m"))
        assertEquals(90, DebridStreamGuard.parseRuntimeMinutes("1h 30min"))
        assertEquals(28, DebridStreamGuard.parseRuntimeMinutes("28m"))
        assertEquals(45, DebridStreamGuard.parseRuntimeMinutes("45"))
        assertEquals(null, DebridStreamGuard.parseRuntimeMinutes(null))
        assertEquals(null, DebridStreamGuard.parseRuntimeMinutes(""))
    }

    @Test
    fun `isErrorSlate detects short video on full length title`() {
        val stream = StreamItem(name = "Stream 1", addonName = "Debrid", addonId = "addon:1")
        DebridStreamGuard.initPlaybackSession(
            stream = stream,
            candidates = listOf(stream),
            contentType = "movie",
            seasonNumber = null,
            metadataRuntimeMinutes = 110,
        )

        // 15-second placeholder video
        assertTrue(DebridStreamGuard.isErrorSlate(durationMs = 15_000L))

        // 30-second placeholder video
        assertTrue(DebridStreamGuard.isErrorSlate(durationMs = 30_000L))

        // 60-second placeholder video
        assertTrue(DebridStreamGuard.isErrorSlate(durationMs = 60_000L))

        // Normal movie duration (2 hours = 7,200,000 ms)
        assertFalse(DebridStreamGuard.isErrorSlate(durationMs = 7_200_000L))
    }

    @Test
    fun `isErrorSlate detects error text patterns regardless of duration`() {
        val stream = StreamItem(name = "Stream 1", addonName = "Debrid", addonId = "addon:1")
        DebridStreamGuard.initPlaybackSession(
            stream = stream,
            candidates = listOf(stream),
            contentType = "movie",
            seasonNumber = null,
            metadataRuntimeMinutes = 110,
        )

        assertTrue(
            DebridStreamGuard.isErrorSlate(
                durationMs = 120_000L,
                streamUrl = "https://torbox.app/stream/failed_opening_torrent.mp4",
            )
        )
        assertTrue(
            DebridStreamGuard.isErrorSlate(
                durationMs = 120_000L,
                filename = "failed-opening-torrent-low-seeds.mp4",
            )
        )
    }

    @Test
    fun `default expected runtime for series episode exceeds 15 minutes`() {
        val stream = StreamItem(name = "Stream 1", addonName = "Debrid", addonId = "addon:1")
        DebridStreamGuard.initPlaybackSession(
            stream = stream,
            candidates = listOf(stream),
            contentType = "series",
            seasonNumber = 1,
            metadataRuntimeMinutes = null,
        )

        // 20s error video should be flagged
        assertTrue(DebridStreamGuard.isErrorSlate(durationMs = 20_000L))

        // 45m real episode should NOT be flagged
        assertFalse(DebridStreamGuard.isErrorSlate(durationMs = 45 * 60 * 1000L))
    }
}

