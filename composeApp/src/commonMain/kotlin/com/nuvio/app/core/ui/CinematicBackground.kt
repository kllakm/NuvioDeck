package com.nuvio.app.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Procedural static stardust / atmospheric dust mote.
 */
private data class DustMote(
    val relX: Float,
    val relY: Float,
    val radiusDp: Float,
    val baseAlpha: Float,
    val speedX: Float,
    val speedY: Float,
    val twinkleSpeed: Float,
    val phase: Float,
    val color: Color,
    val hasGlow: Boolean,
)

/**
 * Creates a high-frequency micro-dither noise tile (128x128) using a triangular distribution
 * to mathematically eliminate color banding across 8-bit, 10-bit, 1080p, and 4K TV/desktop panels.
 */
private fun createCinematicDitherBitmap(): ImageBitmap {
    val size = 128
    val bitmap = ImageBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    val rnd = kotlin.random.Random(1337)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val noise = rnd.nextFloat() - rnd.nextFloat()
            if (noise > 0f) {
                paint.color = Color.White.copy(alpha = noise * 0.042f)
                canvas.drawRect(Rect(x.toFloat(), y.toFloat(), x + 1f, y + 1f), paint)
            } else if (noise < 0f) {
                paint.color = Color.Black.copy(alpha = (-noise) * 0.042f)
                canvas.drawRect(Rect(x.toFloat(), y.toFloat(), x + 1f, y + 1f), paint)
            }
        }
    }
    return bitmap
}

/**
 * Ultra-smooth, dark cinematic background for boot/loading, login, and profile selection pages.
 *
 * Implements:
 * 1. Deep saturated organic gradient: velvet indigo-navy top-left (#090A10), soft glowing deep
 *    violet/amethyst core (#1C1430 to #3E1F5C), and rich dark plum/aubergine/crimson shadows (#2B0C20 to #0F050A).
 * 2. Strict banding prevention via a high-frequency triangular dither/film-grain noise layer.
 * 3. Multi-tiered atmospheric depth: breathing volumetric nebula blooms and subtle floating stardust motes.
 * 4. Deep perimeter and bottom vignetting to guarantee foreground text, input fields, and profile cards maintain
 *    maximum legibility and high contrast.
 */
@Composable
fun CinematicBackground(
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    showParticles: Boolean = true,
) {
    val density = LocalDensity.current

    // Micro-dither tile shader (created once, cached for the lifetime of the composition)
    val ditherBrush = remember {
        val ditherTile = createCinematicDitherBitmap()
        val shader = ImageShader(
            image = ditherTile,
            tileModeX = TileMode.Repeated,
            tileModeY = TileMode.Repeated,
        )
        ShaderBrush(shader)
    }

    // Deterministic atmospheric particles matching the celestial nebula depth
    val dustMotes = remember {
        val colors = listOf(
            Color(0xFFE2D4F8), // Soft lilac
            Color(0xFFFCD4E5), // Pale rose / crimson
            Color(0xFFF0F2FA), // Starlight white
            Color(0xFFD8C7F0), // Violet dust
        )
        val rnd = kotlin.random.Random(42)
        List(38) { i ->
            DustMote(
                relX = rnd.nextFloat(),
                relY = 0.06f + rnd.nextFloat() * 0.76f, // concentrate around upper and middle nebula zone
                radiusDp = 0.75f + rnd.nextFloat() * 1.45f,
                baseAlpha = 0.12f + rnd.nextFloat() * 0.38f,
                speedX = 0.4f + rnd.nextFloat() * 0.6f,
                speedY = 0.3f + rnd.nextFloat() * 0.7f,
                twinkleSpeed = 0.6f + rnd.nextFloat() * 1.2f,
                phase = rnd.nextFloat() * 6.2831855f,
                color = colors[i % colors.size],
                hasGlow = rnd.nextFloat() > 0.65f,
            )
        }
    }

    // Two desynchronized subtle motion loops to create organic breathing without repetition
    val infiniteTransition = rememberInfiniteTransition(label = "cinematic_nebula_transition")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cinematic_pulse_phase",
    )
    val driftPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 32000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cinematic_drift_phase",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val width = size.width
                val height = size.height
                val maxDim = max(width, height)

                // 1. Deep space base
                drawRect(color = Color(0xFF070407), size = size)

                // 2. Center warm dark wine/crimson glow (Netflix-inspired cinematic ambiance)
                val glowCenter = Offset(
                    x = width * (0.50f + 0.015f * sin(pulsePhase)),
                    y = height * (0.38f + 0.012f * cos(pulsePhase)),
                )
                val glowRadius = maxDim * (1.18f + 0.03f * sin(pulsePhase))
                val coreAlpha = 0.52f + 0.03f * sin(pulsePhase)
                val coreColor = if (accentColor != null) {
                    Color(
                        red = (0x3A / 255f * 0.65f + accentColor.red * 0.35f),
                        green = (0x12 / 255f * 0.65f + accentColor.green * 0.35f),
                        blue = (0x1A / 255f * 0.65f + accentColor.blue * 0.35f),
                    )
                } else {
                    Color(0xFF38121A)
                }

                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to coreColor.copy(alpha = coreAlpha),
                            0.18f to coreColor.copy(alpha = coreAlpha * 0.82f),
                            0.36f to Color(0xFF260C14).copy(alpha = 0.38f),
                            0.55f to Color(0xFF18070D).copy(alpha = 0.28f),
                            0.72f to Color(0xFF0F0408).copy(alpha = 0.18f),
                            0.88f to Color(0xFF070204).copy(alpha = 0.08f),
                            1.00f to Color.Transparent,
                        ),
                        center = glowCenter,
                        radius = glowRadius,
                    ),
                    size = size,
                )

                // 3. Gentle perimeter and vertical vignetting for contrast
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.50f to Color(0xFF050204).copy(alpha = 0.25f),
                            0.80f to Color(0xFF030102).copy(alpha = 0.65f),
                            1.00f to Color(0xFF020102).copy(alpha = 0.90f),
                        ),
                        startY = height * 0.45f,
                        endY = height,
                    ),
                    size = size,
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.65f to Color.Transparent,
                            0.85f to Color(0xFF040203).copy(alpha = 0.35f),
                            1.00f to Color(0xFF020102).copy(alpha = 0.70f),
                        ),
                        center = Offset(width * 0.5f, height * 0.5f),
                        radius = maxDim * 0.85f,
                    ),
                    size = size,
                )

                // 7. Subtle floating stardust / atmospheric particles
                if (showParticles) {
                    for (mote in dustMotes) {
                        val driftX = sin(pulsePhase * mote.speedX + mote.phase) * (density.density * 5f)
                        val driftY = cos(driftPhase * mote.speedY + mote.phase) * (density.density * 7f)
                        val cx = mote.relX * width + driftX
                        val cy = mote.relY * height + driftY

                        val twinkle = 0.75f + 0.25f * sin(pulsePhase * mote.twinkleSpeed + mote.phase)
                        val alpha = (mote.baseAlpha * twinkle).coerceIn(0f, 1f)
                        val radiusPx = mote.radiusDp * density.density

                        if (mote.hasGlow) {
                            drawCircle(
                                color = mote.color.copy(alpha = alpha * 0.28f),
                                radius = radiusPx * 2.8f,
                                center = Offset(cx, cy),
                            )
                        }
                        drawCircle(
                            color = mote.color.copy(alpha = alpha),
                            radius = radiusPx,
                            center = Offset(cx, cy),
                        )
                    }
                }

                // 8. Banding-prevention dither & natural cinematic film-grain layer
                drawRect(brush = ditherBrush, size = size)
            },
    )
}

