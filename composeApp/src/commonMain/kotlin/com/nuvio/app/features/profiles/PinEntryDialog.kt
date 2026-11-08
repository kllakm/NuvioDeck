package com.nuvio.app.features.profiles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.controller.ControllerFocusEngine
import com.nuvio.app.controller.ControllerFocusZone
import com.nuvio.app.controller.ControllerHintItem
import com.nuvio.app.controller.ControllerManager
import com.nuvio.app.controller.ControllerScreenContext
import com.nuvio.app.controller.ControllerScreenType
import com.nuvio.app.controller.RegisterControllerHandler
import com.nuvio.app.controller.XboxGlyph
import com.nuvio.app.controller.controllerFocusable
import com.nuvio.app.controller.isControllerNodeFocused
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinEntryDialog(
    profileName: String,
    onVerify: suspend (String) -> PinVerifyResult,
    onDismiss: () -> Unit,
    onVerified: ((String) -> Unit)? = null,
    onForgotPin: (() -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }

    fun submitDigit(digit: String) {
        if (pin.length < 4 && !isVerifying) {
            error = null
            pin += digit
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (pin.length == 4) {
                isVerifying = true
                scope.launch {
                    val result = onVerify(pin)
                    if (result.unlocked) {
                        onVerified?.invoke(pin)
                    } else {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        error = result.message ?: if (result.retryAfterSeconds > 0) {
                            getString(
                                Res.string.pin_locked_try_again,
                                result.retryAfterSeconds,
                            )
                        } else {
                            getString(Res.string.pin_incorrect)
                        }
                        pin = ""
                    }
                    isVerifying = false
                }
            }
        }
    }

    fun deleteDigit() {
        if (pin.isNotEmpty() && !isVerifying) {
            pin = pin.dropLast(1)
            error = null
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val pinHints = remember {
        listOf(
            ControllerHintItem(XboxGlyph.X, "Delete"),
            ControllerHintItem(XboxGlyph.B, "Cancel"),
        )
    }

    RegisterControllerHandler(
        key = "pin_entry_dialog",
        screenContext = ControllerScreenContext(
            screenType = ControllerScreenType.Modal,
            customHints = pinHints,
        ),
        onBack = {
            onDismiss()
            true
        },
        onActionX = {
            deleteDigit()
            true
        },
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        if (ControllerManager.isControllerActive.value || ControllerManager.connectedControllerCount.value > 0) {
            ControllerManager.notifyControllerInput()
            delay(40)
            ControllerFocusEngine.requestFocus("pin_key_1", triggerScroll = false)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ControllerFocusEngine.clearFocusInZone(ControllerFocusZone.Overlay)
            ControllerFocusEngine.ensureValidFocus()
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier
                .width(284.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val digit = event.utf16CodePoint.takeIf { it in '0'.code..'9'.code }?.toChar()?.toString()
                    when {
                        digit != null -> {
                            submitDigit(digit)
                            true
                        }
                        event.key == Key.Backspace || event.key == Key.Delete -> {
                            deleteDigit()
                            true
                        }
                        event.key == Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                },
            shape = RoundedCornerShape(18.dp),
            color = Color(0xF711131A),
            tonalElevation = 12.dp,
            shadowElevation = 20.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = tokens.colors.accent,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = stringResource(Res.string.pin_enter).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                letterSpacing = 1.1.sp,
                            ),
                            color = tokens.colors.textMuted,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = profileName,
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Sleek recessed pill for PIN status dots
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(4) { index ->
                        PinDot(filled = index < pin.length, hasError = error != null)
                    }
                }

                AnimatedVisibility(
                    visible = error != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }

                PinKeypad(
                    onDigit = ::submitDigit,
                    onBackspace = ::deleteDigit,
                    onCancel = onDismiss,
                )

                if (onForgotPin != null) {
                    val forgotId = "pin_key_forgot"
                    val isForgotFocused = isControllerNodeFocused(forgotId)
                    Text(
                        text = stringResource(Res.string.pin_forgot),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isForgotFocused) Color.White else tokens.colors.accent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isForgotFocused) tokens.colors.accent.copy(alpha = 0.28f)
                                else Color.Transparent,
                            )
                            .controllerFocusable(
                                id = forgotId,
                                zone = ControllerFocusZone.Overlay,
                                groupKey = "pin_keypad",
                                shape = RoundedCornerShape(8.dp),
                                scaleOnFocus = 1f,
                                hideFocusRing = true,
                                onSelect = onForgotPin,
                                selectLabel = stringResource(Res.string.pin_forgot),
                            )
                            .clickable(onClick = onForgotPin)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PinDot(filled: Boolean, hasError: Boolean) {
    val tokens = MaterialTheme.nuvio
    val color = when {
        hasError -> MaterialTheme.colorScheme.error
        filled -> tokens.colors.accent
        else -> Color.White.copy(alpha = 0.22f)
    }
    val dotScale = remember { Animatable(1f) }
    LaunchedEffect(filled) {
        if (filled) {
            dotScale.snapTo(1.35f)
            dotScale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh,
                ),
            )
        }
    }
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = dotScale.value
                scaleY = dotScale.value
            }
            .size(10.dp)
            .clip(CircleShape)
            .then(
                if (filled) Modifier.background(color, CircleShape)
                else Modifier.border(1.5.dp, color, CircleShape),
            ),
    )
}

