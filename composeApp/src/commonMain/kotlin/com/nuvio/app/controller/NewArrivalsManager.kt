package com.nuvio.app.controller

import co.touchlab.kermit.Logger
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.core.time.parseEpisodeReleaseEpochMs
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.buildAddonResourceUrl
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.addons.fetchAddonResponseText
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.home.HomeRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamParser
import com.nuvio.app.features.streams.supportsStream
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class NewArrivalNotification(
    val id: String,
    val metaId: String,
    val metaType: String,
    val title: String,
    val headerLabel: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val posterUrl: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val is4K: Boolean = false,
    val detectedAt: Long,
    val isTrackedRelease: Boolean = false,
    val isRead: Boolean = false,
) {
    fun toMetaPreview(): MetaPreview = MetaPreview(
        id = metaId,
        type = metaType,
        name = title,
        poster = posterUrl ?: thumbnailUrl,
        banner = thumbnailUrl,
        landscapePoster = thumbnailUrl,
    )
}

@Serializable
internal data class StoredNewArrivalsPayload(
    val notifications: List<NewArrivalNotification> = emptyList(),
    val seenNotificationIds: Set<String> = emptySet(),
    val lastOpenedAtMs: Long = 0L,
)

object NewArrivalsManager {
    const val MAX_NOTIFICATIONS = 10
    private const val MAX_SEEN_IDS = 600
    private const val STREAM_CHECK_DELAY_MS = 2_500L
    private const val PERIODIC_SCAN_INTERVAL_MS = 30L * 60L * 1_000L
    private const val ADDON_FETCH_TIMEOUT_MS = 6_500L
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
    private const val MOVIE_RELEASE_WINDOW_DAYS = 120L
    private const val SERIES_EPISODE_WINDOW_DAYS = 7L

