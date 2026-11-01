package com.nuvio.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.nuvio.app.controller.ControllerFocusZone
import com.nuvio.app.controller.controllerFocusable
import com.nuvio.app.controller.isControllerNodeFocused
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

private val MenuShape = RoundedCornerShape(NuvioTokens.Space.s20)

private val MenuMinWidth = 180.dp

@Composable
fun Menu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(NuvioTokens.Space.none, NuvioTokens.Space.s6),
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = MenuMinWidth),
        offset = offset,
        shape = MenuShape,
        containerColor = tokens.colors.surfacePopover,
        tonalElevation = tokens.elevation.flat,
        shadowElevation = tokens.elevation.overlay,
        border = BorderStroke(tokens.borders.thin, SurfaceEdge),
        content = content,
    )
}

@Composable
fun MenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val tokens = MaterialTheme.nuvio
    val itemId = remember(text) { "menu_item_${text.hashCode()}" }
    val isFocused = isControllerNodeFocused(itemId)
    val itemBg = when {
        isFocused -> Color(0xFFF3F4F6)
        selected -> tokens.colors.accent.copy(alpha = tokens.opacity.hover)
        else -> Color.Transparent
    }
    val itemTextColor = when {
        isFocused -> Color(0xFF111827)
        enabled -> tokens.colors.textPrimary
        else -> tokens.colors.textDisabled
    }
    val checkmarkTint = if (isFocused) Color(0xFF111827) else tokens.colors.accent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NuvioTokens.Space.s8)
            .clip(tokens.shapes.compactCard)
            .controllerFocusable(
                id = itemId,
                zone = ControllerFocusZone.Overlay,
                enabled = enabled,
                shape = tokens.shapes.compactCard,
                focusScale = 1.0f,
                hideFocusRing = true,
                onSelect = onClick,
                selectLabel = text,
            )
            .background(itemBg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(NuvioTokens.Space.s12),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected || isFocused) FontWeight.SemiBold else null,
            color = itemTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = checkmarkTint,
                modifier = Modifier.size(tokens.icons.md),
            )
        }
    }
}
