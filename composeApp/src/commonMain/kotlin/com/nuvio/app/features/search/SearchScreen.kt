package com.nuvio.app.features.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.controller.controllerFocusable
import kotlinx.coroutines.launch
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.core.network.NetworkStatusRepository
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioNetworkOfflineCard
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.nuvioConsumePointerEvents
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.core.ui.withDuplicateSafeLazyKeys
import com.nuvio.app.core.ui.ScreenActivityEffect
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.firstEnabledManifestError
import com.nuvio.app.features.addons.hasPendingEnabledManifests
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.buildAddonCatalogRefreshSignature
import com.nuvio.app.features.home.components.HomeCatalogRowSection
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import com.nuvio.app.features.home.components.homeSectionHorizontalPaddingForWidth
import com.nuvio.app.features.home.components.HomeSkeletonRow
import com.nuvio.app.core.ui.posterGridColumnCountForCatalogWidth
import com.nuvio.app.features.home.components.posterGridColumnCountForWidth
import com.nuvio.app.isDesktop
import com.nuvio.app.features.watched.WatchedRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.compose_nav_search
import nuvio.composeapp.generated.resources.home_empty_no_sources_title
import nuvio.composeapp.generated.resources.search_empty_no_sources_message
import nuvio.composeapp.generated.resources.compose_search_clear
import nuvio.composeapp.generated.resources.compose_search_discover_title
import nuvio.composeapp.generated.resources.compose_search_empty_failed_message
import nuvio.composeapp.generated.resources.compose_search_empty_failed_title
import nuvio.composeapp.generated.resources.compose_search_empty_no_results_message
import nuvio.composeapp.generated.resources.compose_search_empty_no_results_title
import nuvio.composeapp.generated.resources.compose_search_empty_no_search_catalogs_message
import nuvio.composeapp.generated.resources.compose_search_empty_no_search_catalogs_title
import nuvio.composeapp.generated.resources.compose_search_placeholder
import nuvio.composeapp.generated.resources.compose_search_recent_searches
import nuvio.composeapp.generated.resources.compose_search_remove_recent_search
import org.jetbrains.compose.resources.stringResource

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    topChromePadding: Dp? = null,
    listState: LazyListState = rememberLazyListState(),
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    searchFocusRequestCount: Int = 0,
    scrollToTopRequests: Flow<Unit> = emptyFlow(),
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    var isSearchFocused by remember { mutableStateOf(false) }
    val controllerFocusedNodeId by com.nuvio.app.controller.ControllerFocusEngine.focusedNodeId.collectAsStateWithLifecycle()

    fun focusSearchInputAndOpenKeyboard(fromAutoFocus: Boolean) {
        com.nuvio.app.controller.ControllerFocusEngine.focusNodeById("search_input_field", triggerScroll = false)
        coroutineScope.launch {
            if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                runCatching { listState.scrollToItem(0) }
            }
            withFrameNanos { }
            com.nuvio.app.controller.ControllerFocusEngine.focusNodeById("search_input_field", triggerScroll = false)
            if (!fromAutoFocus && isSearchFocused) {
                runCatching { focusManager.clearFocus(force = true) }
                withFrameNanos { }
            }
            runCatching { focusRequester.requestFocus() }
            withFrameNanos { }
            if (!isSearchFocused) {
                runCatching { focusRequester.requestFocus() }
                delay(16)
            }
            com.nuvio.app.controller.PlatformVirtualKeyboard.show(fromAutoFocus = fromAutoFocus)
        }
    }

    LaunchedEffect(controllerFocusedNodeId) {
        if (
            controllerFocusedNodeId != null &&
            controllerFocusedNodeId != "search_input_field" &&
            isSearchFocused
        ) {
            runCatching { focusRequester.freeFocus() }
            runCatching { focusManager.clearFocus() }
        }
    }

    androidx.compose.runtime.DisposableEffect(focusRequester) {
        com.nuvio.app.controller.PlatformVirtualKeyboard.registerInputFocusRestorer {
            runCatching { focusRequester.requestFocus() }
        }
        onDispose {
            com.nuvio.app.controller.PlatformVirtualKeyboard.registerInputFocusRestorer(null)
        }
    }

    ScreenActivityEffect(listState) { screenActive ->
        if (!screenActive) {
            isSearchFocused = false
            focusRequester.freeFocus()
            listState.stopScroll()
        }
    }

    ScreenActivityEffect(searchFocusRequestCount) { screenActive ->
        if (screenActive && searchFocusRequestCount > 0) {
            focusSearchInputAndOpenKeyboard(fromAutoFocus = false)
        }
    }

    LaunchedEffect(Unit) {
        AddonRepository.initialize()
        WatchedRepository.ensureLoaded()
        SearchHistoryRepository.ensureLoaded()
    }

    val addonsUiState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val uiState by SearchRepository.uiState.collectAsStateWithLifecycle()
    val discoverUiState by SearchRepository.discoverUiState.collectAsStateWithLifecycle()
    val homeCatalogSettingsUiState by remember {
        HomeCatalogSettingsRepository.snapshot()
        HomeCatalogSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val recentSearches by SearchHistoryRepository.uiState.collectAsStateWithLifecycle()
    val watchedUiState by WatchedRepository.uiState.collectAsStateWithLifecycle()
    val fullyWatchedSeriesKeys by WatchedRepository.fullyWatchedSeriesKeys.collectAsStateWithLifecycle()
    val networkStatusUiState by NetworkStatusRepository.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var lastRequestedQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var observedOfflineState by remember { mutableStateOf(false) }
    val discoverInFocus by remember(query, listState) {
        derivedStateOf {
            query.isBlank() && listState.firstVisibleItemIndex > 0
        }
    }

    ScreenActivityEffect(scrollToTopRequests) { screenActive ->
        if (!screenActive) return@ScreenActivityEffect
        scrollToTopRequests.collect {
            listState.animateScrollToItem(0)
        }
    }

    val addonRefreshKey = remember(addonsUiState.addons) {
        buildAddonCatalogRefreshSignature(addonsUiState.addons)
    }
    val addonManifestsLoading = addonsUiState.addons.hasPendingEnabledManifests()

    ScreenActivityEffect(addonRefreshKey, homeCatalogSettingsUiState.hideUnreleasedContent) { screenActive ->
        if (!screenActive) return@ScreenActivityEffect
        SearchRepository.refreshDiscover(addonsUiState.addons)
    }

    ScreenActivityEffect(query, addonRefreshKey, homeCatalogSettingsUiState.hideUnreleasedContent) { screenActive ->
        if (!screenActive) return@ScreenActivityEffect
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            lastRequestedQuery = null
            SearchRepository.clear()
        } else {
            delay(350)
            lastRequestedQuery = normalizedQuery
            SearchRepository.search(
                query = normalizedQuery,
                addons = addonsUiState.addons,
            )
        }
    }

    ScreenActivityEffect(listState, query, discoverUiState.canLoadMore, discoverUiState.isLoading) { screenActive ->
        if (!screenActive || query.isNotBlank()) return@ScreenActivityEffect

        snapshotFlow { listState.layoutInfo }
            .map { layoutInfo ->
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisible >= layoutInfo.totalItemsCount - 4
            }
            .distinctUntilChanged()
            .filter { it && discoverUiState.canLoadMore && !discoverUiState.isLoading }
            .collect {
                SearchRepository.loadMoreDiscover()
            }
    }

    ScreenActivityEffect(query, lastRequestedQuery, uiState.isLoading, uiState.sections) { screenActive ->
        if (!screenActive) return@ScreenActivityEffect
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return@ScreenActivityEffect
        if (lastRequestedQuery != normalizedQuery) return@ScreenActivityEffect
        if (uiState.isLoading || uiState.sections.isEmpty()) return@ScreenActivityEffect
        SearchHistoryRepository.recordSearch(normalizedQuery)
    }

    ScreenActivityEffect(networkStatusUiState.condition, query, addonRefreshKey) { screenActive ->
        if (!screenActive) return@ScreenActivityEffect
        when (networkStatusUiState.condition) {
            NetworkCondition.NoInternet,
            NetworkCondition.ServersUnreachable,
            -> {
                observedOfflineState = true
            }

            NetworkCondition.Online -> {
                if (!observedOfflineState) return@ScreenActivityEffect
                observedOfflineState = false

                val normalizedQuery = query.trim()
                if (normalizedQuery.isBlank()) {
                    SearchRepository.refreshDiscover(
                        addons = addonsUiState.addons,
                        forceRefresh = true,
                    )
                } else {
                    SearchRepository.search(
                        query = normalizedQuery,
                        addons = addonsUiState.addons,
                        forceRefresh = true,
                    )
                }
            }

            NetworkCondition.Unknown,
            NetworkCondition.Checking,
            -> Unit
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val posterCardStyle = rememberPosterCardStyleUiState()
        val discoverColumns = remember(maxWidth, maxHeight, posterCardStyle.widthDp, isDesktop) {
            if (isDesktop) {
                posterGridColumnCountForCatalogWidth(
                    screenWidth = maxWidth,
                    basePosterWidthDp = posterCardStyle.widthDp,
                )
            } else {
                posterGridColumnCountForWidth(maxWidth)
            }
        }
        val homeSectionPadding = remember(maxWidth) {
            homeSectionHorizontalPaddingForWidth(maxWidth.value)
        }
        val headerTitle = when {
            query.isNotBlank() -> stringResource(Res.string.compose_nav_search)
            discoverInFocus -> stringResource(Res.string.compose_search_discover_title)
            else -> stringResource(Res.string.compose_nav_search)
        }

        NuvioScreen(
            horizontalPadding = 0.dp,
            topPadding = if (topChromePadding != null) 0.dp else null,
            listState = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
        item(key = "search_header_section") {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.background)
                        .nuvioConsumePointerEvents(),
                )
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    NuvioScreenHeader(
                        title = headerTitle,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        topPadding = topChromePadding,
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.foundation.layout.Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        NuvioInputField(
                            value = query,
                            onValueChange = {
                                query = it
                            },
                            placeholder = stringResource(Res.string.compose_search_placeholder),
                            shape = RoundedCornerShape(NuvioTokens.Radius.xl),
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .controllerFocusable(
                                    id = "search_input_field",
                                    isDefaultFocus = true,
                                    isTextInput = true,
                                    shape = RoundedCornerShape(16.dp),
                                    onFocused = {
                                        focusSearchInputAndOpenKeyboard(fromAutoFocus = true)
                                    },
                                    onSelect = {
                                        focusSearchInputAndOpenKeyboard(fromAutoFocus = false)
                                    },
                                    selectLabel = "Type with Keyboard",
                                    onSecondaryAction = if (query.isNotEmpty()) {
                                        {
                                            query = ""
                                            SearchRepository.clear()
                                        }
                                    } else {
                                        null
                                    },
                                    secondaryActionLabel = "Clear",
                                )
                                .onFocusChanged {
                                    isSearchFocused = it.isFocused
                                },
                            trailingContent = if (query.isNotEmpty()) {
                                {
                                    IconButton(
                                        onClick = {
                                            query = ""
                                            SearchRepository.clear()
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(Res.string.compose_search_clear),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }

        if (query.isBlank()) {
            if (recentSearches.isNotEmpty()) {
                item(key = "recent_searches") {
                    SearchRecentSection(
                        recentSearches = recentSearches,
                        onSearchPress = { recentQuery ->
                            query = recentQuery
                            com.nuvio.app.controller.PlatformVirtualKeyboard.hide()
                        },
                        onRemoveSearch = SearchHistoryRepository::removeSearch,
                        onClearAll = SearchHistoryRepository::clearAll,
                    )
                }
            }
                discoverContent(
                    state = discoverUiState,
                    isSourceLoading = addonManifestsLoading,
                    columns = discoverColumns,
                    networkCondition = networkStatusUiState.condition,
                    onTypeSelected = SearchRepository::selectDiscoverType,
                    onCatalogSelected = SearchRepository::selectDiscoverCatalog,
                    onGenreSelected = SearchRepository::selectDiscoverGenre,
                    onRetry = {
                        NetworkStatusRepository.requestRefresh(force = true)
                        if (addonsUiState.addons.firstEnabledManifestError() != null) {
                            AddonRepository.refreshAll()
                        } else {
                            SearchRepository.refreshDiscover(
                                addons = addonsUiState.addons,
                                forceRefresh = true,
                            )
                        }
                    },
                    watchedKeys = watchedUiState.watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                )
            } else {
                val normalizedQuery = query.trim()
                val isWaitingForSearch = normalizedQuery.isNotBlank() && lastRequestedQuery != normalizedQuery
                when {
                    isWaitingForSearch -> {
                        items(2) {
                            HomeSkeletonRow(
                                horizontalPadding = homeSectionPadding,
                            )
                        }
                    }

                    (uiState.isLoading || addonManifestsLoading) && uiState.sections.isEmpty() -> {
                        items(2) {
                            HomeSkeletonRow(
                                horizontalPadding = homeSectionPadding,
                            )
                        }
                    }

                    uiState.sections.isEmpty() -> {
                        item {
                            SearchEmptyStateCard(
                                reason = uiState.emptyStateReason,
                                errorMessage = uiState.errorMessage,
                                networkCondition = networkStatusUiState.condition,
                                onRetry = {
                                    if (normalizedQuery.isNotBlank()) {
                                        NetworkStatusRepository.requestRefresh(force = true)
                                        if (addonsUiState.addons.firstEnabledManifestError() != null) {
                                            AddonRepository.refreshAll()
                                        } else {
                                            SearchRepository.search(
                                                query = normalizedQuery,
                                                addons = addonsUiState.addons,
                                                forceRefresh = true,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = homeSectionPadding),
                            )
                        }
                    }

                    else -> {
                        items(
                            items = uiState.sections.withDuplicateSafeLazyKeys { section -> section.key },
                            key = { section -> section.lazyKey },
                        ) { keyedSection ->
                            val section = keyedSection.value
                            HomeCatalogRowSection(
                                section = section,
                                modifier = Modifier.padding(bottom = 12.dp),
                                watchedKeys = watchedUiState.watchedKeys,
                                fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                                onPosterClick = onPosterClick,
                                onPosterLongClick = onPosterLongClick,
                            )
                        }
                        if (uiState.isLoading) {
                            item(key = "search_loading_more") {
                                HomeSkeletonRow(
                                    horizontalPadding = homeSectionPadding,
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
private fun SearchEmptyStateCard(
    reason: SearchEmptyStateReason?,
    errorMessage: String?,
    networkCondition: NetworkCondition,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (
        reason == SearchEmptyStateReason.RequestFailed &&
        (networkCondition == NetworkCondition.NoInternet || networkCondition == NetworkCondition.ServersUnreachable)
    ) {
        NuvioNetworkOfflineCard(
            condition = networkCondition,
            modifier = modifier,
            onRetry = onRetry,
        )
        return
    }

    val title: String
    val message: String

    when (reason) {
        SearchEmptyStateReason.NoActiveAddons -> {
            title = stringResource(Res.string.home_empty_no_sources_title)
            message = stringResource(Res.string.search_empty_no_sources_message)
        }

        SearchEmptyStateReason.NoSearchCatalogs -> {
            title = stringResource(Res.string.compose_search_empty_no_search_catalogs_title)
            message = stringResource(Res.string.compose_search_empty_no_search_catalogs_message)
        }

        SearchEmptyStateReason.RequestFailed -> {
            title = stringResource(Res.string.compose_search_empty_failed_title)
            message = errorMessage ?: stringResource(Res.string.compose_search_empty_failed_message)
        }

        SearchEmptyStateReason.NoResults, null -> {
            title = stringResource(Res.string.compose_search_empty_no_results_title)
            message = stringResource(Res.string.compose_search_empty_no_results_message)
        }
    }

    HomeEmptyStateCard(
        modifier = modifier,
        title = title,
        message = message,
        actionLabel = if (reason == SearchEmptyStateReason.RequestFailed) {
            stringResource(Res.string.action_retry)
        } else {
            null
        },
        onActionClick = if (reason == SearchEmptyStateReason.RequestFailed) onRetry else null,
    )
}

@Composable
private fun SearchRecentSection(
    recentSearches: List<String>,
    onSearchPress: (String) -> Unit,
    onRemoveSearch: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.compose_search_recent_searches),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.compose_search_clear),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onClearAll)
                    .controllerFocusable(
                        id = "recent_searches_clear_all",
                        groupKey = "recent_searches_header",
                        shape = RoundedCornerShape(10.dp),
                        onSelect = onClearAll,
                        selectLabel = "Clear All",
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        recentSearches.forEachIndexed { index, recentQuery ->
            SearchRecentRow(
                index = index,
                query = recentQuery,
                onSearchPress = { onSearchPress(recentQuery) },
                onRemovePress = { onRemoveSearch(recentQuery) },
                onClearAll = onClearAll,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun SearchRecentRow(
    index: Int,
    query: String,
    onSearchPress: () -> Unit,
    onRemovePress: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowGroupKey = "recent_search_row_$index"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSearchPress)
                .controllerFocusable(
                    id = "recent_search_$index",
                    groupKey = rowGroupKey,
                    shape = RoundedCornerShape(16.dp),
                    onSelect = onSearchPress,
                    selectLabel = "Search",
                    onSecondaryAction = onRemovePress,
                    secondaryActionLabel = "Remove",
                )
                .background(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = query,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onRemovePress,
            modifier = Modifier.controllerFocusable(
                id = "recent_search_remove_$index",
                groupKey = rowGroupKey,
                shape = RoundedCornerShape(50),
                onSelect = onRemovePress,
                selectLabel = "Remove",
                onSecondaryAction = onClearAll,
                secondaryActionLabel = "Clear All",
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(Res.string.compose_search_remove_recent_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
