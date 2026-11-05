package com.nuvio.app.core.ui

import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

private const val ImageMemoryCacheBytes = 512L * 1024 * 1024
private const val ImageDiskCacheBytes = 1024L * 1024 * 1024

internal actual val platformProvidesImageLoader: Boolean = false

@OptIn(ExperimentalCoroutinesApi::class)
internal actual fun ImageLoader.Builder.configurePlatformImageLoader(): ImageLoader.Builder {
    val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
    return memoryCache {
        MemoryCache.Builder()
            .maxSizeBytes(ImageMemoryCacheBytes)
            .build()
    }.diskCache {
        DiskCache.Builder()
            .directory(DesktopStorage.cacheDir.resolve("image_cache").toFile())
            .maxSizeBytes(ImageDiskCacheBytes)
            .build()
    }.fetcherCoroutineContext(
        Dispatchers.IO.limitedParallelism(32),
    ).decoderCoroutineContext(
        Dispatchers.Default.limitedParallelism(cpuCores.coerceIn(4, 12)),
    )
}

internal actual fun ComponentRegistry.Builder.addPlatformDecoders() {
    add(SkiaGifDecoder.Factory())
    add(SvgEmbeddedImageDecoder.Factory())
}