@Composable
private fun PinKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onCancel: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("Cancel", "0", "Del"),
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                row.forEach { keyLabel ->
                    val keyId = when (keyLabel) {
                        "Del" -> "pin_key_backspace"
                        "Cancel" -> "pin_key_cancel"
                        else -> "pin_key_$keyLabel"
                    }
                    val isFocused = isControllerNodeFocused(keyId)
                    val hoverSource = remember { MutableInteractionSource() }
                    val isHovered by hoverSource.collectIsHoveredAsState()
                    val onKeyClick = {
                        when (keyLabel) {
                            "Cancel" -> onCancel()
                            "Del" -> onBackspace()
                            else -> onDigit(keyLabel)
                        }
                    }
                    val keyShape = RoundedCornerShape(10.dp)
                    val keyBg = when {
                        isFocused -> tokens.colors.accent.copy(alpha = 0.30f)
                        isHovered -> Color.White.copy(alpha = 0.12f)
                        keyLabel == "Cancel" || keyLabel == "Del" -> Color.White.copy(alpha = 0.04f)
                        else -> Color.White.copy(alpha = 0.07f)
                    }
                    val keyBorder = when {
                        isFocused -> tokens.colors.accent.copy(alpha = 0.85f)
                        isHovered -> Color.White.copy(alpha = 0.20f)
                        else -> Color.White.copy(alpha = 0.07f)
                    }

                    Box(
                        modifier = Modifier
                            .width(76.dp)
                            .height(38.dp)
                            .clip(keyShape)
                            .background(keyBg, keyShape)
                            .border(1.dp, keyBorder, keyShape)
                            .hoverable(hoverSource)
                            .controllerFocusable(
                                id = keyId,
                                zone = ControllerFocusZone.Overlay,
                                groupKey = "pin_keypad",
                                isDefaultFocus = keyLabel == "1",
                                shape = keyShape,
                                scaleOnFocus = 1f,
                                hideFocusRing = true,
                                onSelect = onKeyClick,
                                selectLabel = if (keyLabel == "Del") "Delete" else keyLabel,
                                onSecondaryAction = onBackspace,
                                secondaryActionLabel = "Delete",
                            )
                            .clickable(onClick = onKeyClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (keyLabel) {
                            "Del" -> {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                    contentDescription = "Delete",
                                    tint = if (isFocused) Color.White else tokens.colors.textSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            "Cancel" -> {
                                Text(
                                    text = "Cancel",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isFocused) Color.White else tokens.colors.textMuted,
                                )
                            }
                            else -> {
                                Text(
                                    text = keyLabel,
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                                    color = if (isFocused) Color.White else tokens.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