    private val log = Logger.withTag("NewArrivalsManager")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val camOrScreenerRegex = CamStreamFilter.CAM_OR_FAKE_STREAM_REGEX
    private val uhd4kRegex = Regex(
        """\b(2160p|4K|UHD)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val fullHd1080pRegex = Regex(
        """\b(1080p|1080i|FHD)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val _notifications = MutableStateFlow<List<NewArrivalNotification>>(emptyList())
    val notifications: StateFlow<List<NewArrivalNotification>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _hasUnread = MutableStateFlow(false)
    val hasUnread: StateFlow<Boolean> = _hasUnread.asStateFlow()

    private var seenIds = linkedSetOf<String>()
    private var lastOpenedAtMs = 0L
    private var loadedProfileId: Int? = null
    private var initialized = false
    private var scanJob: Job? = null
    private val evaluatedInSession = mutableSetOf<String>()

    fun ensureStarted() {
        loadFromDiskIfNeeded(force = false)
        if (initialized) return
        initialized = true

        ReleaseWatchlistRepository.ensureStarted()

        scope.launch {
            ProfileRepository.state
                .map { it.activeProfile?.profileIndex ?: 1 }
                .distinctUntilChanged()
                .collectLatest {
                    evaluatedInSession.clear()
                    loadFromDiskIfNeeded(force = true)
                    triggerBackgroundScan(initialDelayMs = 4_000L)
                }
        }

        scope.launch {
            HomeRepository.uiState
                .map { it.sections.size }
                .distinctUntilChanged()
                .collectLatest { sectionCount ->
                    if (sectionCount > 0) {
                        triggerBackgroundScan(initialDelayMs = 3_500L)
                    }
                }
        }
    }

    fun markAllAsRead() {
        loadFromDiskIfNeeded(force = false)
        val now = EpisodeReleaseDatePlatform.nowEpochMs()
        val latestDetected = _notifications.value.maxOfOrNull { it.detectedAt } ?: 0L
        lastOpenedAtMs = maxOf(now, latestDetected)
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        updateUnreadState()
        saveToDisk()
    }

    fun markItemAsRead(id: String) {
        loadFromDiskIfNeeded(force = false)
        _notifications.value = _notifications.value.map { item ->
            if (item.id == id) item.copy(isRead = true) else item
        }
        updateUnreadState()
        saveToDisk()
    }

    fun insertTrackedReleaseNotification(
        metaId: String,
        metaType: String,
        title: String,
        subtitle: String? = null,
        thumbnailUrl: String? = null,
        posterUrl: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        is4K: Boolean = false,
    ) {
        loadFromDiskIfNeeded(force = false)
        val notificationId = "tracked:${metaId}:${seasonNumber ?: 0}:${episodeNumber ?: 0}"
        val notification = NewArrivalNotification(
            id = notificationId,
            metaId = metaId,
            metaType = metaType,
            title = title,
            headerLabel = "TRACKED RELEASE",
            subtitle = subtitle,
            thumbnailUrl = thumbnailUrl,
            posterUrl = posterUrl,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            is4K = is4K,
            detectedAt = EpisodeReleaseDatePlatform.nowEpochMs(),
            isTrackedRelease = true,
            isRead = false,
        )
        seenIds.add(notificationId)
        val filtered = _notifications.value.filterNot { it.id == notificationId }
        val updated = (listOf(notification) + filtered).take(MAX_NOTIFICATIONS)
        _notifications.value = updated
        updateUnreadState()
        saveToDisk()
        log.i { "Added top-priority tracked release notification: $title" }
    }

    fun formatRelativeTimestamp(
        detectedAtMs: Long,
        nowEpochMs: Long = EpisodeReleaseDatePlatform.nowEpochMs(),
    ): String {
        val diffMs = (nowEpochMs - detectedAtMs).coerceAtLeast(0L)
        val minutes = diffMs / (60L * 1_000L)
        val hours = diffMs / (60L * 60L * 1_000L)
        val days = diffMs / MILLIS_PER_DAY
        return when {
            minutes < 15L -> "Just added"
            hours < 24L -> {
                val todayIso = EpisodeReleaseDatePlatform.localIsoDateAtEpochMs(nowEpochMs)
                val detectedIso = EpisodeReleaseDatePlatform.localIsoDateAtEpochMs(detectedAtMs)
                if (todayIso != null && todayIso == detectedIso) "Today" else "Yesterday"
            }
            days == 1L -> "Yesterday"
            days < 7L -> "$days days ago"
            days < 14L -> "1 week ago"
            else -> "${days / 7L} weeks ago"
        }
    }

    private fun loadFromDiskIfNeeded(force: Boolean) {
        val activeProfileId = ProfileRepository.activeProfileId
        if (!force && loadedProfileId == activeProfileId) return
        loadedProfileId = activeProfileId

        val raw = runCatching { NewArrivalsStorage.loadPayload() }.getOrNull()?.trim().orEmpty()
        val decoded = if (raw.isNotEmpty()) {
            runCatching { json.decodeFromString<StoredNewArrivalsPayload>(raw) }
                .getOrDefault(StoredNewArrivalsPayload())
        } else {
            StoredNewArrivalsPayload()
        }

        val cappedNotifications = decoded.notifications
            .sortedByDescending { it.detectedAt }
            .distinctBy { it.id }
            .take(MAX_NOTIFICATIONS)

        seenIds.clear()
        seenIds.addAll(decoded.seenNotificationIds)
        cappedNotifications.forEach { seenIds.add(it.id) }
        lastOpenedAtMs = decoded.lastOpenedAtMs
        _notifications.value = cappedNotifications
        updateUnreadState()
    }

    private fun saveToDisk() {
        val cappedNotifications = _notifications.value
            .sortedByDescending { it.detectedAt }
            .distinctBy { it.id }
            .take(MAX_NOTIFICATIONS)
        val trimmedSeen = if (seenIds.size > MAX_SEEN_IDS) {
            seenIds.toList().takeLast(MAX_SEEN_IDS).toSet()
        } else {
            seenIds.toSet()
        }
        val payload = StoredNewArrivalsPayload(
            notifications = cappedNotifications,
            seenNotificationIds = trimmedSeen,
            lastOpenedAtMs = lastOpenedAtMs,
        )
        runCatching {
            NewArrivalsStorage.savePayload(json.encodeToString(payload))
        }
    }

    private fun updateUnreadState() {
        val count = _notifications.value.count { !it.isRead && (it.detectedAt > lastOpenedAtMs || it.isTrackedRelease) }
        _unreadCount.value = count
        _hasUnread.value = count > 0
    }

    private fun recordVerifiedArrival(notification: NewArrivalNotification) {
        if (notification.id in seenIds) return
        seenIds.add(notification.id)
        val updated = (listOf(notification) + _notifications.value)
            .sortedByDescending { it.detectedAt }
            .distinctBy { it.id }
            .take(MAX_NOTIFICATIONS)
        _notifications.value = updated
        updateUnreadState()
        saveToDisk()
        log.i { "Added verified new arrival notification: ${notification.headerLabel} - ${notification.title}" }
    }

    private fun triggerBackgroundScan(initialDelayMs: Long) {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            delay(initialDelayMs)
            while (true) {
                runCatching { performScanCycle() }
                    .onFailure { if (it is CancellationException) throw it }
                delay(PERIODIC_SCAN_INTERVAL_MS)
            }
        }
    }

