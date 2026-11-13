package com.nuvio.app.controller

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

private const val FROSTED_GLASS_SKSL = """
uniform float2 uResolution;
uniform float3 uBaseColor;
uniform float3 uPrimaryAccent;
uniform float3 uSecondaryAccent;

float gaussianOrb(float2 uv, float2 center, float radius, float aspect) {
    float2 d = float2((uv.x - center.x) * aspect, uv.y - center.y) / radius;
    return exp(-dot(d, d) * 2.55);
}

float ign(float2 px) {
    return fract(52.9829189 * fract(dot(px, float2(0.06711056, 0.00583715))));
}

half4 main(float2 fragCoord) {
    float2 res = max(uResolution, float2(1.0, 1.0));
    float2 uv = clamp(fragCoord / res, 0.0, 1.0);
    float aspect = res.x / res.y;

    // 1. Deep obsidian frost base with dark center-to-corner valleys for crisp contrast
    float3 topBase = mix(uBaseColor, float3(0.048, 0.056, 0.082), 0.78);
    float3 midBase = mix(uBaseColor, float3(0.032, 0.038, 0.056), 0.82);
    float3 botBase = mix(uBaseColor, float3(0.024, 0.028, 0.042), 0.85);

    float3 col = mix(topBase, midBase, smoothstep(0.0, 0.52, uv.y));
    col = mix(col, botBase, smoothstep(0.45, 1.0, uv.y));

    // 2. Five distinct localized multi-hue Gaussian color pools (clear color & luminance differentials)
    // Upper-left: frosted ice-slate blended with primary theme accent
    float3 orb1Col = mix(float3(0.180, 0.290, 0.440), uPrimaryAccent, 0.36);
    // Upper-right: deep sapphire-indigo mist blended with secondary accent
    float3 orb2Col = mix(float3(0.115, 0.165, 0.380), uSecondaryAccent, 0.34);
    // Mid-right: warm amethyst/plum frosted reflection for subtle warm-vs-cool contrast
    float3 orb3Col = mix(float3(0.245, 0.125, 0.285), uPrimaryAccent, 0.30);
    // Lower-left: deep frosted teal-cerulean pool for unmistakable lower-half depth
    float3 orb4Col = mix(float3(0.085, 0.235, 0.310), uSecondaryAccent, 0.28);
    // Lower-right: vibrant twilight indigo-accent bloom balancing lower-left teal
    float3 orb5Col = mix(float3(0.140, 0.130, 0.340), uPrimaryAccent, 0.44);

    float w1 = gaussianOrb(uv, float2(0.13, 0.10), 0.58, aspect) * 0.34;
    float w2 = gaussianOrb(uv, float2(0.88, 0.14), 0.52, aspect) * 0.32;
    float w3 = gaussianOrb(uv, float2(0.76, 0.55), 0.50, aspect) * 0.28;
    float w4 = gaussianOrb(uv, float2(0.16, 0.85), 0.58, aspect) * 0.35;
    float w5 = gaussianOrb(uv, float2(0.86, 0.88), 0.52, aspect) * 0.32;

    col += orb1Col * w1;
    col += orb2Col * w2;
    col += orb3Col * w3;
    col += orb4Col * w4;
    col += orb5Col * w5;

    // 3. Soft diagonal frosted glass sheen across top-left toward lower-right
    float diag = clamp(1.0 - abs((uv.x * 0.72 + uv.y * 0.55) - 0.52) * 2.1, 0.0, 1.0);
    col += mix(float3(0.9, 0.94, 1.0), uPrimaryAccent, 0.25) * (diag * diag * 0.022);

    // 4. Gentle perimeter vignette keeping lower & outer color clouds rich
    float2 vCenter = (uv - float2(0.5, 0.5)) * float2(1.0, 0.96);
    float vig = 1.0 - smoothstep(0.48, 1.32, length(vCenter)) * 0.10;
    col *= vig;

    // 5. True FP32 Triangular Interleaved Gradient Noise dithering + micro-frost texture
    float ignA = ign(fragCoord);
    float ignB = ign(fragCoord + float2(17.0, 41.0));
    float triDither = ignA - ignB;
    float microFrost = ign(fragCoord + float2(5.0, 19.0)) - 0.5;
    col += (triDither * 1.60 + microFrost * 0.75) / 255.0;

    return half4(clamp(col, 0.0, 1.0), 1.0);
}
"""

