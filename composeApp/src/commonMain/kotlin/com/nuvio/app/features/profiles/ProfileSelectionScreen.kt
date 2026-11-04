@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.nuvio.app.features.profiles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.controller.ControllerFocusEngine
import com.nuvio.app.controller.ControllerManager
import com.nuvio.app.controller.ControllerScreenContext
import com.nuvio.app.controller.ControllerScreenType
import com.nuvio.app.controller.RegisterControllerHandler
import com.nuvio.app.controller.controllerFocusable
import com.nuvio.app.isDesktop
import com.nuvio.app.core.ui.NuvioDesktopAnimation
import com.nuvio.app.core.ui.NuvioAsyncImage as AsyncImage
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioToastHost
import com.nuvio.app.core.ui.LocalVerticalBarPadding
import com.nuvio.app.features.membership.CosmeticEntitlement
import com.nuvio.app.features.settings.MemberBrandWordmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileSelectionScreen(
    onProfileSelected: (NuvioProfile) -> Unit,
    onEditProfile: (NuvioProfile) -> Unit,
    onAddProfile: () -> Unit,
    onBack: (() -> Unit)? = null,
    interactionEnabled: Boolean = true,
    activeProfileIndex: Int? = null,
    contentVisible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pinDialogProfile by remember { mutableStateOf<NuvioProfile?>(null) }
    var isEditMode by remember { mutableStateOf(false) }
    var hoveredProfileIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(isEditMode, onBack != null) {
        ControllerManager.updateScreenContext(
            ControllerScreenContext(
                screenType = ControllerScreenType.ProfileSelection,
                selectLabel = if (isEditMode) "Edit Profile" else "Select Profile",
                secondaryActionLabel = if (isEditMode) "Done" else "Manage Profiles",
                backLabel = if (onBack != null || isEditMode) "Back" else null,
            ),
        )
    }

    val isControllerActive by ControllerManager.isControllerActive.collectAsState()

    LaunchedEffect(interactionEnabled, profileState.profiles.size, isControllerActive) {
        if (interactionEnabled && profileState.profiles.isNotEmpty() && isControllerActive) {
            delay(60)
            ControllerFocusEngine.ensureValidFocus()
        }
    }

    RegisterControllerHandler(
        onBack = {
            if (isEditMode) {
                isEditMode = false
                true
            } else if (onBack != null) {
                onBack()
                true
            } else {
                false
            }
        },
        onSecondaryAction = {
            if (interactionEnabled) {
                isEditMode = !isEditMode
                true
            } else {
                false
            }
        },
    )

    val titleAlpha = remember { Animatable(0f) }
    val titleOffset = remember { Animatable(20f) }
    val manageAlpha = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    val dismissAlpha by animateFloatAsState(
        targetValue = if (isDismissing) 0f else 1f,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "profile_dismiss_alpha",
    )
    val dismissScale by animateFloatAsState(
        targetValue = if (isDismissing) 1.04f else 1f,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "profile_dismiss_scale",
    )

    val onProfileClick: (NuvioProfile) -> Unit = { profile ->
        if (interactionEnabled && !isDismissing) {
            routeProfileSelection(
                profile = profile,
                isEditMode = isEditMode,
                activeProfileIndex = activeProfileIndex,
                onEditProfile = onEditProfile,
                onActiveProfileSelected = { scope.launch { showAlreadyActiveProfileToast(it) } },
                onPinRequired = { pinDialogProfile = it },
                onProfileSelected = { selectedProfile ->
                    isDismissing = true
                    scope.launch {
                        delay(120)
                        onProfileSelected(selectedProfile)
                    }
                },
            )
        }
    }

    LaunchedEffect(Unit) {
        AvatarRepository.refreshAvatars()
    }

    LaunchedEffect(Unit) {
        launch { titleAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        launch { titleOffset.animateTo(0f, tween(600, easing = FastOutSlowInEasing)) }
        delay(300)
        manageAlpha.animateTo(1f, tween(500))
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val backgroundProfile = profileState.activeProfile ?: profileState.profiles.firstOrNull()
    val hoveredProfileColor = remember(profileState.profiles, hoveredProfileIndex) {
        if (!isDesktop) {
            null
        } else {
            profileState.profiles
                .firstOrNull { it.profileIndex == hoveredProfileIndex }
                ?.avatarColorHex
                ?.let(::parseHexColor)
        }
    }

    LaunchedEffect(profileState.profiles) {
        if (hoveredProfileIndex != null && profileState.profiles.none { it.profileIndex == hoveredProfileIndex }) {
            hoveredProfileIndex = null
        }
    }

    fun updateHoveredProfile(profile: NuvioProfile, isHovered: Boolean) {
        hoveredProfileIndex = if (isHovered) {
            profile.profileIndex
        } else {
            hoveredProfileIndex.takeUnless { it == profile.profileIndex }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = dismissAlpha
                scaleX = dismissScale
                scaleY = dismissScale
            },
    ) {
        val isTabletLayout = maxWidth >= 768.dp
        ProfileBackgroundBackdrop(
            profile = backgroundProfile,
            colorOverride = hoveredProfileColor,
            modifier = Modifier.fillMaxSize(),
        )

        MemberBrandWordmark(
            height = if (isTabletLayout) 30.dp else 26.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(LocalVerticalBarPadding.current)
                .padding(
                    start = if (onBack != null) (if (isTabletLayout) 72.dp else 56.dp) else (if (isTabletLayout) 48.dp else 24.dp),
                    top = statusBarTop + (if (isTabletLayout) 28.dp else 16.dp),
                )
                .graphicsLayer {
                    alpha = titleAlpha.value
                    translationY = titleOffset.value
                },
        )

        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusBarTop)
                    .then(
                        if (isTabletLayout) {
                            Modifier
                        } else {
                            Modifier.verticalScroll(rememberScrollState())
                        },
                    )
                    .padding(horizontal = 24.dp),
                verticalArrangement = if (isTabletLayout) Arrangement.Center else Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(if (isTabletLayout) 0.dp else 60.dp))

                Text(
                    text = stringResource(Res.string.profile_who_is_watching),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 30.sp,
                        letterSpacing = 0.sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.graphicsLayer {
                        alpha = titleAlpha.value
                        translationY = titleOffset.value
                    },
                )

                Spacer(modifier = Modifier.height(if (isTabletLayout) 28.dp else 48.dp))

                val profiles = profileState.profiles
                val items = profiles.size + if (isEditMode && profiles.size < MAX_PROFILES) 1 else 0

                if (isTabletLayout) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            for (currentIndex in 0 until items) {
                                if (currentIndex < profiles.size) {
                                    val profile = profiles[currentIndex]
                                    ProfileAvatarCard(
                                        profile = profile,
                                        isEditMode = isEditMode,
                                        animDelay = currentIndex * 80,
                                        enabled = interactionEnabled,
                                        isDefaultFocus = currentIndex == 0,
                                        onHoverChange = { isHovered -> updateHoveredProfile(profile, isHovered) },
                                        onEditProfile = { onEditProfile(profile) },
                                        onClick = {
                                            onProfileClick(profile)
                                        },
                                    )
                                } else {
                                    AddProfileCard(
                                        animDelay = currentIndex * 80,
                                        enabled = interactionEnabled,
                                        onClick = onAddProfile,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        var index = 0
                        while (index < items) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                for (col in 0..1) {
                                    if (index < items) {
                                        val currentIndex = index
                                        if (currentIndex < profiles.size) {
                                            val profile = profiles[currentIndex]
                                            ProfileAvatarCard(
                                                profile = profile,
                                                isEditMode = isEditMode,
                                                animDelay = currentIndex * 80,
                                                enabled = interactionEnabled,
                                                isDefaultFocus = currentIndex == 0,
                                                onHoverChange = { isHovered -> updateHoveredProfile(profile, isHovered) },
                                                onEditProfile = { onEditProfile(profile) },
                                                onClick = {
                                                    onProfileClick(profile)
                                                },
                                            )
                                        } else {
                                            AddProfileCard(
                                                animDelay = currentIndex * 80,
                                                enabled = interactionEnabled,
                                                onClick = onAddProfile,
                                            )
                                        }
                                        index++
                                    } else {
                                        if (profiles.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(150.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isTabletLayout) 28.dp else 48.dp))

                val manageLabel = if (isEditMode) {
                    stringResource(Res.string.action_done)
                } else {
                    stringResource(Res.string.profile_manage_profiles)
                }
                Box(
                    modifier = Modifier
                        .graphicsLayer { alpha = manageAlpha.value }
                        .controllerFocusable(
                            id = "profile_manage_button",
                            groupKey = "profile_manage",
                            enabled = interactionEnabled,
                            shape = RoundedCornerShape(24.dp),
                            scaleOnFocus = 1.04f,
                            onSelect = { isEditMode = !isEditMode },
                            selectLabel = manageLabel,
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (isEditMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else Color.Transparent,
                        )
                        .border(
                            width = 1.dp,
                            color = if (isEditMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(24.dp),
                        )
                        .clickable(enabled = interactionEnabled) { isEditMode = !isEditMode }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = manageLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isEditMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(if (isTabletLayout) 0.dp else 32.dp))
            }
        }

        if (onBack != null && interactionEnabled && contentVisible) {
            NuvioBackButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(LocalVerticalBarPadding.current)
                    .padding(start = 16.dp, top = statusBarTop + 8.dp),
            )
        }

        NuvioToastHost(modifier = Modifier.align(Alignment.TopCenter))
    }

    pinDialogProfile?.let { profile ->
        PinEntryDialog(
            profileName = profile.name,
            onVerify = { pin -> ProfileRepository.verifyPin(profile.profileIndex, pin) },
            onVerified = {
                pinDialogProfile = null
                if (interactionEnabled && profile.profileIndex != activeProfileIndex) {
                    onProfileSelected(profile)
                }
            },
            onDismiss = { pinDialogProfile = null },
        )
    }
}

@Composable
private fun ProfileAvatarCard(
    profile: NuvioProfile,
    isEditMode: Boolean,
    animDelay: Int,
    enabled: Boolean,
    isDefaultFocus: Boolean = false,
    onHoverChange: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onClick: () -> Unit,
) {
    val avatarColor = remember(profile.avatarColorHex) {
        parseHexColor(profile.avatarColorHex)
    }
    val avatars by AvatarRepository.avatars.collectAsStateWithLifecycle()
    val avatarItem = remember(profile.avatarId, avatars) {
        profile.avatarId?.let { id -> avatars.find { it.id == id } }
    }
    val avatarImageUrl = remember(profile.avatarUrl, avatarItem) {
        profileAvatarImageUrl(profile, avatarItem)
    }

    val animAlpha = remember { Animatable(0f) }
    val animScale = remember { Animatable(0.85f) }
    val animOffset = remember { Animatable(30f) }

    LaunchedEffect(Unit) {
        delay(animDelay.toLong() + 150)
        launch { animAlpha.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
        launch { animScale.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        launch { animOffset.animateTo(0f, tween(500, easing = FastOutSlowInEasing)) }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val rawHovered by interactionSource.collectIsHoveredAsState()
    var isMouseHovered by remember { mutableStateOf(false) }
    val isControllerActive by ControllerManager.isControllerActive.collectAsState()
    val focusId = "profile_card_${profile.profileIndex}"
    val focusedNodeId by ControllerFocusEngine.focusedNodeId.collectAsState()
    val isControllerFocused = isControllerActive && focusedNodeId == focusId
    val isHovered = isMouseHovered || (!isControllerActive && rawHovered)
    val isSelected = isHovered || isControllerFocused
    val currentOnHoverChange = rememberUpdatedState(onHoverChange)
    val pressScale = if (isPressed) 0.95f else 1f

    val selectionBgAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.15f else 0f,
        animationSpec = if (isSelected) {
            tween(150, easing = FastOutSlowInEasing)
        } else {
            tween(700, easing = FastOutSlowInEasing)
        },
        label = "profile_selection_bg_alpha",
    )
    val hoverScale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1f,
        animationSpec = if (isSelected) {
            tween(150, easing = FastOutSlowInEasing)
        } else {
            tween(700, easing = FastOutSlowInEasing)
        },
        label = "profile_hover_scale",
    )

    LaunchedEffect(isSelected, profile.profileIndex) {
        if (isDesktop) {
            currentOnHoverChange.value(isSelected)
        }
    }

    DisposableEffect(profile.profileIndex) {
        onDispose {
            if (isDesktop) {
                currentOnHoverChange.value(false)
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(150.dp)
            .graphicsLayer {
                alpha = animAlpha.value
                scaleX = animScale.value * pressScale * hoverScale
                scaleY = animScale.value * pressScale * hoverScale
                translationY = animOffset.value
            }
            .then(
                if (isDesktop) {
                    Modifier
                        .onPointerEvent(PointerEventType.Enter) { isMouseHovered = true }
                        .onPointerEvent(PointerEventType.Exit) { isMouseHovered = false }
                        .hoverable(interactionSource)
                } else {
                    Modifier
                },
            )
            .controllerFocusable(
                id = focusId,
                groupKey = "profile_cards",
                isDefaultFocus = isDefaultFocus,
                enabled = enabled,
                shape = RoundedCornerShape(20.dp),
                scaleOnFocus = 1.0f,
                hideFocusRing = true,
                onSelect = onClick,
                selectLabel = if (isEditMode) "Edit Profile" else "Select Profile",
                onSecondaryAction = onEditProfile,
                secondaryActionLabel = "Edit Profile",
            )
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = selectionBgAlpha))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier.size(110.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarImageUrl != null) {
                val bgColor = avatarItem?.bgColor?.let { parseHexColor(it) } ?: avatarColor
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(bgColor.copy(alpha = 0.2f)),
                )
            }

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        if (avatarItem != null) {
                            avatarItem.bgColor?.let { parseHexColor(it) } ?: avatarColor
                        } else {
                            avatarColor.copy(alpha = 0.15f)
                        },
                    )
                    .then(
                        if (avatarImageUrl == null) Modifier.border(2.dp, avatarColor.copy(alpha = 0.4f), CircleShape)
                        else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarImageUrl != null) {
                    AsyncImage(
                        model = avatarImageUrl,
                        contentDescription = avatarItem?.displayName ?: profile.name,
                        modifier = Modifier.size(100.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        desktopAnimation = NuvioDesktopAnimation.Always,
                    )
                } else if (profile.name.isNotBlank()) {
                    Text(
                        text = profile.name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 38.sp),
                        color = avatarColor,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = avatarColor,
                        modifier = Modifier.size(46.dp),
                    )
                }
            }

            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            if (profile.pinEnabled && !isEditMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = profile.name.ifBlank {
                stringResource(Res.string.profile_label_number, profile.profileIndex)
            },
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddProfileCard(
    animDelay: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val animAlpha = remember { Animatable(0f) }
    val animScale = remember { Animatable(0.85f) }
    val animOffset = remember { Animatable(30f) }

    LaunchedEffect(Unit) {
        delay(animDelay.toLong() + 150)
        launch { animAlpha.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
        launch { animScale.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        launch { animOffset.animateTo(0f, tween(500, easing = FastOutSlowInEasing)) }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val rawHovered by interactionSource.collectIsHoveredAsState()
    var isMouseHovered by remember { mutableStateOf(false) }
    val isControllerActive by ControllerManager.isControllerActive.collectAsState()
    val focusedNodeId by ControllerFocusEngine.focusedNodeId.collectAsState()
    val isControllerFocused = isControllerActive && focusedNodeId == "profile_card_add"
    val isHovered = isMouseHovered || (!isControllerActive && rawHovered)
    val isSelected = isHovered || isControllerFocused
    val pressScale = if (isPressed) 0.95f else 1f
    val addLabel = stringResource(Res.string.compose_profile_add_profile)

    val selectionBgAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.15f else 0f,
        animationSpec = if (isSelected) {
            tween(150, easing = FastOutSlowInEasing)
        } else {
            tween(700, easing = FastOutSlowInEasing)
        },
        label = "add_profile_selection_bg_alpha",
    )
    val hoverScale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1f,
        animationSpec = if (isSelected) {
            tween(150, easing = FastOutSlowInEasing)
        } else {
            tween(700, easing = FastOutSlowInEasing)
        },
        label = "add_profile_hover_scale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(150.dp)
            .graphicsLayer {
                alpha = animAlpha.value
                scaleX = animScale.value * pressScale * hoverScale
                scaleY = animScale.value * pressScale * hoverScale
                translationY = animOffset.value
            }
            .then(
                if (isDesktop) {
                    Modifier
                        .onPointerEvent(PointerEventType.Enter) { isMouseHovered = true }
                        .onPointerEvent(PointerEventType.Exit) { isMouseHovered = false }
                        .hoverable(interactionSource)
                } else {
                    Modifier
                },
            )
            .controllerFocusable(
                id = "profile_card_add",
                groupKey = "profile_cards",
                enabled = enabled,
                shape = RoundedCornerShape(20.dp),
                scaleOnFocus = 1.0f,
                hideFocusRing = true,
                onSelect = onClick,
                selectLabel = addLabel,
            )
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = selectionBgAlpha))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier.size(110.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = addLabel,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}