    private data class CandidateMedia(
        val id: String,
        val type: String,
        val name: String,
        val poster: String? = null,
        val banner: String? = null,
        val rawReleaseDate: String? = null,
        val releaseInfo: String? = null,
        val genres: List<String> = emptyList(),
        val priorityTier: Int,
    )

    private suspend fun performScanCycle() {
        MetaDetailsRepository.waitForReadyAddonManifests()
        val enabledAddons = AddonRepository.uiState.value.addons.enabledAddons()
        if (enabledAddons.isEmpty()) return

        val watchEntries = WatchProgressRepository.uiState.value.entries
        val libraryItems = LibraryRepository.uiState.value.items
        val homeState = HomeRepository.uiState.value

        // Compute user's favorite genres from Watch History and Library
        val genreCounts = mutableMapOf<String, Int>()
        libraryItems.forEach { item ->
            item.genres.forEach { genre ->
                val normalized = genre.trim().lowercase()
                if (normalized.isNotEmpty()) {
                    genreCounts[normalized] = (genreCounts[normalized] ?: 0) + 2
                }
            }
        }
        val allHomeItemsByKey = (homeState.heroItems + homeState.sections.flatMap { it.items })
            .associateBy { "${MetaDetailsRepository.normalizePlaybackType(it.type)}:${it.id}" }

        watchEntries.forEach { entry ->
            val normType = MetaDetailsRepository.normalizePlaybackType(entry.parentMetaType.ifBlank { entry.contentType })
            val preview = allHomeItemsByKey["$normType:${entry.parentMetaId}"]
            preview?.genres?.forEach { genre ->
                val normalized = genre.trim().lowercase()
                if (normalized.isNotEmpty()) {
                    genreCounts[normalized] = (genreCounts[normalized] ?: 0) + 3
                }
            }
        }
        val topUserGenres = genreCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
            .toSet()

        val candidates = LinkedHashMap<String, CandidateMedia>()

        // Priority 1: Series in Watch History / Continue Watching
        watchEntries
            .sortedByDescending { it.lastUpdatedEpochMs }
            .forEach { entry ->
                val normType = MetaDetailsRepository.normalizePlaybackType(entry.parentMetaType.ifBlank { entry.contentType })
                if (normType == "series" && entry.parentMetaId.isNotBlank()) {
                    val key = "series:${entry.parentMetaId}"
                    if (key !in candidates && candidates.size < 16) {
                        candidates[key] = CandidateMedia(
                            id = entry.parentMetaId,
                            type = "series",
                            name = entry.title,
                            poster = entry.poster,
                            banner = entry.background ?: entry.episodeThumbnail,
                            priorityTier = 1,
                        )
                    }
                }
            }

        // Priority 2: Series & Movies in Watchlist / Library
        libraryItems
            .sortedByDescending { it.savedAtEpochMs }
            .forEach { item ->
                val normType = MetaDetailsRepository.normalizePlaybackType(item.type)
                if ((normType == "series" || normType == "movie") && item.id.isNotBlank()) {
                    val key = "$normType:${item.id}"
                    if (key !in candidates && candidates.size < 28) {
                        candidates[key] = CandidateMedia(
                            id = item.id,
                            type = normType,
                            name = item.name,
                            poster = item.poster,
                            banner = item.landscapePoster ?: item.banner,
                            releaseInfo = item.releaseInfo,
                            genres = item.genres,
                            priorityTier = 2,
                        )
                    }
                }
            }

        // Priority 3: Home Hero + Catalog items (prioritizing genre matches & items with recent release info)
        val nowEpochMs = EpisodeReleaseDatePlatform.nowEpochMs()
        val currentYear = EpisodeReleaseDatePlatform.localIsoDateAtEpochMs(nowEpochMs)
            ?.take(4)
            ?.toIntOrNull()
            ?: 2026

        val catalogPreviews = (homeState.heroItems + homeState.sections.flatMap { it.items })
            .distinctBy { "${MetaDetailsRepository.normalizePlaybackType(it.type)}:${it.id}" }
            .filter { preview ->
                val normType = MetaDetailsRepository.normalizePlaybackType(preview.type)
                if (normType != "movie" && normType != "series") return@filter false
                if (normType == "movie") {
                    // Pre-filter out obviously old movies before network metadata calls
                    val year = extractFourDigitYear(preview.rawReleaseDate ?: preview.releaseInfo)
                    if (year != null && year < currentYear - 1) return@filter false
                }
                true
            }
            .sortedWith(
                compareByDescending<MetaPreview> { preview ->
                    !preview.rawReleaseDate.isNullOrBlank()
                }.thenByDescending { preview ->
                    preview.genres.count { it.trim().lowercase() in topUserGenres }
                }
            )

        for (preview in catalogPreviews) {
            if (candidates.size >= 48) break
            val normType = MetaDetailsRepository.normalizePlaybackType(preview.type)
            val key = "$normType:${preview.id}"
            if (key !in candidates) {
                candidates[key] = CandidateMedia(
                    id = preview.id,
                    type = normType,
                    name = preview.name,
                    poster = preview.poster,
                    banner = preview.landscapePoster ?: preview.banner,
                    rawReleaseDate = preview.rawReleaseDate,
                    releaseInfo = preview.releaseInfo,
                    genres = preview.genres,
                    priorityTier = 3,
                )
            }
        }

        for ((key, candidate) in candidates) {
            if (key in evaluatedInSession) continue
            evaluatedInSession.add(key)

            val checkedStream = when (candidate.type) {
                "series" -> evaluateSeriesCandidate(candidate)
                "movie" -> evaluateMovieCandidate(candidate)
                else -> false
            }

            if (checkedStream) {
                // Safe 2-3 second delay between stream checks to avoid addon rate limits
                delay(STREAM_CHECK_DELAY_MS)
            } else {
                delay(350L)
            }
        }
    }

