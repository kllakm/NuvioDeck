package com.nuvio.app.features.profiles

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private val PinKeypadNetflixFont: FontFamily
    @Composable
    get() = FontFamily(
        Font(Res.font.netflix_sans_thin, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.netflix_sans_thin, FontWeight.Medium, FontStyle.Normal),
        Font(Res.font.netflix_sans_thin, FontWeight.SemiBold, FontStyle.Normal),
    )

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
    var pin by remember(profileName) { mutableStateOf("") }
    var error by remember(profileName) { mutableStateOf<String?>(null) }
    var isVerifying by remember(profileName) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }

    fun submitDigit(digit: String) {
        if (pin.length < 4 && !isVerifying) {
            error = null
            pin += digit
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (pin.length == 4) {
                val submittedPin = pin
                isVerifying = true
                scope.launch {
                    val result = onVerify(submittedPin)
                    if (result.unlocked) {
                        onVerified?.invoke(submittedPin)
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
                .width(288.dp)
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
            color = Color(0xF7111217),
            tonalElevation = 12.dp,
            shadowElevation = 24.dp,
        ) {
            ProfilePinPadContent(
                title = profileName,
                subtitle = stringResource(Res.string.pin_enter),
                pin = pin,
                isVerifying = isVerifying,
                error = error,
                groupKey = "pin_keypad",
                resolveKeyNodeId = { keyLabel ->
                    when (keyLabel) {
                        "Del" -> "pin_key_backspace"
                        "Cancel" -> "pin_key_cancel"
                        else -> "pin_key_$keyLabel"
                    }
                },
                onDigit = ::submitDigit,
                onBackspace = ::deleteDigit,
                onCancel = onDismiss,
                footer = if (onForgotPin != null) {
                    {
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
                } else {
                    null
                },
            )
        }
    }
}

@Composable
internal fun ProfilePinPadContent(
    title: String,
    subtitle: String,
    pin: String,
    isVerifying: Boolean,
    error: String?,
    groupKey: String,
    resolveKeyNodeId: (String) -> String,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    val hasError = !error.isNullOrBlank()
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) {
            val keyframes = listOf(-10f, 10f, -7f, 7f, -4f, 0f)
            keyframes.forEach { target ->
                shakeOffset.animateTo(target, animationSpec = tween(durationMillis = 36))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                color = tokens.colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }

        // Fixed-width transparent dot/spinner container — width never changes when verifying
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .graphicsLayer { translationX = shakeOffset.value },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = isVerifying,
                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(110)) },
                contentAlignment = Alignment.Center,
                label = "pin_indicator_content",
            ) { verifying ->
                if (verifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(4) { index ->
                            PinDot(
                                filled = index < pin.length,
                                hasError = hasError,
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = hasError,
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
            groupKey = groupKey,
            enabled = !isVerifying,
            resolveKeyNodeId = resolveKeyNodeId,
            onDigit = onDigit,
            onBackspace = onBackspace,
            onCancel = onCancel,
        )

        footer?.invoke()
    }
}

@Composable
private fun PinDot(
    filled: Boolean,
    hasError: Boolean,
) {
    val dotColor by animateColorAsState(
        targetValue = when {
            hasError -> MaterialTheme.colorScheme.error
            filled -> Color.White
            else -> Color.White.copy(alpha = 0.22f)
        },
        animationSpec = tween(140),
        label = "pin_dot_color",
    )
    val dotScale = remember { Animatable(1f) }
    LaunchedEffect(filled) {
        if (filled) {
            dotScale.snapTo(1.28f)
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
            .background(dotColor, CircleShape),
    )
}

@Composable
private fun PinKeypad(
    groupKey: String,
    enabled: Boolean,
    resolveKeyNodeId: (String) -> String,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onCancel: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val keypadFont = PinKeypadNetflixFont
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("Cancel", "0", "Del"),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.65f),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                row.forEach { keyLabel ->
                    val keyId = resolveKeyNodeId(keyLabel)
                    val isFocused = isControllerNodeFocused(keyId)
                    val hoverSource = remember { MutableInteractionSource() }
                    val isHovered by hoverSource.collectIsHoveredAsState()
                    val isUtility = keyLabel == "Cancel" || keyLabel == "Del"
                    val onKeyClick = {
                        when (keyLabel) {
                            "Cancel" -> onCancel()
                            "Del" -> if (enabled) onBackspace()
                            else -> if (enabled) onDigit(keyLabel)
                        }
                    }
                    val keyShape = RoundedCornerShape(10.dp)
                    val keyBg by animateColorAsState(
                        targetValue = when {
                            isFocused -> tokens.colors.accent.copy(alpha = 0.34f)
                            isHovered -> Color.White.copy(alpha = 0.16f)
                            isUtility -> Color.Transparent
                            else -> Color.White.copy(alpha = 0.085f)
                        },
                        animationSpec = tween(130),
                        label = "pin_key_bg",
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(keyShape)
                            .background(keyBg, keyShape)
                            .hoverable(hoverSource)
                            .controllerFocusable(
                                id = keyId,
                                zone = ControllerFocusZone.Overlay,
                                groupKey = groupKey,
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
                                    tint = if (isFocused || isHovered) Color.White else Color.White.copy(alpha = 0.70f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            "Cancel" -> {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Cancel",
                                    tint = if (isFocused || isHovered) Color.White else Color.White.copy(alpha = 0.56f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            else -> {
                                Text(
                                    text = keyLabel,
                                    fontFamily = keypadFont,
                                    fontSize = 22.sp,
                                    lineHeight = 24.sp,
                                    color = if (isFocused || isHovered) Color.White else Color.White.copy(alpha = 0.94f),
                                    fontWeight = FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
