package com.nuvio.app.controller

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nuvio.app.AppScreenTab
import com.nuvio.app.navigation.AppRoute
import com.nuvio.app.navigation.CatalogRoute
import com.nuvio.app.navigation.DetailRoute
import com.nuvio.app.navigation.EntityBrowseRoute
import com.nuvio.app.navigation.FolderDetailRoute
import com.nuvio.app.navigation.PersonDetailRoute
import com.nuvio.app.navigation.PlayerRoute
import com.nuvio.app.navigation.StreamRoute
import com.nuvio.app.navigation.TabsRoute
import com.nuvio.app.features.player.dispatchNavigationBack

@Composable
fun ControllerNavigationHost(
    currentRoute: Any?,
    selectedTab: AppScreenTab,
    isModalOverlayOpen: Boolean,
    onBack: () -> Boolean,
    onShoulderLeft: (() -> Boolean)? = null,
    onShoulderRight: (() -> Boolean)? = null,
    onTertiaryAction: (() -> Boolean)? = null,
    onViewAction: (() -> Boolean)? = null,
    onMenuAction: (() -> Boolean)? = null,
) {
    val routeIdentity = when (currentRoute) {
        is DetailRoute -> "DetailRoute:${currentRoute.type}:${currentRoute.id}"
        is CatalogRoute -> "CatalogRoute:${currentRoute.launchId}"
        is PersonDetailRoute -> "PersonDetailRoute:${currentRoute.personId}"
        is EntityBrowseRoute -> "EntityBrowseRoute:${currentRoute.entityId}"
        is FolderDetailRoute -> "FolderDetailRoute:${currentRoute.collectionId}:${currentRoute.folderId}"
        null -> ""
        else -> currentRoute::class.simpleName ?: ""
    }

    val routeAndTabKey = "$routeIdentity:$selectedTab"
    remember(routeAndTabKey) {
        ControllerFocusEngine.onRouteOrTabChanged(routeAndTabKey, focusImmediately = false)
        true
    }

    LaunchedEffect(currentRoute, selectedTab, isModalOverlayOpen) {
        ControllerFocusEngine.onRouteOrTabChanged(routeAndTabKey)
        val context = when {
            isModalOverlayOpen -> ControllerScreenContext(
                screenType = ControllerScreenType.ModalOverlay,
                selectLabel = "Select",
                backLabel = "Close",
            )
            currentRoute is PlayerRoute -> ControllerScreenContext(
                screenType = ControllerScreenType.Player,
                selectLabel = "Play / Pause",
                backLabel = "Exit Player",
                secondaryActionLabel = "Audio",
                tertiaryActionLabel = "Subtitles",
                lbLabel = "-10s",
                rbLabel = "+10s",
                viewLabel = "Sources",
                menuLabel = "Episodes",
                dPadLabel = "Seek / Volume",
            )
            currentRoute is DetailRoute -> ControllerScreenContext(
                screenType = ControllerScreenType.Details,
                selectLabel = "Select",
                backLabel = "Back",
                dPadLabel = "Navigate",
                rightStickLabel = "Scroll",
            )
            currentRoute is StreamRoute -> ControllerScreenContext(
                screenType = ControllerScreenType.Streams,
                selectLabel = "Play Stream",
                backLabel = "Back",
                secondaryActionLabel = "Stream Options",
                tertiaryActionLabel = "Reload",
                lbLabel = "Prev Addon",
                rbLabel = "Next Addon",
                dPadLabel = "Select Stream",
            )
            currentRoute is FolderDetailRoute -> ControllerScreenContext(
                screenType = ControllerScreenType.Catalog,
                selectLabel = "Open",
                backLabel = "Back",
                secondaryActionLabel = "Options",
                hasSeasonsOrTabs = false,
                seasonsOrTabsLabel = null,
                lbLabel = null,
                rbLabel = null,
                dPadLabel = "Browse Grid",
                rightStickLabel = "Scroll",
            )
            currentRoute is CatalogRoute || currentRoute is EntityBrowseRoute -> ControllerScreenContext(
                screenType = ControllerScreenType.Catalog,
                selectLabel = "Open",
                backLabel = "Back",
                secondaryActionLabel = "Options",
                dPadLabel = "Browse Grid",
                rightStickLabel = "Scroll",
            )
            currentRoute !is TabsRoute -> ControllerScreenContext(
                screenType = ControllerScreenType.Settings,
                selectLabel = "Select",
                backLabel = "Back",
                dPadLabel = "Navigate",
                rightStickLabel = "Scroll",
            )
            selectedTab == AppScreenTab.Home -> ControllerScreenContext(
                screenType = ControllerScreenType.Home,
                selectLabel = "Open",
                backLabel = "Exit",
                secondaryActionLabel = "Options",
                tertiaryActionLabel = "Quick Search",
                lbLabel = "Prev Tab",
                rbLabel = "Next Tab",
                viewLabel = "Switch Profile",
                menuLabel = "Navigation",
                dPadLabel = "Navigate",
                rightStickLabel = "Scroll",
            )
            selectedTab == AppScreenTab.Search -> ControllerScreenContext(
                screenType = ControllerScreenType.Search,
                selectLabel = "Select",
                backLabel = "Home",
                secondaryActionLabel = "Clear / Options",
                tertiaryActionLabel = "Focus Search",
                lbLabel = "Prev Tab",
                rbLabel = "Next Tab",
                dPadLabel = "Navigate",
                rightStickLabel = "Scroll",
            )
            selectedTab == AppScreenTab.Library -> ControllerScreenContext(
                screenType = ControllerScreenType.Library,
                selectLabel = "Open",
                backLabel = "Home",
                secondaryActionLabel = "Options",
                tertiaryActionLabel = "Quick Search",
                lbLabel = "Prev Tab",
                rbLabel = "Next Tab",
                dPadLabel = "Navigate",
                rightStickLabel = "Scroll",
            )
            selectedTab == AppScreenTab.Settings -> ControllerScreenContext(
                screenType = ControllerScreenType.Settings,
                selectLabel = "Select",
                backLabel = "Home",
                lbLabel = "Prev Tab",
                rbLabel = "Next Tab",
                viewLabel = "Switch Profile",
                dPadLabel = "Navigate",
                rightStickLabel = "Scroll",
            )
            else -> ControllerScreenContext()
        }
        ControllerManager.updateScreenContext(context)
    }

    RegisterControllerHandler(
        onBack = onBack,
        onShoulderLeft = onShoulderLeft,
        onShoulderRight = onShoulderRight,
        onTertiaryAction = onTertiaryAction,
        onViewAction = onViewAction,
        onMenuAction = onMenuAction,
    )
}

@Composable
fun BoxScope.ControllerMainOverlay(currentRoute: Any?) {
    if (currentRoute !is PlayerRoute) {
        ControllerHintToolbar(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 20.dp)
                .zIndex(100f),
        )
    }
}

@Composable
fun BoxScope.ControllerGateOverlay(visible: Boolean) {
    // Controller Hint Toolbar remains strictly hidden on splash, boot, login, profile picker, and PIN entry
}
