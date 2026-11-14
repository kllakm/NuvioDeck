package com.nuvio.app.core.poster

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.compose.LocalPlatformContext
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult

private val ImdbMetaIdRegex = Regex("""^tt\d{5,}$""")

/**
 * Coil interceptor that detects failed artwork loads and retries any configured
 * fallback URLs stored in [memoryCacheKeyExtras] ([FALLBACK_URL_KEY] then
 * [SECONDARY_FALLBACK_URL_KEY]).
 */
class CustomPosterFallbackInterceptor : Interceptor {

    companion object {
        const val FALLBACK_URL_KEY = "custom_poster_fallback_url"
        const val SECONDARY_FALLBACK_URL_KEY = "custom_poster_secondary_fallback_url"
    }

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val extras = chain.request.memoryCacheKeyExtras
        val fallbackUrls = listOfNotNull(
            extras[FALLBACK_URL_KEY]?.takeIf { it.isNotBlank() },
            extras[SECONDARY_FALLBACK_URL_KEY]?.takeIf { it.isNotBlank() },
        )

        var result = chain.proceed()
        if (result !is ErrorResult || fallbackUrls.isEmpty()) return result

        val cleanedExtras = extras - FALLBACK_URL_KEY - SECONDARY_FALLBACK_URL_KEY
        val attempted = hashSetOf(chain.request.data?.toString())
        for (candidateUrl in fallbackUrls) {
            if (!attempted.add(candidateUrl)) continue
            val fallbackRequest = chain.request.newBuilder()
                .data(candidateUrl)
                .memoryCacheKeyExtras(cleanedExtras)
                .build()
            result = chain.withRequest(fallbackRequest).proceed()
            if (result !is ErrorResult) return result
        }

        return result
    }
}

fun metahubFallbackArtworkUrl(
    id: String?,
    isLandscape: Boolean = false,
): String? {
    val imdbId = id?.trim()?.substringBefore(':').orEmpty()
    if (!ImdbMetaIdRegex.matches(imdbId)) return null
    val segment = if (isLandscape) "background" else "poster"
    return "https://images.metahub.space/$segment/medium/$imdbId/img"
}

@Composable
fun rememberArtworkImageModel(
    primaryUrl: String?,
    fallbackUrl: String? = null,
    secondaryFallbackUrl: String? = null,
): Any? {
    val platformContext = LocalPlatformContext.current
    val candidates = remember(primaryUrl, fallbackUrl, secondaryFallbackUrl) {
        buildList {
            listOf(primaryUrl, fallbackUrl, secondaryFallbackUrl).forEach { candidate ->
                val trimmed = candidate?.trim()?.takeIf { it.isNotEmpty() }
                if (trimmed != null && trimmed !in this) {
                    add(trimmed)
                }
            }
        }
    }
    val resolvedPrimary = candidates.getOrNull(0) ?: return null
    val resolvedFallback = candidates.getOrNull(1)
    val resolvedSecondaryFallback = candidates.getOrNull(2)
    return remember(resolvedPrimary, resolvedFallback, resolvedSecondaryFallback, platformContext) {
        if (resolvedFallback != null || resolvedSecondaryFallback != null) {
            val extras = buildMap {
                if (resolvedFallback != null) {
                    put(CustomPosterFallbackInterceptor.FALLBACK_URL_KEY, resolvedFallback)
                }
                if (resolvedSecondaryFallback != null) {
                    put(CustomPosterFallbackInterceptor.SECONDARY_FALLBACK_URL_KEY, resolvedSecondaryFallback)
                }
            }
            ImageRequest.Builder(platformContext)
                .data(resolvedPrimary)
                .memoryCacheKeyExtras(extras)
                .build()
        } else {
            resolvedPrimary
        }
    }
}
