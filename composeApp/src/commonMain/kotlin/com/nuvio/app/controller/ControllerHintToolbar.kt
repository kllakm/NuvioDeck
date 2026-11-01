package com.nuvio.app.controller

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.nuvio

private val XboxGreen = Color(0xFF107C10)
private val XboxRed = Color(0xFFE81123)
private val XboxBlue = Color(0xFF0078D7)
private val XboxAmber = Color(0xFFFFB900)
private val DarkGlassBackground = Color(12, 12, 16, (255 * 0.68f).toInt())
private val GlyphSurface = Color(0xFF232733)
private val GlyphBorder = Color(0x66FFFFFF)

/**
 * Dynamic, context-aware Xbox-glyph Controller Hint Toolbar anchored to the bottom-right corner.
 * Appears with a smooth fade/slide animation only when [ControllerManager.isControllerActive] is true,
 * and hides immediately when switching back to mouse or keyboard.
 */
@Composable
fun ControllerHintToolbar(
    modifier: Modifier = Modifier,
    hideForNativePlayer: Boolean = false,
) {
    val isControllerActive by ControllerManager.isControllerActive.collectAsState()
    val screenContext by ControllerManager.screenContext.collectAsState()
    val focusedSnapshot by ControllerFocusEngine.focusedNodeSnapshot.collectAsState()
    val hasMountedContent by ControllerFocusEngine.hasInteractiveContent.collectAsState()

    val hints = remember(screenContext, focusedSnapshot) {
        buildContextHints(screenContext, focusedSnapshot)
    }

    AnimatedVisibility(
        visible = isControllerActive && !hideForNativePlayer && focusedSnapshot != null && hasMountedContent && hints.isNotEmpty(),
        enter = fadeIn(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) +
            slideInVertically(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)) { it / 2 },
        exit = fadeOut(animationSpec = tween(durationMillis = 120)) +
            slideOutVertically(animationSpec = tween(durationMillis = 140)) { it / 2 },
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DarkGlassBackground,
            contentColor = Color.White,
            border = null,
            shadowElevation = 12.dp,
            tonalElevation = 0.dp,
        ) {
            AnimatedContent(
                targetState = hints,
                transitionSpec = {
                    fadeIn(tween(150)) togetherWith fadeOut(tween(100))
                },
                label = "controller_hints_content",
            ) { currentHints ->
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    currentHints.forEachIndexed { index, item ->
                        ControllerHintChip(item = item)
                        if (index < currentHints.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(14.dp)
                                    .background(Color.White.copy(alpha = 0.12f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun buildContextHints(
    context: ControllerScreenContext,
    focused: ControllerFocusNodeSnapshot?,
): List<ControllerHintItem> {
    if (context.customHints != null && context.customHints.isNotEmpty()) {
        return context.customHints.filter { it.glyph != XboxGlyph.DPad && it.glyph != XboxGlyph.LS }
    }

    // 1. Active modal / overlay / sheet / dialog
    if (focused?.zone == ControllerFocusZone.Overlay ||
        context.screenType == ControllerScreenType.Modal ||
        context.screenType == ControllerScreenType.ModalOverlay
    ) {
        val isPinModal = focused?.id?.startsWith("pin_key_") == true || context.customHints?.any { it.actionLabel == "Enter Digit" } == true
        if (isPinModal) {
            return listOf(
                ControllerHintItem(XboxGlyph.A, "Enter Digit"),
                ControllerHintItem(XboxGlyph.X, "Delete"),
                ControllerHintItem(XboxGlyph.B, "Cancel"),
            )
        }
        return buildList {
            add(ControllerHintItem(XboxGlyph.A, focused?.selectLabel ?: context.selectLabel ?: "Select"))
            if (focused?.hasSecondaryAction == true && !focused.secondaryActionLabel.isNullOrBlank()) {
                add(ControllerHintItem(XboxGlyph.X, focused.secondaryActionLabel))
            }
            add(ControllerHintItem(XboxGlyph.B, context.backLabel ?: "Close"))
        }
    }

    // 2. Active Text Input (Search bar, URL input, etc.)
    if (focused?.isTextInput == true) {
        val keyboardOpen = PlatformVirtualKeyboard.isKeyboardActive
        return buildList {
            add(ControllerHintItem(XboxGlyph.A, if (keyboardOpen) "Keyboard" else (focused.selectLabel.takeIf { it.isNotBlank() } ?: "Type / Keyboard")))
            if (focused.hasSecondaryAction && !focused.secondaryActionLabel.isNullOrBlank()) {
                add(ControllerHintItem(XboxGlyph.X, focused.secondaryActionLabel))
            }
            add(ControllerHintItem(XboxGlyph.B, if (keyboardOpen) "Close Keyboard" else (context.backLabel ?: "Home")))
        }
    }

    // 3. Navigation Zone (Sidebar / TopBar)
    if (focused?.zone == ControllerFocusZone.Navigation) {
        return listOf(
            ControllerHintItem(XboxGlyph.A, focused.selectLabel.ifBlank { "Switch Tab" }),
            ControllerHintItem(XboxGlyph.LB_RB, "Tabs"),
            ControllerHintItem(XboxGlyph.B, "Content"),
        )
    }

    // 3. Screen-specific action button hints
    val selectLabel = focused?.selectLabel?.takeIf { it.isNotBlank() } ?: "Select"
    val xLabel = focused?.secondaryActionLabel?.takeIf { focused.hasSecondaryAction && it.isNotBlank() }
    val yLabel = focused?.tertiaryActionLabel?.takeIf { focused.hasTertiaryAction && it.isNotBlank() }

    return when (context.screenType) {
        ControllerScreenType.ProfileSelection -> buildList {
            add(ControllerHintItem(XboxGlyph.A, selectLabel))
            if (xLabel != null) add(ControllerHintItem(XboxGlyph.X, xLabel))
            if (context.canNavigateBack) add(ControllerHintItem(XboxGlyph.B, "Back"))
        }

        ControllerScreenType.Home -> buildList {
            add(ControllerHintItem(XboxGlyph.A, selectLabel))
            if (xLabel != null) {
                add(ControllerHintItem(XboxGlyph.X, xLabel))
            }
            add(ControllerHintItem(XboxGlyph.Y, yLabel ?: "Search"))
            add(ControllerHintItem(XboxGlyph.LB_RB, "Tabs"))
            add(ControllerHintItem(XboxGlyph.Menu, "Navigation"))
        }

        ControllerScreenType.Search -> buildList {
            add(ControllerHintItem(XboxGlyph.A, selectLabel))
            add(ControllerHintItem(XboxGlyph.X, xLabel ?: "Clear Search"))
            add(ControllerHintItem(XboxGlyph.Y, yLabel ?: "Focus Search"))
            add(ControllerHintItem(XboxGlyph.LB_RB, "Tabs"))
            add(ControllerHintItem(XboxGlyph.Menu, "Navigation"))
            add(ControllerHintItem(XboxGlyph.B, "Back"))
        }

        ControllerScreenType.Library -> buildList {
            add(ControllerHintItem(XboxGlyph.A, selectLabel))
            if (xLabel != null) {
                add(ControllerHintItem(XboxGlyph.X, xLabel))
            }
            add(ControllerHintItem(XboxGlyph.Y, yLabel ?: "Search"))
            add(ControllerHintItem(XboxGlyph.LB_RB, context.seasonsOrTabsLabel ?: "Tabs"))
            add(ControllerHintItem(XboxGlyph.Menu, "Navigation"))
        }

        ControllerScreenType.Settings -> buildList {
            add(ControllerHintItem(XboxGlyph.A, selectLabel))
            if (xLabel != null) add(ControllerHintItem(XboxGlyph.X, xLabel))
            add(ControllerHintItem(XboxGlyph.LB_RB, "Tabs"))
            add(ControllerHintItem(XboxGlyph.Menu, "Navigation"))
            add(ControllerHintItem(XboxGlyph.B, if (context.canNavigateBack) "Back" else "Menu"))
        }

        ControllerScreenType.Catalog -> buildList {
            add(ControllerHintItem(XboxGlyph.A, selectLabel))
            if (xLabel != null) add(ControllerHintItem(XboxGlyph.X, xLabel))
            if (context.hasSeasonsOrTabs) {
                add(ControllerHintItem(XboxGlyph.LB_RB, context.seasonsOrTabsLabel ?: "Switch Folder"))
            }
            add(ControllerHintItem(XboxGlyph.Menu, "Navigation"))
            add(ControllerHintItem(XboxGlyph.B, "Back"))
        }

        ControllerScreenType.Details -> buildList {
            add(ControllerHintItem(XboxGlyph.A, selectLabel))
            if (xLabel != null) {
                add(ControllerHintItem(XboxGlyph.X, xLabel))
            }
            if (yLabel != null) {
                add(ControllerHintItem(XboxGlyph.Y, yLabel))
            }
            if (context.hasSeasonsOrTabs) {
                add(ControllerHintItem(XboxGlyph.LB_RB, context.seasonsOrTabsLabel ?: "Season"))
            }
            add(ControllerHintItem(XboxGlyph.B, "Back"))
        }

        ControllerScreenType.Streams -> buildList {
            add(ControllerHintItem(XboxGlyph.A, selectLabel.takeIf { it != "Select" } ?: "Play"))
            add(ControllerHintItem(XboxGlyph.X, xLabel ?: "Options"))
            add(ControllerHintItem(XboxGlyph.Y, yLabel ?: "Reload"))
            if (context.hasSeasonsOrTabs) {
                add(ControllerHintItem(XboxGlyph.LB_RB, "Addon Filter"))
            }
            add(ControllerHintItem(XboxGlyph.B, "Back"))
        }

        ControllerScreenType.Player -> listOf(
            ControllerHintItem(XboxGlyph.A, "Play / Pause"),
            ControllerHintItem(XboxGlyph.LB_RB, "Seek 10s"),
            ControllerHintItem(XboxGlyph.X, "Audio"),
            ControllerHintItem(XboxGlyph.Y, "Subtitles"),
            ControllerHintItem(XboxGlyph.Menu, "Episodes / Info"),
            ControllerHintItem(XboxGlyph.B, "Back"),
        )

        ControllerScreenType.Modal,
        ControllerScreenType.ModalOverlay -> listOf(
            ControllerHintItem(XboxGlyph.A, selectLabel),
            ControllerHintItem(XboxGlyph.B, "Close"),
        )
    }
}

@Composable
private fun ControllerHintChip(
    item: ControllerHintItem,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        XboxButtonGlyphBadge(glyph = item.glyph)
        Text(
            text = item.actionLabel,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.1.sp,
            ),
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 1,
        )
    }
}

@Composable
fun XboxButtonGlyphBadge(
    glyph: XboxGlyph,
    modifier: Modifier = Modifier,
) {
    when (glyph) {
        XboxGlyph.A -> XboxFaceButtonBadge("A", XboxGreen, modifier)
        XboxGlyph.B -> XboxFaceButtonBadge("B", XboxRed, modifier)
        XboxGlyph.X -> XboxFaceButtonBadge("X", XboxBlue, modifier)
        XboxGlyph.Y -> XboxFaceButtonBadge("Y", XboxAmber, modifier, darkText = true)
        XboxGlyph.LB -> XboxBumperBadge("LB", modifier)
        XboxGlyph.RB -> XboxBumperBadge("RB", modifier)
        XboxGlyph.LB_RB -> {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XboxBumperBadge("LB")
                XboxBumperBadge("RB")
            }
        }
        XboxGlyph.LT -> XboxTriggerBadge("LT", modifier)
        XboxGlyph.RT -> XboxTriggerBadge("RT", modifier)
        XboxGlyph.LT_RT -> {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XboxTriggerBadge("LT")
                XboxTriggerBadge("RT")
            }
        }
        XboxGlyph.DPad -> XboxDPadBadge(modifier)
        XboxGlyph.LS -> XboxStickBadge("LS", modifier)
        XboxGlyph.RS -> XboxStickBadge("RS", modifier)
        XboxGlyph.View -> XboxViewBadge(modifier)
        XboxGlyph.Menu -> XboxMenuBadge(modifier)
    }
}

@Composable
private fun XboxFaceButtonBadge(
    letter: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    darkText: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.92f))
            .border(1.2.dp, Color.White.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = if (darkText) Color(0xFF14161D) else Color.White,
        )
    }
}

