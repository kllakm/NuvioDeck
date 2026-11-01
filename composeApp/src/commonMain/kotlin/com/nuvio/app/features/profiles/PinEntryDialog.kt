package com.nuvio.app.features.profiles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import com.nuvio.app.controller.isControllerNodeFocused
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nuvio.app.controller.ControllerFocusEngine
import com.nuvio.app.controller.ControllerFocusZone
import com.nuvio.app.controller.ControllerHintItem
import com.nuvio.app.controller.ControllerManager
import com.nuvio.app.controller.ControllerScreenContext
import com.nuvio.app.controller.ControllerScreenType
import com.nuvio.app.controller.RegisterControllerHandler
import com.nuvio.app.controller.XboxGlyph
import com.nuvio.app.controller.controllerFocusable
import com.nuvio.app.core.ui.DialogSurface
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
            ControllerHintItem(XboxGlyph.A, "Enter Digit"),
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
            delay(50)
            ControllerFocusEngine.ensureValidFocus()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ControllerFocusEngine.clearFocusInZone(ControllerFocusZone.Overlay)
            ControllerFocusEngine.ensureValidFocus()
        }
    }

    DialogSurface(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.pin_enter),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = profileName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            PinKeypad(
                onDigit = ::submitDigit,
                onBackspace = ::deleteDigit,
            )

            if (onForgotPin != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.pin_forgot),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onForgotPin)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun PinDot(filled: Boolean, hasError: Boolean) {
    val color = when {
        hasError -> MaterialTheme.colorScheme.error
        filled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val dotScale = remember { Animatable(1f) }
    LaunchedEffect(filled) {
        if (filled) {
            dotScale.snapTo(1.5f)
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
            .size(16.dp)
            .clip(CircleShape)
            .then(
                if (filled) Modifier.background(color)
                else Modifier.border(2.dp, color, CircleShape)
            ),
    )
}

@Composable
private fun PinKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫"),
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(modifier = Modifier.size(64.dp))
                        "⌫" -> {
                            val isBackspaceFocused = isControllerNodeFocused("pin_key_backspace")
                            val backspaceBg = if (isBackspaceFocused) Color(0xFFF3F4F6) else MaterialTheme.colorScheme.surfaceVariant
                            val backspaceTint = if (isBackspaceFocused) Color(0xFF111827) else MaterialTheme.colorScheme.onSurface
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .controllerFocusable(
                                        id = "pin_key_backspace",
                                        zone = ControllerFocusZone.Overlay,
                                        groupKey = "pin_keypad",
                                        isDefaultFocus = false,
                                        shape = CircleShape,
                                        scaleOnFocus = 1.06f,
                                        hideFocusRing = true,
                                        onSelect = onBackspace,
                                        selectLabel = "Delete",
                                        onSecondaryAction = onBackspace,
                                        secondaryActionLabel = "Delete",
                                    )
                                    .clip(CircleShape)
                                    .background(backspaceBg)
                                    .clickable(onClick = onBackspace),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                    contentDescription = "Delete",
                                    tint = backspaceTint,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        else -> {
                            val keyId = "pin_key_$key"
                            val isKeyFocused = isControllerNodeFocused(keyId)
                            val keyBg = if (isKeyFocused) Color(0xFFF3F4F6) else MaterialTheme.colorScheme.surfaceVariant
                            val keyColor = if (isKeyFocused) Color(0xFF111827) else MaterialTheme.colorScheme.onSurface
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .controllerFocusable(
                                        id = keyId,
                                        zone = ControllerFocusZone.Overlay,
                                        groupKey = "pin_keypad",
                                        isDefaultFocus = key == "1",
                                        shape = CircleShape,
                                        scaleOnFocus = 1.06f,
                                        hideFocusRing = true,
                                        onSelect = { onDigit(key) },
                                        selectLabel = "Enter Digit",
                                        onSecondaryAction = onBackspace,
                                        secondaryActionLabel = "Delete",
                                    )
                                    .clip(CircleShape)
                                    .background(keyBg)
                                    .clickable { onDigit(key) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = keyColor,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
