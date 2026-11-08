package com.nuvio.app.controller

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.AppScreenTab
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.core.ui.AppTheme
import com.nuvio.app.core.ui.FloatingNavigationItem
import com.nuvio.app.core.ui.NuvioAsyncImage as AsyncImage
import com.nuvio.app.core.ui.NuvioNavBarScrollState
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.PlayerFullscreenIcon
import com.nuvio.app.core.ui.appTheme
import com.nuvio.app.core.ui.desktopNavigationLabelFraction
import com.nuvio.app.core.ui.isFullscreenActionActive
import com.nuvio.app.core.ui.isFullscreenActionSupported
import com.nuvio.app.core.ui.jelly.JellyNavigationBar
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.toggleFullscreenAction
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.profiles.ActiveProfileMiniAvatar
import com.nuvio.app.features.profiles.AvatarRepository
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.settings.AppIconOption
import com.nuvio.app.features.settings.AppIconRepository
import com.nuvio.app.features.settings.NavBarStyle
import com.nuvio.app.features.settings.transparentPreviewResource
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_exit_fullscreen
import nuvio.composeapp.generated.resources.action_fullscreen
import nuvio.composeapp.generated.resources.app_brand_name
import nuvio.composeapp.generated.resources.compose_profile_add_profile
import nuvio.composeapp.generated.resources.pin_incorrect
import nuvio.composeapp.generated.resources.pin_locked_try_again
import nuvio.composeapp.generated.resources.profile_label_number
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.stringResource

private const val TOP_NAV_FULLSCREEN_ID = "top_nav_fullscreen"
private const val TOP_NAV_NOTIFICATIONS_ID = "top_nav_notifications"
private const val TOP_NAV_PROFILE_ID = "top_nav_profile"

object TopBarTabOrderController {
    var isNotificationOpen: Boolean = false
        internal set
    var isProfileOpen: Boolean = false
        internal set

    internal var focusFullscreenAction: (() -> Unit)? = null
    internal var openNotificationsAction: (() -> Unit)? = null
    internal var openProfileAction: (() -> Unit)? = null
    internal var closeDropdownsAction: (() -> Unit)? = null

    /**
     * Cycles through the 7 top bar items in visual left-to-right order:
     * 0: Home -> 1: Search -> 2: Library -> 3: Settings -> 4: Fullscreen -> 5: Notifications -> 6: Profile
     */
    fun cycleTabOrder(
        step: Int,
        selectedTab: AppScreenTab,
        activateTab: (AppScreenTab) -> Unit,
    ): Boolean {
        val tabs = AppScreenTab.entries
        val totalSlots = tabs.size + 3 // 4 main tabs + Fullscreen (4) + Notifications (5) + Profile (6)
        val focusedId = ControllerFocusEngine.focusedNodeId.value

        val currentSlot = when {
            isProfileOpen || focusedId == TOP_NAV_PROFILE_ID -> tabs.size + 2
            isNotificationOpen || focusedId == TOP_NAV_NOTIFICATIONS_ID -> tabs.size + 1
            focusedId == TOP_NAV_FULLSCREEN_ID -> tabs.size
            else -> tabs.indexOf(selectedTab).coerceIn(0, tabs.lastIndex)
        }

        val nextSlot = (currentSlot + step + totalSlots) % totalSlots
        ControllerFocusEngine.setTopBarVisibleByScroll(true)

        return when (nextSlot) {
            in 0 until tabs.size -> {
                closeDropdownsAction?.invoke()
                activateTab(tabs[nextSlot])
                true
            }
            tabs.size -> {
                focusFullscreenAction?.invoke()
                true
            }
            tabs.size + 1 -> {
                openNotificationsAction?.invoke()
                true
            }
            tabs.size + 2 -> {
                openProfileAction?.invoke()
                true
            }
            else -> false
        }
    }
}