@Composable
private fun XboxBumperBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    Box(
        modifier = modifier
            .height(20.dp)
            .clip(shape)
            .background(GlyphSurface)
            .border(1.dp, GlyphBorder, shape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = Color.White,
        )
    }
}

@Composable
private fun XboxTriggerBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    Box(
        modifier = modifier
            .height(20.dp)
            .clip(shape)
            .background(GlyphSurface)
            .border(1.dp, GlyphBorder, shape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = Color.White,
        )
    }
}

@Composable
private fun XboxStickBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(GlyphSurface)
            .border(1.2.dp, GlyphBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = Color.White,
        )
    }
}

@Composable
private fun XboxDPadBadge(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val armThickness = w * 0.34f
            val armOffset = (w - armThickness) / 2f
            val radius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            // Outer border/fill for cross
            drawRoundRect(
                color = GlyphBorder,
                topLeft = Offset(armOffset - 1f, 0f),
                size = Size(armThickness + 2f, h),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = GlyphBorder,
                topLeft = Offset(0f, armOffset - 1f),
                size = Size(w, armThickness + 2f),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = GlyphSurface,
                topLeft = Offset(armOffset, 1f),
                size = Size(armThickness, h - 2f),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = GlyphSurface,
                topLeft = Offset(1f, armOffset),
                size = Size(w - 2f, armThickness),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun XboxViewBadge(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(GlyphSurface)
            .border(1.dp, GlyphBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            val stroke = 1.4.dp.toPx()
            val boxW = size.width * 0.62f
            val boxH = size.height * 0.56f
            // Back rectangle (top-left)
            drawRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(0f, 0f),
                size = Size(boxW, boxH),
                style = Stroke(width = stroke),
            )
            // Front rectangle (bottom-right)
            drawRect(
                color = GlyphSurface,
                topLeft = Offset(size.width - boxW, size.height - boxH),
                size = Size(boxW, boxH),
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(size.width - boxW, size.height - boxH),
                size = Size(boxW, boxH),
                style = Stroke(width = stroke),
            )
        }
    }
}

@Composable
private fun XboxMenuBadge(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(GlyphSurface)
            .border(1.dp, GlyphBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(11.dp)) {
            val stroke = 1.5.dp.toPx()
            val y1 = size.height * 0.22f
            val y2 = size.height * 0.5f
            val y3 = size.height * 0.78f
            drawLine(
                color = Color.White,
                start = Offset(0f, y1),
                end = Offset(size.width, y1),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White,
                start = Offset(0f, y2),
                end = Offset(size.width, y2),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White,
                start = Offset(0f, y3),
                end = Offset(size.width, y3),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

