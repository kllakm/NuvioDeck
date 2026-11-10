package com.nuvio.app.controller

import com.nuvio.app.features.streams.StreamItem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CamStreamFilterTest {

    @BeforeTest
    fun setUp() {
        CamStreamFilter.setEnabled(true)
    }

    @AfterTest
    fun tearDown() {
        CamStreamFilter.setEnabled(true)
    }

    @Test
    fun `detects cam release markers`() {
        val camMarkers = listOf(
            "Movie.2026.CAM.x264",
            "Movie.2026.HDCAM.1080p",
            "Movie.2026.HQ-CAM.720p",
            "Movie.2026.CAMRIP",
            "Movie.2026.TS.XviD",
            "Movie.2026.HD-TS.1080p",
            "Movie.2026.TELESYNC",
            "Movie.2026.TC.720p",
            "Movie.2026.TELECINE",
            "Movie.2026.SCR.DVD",
            "Movie.2026.SCREENER",
            "Movie.2026.DVD-SCR",
            "Movie.2026.WORKPRINT",
            "Movie.2026.WP.x264",
        )
        for (name in camMarkers) {
            assertTrue(CamStreamFilter.isCamOrFake(name), "Expected '$name' to be detected as CAM/fake")
        }
    }

    @Test
    fun `detects line dubbed audio and ad markers`() {
        val audioAndAdMarkers = listOf(
            "Movie.2026.1080p.LD-AAC",
            "Movie.2026.1080p.LINE.Audio",
            "Movie.2026.1080p.LINE-DUB",
            "Movie.2026.HQ-NoADS-NoLogo.1080p",
            "Movie.2026.NoLogo.720p",
            "Movie.2026.HQ-NoADS.1080p",
        )
        for (name in audioAndAdMarkers) {
            assertTrue(CamStreamFilter.isCamOrFake(name), "Expected '$name' to be detected as fake/line-dubbed")
        }
    }

    @Test
    fun `detects known cam P2P groups`() {
        val groupReleases = listOf(
            "Movie.2026.HQ-NoADS-NoLogo.1080p.LD-AAC.MTRx",
            "Movie.2026.1080p.CPG",
            "Movie.2026.720p.Will1869",
            "Movie.2026.1080p.BiTOR",
        )
        for (name in groupReleases) {
            assertTrue(CamStreamFilter.isCamOrFake(name), "Expected group '$name' to be detected as CAM release")
        }
    }

    @Test
    fun `permits clean HD and 4K releases`() {
        val cleanReleases = listOf(
            "Movie.2026.1080p.WEB-DL.DDP5.1.Atmos.H.264",
            "Movie.2026.2160p.UHD.HDR.BluRay.x265",
            "Show.S01E01.720p.HDTV.x264",
            "The.Matrix.1999.1080p.Remux",
            "Animation.Short.2025.1080p.WEB",
        )
        for (name in cleanReleases) {
            assertFalse(CamStreamFilter.isCamOrFake(name), "Expected clean release '$name' NOT to be detected as CAM")
        }
    }

    @Test
    fun `checks stream item fields correctly`() {
        val fakeStream = StreamItem(
            name = "Torrentio [RD+]",
            description = "Movie.2026.HQ-NoADS-NoLogo.1080p.LD-AAC.MTRx\n2.4 GB",
            addonName = "Torrentio",
            addonId = "addon:torrentio",
        )
        assertTrue(CamStreamFilter.isCamOrFake(fakeStream))

        val cleanStream = StreamItem(
            name = "Torrentio [RD+]",
            description = "Movie.2026.1080p.AMZN.WEB-DL.DDP5.1.H.264\n5.2 GB",
            addonName = "Torrentio",
            addonId = "addon:torrentio",
        )
        assertFalse(CamStreamFilter.isCamOrFake(cleanStream))
    }

    @Test
    fun `toggling filter off disables detection and filtering`() {
        val fakeStream = StreamItem(
            name = "Torrentio [RD+]",
            description = "Movie.2026.HDCAM.1080p.LD-AAC\n1.8 GB",
            addonName = "Torrentio",
            addonId = "addon:torrentio",
        )
        val cleanStream = StreamItem(
            name = "Torrentio [RD+]",
            description = "Movie.2026.1080p.WEB-DL\n4.5 GB",
            addonName = "Torrentio",
            addonId = "addon:torrentio",
        )
        val all = listOf(fakeStream, cleanStream)

        CamStreamFilter.setEnabled(true)
        assertTrue(CamStreamFilter.isCamOrFake(fakeStream))
        assertEquals(listOf(cleanStream), CamStreamFilter.filterCamStreams(all))

        CamStreamFilter.setEnabled(false)
        assertFalse(CamStreamFilter.isCamOrFake(fakeStream))
        assertEquals(all, CamStreamFilter.filterCamStreams(all))
    }
}
