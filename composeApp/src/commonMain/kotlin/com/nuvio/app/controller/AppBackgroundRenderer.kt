package com.nuvio.app.controller

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

internal expect fun createFrostedGlassDrawer(
    physicalWidth: Float,
    physicalHeight: Float,
    baseColor: Color,
    primaryAccent: Color,
    secondaryAccent: Color,
): DrawScope.() -> Unit

internal expect fun createCinematicStudioDrawer(
    physicalWidth: Float,
    physicalHeight: Float,
    accentColor: Color?,
): DrawScope.() -> Unit