@Composable
internal fun NuvioDeckTopNavigationBar(
    items: List<FloatingNavigationItem>,
    modifier: Modifier = Modifier,
    scrollState: NuvioNavBarScrollState? = null,
    hazeState: HazeState? = null,
    contentPadding: PaddingValues = PaddingValues(top = 10.dp, bottom = 8.dp),
    navBarStyle: NavBarStyle = NavBarStyle.ADAPTIVE,
    isHeroEnabled: Boolean = false,
    profileSwitcherOpen: Boolean = false,
    windowWidth: Dp? = null,
    glowEnabled: Boolean = true,
    onProfileSelected: ((NuvioProfile) -> Unit)? = null,
    onAddProfileRequested: (() -> Unit)? = null,
    onNotificationClick: ((MetaPreview) -> Unit)? = null,
    onDropdownOpenChanged: ((Boolean) -> Unit)? = null,
) {
    if (items.isEmpty()) return

    LaunchedEffect(Unit) {
        NewArrivalsManager.ensureStarted()
        AvatarRepository.refreshAvatars()
    }

    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val rawHovered by interactionSource.collectIsHoveredAsState()
    val isControllerActive by ControllerManager.isControllerActive.collectAsState()
    val isControllerNavExpanded by ControllerFocusEngine.isNavigationZoneExpanded.collectAsState()
    val focusedSnapshot by ControllerFocusEngine.focusedNodeSnapshot.collectAsState()
    val isTopBarControllerFocused = isControllerNavExpanded ||
        focusedSnapshot?.zone == ControllerFocusZone.Navigation ||
        focusedSnapshot?.groupKey == "top_nav"
    val isHovered = (!isControllerActive && rawHovered) || isTopBarControllerFocused

    var isNotificationDropdownOpen by remember { mutableStateOf(false) }
    var isProfileDropdownOpen by remember { mutableStateOf(false) }
    val anyDropdownOpen = isNotificationDropdownOpen || isProfileDropdownOpen || profileSwitcherOpen

    LaunchedEffect(isNotificationDropdownOpen, isProfileDropdownOpen) {
        TopBarTabOrderController.isNotificationOpen = isNotificationDropdownOpen
        TopBarTabOrderController.isProfileOpen = isProfileDropdownOpen
        onDropdownOpenChanged?.invoke(isNotificationDropdownOpen || isProfileDropdownOpen)
    }

    TopBarTabOrderController.focusFullscreenAction = {
        ControllerFocusEngine.setTopBarVisibleByScroll(true)
        isNotificationDropdownOpen = false
        isProfileDropdownOpen = false
        scope.launch {
            delay(25)
            ControllerFocusEngine.requestFocus(TOP_NAV_FULLSCREEN_ID, triggerScroll = false)
        }
    }
    TopBarTabOrderController.openNotificationsAction = {
        ControllerFocusEngine.setTopBarVisibleByScroll(true)
        isProfileDropdownOpen = false
        isNotificationDropdownOpen = true
        NewArrivalsManager.markAllAsRead()
        scope.launch {
            delay(25)
            ControllerFocusEngine.requestFocus(TOP_NAV_NOTIFICATIONS_ID, triggerScroll = false)
        }
    }
    TopBarTabOrderController.openProfileAction = {
        ControllerFocusEngine.setTopBarVisibleByScroll(true)
        isNotificationDropdownOpen = false
        isProfileDropdownOpen = true
        scope.launch {
            delay(25)
            ControllerFocusEngine.requestFocus(TOP_NAV_PROFILE_ID, triggerScroll = false)
        }
    }
    TopBarTabOrderController.closeDropdownsAction = {
        isNotificationDropdownOpen = false
        isProfileDropdownOpen = false
    }

    // Track scroll direction from both nestedScroll (mouse wheel/touch) and active LazyList/Grid/ScrollState (controller D-Pad & mouse wheel)
    val isTopBarVisibleByController by ControllerFocusEngine.isTopBarVisibleByScroll.collectAsState()

    val isHomeSelected = items.first().selected
    val isSettingsSelected = items.last().selected
    ControllerFocusEngine.isHomeTabActive = isHomeSelected

    val nestedLabelVisibility = scrollState?.labelVisibility ?: 1f
    val totalScrollOffset = scrollState?.totalScrollOffset ?: 0f
    LaunchedEffect(nestedLabelVisibility, isSettingsSelected) {
        if (isSettingsSelected) {
            ControllerFocusEngine.setTopBarVisibleByScroll(true)
        } else if (nestedLabelVisibility <= 0.05f && (isHomeSelected || totalScrollOffset > 95f)) {
            ControllerFocusEngine.setTopBarVisibleByScroll(false)
        } else if (nestedLabelVisibility >= 0.95f) {
            ControllerFocusEngine.setTopBarVisibleByScroll(true)
        }
    }

    val shouldKeepBarInView = isSettingsSelected ||
        isTopBarVisibleByController ||
        isTopBarControllerFocused ||
        anyDropdownOpen

    val slideOffFraction by animateFloatAsState(
        targetValue = if (shouldKeepBarInView) 0f else 1f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "top_nav_slide_off_fraction",
    )

    val isScrolledAwayFromTop = totalScrollOffset > if (isSettingsSelected) 70f else 35f

    val labelFraction by animateFloatAsState(
        targetValue = desktopNavigationLabelFraction(
            style = navBarStyle,
            isHomeSelected = isHomeSelected,
            isHeroEnabled = isHeroEnabled,
            isHovered = isHovered,
            profileSwitcherOpen = anyDropdownOpen,
            isScrolledAwayFromTop = isScrolledAwayFromTop,
        ),
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "desktop_nav_labels",
    )

    val effectiveItems: List<FloatingNavigationItem> = remember(items, isNotificationDropdownOpen, isProfileDropdownOpen) {
        if (isNotificationDropdownOpen || isProfileDropdownOpen) {
            items.map { item ->
                FloatingNavigationItem(
                    selected = false,
                    onClick = item.onClick,
                    icon = item.icon,
                    drawable = item.drawable,
                    label = item.label,
                    content = item.content,
                )
            }
        } else {
            items
        }
    }

    val notifications by NewArrivalsManager.notifications.collectAsStateWithLifecycle()
    val unreadCount by NewArrivalsManager.unreadCount.collectAsStateWithLifecycle()
    val hasUnread by NewArrivalsManager.hasUnread.collectAsStateWithLifecycle()
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val avatars by AvatarRepository.avatars.collectAsStateWithLifecycle()
    val activeProfile = profileState.activeProfile
    val profiles = profileState.profiles

    val lastTabFocusId = remember(items) {
        val lastIdx = items.lastIndex
        "top_nav_${items[lastIdx].label}_$lastIdx"
    }

    fun closeNotificationDropdown(restoreFocus: Boolean) {
        isNotificationDropdownOpen = false
        if (restoreFocus) {
            scope.launch {
                delay(20)
                ControllerFocusEngine.requestFocus(TOP_NAV_NOTIFICATIONS_ID, triggerScroll = false)
            }
        }
    }

    fun closeProfileDropdown(restoreFocus: Boolean) {
        isProfileDropdownOpen = false
        if (restoreFocus) {
            scope.launch {
                delay(20)
                ControllerFocusEngine.requestFocus(TOP_NAV_PROFILE_ID, triggerScroll = false)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(NuvioTokens.Z.navigation),
    ) {
        // Click-outside backdrop when either dropdown is open
        if (isNotificationDropdownOpen || isProfileDropdownOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (isNotificationDropdownOpen) closeNotificationDropdown(restoreFocus = false)
                            if (isProfileDropdownOpen) closeProfileDropdown(restoreFocus = false)
                        },
                    ),
            )
        }

        // Full-width Top Navigation Bar with smooth top-to-bottom black-to-transparent gradient & auto-hide slide
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = -size.height * slideOffFraction
                    alpha = (1f - slideOffFraction).coerceIn(0f, 1f)
                }
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.96f),
                            0.45f to Color.Black.copy(alpha = 0.75f),
                            0.78f to Color.Black.copy(alpha = 0.36f),
                            1.0f to Color.Transparent,
                        ),
                    ),
                )
                .hoverable(interactionSource)
                .padding(contentPadding)
                .padding(horizontal = 28.dp, vertical = 6.dp),
        ) {
            val viewportWidth = windowWidth ?: maxWidth
            val iconSize = when {
                viewportWidth < 800.dp -> 24.dp
                viewportWidth > 1600.dp -> 28.dp
                else -> 26.dp
            }
            val barHeight = iconSize + 20.dp
            val trackWidth = ((iconSize + 22.dp + 68.dp * labelFraction) * effectiveItems.size + 12.dp)
                .coerceAtMost((maxWidth - 220.dp).coerceAtLeast(200.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight + 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Far Left: Nuvio icon-only graphical mark + Main Navigation Tabs (no glass pill background)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    NuvioIconOnlyLogo(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { items.firstOrNull()?.onClick?.invoke() },
                            ),
                    )

                    JellyNavigationBar(
                        items = effectiveItems,
                        labelFraction = labelFraction,
                        glowStrength = 0f,
                        horizontalLabels = true,
                        iconSize = iconSize,
                        modifier = Modifier
                            .width(trackWidth)
                            .height(barHeight),
                    ) {
                        // Intentionally empty: no glass pill background behind top menu icons
                    }
                }

                // Far Right: Fullscreen Toggle + Notification Bell Icon + Standalone Profile Photo Avatar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (isFullscreenActionSupported) {
                        TopBarFullscreenButton(
                            onMoveLeft = {
                                ControllerFocusEngine.requestFocus(lastTabFocusId, triggerScroll = false)
                            },
                            onMoveRight = {
                                ControllerFocusEngine.requestFocus(TOP_NAV_NOTIFICATIONS_ID, triggerScroll = false)
                            },
                        )
                    }

                    TopBarNotificationBellButton(
                        hasUnread = hasUnread,
                        unreadCount = unreadCount,
                        isOpen = isNotificationDropdownOpen,
                        onMoveLeft = {
                            if (isFullscreenActionSupported) {
                                ControllerFocusEngine.requestFocus(TOP_NAV_FULLSCREEN_ID, triggerScroll = false)
                            } else {
                                ControllerFocusEngine.requestFocus(lastTabFocusId, triggerScroll = false)
                            }
                        },
                        onMoveRight = {
                            ControllerFocusEngine.requestFocus(TOP_NAV_PROFILE_ID, triggerScroll = false)
                        },
                        onClick = {
                            val nextOpen = !isNotificationDropdownOpen
                            isProfileDropdownOpen = false
                            isNotificationDropdownOpen = nextOpen
                            if (nextOpen) {
                                NewArrivalsManager.markAllAsRead()
                            }
                        },
                    )

                    TopBarProfileAvatarButton(
                        profile = activeProfile,
                        avatars = avatars,
                        isOpen = isProfileDropdownOpen,
                        onMoveLeft = {
                            ControllerFocusEngine.requestFocus(TOP_NAV_NOTIFICATIONS_ID, triggerScroll = false)
                        },
                        onClick = {
                            val nextOpen = !isProfileDropdownOpen
                            isNotificationDropdownOpen = false
                            isProfileDropdownOpen = nextOpen
                        },
                    )
                }
            }
        }

        // Anchored Notification Center Dropdown (Top-Right below Bell)
        AnimatedVisibility(
            visible = isNotificationDropdownOpen,
            enter = fadeIn(tween(180)) + slideInVertically(tween(200, easing = FastOutSlowInEasing)) { -it / 6 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(160, easing = FastOutSlowInEasing)) { -it / 6 },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 68.dp, end = 64.dp)
                .zIndex(NuvioTokens.Z.sheet),
        ) {
            NotificationCenterDropdownPanel(
                notifications = notifications,
                onDismiss = { closeNotificationDropdown(restoreFocus = true) },
                onNotificationSelected = { notification ->
                    isNotificationDropdownOpen = false
                    onNotificationClick?.invoke(notification.toMetaPreview())
                },
            )
        }

        // Anchored Standalone Profile Switcher Dropdown (Top-Right below Profile Photo)
        AnimatedVisibility(
            visible = isProfileDropdownOpen,
            enter = fadeIn(tween(180)) + slideInVertically(tween(200, easing = FastOutSlowInEasing)) { -it / 6 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(160, easing = FastOutSlowInEasing)) { -it / 6 },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 68.dp, end = 24.dp)
                .zIndex(NuvioTokens.Z.sheet),
        ) {
            StandaloneProfileSwitcherDropdownPanel(
                profiles = profiles,
                activeProfile = activeProfile,
                avatars = avatars,
                onDismiss = { closeProfileDropdown(restoreFocus = true) },
                onProfileSelected = { profile ->
                    isProfileDropdownOpen = false
                    onProfileSelected?.invoke(profile)
                },
                onManageProfilesClicked = {
                    isProfileDropdownOpen = false
                    onAddProfileRequested?.invoke()
                },
            )
        }
    }
}

