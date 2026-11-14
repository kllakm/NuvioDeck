package com.nuvio.app.controller

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

private const val WhiteButtonLuminanceThreshold = 0.72f
private var nextControllerButtonFocusCounter = 0L

internal val ControllerFocusedWhiteButtonBg = Color(0xFF0D0E15)
internal val ControllerFocusedWhiteButtonFg = Color.White
internal val ControllerFocusedWhiteButtonBorder = BorderStroke(2.dp, Color.White)

internal data class ControllerWhiteButtonColors(
    val isWhiteButton: Boolean,
    val isSelected: Boolean,
    val containerColor: Color,
    val contentColor: Color,
)

@Composable
internal fun rememberControllerButtonFocusId(prefix: String = "ctrl_btn"): String {
    return remember(prefix) { "${prefix}_${++nextControllerButtonFocusCounter}" }
}

internal fun Color.isWhiteButtonFill(): Boolean =
    alpha >= 0.75f && luminance() >= WhiteButtonLuminanceThreshold

/**
 * Resolves consistent controller-selected fill and content colors for white / bright primary buttons
 * while letting `.controllerFocusable(...)` draw its standard white focus border and gradient sheen.
 */
@Composable
internal fun rememberControllerWhiteButtonColors(
    focusId: String,
    defaultContainerColor: Color,
    defaultContentColor: Color,
    enabled: Boolean = true,
): ControllerWhiteButtonColors {
    val isFocused = enabled && focusId.isNotEmpty() && isControllerNodeFocused(focusId)
    val isWhite = defaultContainerColor.isWhiteButtonFill()
    val applySelectedStyle = isFocused && isWhite
    return remember(
        isWhite,
        applySelectedStyle,
        defaultContainerColor,
        defaultContentColor,
    ) {
        ControllerWhiteButtonColors(
            isWhiteButton = isWhite,
            isSelected = applySelectedStyle,
            containerColor = if (applySelectedStyle) ControllerFocusedWhiteButtonBg else defaultContainerColor,
            contentColor = if (applySelectedStyle) ControllerFocusedWhiteButtonFg else defaultContentColor,
        )
    }
}

