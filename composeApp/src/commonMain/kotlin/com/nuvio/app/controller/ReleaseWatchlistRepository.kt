package com.nuvio.app.controller

import co.touchlab.kermit.Logger
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class TrackedReleaseStatus {
    TRACKING,
    RELEASED,
}

@Serializable
data class TrackedReleaseItem(
    val id: String,
    val metaId: String,
    val title: String,
    val mediaType: String,
    val poster: String? = null,
    val backdrop: String? = null,
    val episodeId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val status: TrackedReleaseStatus = TrackedReleaseStatus.TRACKING,
    val addedAt: Long = EpisodeReleaseDatePlatform.nowEpochMs(),
)

@Serializable
data class ReleaseWatchlistPayload(
    val items: List<TrackedReleaseItem> = emptyList(),
)

object ReleaseWatchlistRepository {
    private val log = Logger.withTag("ReleaseWatchlistRepo")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    private val _items = MutableStateFlow<List<TrackedReleaseItem>>(emptyList())
    val items: StateFlow<List<TrackedReleaseItem>> = _items.asStateFlow()

    private var initialized = false
    private var pollJob: Job? = null
    private const val POLL_INTERVAL_MS = 12 * 60 * 60 * 1_000L // 12 hours
    private const val POLITE_CHECK_DELAY_MS = 2_500L // 2.5s between checks

    fun ensureStarted() {
        loadFromDiskIfNeeded()
        if (initialized) return
        initialized = true

        startPeriodicPolling()
    }

    private fun loadFromDiskIfNeeded() {
        val raw = runCatching { ReleaseWatchlistStorage.loadPayload() }.getOrNull()?.trim().orEmpty()
        if (raw.isNotEmpty()) {
            val payload = runCatching { json.decodeFromString<ReleaseWatchlistPayload>(raw) }
                .getOrDefault(ReleaseWatchlistPayload())
            _items.value = payload.items
        }
    }

    private fun saveToDisk() {
        val payload = ReleaseWatchlistPayload(items = _items.value)
        runCatching {
            ReleaseWatchlistStorage.savePayload(json.encodeToString(payload))
        }.onFailure {
            log.w(it) { "Failed to save release watchlist to disk" }
        }
    }

    fun isTracked(metaId: String, episodeId: String? = null): Boolean {
        loadFromDiskIfNeeded()
        val targetId = buildItemId(metaId, episodeId)
        return _items.value.any { it.id == targetId && it.status == TrackedReleaseStatus.TRACKING }
    }

    fun isTrackedFlow(metaId: String, episodeId: String? = null): Flow<Boolean> {
        val targetId = buildItemId(metaId, episodeId)
        return _items.map { list ->
            list.any { it.id == targetId && it.status == TrackedReleaseStatus.TRACKING }
        }
    }

    fun toggleTracking(meta: MetaDetails, episode: MetaVideo? = null): Boolean {
        loadFromDiskIfNeeded()
        val itemId = buildItemId(meta.id, episode?.id)
        val currentList = _items.value
        val existingIndex = currentList.indexOfFirst { it.id == itemId }

        val isNowTracking: Boolean
        if (existingIndex >= 0) {
            val existing = currentList[existingIndex]
            if (existing.status == TrackedReleaseStatus.TRACKING) {
                // Untrack (remove from list)
                _items.value = currentList.filterNot { it.id == itemId }
                isNowTracking = false
                log.i { "Untracked release: ${meta.name} (${itemId})" }
            } else {
                // Re-enable tracking
                _items.value = currentList.map {
                    if (it.id == itemId) it.copy(status = TrackedReleaseStatus.TRACKING) else it
                }
                isNowTracking = true
                log.i { "Re-tracked release: ${meta.name} (${itemId})" }
            }
        } else {
            val newItem = TrackedReleaseItem(
                id = itemId,
                metaId = meta.id,
                title = meta.name,
                mediaType = meta.type,
                poster = meta.poster,
                backdrop = meta.background,
                episodeId = episode?.id,
                seasonNumber = episode?.season,
                episodeNumber = episode?.episode,
                status = TrackedReleaseStatus.TRACKING,
                addedAt = EpisodeReleaseDatePlatform.nowEpochMs(),
            )
            _items.value = listOf(newItem) + currentList
            isNowTracking = true
            log.i { "Added tracked release: ${meta.name} (${itemId})" }
        }

        saveToDisk()
        return isNowTracking
    }

    private fun buildItemId(metaId: String, episodeId: String?): String {
        return if (!episodeId.isNullOrBlank()) "${metaId}:${episodeId}" else metaId
    }

    private fun startPeriodicPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            delay(5_000L) // Initial startup delay
            while (true) {
                runCatching { performPollingCycle() }
                    .onFailure { if (it is CancellationException) throw it }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    suspend fun performPollingCycle() {
        loadFromDiskIfNeeded()
        val trackingItems = _items.value.filter { it.status == TrackedReleaseStatus.TRACKING }
        if (trackingItems.isEmpty()) return

        log.d { "Checking streams for ${trackingItems.size} tracked releases..." }

        for (item in trackingItems) {
            delay(POLITE_CHECK_DELAY_MS)

            val videoId = item.episodeId?.takeIf { it.isNotBlank() } ?: item.metaId
            val verification = runCatching {
                NewArrivalsManager.verifyCleanHdStreamAvailable(
                    type = item.mediaType,
                    videoId = videoId,
                    embeddedStreams = emptyList(),
                )
            }.getOrNull()

            if (verification?.isAvailable == true) {
                log.i { "Tracked release verified clean HD stream found! ${item.title} (4K=${verification.has4K})" }

                // Update status to RELEASED
                _items.value = _items.value.map {
                    if (it.id == item.id) it.copy(status = TrackedReleaseStatus.RELEASED) else it
                }
                saveToDisk()

                // Insert top-priority notification
                val subtitle = if (item.seasonNumber != null && item.episodeNumber != null) {
                    "Season ${item.seasonNumber}, Episode ${item.episodeNumber}"
                } else {
                    "Full Release Now Available"
                }

                NewArrivalsManager.insertTrackedReleaseNotification(
                    metaId = item.metaId,
                    metaType = item.mediaType,
                    title = item.title,
                    subtitle = subtitle,
                    thumbnailUrl = item.backdrop ?: item.poster,
                    posterUrl = item.poster,
                    seasonNumber = item.seasonNumber,
                    episodeNumber = item.episodeNumber,
                    is4K = verification.has4K,
                )
            }
        }
    }
}
