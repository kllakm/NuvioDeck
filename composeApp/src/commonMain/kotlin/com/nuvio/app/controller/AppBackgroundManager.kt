package com.nuvio.app.controller

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the application's toggleable backdrop mode:
 * - Frosted Glass (default): 32-bit floating-point GPU ambient mesh shader (Desktop SkSL)
 *   combining 5 softly blurred Gaussian light clouds, smoked obsidian base, delicate rim sheen,
 *   perimeter vignette, and 1:1 physical-pixel Triangular Interleaved Gradient Noise dithering
 *   to eliminate contour banding across 6-bit/8-bit/10-bit displays.
 * - Classic Flat: upstream flat solid theme background.
 *
 * When AMOLED Black is enabled (background == Color.Black), pure black is always preserved.
 */
object AppBackgroundManager {
    private val _glassEnabled = MutableStateFlow(AppBackgroundStorage.loadGlassEnabled() ?: true)
    val glassEnabled: StateFlow<Boolean> = _glassEnabled.asStateFlow()

    fun setGlassEnabled(enabled: Boolean) {
        _glassEnabled.value = enabled
        AppBackgroundStorage.saveGlassEnabled(enabled)
    }

    @Composable
    fun isFrostedGlassActive(baseColor: Color = MaterialTheme.nuvio.colors.background): Boolean {
        val enabled by glassEnabled.collectAsStateWithLifecycle()
        return enabled && baseColor != Color.Black
    }
}

/**
 * Applies the selected app backdrop (Frosted Glass GPU-dithered gradient or Classic Flat).
 *
 * @param fallbackColor Optional explicit background color (defaults to current theme background).
 * @param transparentInGlassMode When true, skips drawing in Frosted Glass mode so non-sticky
 *        inline headers seamlessly reveal the continuous backdrop behind them.
 * @param frostedScrimOnly When true, draws a matching soft translucent top scrim (ideal for sticky
 *        overlay headers like CatalogHeader over scrolling content) instead of a full-height gradient.
 */
fun Modifier.nuvioAppBackground(
    fallbackColor: Color? = null,
    transparentInGlassMode: Boolean = false,
    frostedScrimOnly: Boolean = false,
): Modifier = composed {
    val colors = MaterialTheme.nuvio.colors
    val scheme = MaterialTheme.colorScheme
    val baseColor = fallbackColor ?: colors.background
    val glassEnabled by AppBackgroundManager.glassEnabled.collectAsStateWithLifecycle()
    val useGlass = glassEnabled && baseColor != Color.Black && fallbackColor != Color.Transparent

    if (!useGlass) {
        if (baseColor == Color.Transparent) {
            this
        } else {
            drawBehind {
                drawRect(baseColor)
            }
        }
    } else if (transparentInGlassMode) {
        this
    } else if (frostedScrimOnly) {
        val topSlate = lerp(baseColor, Color(0xFF151923), 0.76f)
        drawBehind {
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to topSlate.copy(alpha = 0.94f),
                        0.55f to topSlate.copy(alpha = 0.86f),
                        0.82f to topSlate.copy(alpha = 0.45f),
                        1.00f to Color.Transparent,
                    ),
                ),
            )
        }
    } else {
        val density = LocalDensity.current.density
        val primaryAccent = scheme.primary
        val secondaryAccent = scheme.secondary

        drawWithCache {
            val invScale = (1f / density).coerceIn(0.2f, 1f)
            val physicalWidth = (size.width / invScale).coerceAtLeast(1f)
            val physicalHeight = (size.height / invScale).coerceAtLeast(1f)
            val brushDrawer = createFrostedGlassDrawer(
                physicalWidth = physicalWidth,
                physicalHeight = physicalHeight,
                baseColor = baseColor,
                primaryAccent = primaryAccent,
                secondaryAccent = secondaryAccent,
            )
            onDrawBehind {
                withTransform({
                    scale(invScale, invScale, Offset.Zero)
                }) {
                    brushDrawer()
                }
            }
        }
    }
}

/**
 * Applies the 32-bit FP SkSL studio backdrop for boot/loading, login, and profile screens,
 * eliminating contour banding via Gaussian light clouds and 1:1 physical-pixel IGN dithering.
 */
fun Modifier.nuvioCinematicBackground(accentColor: Color? = null): Modifier = composed {
    val density = LocalDensity.current.density
    drawWithCache {
        val invScale = (1f / density).coerceIn(0.2f, 1f)
        val physicalWidth = (size.width / invScale).coerceAtLeast(1f)
        val physicalHeight = (size.height / invScale).coerceAtLeast(1f)
        val brushDrawer = createCinematicStudioDrawer(
            physicalWidth = physicalWidth,
            physicalHeight = physicalHeight,
            accentColor = accentColor,
        )
        onDrawBehind {
            withTransform({
                scale(invScale, invScale, Offset.Zero)
            }) {
                brushDrawer()
            }
        }
    }
}

/**
 * Feather-masks Home hero backdrop artwork directly to transparent edges when Frosted Glass
 * background mode is active, eliminating opaque flat-background seams.
 */
fun Modifier.nuvioHeroBackdropMask(
    enabled: Boolean,
    useDesktopFrame: Boolean,
    topFadeHeightPx: Float = 0f,
    bottomFadeHeightPx: Float = 0f,
    topOverlayAlpha: Float = 0.78f,
): Modifier {
    if (!enabled) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (useDesktopFrame) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.04f),
                            0.08f to Color.Black.copy(alpha = 0.10f),
                            0.16f to Color.Black.copy(alpha = 0.24f),
                            0.26f to Color.Black.copy(alpha = 0.46f),
                            0.36f to Color.Black.copy(alpha = 0.70f),
                            0.46f to Color.Black.copy(alpha = 0.88f),
                            0.54f to Color.Black,
                        ),
                    ),
                    blendMode = BlendMode.DstIn,
                )
                if (topFadeHeightPx > 0f && size.height > 0f) {
                    val topStop = (topFadeHeightPx / size.height).coerceIn(0.01f, 0.5f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(alpha = (1f - topOverlayAlpha).coerceIn(0f, 1f)),
                                topStop to Color.Black,
                            ),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                if (bottomFadeHeightPx > 0f && size.height > 0f) {
                    val bottomStart = (1f - (bottomFadeHeightPx / size.height)).coerceIn(0.2f, 0.99f)
                    val bottomMid = ((bottomStart + 1f) * 0.5f).coerceIn(bottomStart + 0.005f, 0.995f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                bottomStart to Color.Black,
                                bottomMid to Color.Black.copy(alpha = 0.45f),
                                1.00f to Color.Transparent,
                            ),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            } else {
                val bottomFadeFrac = if (bottomFadeHeightPx > 0f && size.height > 0f) {
                    (bottomFadeHeightPx / size.height).coerceIn(0.05f, 0.7f)
                } else {
                    0.25f
                }
                val fadeStart = (1f - bottomFadeFrac * 1.45f).coerceIn(0.15f, 0.85f)
                val midFade = (fadeStart + (1f - fadeStart) * 0.45f).coerceIn(fadeStart + 0.01f, 0.98f)
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.98f),
                            fadeStart to Color.Black.copy(alpha = 0.66f),
                            midFade to Color.Black.copy(alpha = 0.22f),
                            1.00f to Color.Transparent,
                        ),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}