private const val CINEMATIC_STUDIO_SKSL = """
uniform float2 uResolution;
uniform float3 uAccentColor;

float gaussianOrb(float2 uv, float2 center, float radius, float aspect) {
    float2 d = float2((uv.x - center.x) * aspect, uv.y - center.y) / radius;
    return exp(-dot(d, d) * 1.95);
}

float ign(float2 px) {
    return fract(52.9829189 * fract(dot(px, float2(0.06711056, 0.00583715))));
}

half4 main(float2 fragCoord) {
    float2 res = max(uResolution, float2(1.0, 1.0));
    float2 uv = clamp(fragCoord / res, 0.0, 1.0);
    float aspect = res.x / res.y;

    // 1. Deep obsidian-charcoal base vertical studio gradient
    float3 topCol = float3(0.039, 0.047, 0.071); // #0A0C12
    float3 midCol = float3(0.031, 0.035, 0.055); // #08090E
    float3 botCol = float3(0.020, 0.024, 0.035); // #050609

    float3 col = mix(topCol, midCol, smoothstep(0.0, 0.48, uv.y));
    col = mix(col, botCol, smoothstep(0.45, 1.0, uv.y));

    // 2. Soft profile-accented studio key light & balanced lower counter-clouds
    float3 ambientTint = mix(float3(0.078, 0.094, 0.141), uAccentColor, 0.45);
    float3 counterTint = mix(float3(0.055, 0.072, 0.118), uAccentColor, 0.28);

    float keyGlow = gaussianOrb(uv, float2(0.50, 0.34), 0.92, aspect) * 0.34;
    float upperHorizon = gaussianOrb(uv, float2(0.22, 0.10), 0.85, aspect) * 0.11;
    float lowerLeftGlow = gaussianOrb(uv, float2(0.20, 0.80), 0.88, aspect) * 0.13;
    float lowerRightGlow = gaussianOrb(uv, float2(0.82, 0.84), 0.86, aspect) * 0.11;

    col += ambientTint * keyGlow;
    col += uAccentColor * (upperHorizon * 0.45);
    col += counterTint * (lowerLeftGlow + lowerRightGlow);

    // 3. Subtle top-edge horizon accent wash
    float horizonT = clamp(1.0 - uv.y * 1.75, 0.0, 1.0);
    col += uAccentColor * (horizonT * horizonT * 0.045);

    // 4. Smooth studio bottom & perimeter vignette preserving contrast
    float botDark = smoothstep(0.48, 1.0, uv.y) * 0.28;
    float2 vCenter = (uv - float2(0.5, 0.45)) * float2(1.05, 1.0);
    float edgeVig = smoothstep(0.42, 1.25, length(vCenter)) * 0.16;
    col *= (1.0 - clamp(botDark + edgeVig, 0.0, 0.42));

    // 5. True FP32 Triangular Interleaved Gradient Noise dithering before quantization
    float ignA = ign(fragCoord);
    float ignB = ign(fragCoord + float2(17.0, 41.0));
    float triDither = ignA - ignB;
    float microFrost = ign(fragCoord + float2(5.0, 19.0)) - 0.5;
    col += (triDither * 1.60 + microFrost * 0.65) / 255.0;

    return half4(clamp(col, 0.0, 1.0), 1.0);
}
"""

private val frostedGlassEffect: RuntimeEffect? by lazy {
    runCatching { RuntimeEffect.makeForShader(FROSTED_GLASS_SKSL) }.getOrNull()
}

private val cinematicStudioEffect: RuntimeEffect? by lazy {
    runCatching { RuntimeEffect.makeForShader(CINEMATIC_STUDIO_SKSL) }.getOrNull()
}

internal actual fun createFrostedGlassDrawer(
    physicalWidth: Float,
    physicalHeight: Float,
    baseColor: Color,
    primaryAccent: Color,
    secondaryAccent: Color,
): DrawScope.() -> Unit {
    val w = physicalWidth.coerceAtLeast(1f)
    val h = physicalHeight.coerceAtLeast(1f)
    val effect = frostedGlassEffect
    val skiaShader = if (effect != null) {
        runCatching {
            RuntimeShaderBuilder(effect).apply {
                uniform("uResolution", w, h)
                uniform("uBaseColor", baseColor.red, baseColor.green, baseColor.blue)
                uniform("uPrimaryAccent", primaryAccent.red, primaryAccent.green, primaryAccent.blue)
                uniform("uSecondaryAccent", secondaryAccent.red, secondaryAccent.green, secondaryAccent.blue)
            }.makeShader()
        }.getOrNull()
    } else null

    if (skiaShader != null) {
        val paint = Paint().apply { shader = skiaShader }
        val rect = Rect.makeWH(w, h)
        return {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRect(rect, paint)
            }
        }
    }

    val fallbackBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to lerp(baseColor, Color(0xFF151923), 0.78f),
            0.50f to lerp(baseColor, Color(0xFF0D1017), 0.80f),
            1.00f to lerp(baseColor, Color(0xFF07080D), 0.86f),
        ),
    )
    val fallbackSize = Size(w, h)
    return {
        drawRect(brush = fallbackBrush, size = fallbackSize)
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
    val effect = cinematicStudioEffect
    val skiaShader = if (effect != null) {
        runCatching {
            RuntimeShaderBuilder(effect).apply {
                uniform("uResolution", w, h)
                uniform("uAccentColor", effectiveAccent.red, effectiveAccent.green, effectiveAccent.blue)
            }.makeShader()
        }.getOrNull()
    } else null

    if (skiaShader != null) {
        val paint = Paint().apply { shader = skiaShader }
        val rect = Rect.makeWH(w, h)
        return {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRect(rect, paint)
            }
        }
    }

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
        radius = kotlin.math.max(w, h) * 0.90f,
    )
    val fallbackSize = Size(w, h)
    return {
        drawRect(brush = baseBrush, size = fallbackSize)
        drawRect(brush = glowBrush, size = fallbackSize)
    }
}
