package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.nuvio.app.controller.nuvioCinematicBackground

/**
 * Refined, subtle studio backdrop for boot/loading, login, and profile selection pages.
 *
 * Delegates rendering to [nuvioCinematicBackground] so desktop builds evaluate the deep
 * obsidian-charcoal studio gradient and animated profile accent wash in a 32-bit floating-point
 * SkSL shader with 1:1 physical-pixel Triangular Interleaved Gradient Noise dithering to prevent
 * contour banding across displays.
 */
@Composable
fun CinematicBackground(
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    showParticles: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .nuvioCinematicBackground(accentColor = accentColor),
    )
}
