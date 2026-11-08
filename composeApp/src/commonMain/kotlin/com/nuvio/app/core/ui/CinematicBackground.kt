package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.max

/**
 * Creates a high-frequency micro-dither noise tile (64x64) using a triangular distribution
 * rendered at 1:1 native physical device pixels to mathematically eliminate color banding across
 * 8-bit, 10-bit, 1080p, 1440p, and 4K High-DPI panels without visible grain.
 */
private val cachedDitherBrush: ShaderBrush by lazy {
    val size = 64
    val bitmap = ImageBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    val rnd = kotlin.random.Random(1337)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val noise = rnd.nextFloat() - rnd.nextFloat()
            if (noise > 0f) {
                paint.color = Color.White.copy(alpha = noise * 0.012f)
                canvas.drawRect(Rect(x.toFloat(), y.toFloat(), x + 1f, y + 1f), paint)
            } else if (noise < 0f) {
                paint.color = Color.Black.copy(alpha = (-noise) * 0.012f)
                canvas.drawRect(Rect(x.toFloat(), y.toFloat(), x + 1f, y + 1f), paint)
            }
        }
    }
    val shader = ImageShader(
        image = bitmap,
        tileModeX = TileMode.Repeated,
        tileModeY = TileMode.Repeated,
    )
    ShaderBrush(shader)
}

/**
 * Refined, subtle studio backdrop for boot/loading, login, and profile selection pages.
 *
 * Uses a deep obsidian-slate studio base with a soft, understated radial ambient wash
 * tinted by the active profile's accent color, smooth perimeter vignetting, and 1:1 native
 * pixel anti-banding dither (no stars or distracting particles).
 */
@Composable
fun CinematicBackground(
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    showParticles: Boolean = false,
) {
    val density = LocalDensity.current
    val ditherBrush = cachedDitherBrush

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val width = size.width
                val height = size.height
                val maxDim = max(width, height)

                // 1. Clean, deep obsidian-charcoal base vertical gradient
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0xFF0A0C12),
                            0.45f to Color(0xFF08090E),
                            1.00f to Color(0xFF050609),
                        ),
                    ),
                    size = size,
                )

                // 2. Subtle, professional ambient light wash tinted with the profile accent color
                val effectiveAccent = accentColor ?: Color(0xFF3B4866)
                val ambientTint = Color(
                    red = (0x14 / 255f * 0.55f + effectiveAccent.red * 0.45f).coerceIn(0f, 1f),
                    green = (0x18 / 255f * 0.55f + effectiveAccent.green * 0.45f).coerceIn(0f, 1f),
                    blue = (0x24 / 255f * 0.55f + effectiveAccent.blue * 0.45f).coerceIn(0f, 1f),
                )

                // Soft upper-center studio key light
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to ambientTint.copy(alpha = 0.34f),
                            0.18f to ambientTint.copy(alpha = 0.27f),
                            0.38f to ambientTint.copy(alpha = 0.17f),
                            0.58f to ambientTint.copy(alpha = 0.09f),
                            0.78f to ambientTint.copy(alpha = 0.03f),
                            1.00f to Color.Transparent,
                        ),
                        center = Offset(width * 0.50f, height * 0.34f),
                        radius = maxDim * 0.95f,
                    ),
                    size = size,
                )

                // Subtle top-edge horizon wash for refined depth
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to effectiveAccent.copy(alpha = 0.09f),
                            0.28f to effectiveAccent.copy(alpha = 0.03f),
                            0.60f to Color.Transparent,
                        ),
                        startY = 0f,
                        endY = height * 0.65f,
                    ),
                    size = size,
                )

                // 3. Smooth studio perimeter & bottom vignette for high foreground contrast
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.55f to Color(0xFF040508).copy(alpha = 0.22f),
                            0.85f to Color(0xFF030406).copy(alpha = 0.55f),
                            1.00f to Color(0xFF020305).copy(alpha = 0.82f),
                        ),
                        startY = height * 0.45f,
                        endY = height,
                    ),
                    size = size,
                )

                // 4. High-DPI native 1:1 physical pixel anti-banding dither layer
                val invScale = (1f / density.density).coerceIn(0.2f, 1f)
                withTransform({
                    scale(invScale, invScale, Offset.Zero)
                }) {
                    drawRect(
                        brush = ditherBrush,
                        size = size.copy(
                            width = width / invScale,
                            height = height / invScale,
                        ),
                    )
                }
            },
    )
}
