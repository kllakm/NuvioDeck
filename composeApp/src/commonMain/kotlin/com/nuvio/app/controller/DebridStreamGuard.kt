package com.nuvio.app.controller

import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.player.PlayerScreenRuntime
import com.nuvio.app.features.player.removeFailedStreamFromCache
import com.nuvio.app.features.player.switchToSource
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamsRepository

object DebridStreamGuard {
    private val log = Logger.withTag("DebridStreamGuard")

    private val DEBRID_ERROR_TEXT_REGEX = Regex(
        """\b(failed[-_]?opening[-_]?torrent|low[-_]?seeds|torrent[-_]?failed|download[-_]?failed|error[-_]?video)\b""",
        RegexOption.IGNORE_CASE,
    )

    private var candidateStreams: List<StreamItem> = emptyList()
    private var activeStream: StreamItem? = null
    private var expectedDurationMs: Long = 0L
    private var isRecoveryInFlight: Boolean = false
    private var errorDetectedForActiveStream: Boolean = false

    fun parseRuntimeMinutes(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim().lowercase()
        val hoursMatch = Regex("""(\d+)\s*h""").find(text)
        val minsMatch = Regex("""(\d+)\s*m""").find(text)
        if (hoursMatch != null || minsMatch != null) {
            val hours = hoursMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val mins = minsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val total = hours * 60 + mins
            if (total > 0) return total
        }
        val digitsMatch = Regex("""(\d+)""").find(text)
        return digitsMatch?.groupValues?.get(1)?.toIntOrNull()
    }

    fun initPlaybackSession(
        stream: StreamItem?,
        candidates: List<StreamItem>,
        contentType: String?,
        seasonNumber: Int?,
        metadataRuntimeMinutes: Int? = null,
    ) {
        activeStream = stream
        candidateStreams = if (stream != null) {
            candidates.filterNot { it == stream }
        } else {
            candidates
        }
        isRecoveryInFlight = false
        errorDetectedForActiveStream = false

        expectedDurationMs = when {
            metadataRuntimeMinutes != null && metadataRuntimeMinutes > 0 -> {
                metadataRuntimeMinutes * 60_000L
            }
            contentType.equals("movie", ignoreCase = true) || seasonNumber != null || contentType.equals("series", ignoreCase = true) -> {
                // Movies and TV episodes default to at least 20 minutes (> 15m)
                20 * 60_000L
            }
            else -> 0L
        }
        log.d {
            "Playback session initialized: ${candidateStreams.size} remaining candidates, expectedDurationMs=$expectedDurationMs"
        }
    }

    fun isErrorSlate(
        durationMs: Long,
        streamUrl: String? = null,
        filename: String? = null,
    ): Boolean {
        val urlText = streamUrl.orEmpty()
        val fileText = filename.orEmpty()
        if (DEBRID_ERROR_TEXT_REGEX.containsMatchIn(urlText) || DEBRID_ERROR_TEXT_REGEX.containsMatchIn(fileText)) {
            return true
        }
        if (expectedDurationMs > 15 * 60 * 1000L && durationMs in 1..60_000L) {
            return true
        }
        return false
    }

    internal fun checkAndRecover(
        runtime: PlayerScreenRuntime,
        durationMs: Long,
        streamUrl: String?,
        filename: String?,
        onRequestBack: () -> Unit,
    ): Boolean {
        if (isRecoveryInFlight || errorDetectedForActiveStream) return false
        if (durationMs <= 0L) return false

        if (!isErrorSlate(durationMs, streamUrl, filename)) {
            return false
        }

        errorDetectedForActiveStream = true
        isRecoveryInFlight = true
        log.w {
            "Debrid error video slate detected! Loaded duration=${durationMs}ms, expected=${expectedDurationMs}ms. Recovering..."
        }

        // Purge dead stream from cache
        runtime.removeFailedStreamFromCache()

        // Sync with StreamsRepository
        activeStream?.let { StreamsRepository.skipAutoPlayStream(it) }

        // Find next candidate stream
        val nextStream = candidateStreams.firstOrNull()
            ?: StreamsRepository.uiState.value.autoPlayCandidates.firstOrNull()

        if (nextStream != null) {
            candidateStreams = candidateStreams.drop(1)
            activeStream = nextStream
            errorDetectedForActiveStream = false
            isRecoveryInFlight = false

            NuvioToastController.show("Stream unavailable on debrid. Trying next stream...")
            log.i { "Auto-advancing to next candidate stream: ${nextStream.streamLabel}" }
            runtime.switchToSource(nextStream)
            runtime.activeInitialPositionMs = runtime.initialPositionMs
            return true
        } else {
            isRecoveryInFlight = false
            log.w { "All candidate streams exhausted after debrid error slate. Exiting cleanly to Details." }
            NuvioToastController.show("Stream unavailable on debrid. No available streams could be played.")
            onRequestBack()
            return true
        }
    }
}
