package com.nuvio.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.nuvio.app.controller.controllerFocusable

@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tokens = MaterialTheme.nuvio
    val chipShape = RoundedCornerShape(NuvioTokens.Radius.md)
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.controllerFocusable(
            enabled = enabled,
            shape = chipShape,
            scaleOnFocus = 1.03f,
            onSelect = onClick,
            selectLabel = label,
        ),
        enabled = enabled,
        shape = chipShape,
        color = if (selected) tokens.colors.overlaySelected else Color.Transparent,
        contentColor = when {
            !enabled -> tokens.colors.textDisabled
            selected -> tokens.colors.textPrimary
            else -> tokens.colors.textMuted
        },
        border = BorderStroke(
            width = tokens.borders.thin,
            color = when {
                selected -> tokens.colors.borderSelected
                enabled -> tokens.colors.borderStrong
                else -> tokens.colors.borderDefault
            },
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = NuvioTokens.Space.s12,
                vertical = tokens.components.chipVerticalPadding,
            ),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
