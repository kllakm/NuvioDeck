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
     * - Audio/dubbed markers: LD, LINE-DUB, LINE-AUDIO, LINE.AAC, etc. (requiring audio qualifier after LINE so titles like "Walk the Line" pass)
     * - Ad/watermark markers: NoADS, NoLogo, HQ-NoADS, HQ-NoLogo
     * - Known cam P2P groups: MTRx, CPG, Will1869, BiTOR
     */
    val CAM_OR_FAKE_STREAM_REGEX = Regex(
        """\b(CAM|HDCAM|HQ-?CAM|CAM-?RIP|TS|HD-?TS|TELESYNC|TC|HDTC|PDVD|TELECINE|SCR|SCREENER|DVD-?SCR|BDSCR|WORKPRINT|WP|LD|LINE(?:[.\-_ ]?(?:DUB|AUDIO|AAC|AC3|MP3|DD|2\.0))|NoADS|NoLogo|HQ-NoADS|HQ-NoLogo|MTRx|CPG|Will1869|BiTOR)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val trailingVideoExtensionRegex = Regex(
        """\.(?:mkv|mp4|avi|mov|webm|m4v|ts|m2ts|mpg|mpeg|flv|wmv|m3u8)(?=$|[\s)\]/"'])""",
        RegexOption.IGNORE_CASE,
    )

    private val videoFilenameExtensions = setOf(
        "mkv", "mp4", "avi", "mov", "webm", "m4v",
        "ts", "m2ts", "mpg", "mpeg", "flv", "wmv",
    )

    /** Checks for CAM/fake markers regardless of whether the user setting is enabled (e.g. HD arrival verification). */
    fun containsCamOrFakeMarker(text: String): Boolean {
        if (text.isBlank()) return false
        val normalized = trailingVideoExtensionRegex.replace(text, "")
        return CAM_OR_FAKE_STREAM_REGEX.containsMatchIn(normalized)
    }

    fun isCamOrFake(text: String): Boolean {
        if (!isEnabled()) return false
        return containsCamOrFakeMarker(text)
    }

    fun isCamOrFake(stream: StreamItem): Boolean {
        if (!isEnabled()) return false
        val text = buildString {
            append(stream.name.orEmpty()).append(' ')
            stream.title?.takeIf { it != stream.description }?.let { append(it).append(' ') }
            append(stream.streamLabel).append(' ')
            append(stream.description.orEmpty()).append(' ')
            stream.behaviorHints.filename?.let { append(it).append(' ') }
            stream.playableDirectUrl?.let(::extractVideoFilenameStemFromUrl)?.let { append(it) }
        }
        return containsCamOrFakeMarker(text)
    }

    internal fun extractVideoFilenameStemFromUrl(url: String): String? {
        val clean = url.substringBefore('#').substringBefore('?')
        val schemeIdx = clean.indexOf("://")
        val authorityStart = if (schemeIdx >= 0) schemeIdx + 3 else 0
        val slashIdx = clean.indexOf('/', authorityStart)
        if (slashIdx < 0) return null
        val rawSegment = clean.substring(slashIdx + 1).substringAfterLast('/')
        if (rawSegment.isBlank()) return null

        val decodedSegment = decodePercentUtf8(rawSegment)
            .substringAfterLast('/')
            .substringBefore('?')
            .trim()
        val dotIndex = decodedSegment.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex >= decodedSegment.lastIndex) return null
        val ext = decodedSegment.substring(dotIndex + 1).lowercase()
        if (ext !in videoFilenameExtensions) return null
        return decodedSegment.substring(0, dotIndex)
    }

    private fun decodePercentUtf8(value: String): String {
        if ('%' !in value && '+' !in value) return value
        val result = StringBuilder(value.length)
        var byteBuffer = ByteArray(16)
        var byteCount = 0

        fun flushBytes() {
            if (byteCount > 0) {
                result.append(byteBuffer.decodeToString(0, byteCount))
                byteCount = 0
            }
        }

        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '%' && i + 2 < value.length) {
                val hi = value[i + 1].digitToIntOrNull(16)
                val lo = value[i + 2].digitToIntOrNull(16)
                if (hi != null && lo != null) {
                    if (byteCount == byteBuffer.size) {
                        byteBuffer = byteBuffer.copyOf(byteBuffer.size * 2)
                    }
                    byteBuffer[byteCount++] = ((hi shl 4) or lo).toByte()
                    i += 3
                    continue
                }
            }
            flushBytes()
            result.append(if (ch == '+') ' ' else ch)
            i++
        }
        flushBytes()
        return result.toString()
    }

    fun filterCamStreams(streams: List<StreamItem>): List<StreamItem> {
        if (!isEnabled()) return streams
        return streams.filterNot { isCamOrFake(it) }
    }
}