    private suspend fun evaluateSeriesCandidate(candidate: CandidateMedia): Boolean {
        val meta = MetaDetailsRepository.peek("series", candidate.id)
            ?: runCatching { MetaDetailsRepository.fetch("series", candidate.id, cacheResult = true) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
            ?: return false

        val nowEpochMs = EpisodeReleaseDatePlatform.nowEpochMs()
        val recentCutoffMs = nowEpochMs - (SERIES_EPISODE_WINDOW_DAYS * MILLIS_PER_DAY)
        val isCurrentlyWatching = candidate.priorityTier == 1

        if (isCurrentlyWatching) {
            // Priority 1: User is currently watching this series in Watch History / Continue Watching.
            // Check for newly aired episodes within the last 7 days.
            val recentEpisodes = meta.videos
                .filter { video ->
                    val season = video.season ?: 0
                    val episode = video.episode ?: 0
                    if (season <= 0 || episode <= 0) return@filter false
                    val releasedMs = parseEpisodeReleaseEpochMs(video.released) ?: return@filter false
                    releasedMs in recentCutoffMs..nowEpochMs
                }
                .sortedWith(
                    compareByDescending<MetaVideo> { parseEpisodeReleaseEpochMs(it.released) ?: 0L }
                        .thenByDescending { it.season ?: 0 }
                        .thenByDescending { it.episode ?: 0 }
                )

            val latestRecentEpisode = recentEpisodes.firstOrNull() ?: return false
            val season = latestRecentEpisode.season ?: return false
            val episode = latestRecentEpisode.episode ?: return false
            val notificationId = "series:${meta.id}:S${season}E${episode}"
            if (notificationId in seenIds) return false

            // Verify HD/4K stream availability right now
            val videoId = buildPlaybackVideoId(
                parentMetaId = meta.id,
                seasonNumber = season,
                episodeNumber = episode,
                fallbackVideoId = latestRecentEpisode.id,
            )
            val verification = verifyCleanHdStreamAvailable(
                type = "series",
                videoId = videoId,
                embeddedStreams = latestRecentEpisode.streams,
            )
            if (!verification.isAvailable) return true

            val thumbnail = latestRecentEpisode.thumbnail?.takeIf { it.isNotBlank() }
                ?: meta.background?.takeIf { it.isNotBlank() }
                ?: candidate.banner?.takeIf { it.isNotBlank() }
                ?: meta.poster?.takeIf { it.isNotBlank() }
                ?: candidate.poster

            val episodeCode = "S${season}E${episode}"
            val headerLabel = "New Episode • $episodeCode"
            val notification = NewArrivalNotification(
                id = notificationId,
                metaId = meta.id,
                metaType = "series",
                title = meta.name.ifBlank { candidate.name },
                headerLabel = headerLabel,
                subtitle = latestRecentEpisode.title.takeIf { it.isNotBlank() && !it.equals("Episode $episode", ignoreCase = true) },
                thumbnailUrl = thumbnail,
                posterUrl = meta.poster ?: candidate.poster,
                seasonNumber = season,
                episodeNumber = episode,
                is4K = verification.has4K,
                detectedAt = EpisodeReleaseDatePlatform.nowEpochMs(),
            )
            recordVerifiedArrival(notification)
            return true
        } else {
            // User is NOT currently watching this series.
            // ONLY announce if this is a BRAND NEW TV SHOW IN GENERAL that has landed in the last few days
            // (Season 1 premiere episode aired within the recent window, or initial release within window).
            val premiereEpisode = meta.videos.firstOrNull { (it.season ?: 0) == 1 && (it.episode ?: 0) == 1 }
            val premiereReleasedMs = premiereEpisode?.released?.let { parseEpisodeReleaseEpochMs(it) }
                ?: parseEpisodeReleaseEpochMs(meta.releaseInfo)

            if (premiereReleasedMs == null || premiereReleasedMs !in recentCutoffMs..nowEpochMs) {
                // Not a brand new show that premiered in the last few days -> skip
                return false
            }

            val notificationId = "series_new:${meta.id}"
            if (notificationId in seenIds) return false

            val videoId = buildPlaybackVideoId(
                parentMetaId = meta.id,
                seasonNumber = 1,
                episodeNumber = 1,
                fallbackVideoId = premiereEpisode?.id,
            )
            val verification = verifyCleanHdStreamAvailable(
                type = "series",
                videoId = videoId,
                embeddedStreams = premiereEpisode?.streams.orEmpty(),
            )
            if (!verification.isAvailable) return true

            val thumbnail = premiereEpisode?.thumbnail?.takeIf { it.isNotBlank() }
                ?: meta.background?.takeIf { it.isNotBlank() }
                ?: candidate.banner?.takeIf { it.isNotBlank() }
                ?: meta.poster?.takeIf { it.isNotBlank() }
                ?: candidate.poster

            val notification = NewArrivalNotification(
                id = notificationId,
                metaId = meta.id,
                metaType = "series",
                title = meta.name.ifBlank { candidate.name },
                headerLabel = "New Series",
                subtitle = meta.releaseInfo ?: meta.genres.take(2).joinToString(" • "),
                thumbnailUrl = thumbnail,
                posterUrl = meta.poster ?: candidate.poster,
                seasonNumber = 1,
                episodeNumber = 1,
                is4K = verification.has4K,
                detectedAt = EpisodeReleaseDatePlatform.nowEpochMs(),
            )
            recordVerifiedArrival(notification)
            return true
        }
    }

    private suspend fun evaluateMovieCandidate(candidate: CandidateMedia): Boolean {
        val notificationId = "movie:${candidate.id}"
        if (notificationId in seenIds) return false

        val nowEpochMs = EpisodeReleaseDatePlatform.nowEpochMs()
        val recentWindowCutoffMs = nowEpochMs - (MOVIE_RELEASE_WINDOW_DAYS * MILLIS_PER_DAY)

        // Check candidate's rawReleaseDate first if available
        val previewReleaseEpochMs = parseEpisodeReleaseEpochMs(candidate.rawReleaseDate)
        if (previewReleaseEpochMs != null) {
            if (previewReleaseEpochMs > nowEpochMs || previewReleaseEpochMs < recentWindowCutoffMs) {
                return false
            }
        }

        val meta = MetaDetailsRepository.peek("movie", candidate.id)
            ?: runCatching { MetaDetailsRepository.fetch("movie", candidate.id, cacheResult = true) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
            ?: return false

        // Strict Release Date Gate for Movies:
        // Must have a verified recent release date within the last 120 days (excluding older trending catalog movies).
        val resolvedReleaseEpochMs = previewReleaseEpochMs
            ?: parseEpisodeReleaseEpochMs(meta.videos.firstOrNull()?.released)
            ?: parseEpisodeReleaseEpochMs(meta.lastAirDate)
            ?: parseEpisodeReleaseEpochMs(meta.releaseInfo)
            ?: fetchRawMovieReleasedEpochMs(meta.id)

        if (resolvedReleaseEpochMs == null || resolvedReleaseEpochMs > nowEpochMs || resolvedReleaseEpochMs < recentWindowCutoffMs) {
            return false
        }

        val videoId = meta.defaultVideoId?.takeIf { it.isNotBlank() }
            ?: meta.videos.firstOrNull()?.id?.takeIf { it.isNotBlank() }
            ?: meta.id

        val verification = verifyCleanHdStreamAvailable(
            type = "movie",
            videoId = videoId,
            embeddedStreams = meta.videos.flatMap { it.streams },
        )
        if (!verification.isAvailable) return true

        val thumbnail = meta.background?.takeIf { it.isNotBlank() }
            ?: candidate.banner?.takeIf { it.isNotBlank() }
            ?: meta.poster?.takeIf { it.isNotBlank() }
            ?: candidate.poster

        val headerLabel = if (verification.has4K) "Now Available in 4K" else "New Arrival"
        val notification = NewArrivalNotification(
            id = notificationId,
            metaId = meta.id,
            metaType = "movie",
            title = meta.name.ifBlank { candidate.name },
            headerLabel = headerLabel,
            subtitle = meta.releaseInfo?.take(4),
            thumbnailUrl = thumbnail,
            posterUrl = meta.poster ?: candidate.poster,
            is4K = verification.has4K,
            detectedAt = EpisodeReleaseDatePlatform.nowEpochMs(),
        )
        recordVerifiedArrival(notification)
        return true
    }

    private suspend fun fetchRawMovieReleasedEpochMs(metaId: String): Long? {
        val manifests = AddonRepository.uiState.value.addons
            .enabledAddons()
            .mapNotNull { it.manifest }
            .filter { it.supportsStream("movie", metaId) || it.resources.any { r -> r.name == "meta" } }
            .take(2)

        for (manifest in manifests) {
            val url = buildAddonResourceUrl(
                manifestUrl = manifest.transportUrl,
                resource = "meta",
                type = "movie",
                id = metaId,
            )
            val releasedStr = withTimeoutOrNull(ADDON_FETCH_TIMEOUT_MS) {
                runCatching {
                    val payload = fetchAddonResponseText(url)
                    val root = json.parseToJsonElement(payload) as? JsonObject
                    val metaObj = root?.get("meta") as? JsonObject
                    (metaObj?.get("released") as? JsonPrimitive)?.contentOrNull
                }.getOrNull()
            }
            val parsed = parseEpisodeReleaseEpochMs(releasedStr)
            if (parsed != null) return parsed
        }
        return null
    }

    internal data class StreamVerificationResult(
        val isAvailable: Boolean,
        val has4K: Boolean,
    )

    internal suspend fun probeHasAnyStreams(
        type: String,
        videoId: String,
    ): Boolean {
        return verifyCleanHdStreamAvailable(
            type = type,
            videoId = videoId,
            embeddedStreams = emptyList(),
        ).isAvailable
    }

    internal suspend fun verifyCleanHdStreamAvailable(
        type: String,
        videoId: String,
        embeddedStreams: List<StreamItem> = emptyList(),
    ): StreamVerificationResult {
        val embeddedEval = evaluateCleanHdStreams(embeddedStreams)
        if (embeddedEval.isAvailable) return embeddedEval

        val streamAddons = AddonRepository.uiState.value.addons
            .enabledAddons()
            .mapNotNull { addon ->
                val manifest = addon.manifest ?: return@mapNotNull null
                if (!manifest.supportsStream(type, videoId)) return@mapNotNull null
                Triple(
                    addon.displayTitle.ifBlank { manifest.name },
                    "addon:${manifest.id}:${addon.manifestUrl}",
                    manifest,
                )
            }

        if (streamAddons.isEmpty()) return StreamVerificationResult(isAvailable = false, has4K = false)

        var foundAnyCleanHd = false
        var found4K = false

        for ((displayName, addonId, manifest) in streamAddons) {
            val url = buildAddonResourceUrl(
                manifestUrl = manifest.transportUrl,
                resource = "stream",
                type = type,
                id = videoId,
            )
            val streams = withTimeoutOrNull(ADDON_FETCH_TIMEOUT_MS) {
                runCatching {
                    val payload = fetchAddonResponseText(url = url, forceRefresh = false)
                    StreamParser.parse(
                        payload = payload,
                        addonName = displayName,
                        addonId = addonId,
                        addonLogo = manifest.logoUrl,
                    )
                }.getOrDefault(emptyList())
            }.orEmpty()

            val eval = evaluateCleanHdStreams(streams)
            if (eval.isAvailable) {
                foundAnyCleanHd = true
                if (eval.has4K) {
                    found4K = true
                    break
                }
            }
        }

        return StreamVerificationResult(
            isAvailable = foundAnyCleanHd,
            has4K = found4K,
        )
    }

    private fun evaluateCleanHdStreams(streams: List<StreamItem>): StreamVerificationResult {
        if (streams.isEmpty()) return StreamVerificationResult(isAvailable = false, has4K = false)
        var has1080pOrBetter = false
        var has4K = false

        for (stream in streams) {
            if (!stream.hasPlayableSource) continue
            val combinedText = buildString {
                stream.name?.let { append(it).append(' ') }
                stream.title?.let { append(it).append(' ') }
                stream.description?.let { append(it).append(' ') }
                stream.behaviorHints.filename?.let { append(it).append(' ') }
            }
            if (combinedText.isBlank()) continue

            // Explicitly reject CAM, TS, HDTS, TELESYNC, SCREENER, etc.
            if (camOrScreenerRegex.containsMatchIn(combinedText)) continue

            val is4KStream = uhd4kRegex.containsMatchIn(combinedText)
            val is1080pStream = fullHd1080pRegex.containsMatchIn(combinedText)

            if (is4KStream) {
                has4K = true
                has1080pOrBetter = true
                break
            } else if (is1080pStream) {
                has1080pOrBetter = true
            }
        }

        return StreamVerificationResult(
            isAvailable = has1080pOrBetter,
            has4K = has4K,
        )
    }

    private fun extractFourDigitYear(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val match = Regex("""\b(19|20)\d{2}\b""").find(text) ?: return null
        return match.value.toIntOrNull()
    }
}

