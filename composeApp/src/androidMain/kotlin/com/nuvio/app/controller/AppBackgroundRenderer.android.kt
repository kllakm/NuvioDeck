package com.nuvio.app.controller

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.math.max

internal actual fun createFrostedGlassDrawer(
    physicalWidth: Float,
    physicalHeight: Float,
    baseColor: Color,
    primaryAccent: Color,
    secondaryAccent: Color,
): DrawScope.() -> Unit {
    val brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to lerp(baseColor, Color(0xFF151923), 0.78f),
            0.50f to lerp(baseColor, Color(0xFF0D1017), 0.80f),
            1.00f to lerp(baseColor, Color(0xFF07080D), 0.86f),
        ),
    )
    val rectSize = Size(physicalWidth.coerceAtLeast(1f), physicalHeight.coerceAtLeast(1f))
    return {
        drawRect(brush = brush, size = rectSize)
    }
}

internal actual fun createCinematicStudioDrawer(
    physicalWidth: Float,
    physicalHeight: Float,
    accentColor: Color?,
): DrawScope.() -> Unit {
    val w = physicalWidth.coerceAtLeast(1f)
    val h = physicalHeight.coerceAtLeast(1f)
    val effectiveAccent = accentColor ?: Color(0xFF3B4866)
    val baseBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to Color(0xFF0A0C12),
            0.45f to Color(0xFF08090E),
            1.00f to Color(0xFF050609),
        ),
    )
    val glowBrush = Brush.radialGradient(
        colorStops = arrayOf(
            0.00f to effectiveAccent.copy(alpha = 0.24f),
            0.55f to effectiveAccent.copy(alpha = 0.08f),
            1.00f to Color.Transparent,
        ),
        center = Offset(w * 0.50f, h * 0.34f),
        radius = max(w, h) * 0.90f,
    )
    val rectSize = Size(w, h)
    return {
        drawRect(brush = baseBrush, size = rectSize)
        drawRect(brush = glowBrush, size = rectSize)
    }
}
