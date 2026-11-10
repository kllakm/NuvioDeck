package com.nuvio.app.controller

import com.nuvio.app.features.streams.StreamItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CamStreamFilter {
    private val _enabledFlow = MutableStateFlow(CamStreamFilterStorage.loadEnabled() ?: true)
    val enabledFlow: StateFlow<Boolean> = _enabledFlow.asStateFlow()

    fun isEnabled(): Boolean = _enabledFlow.value

    fun setEnabled(enabled: Boolean) {
        _enabledFlow.value = enabled
        CamStreamFilterStorage.saveEnabled(enabled)
    }

    /**
     * Rejects modern cam/fake release disguises (e.g. 1080p re-encodes with line-dubbed audio,
     * HQ-NoADS-NoLogo.1080p...LD-AAC...MTRx):
     * - Release markers: CAM, HDCAM, HQ-CAM, CAM-RIP, TS, HD-TS, TELESYNC, TC, HDTC, PDVD, TELECINE, SCR, SCREENER, DVD-SCR, BDSCR, WORKPRINT, WP
     * - Audio/dubbed markers: LD, LINE, LINE-DUB, LINE-AUDIO
     * - Ad/watermark markers: NoADS, NoLogo, HQ-NoADS, HQ-NoLogo
     * - Known cam P2P groups: MTRx, CPG, Will1869, BiTOR
     */
    val CAM_OR_FAKE_STREAM_REGEX = Regex(
        """\b(CAM|HDCAM|HQ-?CAM|CAM-?RIP|TS|HD-?TS|TELESYNC|TC|HDTC|PDVD|TELECINE|SCR|SCREENER|DVD-?SCR|BDSCR|WORKPRINT|WP|LD|LINE|LINE-?DUB|LINE-?AUDIO|NoADS|NoLogo|HQ-NoADS|HQ-NoLogo|MTRx|CPG|Will1869|BiTOR)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun isCamOrFake(text: String): Boolean {
        if (!isEnabled()) return false
        return CAM_OR_FAKE_STREAM_REGEX.containsMatchIn(text)
    }

    fun isCamOrFake(stream: StreamItem): Boolean {
        if (!isEnabled()) return false
        val text = buildString {
            append(stream.name.orEmpty()).append(' ')
            append(stream.streamLabel).append(' ')
            append(stream.description.orEmpty()).append(' ')
            stream.behaviorHints.filename?.let { append(it).append(' ') }
            stream.playableDirectUrl?.let { append(it) }
        }
        return isCamOrFake(text)
    }

    fun filterCamStreams(streams: List<StreamItem>): List<StreamItem> {
        if (!isEnabled()) return streams
        return streams.filterNot { isCamOrFake(it) }
    }
}
