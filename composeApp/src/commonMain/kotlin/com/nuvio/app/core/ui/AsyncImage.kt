package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal enum class NuvioDesktopImageScaling {
    Auto,
    Disabled,
}

/** Desktop only: how animated GIF / WebP artwork plays. Other platforms already animate. */
enum class NuvioDesktopAnimation {
    /** Show the first frame. */
    Off,

    /** Show the first frame until the pointer is over the image, then play. */
    OnHover,

    /** Play whenever the image is on screen. */
    Always,
}

@Composable
internal expect fun NuvioAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: Painter? = null,
    error: Painter? = null,
    fallback: Painter? = error,
    onLoading: ((AsyncImagePainter.State.Loading) -> Unit)? = null,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality? = null,
    clipToBounds: Boolean = true,
    desktopImageScaling: NuvioDesktopImageScaling = NuvioDesktopImageScaling.Auto,
    imageLoader: ImageLoader? = null,
    /** Desktop only: playback mode for animated GIF / WebP. */
    desktopAnimation: NuvioDesktopAnimation = NuvioDesktopAnimation.Off,
)

/**
 * Preloads hover preview assets (landscape banner, logo, and poster) asynchronously with bounded
 * background concurrency so card hover popups appear immediately without stalling scroll.
 */
internal object HoverPreviewAssetPrefetcher {
    private const val MaxTrackedKeys = 600
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val backgroundSemaphore = Semaphore(permits = 2)
    private val stateMutex = Mutex()
    private val completedKeys = HashSet<String>(MaxTrackedKeys)
    private val completedOrder = ArrayDeque<String>(MaxTrackedKeys)
    private val backgroundInFlightKeys = HashSet<String>()
    private val immediateInFlightKeys = HashSet<String>()

    fun prefetchBackground(
        context: PlatformContext,
        bannerUrl: String?,
        logoUrl: String?,
        posterUrl: String?,
    ) {
        enqueueAssets(
            context = context,
            bannerUrl = bannerUrl,
            logoUrl = logoUrl,
            posterUrl = posterUrl,
            throttle = true,
        )
    }

    fun prefetchImmediate(
        context: PlatformContext,
        bannerUrl: String?,
        logoUrl: String?,
        posterUrl: String?,
    ) {
        enqueueAssets(
            context = context,
            bannerUrl = bannerUrl,
            logoUrl = logoUrl,
            posterUrl = posterUrl,
            throttle = false,
        )
    }

    private fun enqueueAssets(
        context: PlatformContext,
        bannerUrl: String?,
        logoUrl: String?,
        posterUrl: String?,
        throttle: Boolean,
    ) {
        val artworkUrl = bannerUrl?.takeIf { it.isNotBlank() } ?: posterUrl?.takeIf { it.isNotBlank() }
        val normalizedLogo = logoUrl?.takeIf { it.isNotBlank() }
        val normalizedPoster = posterUrl?.takeIf { it.isNotBlank() }
        if (artworkUrl == null && normalizedLogo == null && normalizedPoster == null) return

        scope.launch {
            if (artworkUrl != null) {
                launchSingleAsset(context, artworkUrl, NuvioDesktopAnimation.Always, throttle)
            }
            if (normalizedLogo != null) {
                launchSingleAsset(context, normalizedLogo, NuvioDesktopAnimation.Off, throttle)
            }
            if (normalizedPoster != null && normalizedPoster != artworkUrl) {
                launchSingleAsset(context, normalizedPoster, NuvioDesktopAnimation.Off, throttle)
            }
        }
    }

    private fun CoroutineScope.launchSingleAsset(
        context: PlatformContext,
        url: String,
        desktopAnimation: NuvioDesktopAnimation,
        throttle: Boolean,
    ) {
        val cacheKey = "${desktopAnimation.name}|$url"
        launch {
            val inFlightSet = if (throttle) backgroundInFlightKeys else immediateInFlightKeys
            val shouldFetch = stateMutex.withLock {
                if (completedKeys.contains(cacheKey) || inFlightSet.contains(cacheKey)) {
                    false
                } else {
                    inFlightSet.add(cacheKey)
                    true
                }
            }
            if (!shouldFetch) return@launch

            try {
                if (throttle) {
                    backgroundSemaphore.withPermit {
                        val alreadyCompleted = stateMutex.withLock { completedKeys.contains(cacheKey) }
                        if (!alreadyCompleted) {
                            prefetchNuvioImage(context, url, desktopAnimation)
                            markCompleted(cacheKey)
                        }
                    }
                } else {
                    val alreadyCompleted = stateMutex.withLock { completedKeys.contains(cacheKey) }
                    if (!alreadyCompleted) {
                        prefetchNuvioImage(context, url, desktopAnimation)
                        markCompleted(cacheKey)
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    stateMutex.withLock { inFlightSet.remove(cacheKey) }
                }
            }
        }
    }

    private suspend fun markCompleted(cacheKey: String) {
        stateMutex.withLock {
            if (completedKeys.add(cacheKey)) {
                if (completedOrder.size >= MaxTrackedKeys) {
                    completedOrder.removeFirstOrNull()?.let { completedKeys.remove(it) }
                }
                completedOrder.addLast(cacheKey)
            }
        }
    }
}

/**
 * Asynchronously warms the image loader cache using the same decode and animation cache key options
 * as [NuvioAsyncImage].
 */
internal expect suspend fun prefetchNuvioImage(
    context: PlatformContext,
    url: String,
    desktopAnimation: NuvioDesktopAnimation = NuvioDesktopAnimation.Off,
)