@Composable
private fun NuvioIconOnlyLogo(
    modifier: Modifier = Modifier,
) {
    val state by remember {
        AppIconRepository.ensureLoaded()
        AppIconRepository.state
    }.collectAsStateWithLifecycle()
    val iconRes: DrawableResource = when (MaterialTheme.appTheme) {
        AppTheme.JADE -> AppIconOption.EMERALD.transparentPreviewResource
        AppTheme.ROSE_GOLD -> AppIconOption.ROSE_GOLD.transparentPreviewResource
        AppTheme.ARCTIC_BLUE -> AppIconOption.ARCTIC_BLUE.transparentPreviewResource
        AppTheme.GRAPHITE -> AppIconOption.GRAPHITE.transparentPreviewResource
        else -> state.selected.transparentPreviewResource
    }
    Image(
        bitmap = imageResource(iconRes),
        contentDescription = stringResource(Res.string.app_brand_name),
        modifier = modifier,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.High,
    )
}

@Composable
private fun TopBarFullscreenButton(
    onMoveLeft: () -> Boolean,
    onMoveRight: () -> Boolean,
) {
    val tokens = MaterialTheme.nuvio
    val isActive = isFullscreenActionActive()
    val isFocused = isControllerNodeFocused(TOP_NAV_FULLSCREEN_ID)
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val highlighted = isFocused || isHovered
    val label = stringResource(
        if (isActive) {
            Res.string.action_exit_fullscreen
        } else {
            Res.string.action_fullscreen
        },
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> tokens.colors.accent.copy(alpha = 0.28f)
            isHovered -> Color.White.copy(alpha = 0.14f)
            else -> Color.Transparent
        },
        animationSpec = tween(160),
        label = "fullscreen_btn_bg_color",
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bgColor, CircleShape)
            .hoverable(hoverSource)
            .controllerFocusable(
                id = TOP_NAV_FULLSCREEN_ID,
                zone = ControllerFocusZone.Navigation,
                groupKey = "top_nav",
                shape = CircleShape,
                scaleOnFocus = 1f,
                hideFocusRing = true,
                onSelect = ::toggleFullscreenAction,
                selectLabel = label,
                onMoveLeft = onMoveLeft,
                onMoveRight = onMoveRight,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = ::toggleFullscreenAction,
            ),
        contentAlignment = Alignment.Center,
    ) {
        PlayerFullscreenIcon(
            isExit = isActive,
            tint = if (highlighted) Color.White else tokens.colors.textPrimary.copy(alpha = 0.88f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TopBarNotificationBellButton(
    hasUnread: Boolean,
    unreadCount: Int,
    isOpen: Boolean,
    onMoveLeft: () -> Boolean,
    onMoveRight: () -> Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val isFocused = isControllerNodeFocused(TOP_NAV_NOTIFICATIONS_ID)
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val highlighted = isFocused || isOpen || isHovered

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused || isOpen -> tokens.colors.accent.copy(alpha = 0.28f)
            isHovered -> Color.White.copy(alpha = 0.14f)
            else -> Color.Transparent
        },
        animationSpec = tween(160),
        label = "bell_bg_color",
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bgColor, CircleShape)
            .hoverable(hoverSource)
            .controllerFocusable(
                id = TOP_NAV_NOTIFICATIONS_ID,
                zone = ControllerFocusZone.Navigation,
                groupKey = "top_nav",
                shape = CircleShape,
                scaleOnFocus = 1f,
                hideFocusRing = true,
                onSelect = onClick,
                selectLabel = "Notifications",
                onMoveLeft = onMoveLeft,
                onMoveRight = onMoveRight,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (hasUnread || isOpen) Icons.Rounded.Notifications else Icons.Rounded.NotificationsNone,
            contentDescription = "Notifications",
            tint = if (highlighted) Color.White else tokens.colors.textPrimary.copy(alpha = 0.88f),
            modifier = Modifier.size(22.dp),
        )

        if (hasUnread) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(if (unreadCount > 1) 15.dp else 10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE50914), CircleShape)
                    .border(1.5.dp, Color(0xFF0A0A0E), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (unreadCount > 1) {
                    Text(
                        text = unreadCount.coerceAtMost(9).toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBarProfileAvatarButton(
    profile: NuvioProfile?,
    avatars: List<com.nuvio.app.features.profiles.AvatarCatalogItem>,
    isOpen: Boolean,
    onMoveLeft: () -> Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val isFocused = isControllerNodeFocused(TOP_NAV_PROFILE_ID)
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused || isOpen -> tokens.colors.accent.copy(alpha = 0.28f)
            isHovered -> Color.White.copy(alpha = 0.14f)
            else -> Color.Transparent
        },
        animationSpec = tween(160),
        label = "profile_btn_bg_color",
    )
    val profileLabel = profile?.name?.takeIf { it.isNotBlank() } ?: "Profile"

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bgColor, CircleShape)
            .hoverable(hoverSource)
            .controllerFocusable(
                id = TOP_NAV_PROFILE_ID,
                zone = ControllerFocusZone.Navigation,
                groupKey = "top_nav",
                shape = CircleShape,
                scaleOnFocus = 1f,
                hideFocusRing = true,
                onSelect = onClick,
                selectLabel = profileLabel,
                onMoveLeft = onMoveLeft,
                onMoveRight = { true },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ActiveProfileMiniAvatar(
            profile = profile,
            avatars = avatars,
            selected = isFocused || isOpen,
            size = 30,
        )
    }
}

@Composable
private fun NotificationCenterDropdownPanel(
    notifications: List<NewArrivalNotification>,
    onDismiss: () -> Unit,
    onNotificationSelected: (NewArrivalNotification) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val cappedItems = remember(notifications) {
        notifications.sortedByDescending { it.detectedAt }.take(NewArrivalsManager.MAX_NOTIFICATIONS)
    }

    RegisterControllerHandler(
        key = "top_nav_notifications_dropdown_handler",
        onBack = {
            onDismiss()
            true
        },
    )

    LaunchedEffect(cappedItems.size) {
        delay(30)
        val firstId = if (cappedItems.isNotEmpty()) {
            "notification_item_${cappedItems.first().id}"
        } else {
            "notification_empty_card"
        }
        ControllerFocusEngine.requestFocus(firstId, triggerScroll = false)
    }

    Surface(
        modifier = Modifier
            .width(428.dp)
            .heightIn(max = 560.dp)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        shape = RoundedCornerShape(18.dp),
        color = Color(0xF5111218),
        tonalElevation = 12.dp,
        shadowElevation = 18.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "New Arrivals",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.colors.textPrimary,
                    )
                    Text(
                        text = "Verified 1080p & 4K releases ready to stream",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.colors.textMuted,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${cappedItems.size} / ${NewArrivalsManager.MAX_NOTIFICATIONS}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.colors.textPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(8.dp))

            if (cappedItems.isEmpty()) {
                val emptyId = "notification_empty_card"
                val isEmptyFocused = isControllerNodeFocused(emptyId)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isEmptyFocused) tokens.colors.accent.copy(alpha = 0.24f)
                            else Color.White.copy(alpha = 0.04f),
                        )
                        .controllerFocusable(
                            id = emptyId,
                            zone = ControllerFocusZone.Overlay,
                            groupKey = "top_notifications_dropdown",
                            isDefaultFocus = true,
                            shape = RoundedCornerShape(14.dp),
                            scaleOnFocus = 1f,
                            hideFocusRing = true,
                            onSelect = onDismiss,
                            selectLabel = "Close",
                            onMoveUp = { true },
                            onMoveDown = { true },
                            onMoveLeft = { true },
                            onMoveRight = { true },
                        )
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsNone,
                            contentDescription = null,
                            tint = tokens.colors.textMuted,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = "No New Arrivals Yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.colors.textPrimary,
                        )
                        Text(
                            text = "Brand-new episodes from your Watch History and newly released movies with verified HD/4K streams will appear here automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.colors.textMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                val nowEpochMs = remember(cappedItems) { EpisodeReleaseDatePlatform.nowEpochMs() }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(
                        items = cappedItems,
                        key = { _, item -> item.id },
                    ) { index, item ->
                        val nodeId = "notification_item_${item.id}"
                        val isFocused = isControllerNodeFocused(nodeId)
                        val hoverSource = remember { MutableInteractionSource() }
                        val isHovered by hoverSource.collectIsHoveredAsState()

                        val rowBgColor = when {
                            isFocused -> tokens.colors.accent.copy(alpha = 0.28f)
                            isHovered -> Color.White.copy(alpha = 0.09f)
                            else -> Color(0xFF191B24)
                        }
                        val rowBorderColor = if (isFocused) {
                            tokens.colors.accent.copy(alpha = 0.75f)
                        } else {
                            Color.White.copy(alpha = 0.05f)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(rowBgColor, RoundedCornerShape(12.dp))
                                .border(1.dp, rowBorderColor, RoundedCornerShape(12.dp))
                                .hoverable(hoverSource)
                                .controllerFocusable(
                                    id = nodeId,
                                    zone = ControllerFocusZone.Overlay,
                                    groupKey = "top_notifications_dropdown",
                                    isDefaultFocus = index == 0,
                                    shape = RoundedCornerShape(12.dp),
                                    scaleOnFocus = 1f,
                                    hideFocusRing = true,
                                    onSelect = { onNotificationSelected(item) },
                                    selectLabel = "View Details",
                                    onMoveUp = {
                                        if (index > 0) {
                                            val prevId = "notification_item_${cappedItems[index - 1].id}"
                                            scope.launch {
                                                runCatching { listState.animateScrollToItem((index - 1).coerceAtLeast(0)) }
                                                ControllerFocusEngine.requestFocus(prevId, triggerScroll = false)
                                            }
                                        }
                                        true
                                    },
                                    onMoveDown = {
                                        if (index < cappedItems.lastIndex) {
                                            val nextId = "notification_item_${cappedItems[index + 1].id}"
                                            scope.launch {
                                                runCatching { listState.animateScrollToItem(index + 1) }
                                                ControllerFocusEngine.requestFocus(nextId, triggerScroll = false)
                                            }
                                        }
                                        true
                                    },
                                    onMoveLeft = { true },
                                    onMoveRight = { true },
                                )
                                .clickable { onNotificationSelected(item) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // 16:9 Landscape Thumbnail
                            Box(
                                modifier = Modifier
                                    .width(116.dp)
                                    .height(65.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0B0C10)),
                            ) {
                                val imageUrl = item.thumbnailUrl ?: item.posterUrl
                                if (!imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                if (item.is4K) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xD9000000))
                                            .padding(horizontal = 5.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = "4K",
                                            color = tokens.colors.accent,
                                            fontSize = 9.sp,
                                            lineHeight = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                    }
                                }
                            }

                            // Header Label, Title, and Relative Timestamp
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = item.headerLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFocused) Color.White else tokens.colors.accent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!item.subtitle.isNullOrBlank()) {
                                    Text(
                                        text = item.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = NewArrivalsManager.formatRelativeTimestamp(item.detectedAt, nowEpochMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isFocused) Color.White.copy(alpha = 0.80f) else tokens.colors.textMuted,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StandaloneProfileSwitcherDropdownPanel(
    profiles: List<NuvioProfile>,
    activeProfile: NuvioProfile?,
    avatars: List<com.nuvio.app.features.profiles.AvatarCatalogItem>,
    onDismiss: () -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onManageProfilesClicked: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val scope = rememberCoroutineScope()
    var pinTargetProfile by remember { mutableStateOf<NuvioProfile?>(null) }

    RegisterControllerHandler(
        key = "top_nav_profile_dropdown_handler",
        onBack = {
            if (pinTargetProfile != null) {
                pinTargetProfile = null
                true
            } else {
                onDismiss()
                true
            }
        },
    )

    val manageNodeId = "top_profile_dropdown_manage"

    LaunchedEffect(profiles, pinTargetProfile) {
        delay(30)
        if (pinTargetProfile != null) {
            ControllerFocusEngine.requestFocus("top_profile_pin_digit_1", triggerScroll = false)
        } else {
            val activeIdx = profiles.indexOfFirst { it.profileIndex == activeProfile?.profileIndex }.coerceAtLeast(0)
            val firstId = profiles.getOrNull(activeIdx)?.let { "top_profile_dropdown_item_${it.profileIndex}" }
                ?: manageNodeId
            ControllerFocusEngine.requestFocus(firstId, triggerScroll = false)
        }
    }

    Surface(
        modifier = Modifier
            .width(276.dp)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    if (pinTargetProfile != null) {
                        pinTargetProfile = null
                    } else {
                        onDismiss()
                    }
                    true
                } else {
                    false
                }
            },
        shape = RoundedCornerShape(18.dp),
        color = Color(0xF5111218),
        tonalElevation = 12.dp,
        shadowElevation = 18.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        if (pinTargetProfile != null) {
            val target = pinTargetProfile!!
            DropdownPinEntryPad(
                profile = target,
                onCancel = { pinTargetProfile = null },
                onVerified = {
                    pinTargetProfile = null
                    onProfileSelected(target)
                },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Switch Profile",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = tokens.colors.textMuted,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )

                profiles.forEachIndexed { index, profile ->
                    val isActive = profile.profileIndex == activeProfile?.profileIndex
                    val nodeId = "top_profile_dropdown_item_${profile.profileIndex}"
                    val isFocused = isControllerNodeFocused(nodeId)
                    val hoverSource = remember { MutableInteractionSource() }
                    val isHovered by hoverSource.collectIsHoveredAsState()

                    val rowBgColor = when {
                        isFocused -> tokens.colors.accent.copy(alpha = 0.28f)
                        isHovered -> Color.White.copy(alpha = 0.10f)
                        isActive -> Color.White.copy(alpha = 0.06f)
                        else -> Color.Transparent
                    }

                    val selectProfileAction = {
                        if (isActive) {
                            onDismiss()
                        } else if (profile.pinEnabled) {
                            pinTargetProfile = profile
                        } else {
                            onProfileSelected(profile)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(rowBgColor, RoundedCornerShape(12.dp))
                            .hoverable(hoverSource)
                            .controllerFocusable(
                                id = nodeId,
                                zone = ControllerFocusZone.Overlay,
                                groupKey = "top_profile_dropdown",
                                isDefaultFocus = isActive,
                                shape = RoundedCornerShape(12.dp),
                                scaleOnFocus = 1f,
                                hideFocusRing = true,
                                onSelect = selectProfileAction,
                                selectLabel = if (isActive) "Active Profile" else "Switch Profile",
                                onMoveUp = {
                                    if (index > 0) {
                                        val prev = profiles[index - 1]
                                        ControllerFocusEngine.requestFocus(
                                            "top_profile_dropdown_item_${prev.profileIndex}",
                                            triggerScroll = false,
                                        )
                                    }
                                    true
                                },
                                onMoveDown = {
                                    if (index < profiles.lastIndex) {
                                        val next = profiles[index + 1]
                                        ControllerFocusEngine.requestFocus(
                                            "top_profile_dropdown_item_${next.profileIndex}",
                                            triggerScroll = false,
                                        )
                                    } else {
                                        ControllerFocusEngine.requestFocus(manageNodeId, triggerScroll = false)
                                    }
                                    true
                                },
                                onMoveLeft = { true },
                                onMoveRight = { true },
                            )
                            .clickable(onClick = selectProfileAction)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ActiveProfileMiniAvatar(
                            profile = profile,
                            avatars = avatars,
                            selected = isActive || isFocused,
                            size = 32,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = profile.name.ifBlank {
                                stringResource(Res.string.profile_label_number, profile.profileIndex)
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isActive || isFocused) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive || isFocused) tokens.colors.textPrimary else tokens.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isActive) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Active",
                                tint = if (isFocused) Color.White else tokens.colors.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        } else if (profile.pinEnabled) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "PIN Protected",
                                tint = tokens.colors.textMuted,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(4.dp))

                val isManageFocused = isControllerNodeFocused(manageNodeId)
                val manageHoverSource = remember { MutableInteractionSource() }
                val isManageHovered by manageHoverSource.collectIsHoveredAsState()
                val manageBg = when {
                    isManageFocused -> tokens.colors.accent.copy(alpha = 0.28f)
                    isManageHovered -> Color.White.copy(alpha = 0.10f)
                    else -> Color.Transparent
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(manageBg, RoundedCornerShape(12.dp))
                        .hoverable(manageHoverSource)
                        .controllerFocusable(
                            id = manageNodeId,
                            zone = ControllerFocusZone.Overlay,
                            groupKey = "top_profile_dropdown",
                            shape = RoundedCornerShape(12.dp),
                            scaleOnFocus = 1f,
                            hideFocusRing = true,
                            onSelect = onManageProfilesClicked,
                            selectLabel = stringResource(Res.string.compose_profile_add_profile),
                            onMoveUp = {
                                val lastProfile = profiles.lastOrNull()
                                if (lastProfile != null) {
                                    ControllerFocusEngine.requestFocus(
                                        "top_profile_dropdown_item_${lastProfile.profileIndex}",
                                        triggerScroll = false,
                                    )
                                }
                                true
                            },
                            onMoveDown = { true },
                            onMoveLeft = { true },
                            onMoveRight = { true },
                        )
                        .clickable(onClick = onManageProfilesClicked)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = if (isManageFocused) Color.White else tokens.colors.textPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(Res.string.compose_profile_add_profile),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isManageFocused) FontWeight.Bold else FontWeight.Medium,
                        color = if (isManageFocused) Color.White else tokens.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DropdownPinEntryPad(
    profile: NuvioProfile,
    onCancel: () -> Unit,
    onVerified: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val scope = rememberCoroutineScope()
    var pin by remember(profile.profileIndex) { mutableStateOf("") }
    var error by remember(profile.profileIndex) { mutableStateOf<String?>(null) }
    var isVerifying by remember(profile.profileIndex) { mutableStateOf(false) }

    fun submitDigit(digit: String) {
        if (pin.length < 4 && !isVerifying) {
            error = null
            pin += digit
            if (pin.length == 4) {
                isVerifying = true
                scope.launch {
                    val result = ProfileRepository.verifyPin(profile.profileIndex, pin)
                    if (result.unlocked) {
                        onVerified()
                    } else {
                        error = if (result.retryAfterSeconds > 0) {
                            getString(Res.string.pin_locked_try_again, result.retryAfterSeconds)
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
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
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
                    text = "PROFILE PIN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        letterSpacing = 1.1.sp,
                    ),
                    fontWeight = FontWeight.Bold,
                    color = tokens.colors.textMuted,
                )
            }
            Text(
                text = profile.name.ifBlank { stringResource(Res.string.profile_label_number, profile.profileIndex) },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.colors.textPrimary,
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
            repeat(4) { idx ->
                val filled = idx < pin.length
                val hasErr = !error.isNullOrBlank()
                val dotColor = when {
                    hasErr -> MaterialTheme.colorScheme.error
                    filled -> tokens.colors.accent
                    else -> Color.White.copy(alpha = 0.22f)
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .then(
                            if (filled) {
                                Modifier.background(dotColor, CircleShape)
                            } else {
                                Modifier.border(1.5.dp, dotColor, CircleShape)
                            },
                        ),
                )
            }
        }

        if (!error.isNullOrBlank()) {
            Text(
                text = error!!,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("Cancel", "0", "Del"),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            rows.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.forEach { keyLabel ->
                        val nodeId = "top_profile_pin_digit_$keyLabel"
                        val isFocused = isControllerNodeFocused(nodeId)
                        val hoverSource = remember { MutableInteractionSource() }
                        val isHovered by hoverSource.collectIsHoveredAsState()
                        val onKeyClick = {
                            when (keyLabel) {
                                "Cancel" -> onCancel()
                                "Del" -> deleteDigit()
                                else -> submitDigit(keyLabel)
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
                                    id = nodeId,
                                    zone = ControllerFocusZone.Overlay,
                                    groupKey = "top_profile_pin_pad",
                                    isDefaultFocus = keyLabel == "1",
                                    shape = keyShape,
                                    scaleOnFocus = 1f,
                                    hideFocusRing = true,
                                    onSelect = onKeyClick,
                                    selectLabel = keyLabel,
                                    onSecondaryAction = ::deleteDigit,
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
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isFocused) Color.White else tokens.colors.textPrimary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

