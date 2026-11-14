package com.nuvio.app.features.collection

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.catalog.CATALOG_PAGE_SIZE
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.catalog.mergeCatalogItems
import com.nuvio.app.features.catalog.nextCatalogPaginationState
import com.nuvio.app.features.catalog.supportsPagination
import com.nuvio.app.core.poster.withCustomPosterUrls
import com.nuvio.app.core.i18n.localizedMediaTypeLabel
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.filterReleasedItems
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.trakt.TraktPublicListSourceResolver
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.collections_folder_addon_not_found
import nuvio.composeapp.generated.resources.collections_folder_trakt_movie_list
import nuvio.composeapp.generated.resources.collections_folder_trakt_series_list
import nuvio.composeapp.generated.resources.collections_tab_all
import org.jetbrains.compose.resources.getString

data class FolderTab(
    val label: String,
    val typeLabel: String = "",
    val source: CollectionSource? = null,
    val sourceKey: String? = null,
    val manifestUrl: String? = null,
    val type: String = "",
    val catalogId: String = "",
    val genre: String? = null,
    val supportsPagination: Boolean = false,
    val items: List<MetaPreview> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val nextSkip: Int? = null,
    val consecutiveDuplicatePages: Int = 0,
    val error: String? = null,
    val isAllTab: Boolean = false,
) {
    val canLoadMore: Boolean
        get() = supportsPagination && nextSkip != null
}

data class FolderDetailUiState(
    val folder: CollectionFolder? = null,
    val collectionTitle: String = "",
    val viewMode: FolderViewMode = FolderViewMode.TABBED_GRID,
    val tabs: List<FolderTab> = emptyList(),
    val selectedTabIndex: Int = 0,
    val isLoading: Boolean = true,
    val showAllTab: Boolean = true,
) {
    val selectedTab: FolderTab?
        get() = tabs.getOrNull(selectedTabIndex)

    val selectedTabCanLoadMore: Boolean
        get() {
            val currentTab = selectedTab ?: return false
            return if (currentTab.isAllTab) {
                tabs.any { !it.isAllTab && it.canLoadMore }
            } else {
                currentTab.canLoadMore
            }
        }

    val selectedTabIsLoadingMore: Boolean
        get() {
            val currentTab = selectedTab ?: return false
            return if (currentTab.isAllTab) {
                tabs.any { !it.isAllTab && it.isLoadingMore }
            } else {
                currentTab.isLoadingMore
            }
        }
}

object FolderDetailRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("FolderDetailRepository")

    private val _uiState = MutableStateFlow(FolderDetailUiState())
    val uiState: StateFlow<FolderDetailUiState> = _uiState.asStateFlow()

    private val loadJobs = mutableMapOf<Int, Job>()
    private var activeCollectionId: String? = null
    private var activeFolderId: String? = null

    fun initialize(collectionId: String, folderId: String) {
        val current = _uiState.value
        if (
            activeCollectionId == collectionId &&
            activeFolderId == folderId &&
            current.folder?.id == folderId &&
            current.tabs.isNotEmpty()
        ) {
            return
        }

        clear()
        activeCollectionId = collectionId
        activeFolderId = folderId

        val collection = CollectionRepository.getCollection(collectionId)
        if (collection == null) {
            _uiState.value = FolderDetailUiState(isLoading = false)
            return
        }

        val folder = collection.folders.find { it.id == folderId }
        if (folder == null) {
            _uiState.value = FolderDetailUiState(isLoading = false)
            return
        }

        val sources = folder.resolvedSources
        val showAll = collection.showAllTab && sources.size > 1
        val addons = AddonRepository.uiState.value.addons

        val tabs = buildList {
            if (showAll) {
                add(
                    FolderTab(
                        label = runBlocking { getString(Res.string.collections_tab_all) },
                        isAllTab = true,
                        isLoading = true,
                    ),
                )
            }
            sources.forEachIndexed { sourceIndex, source ->
                if (source.isTmdb) {
                    val mediaType = TmdbCollectionMediaType.fromString(source.mediaType)
                    val type = if (mediaType == TmdbCollectionMediaType.TV) "series" else "movie"
                    add(
                        FolderTab(
                            label = source.title?.takeIf { it.isNotBlank() } ?: "TMDB",
                            typeLabel = "TMDB",
                            source = source,
                            sourceKey = source.catalogRouteKey(),
                            type = type,
                            catalogId = tmdbCatalogId(source),
                            supportsPagination = source.tmdbSourceType !in setOf(
                                TmdbCollectionSourceType.COLLECTION.name,
                                TmdbCollectionSourceType.PERSON.name,
                                TmdbCollectionSourceType.DIRECTOR.name,
                            ),
                            isLoading = true,
                        ),
                    )
                } else if (source.isTrakt) {
                    val mediaType = TmdbCollectionMediaType.fromString(source.mediaType)
                    val type = if (mediaType == TmdbCollectionMediaType.TV) "series" else "movie"
                    val typeLabel = runBlocking {
                        getString(
                            if (mediaType == TmdbCollectionMediaType.TV) {
                                Res.string.collections_folder_trakt_series_list
                            } else {
                                Res.string.collections_folder_trakt_movie_list
                            },
                        )
                    }
                    add(
                        FolderTab(
                            label = source.title?.takeIf { it.isNotBlank() } ?: "Trakt",
                            typeLabel = typeLabel,
                            source = source,
                            sourceKey = source.catalogRouteKey(),
                            type = type,
                            catalogId = traktCatalogId(source),
                            supportsPagination = true,
                            isLoading = true,
                        ),
                    )
                } else {
                    val catalogSource = source.addonCatalogSource() ?: return@forEachIndexed
                    val resolvedCatalog = addons.findCollectionCatalog(catalogSource)
                    val addon = resolvedCatalog?.addon
                    val catalog = resolvedCatalog?.catalog
                    val label = catalog?.name ?: catalogSource.catalogId
                    val typeLabel = localizedMediaTypeLabel(catalogSource.type)
                    val genreSuffix = if (catalogSource.genre != null) " · ${catalogSource.genre}" else ""
                    add(
                        FolderTab(
                            label = "$label ($typeLabel)$genreSuffix",
                            typeLabel = typeLabel,
                            source = source,
                            sourceKey = source.catalogRouteKey(),
                            manifestUrl = addon?.manifestUrl,
                            type = catalogSource.type,
                            catalogId = catalogSource.catalogId,
                            genre = catalogSource.genre,
                            supportsPagination = catalog?.supportsPagination() == true,
                            isLoading = true,
                        ),
                    )
                }
            }
        }

        _uiState.value = FolderDetailUiState(
            folder = folder,
            collectionTitle = collection.title,
            viewMode = collection.folderViewMode,
            tabs = tabs,
            selectedTabIndex = 0,
            isLoading = true,
            showAllTab = showAll,
        )

        // Load catalog data for each source
        sources.forEachIndexed { sourceIndex, source ->
            val tabIndex = if (showAll) sourceIndex + 1 else sourceIndex
            val catalogSource = source.addonCatalogSource()
            val resolvedCatalog = catalogSource?.let { addons.findCollectionCatalog(it) }
            if (!source.isTmdb && !source.isTrakt && resolvedCatalog == null) {
                updateTab(tabIndex) {
                    it.copy(
                        isLoading = false,
                        error = runBlocking {
                            getString(Res.string.collections_folder_addon_not_found, catalogSource?.addonId.orEmpty())
                        },
                    )
                }
                return@forEachIndexed
            }

            loadTabPage(tabIndex, reset = true)
        }

        // If no sources, mark as done
        if (sources.isEmpty()) {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTabIndex = index)
    }

    fun clear() {
        loadJobs.values.forEach { it.cancel() }
        loadJobs.clear()
        activeCollectionId = null
        activeFolderId = null
        _uiState.value = FolderDetailUiState()
    }

    fun loadMoreSelectedTab() {
        val current = _uiState.value
        val selectedTab = current.selectedTab ?: return
        if (selectedTab.isAllTab) {
            current.tabs.forEachIndexed { index, tab ->
                if (!tab.isAllTab && tab.canLoadMore && !tab.isLoading && !tab.isLoadingMore) {
                    loadTabPage(index, reset = false)
                }
            }
            return
        }

        if (selectedTab.canLoadMore && !selectedTab.isLoading && !selectedTab.isLoadingMore) {
            loadTabPage(current.selectedTabIndex, reset = false)
        }
    }

    private fun updateTab(index: Int, transform: (FolderTab) -> FolderTab) {
        _uiState.update { current ->
            val updatedTabs = current.tabs.toMutableList()
            if (index !in updatedTabs.indices) return@update current
            updatedTabs[index] = transform(updatedTabs[index])

            val allDone = updatedTabs.none { !it.isAllTab && it.isLoading }
            current.copy(
                tabs = updatedTabs,
                isLoading = !allDone,
            )
        }
    }

    private fun loadTabPage(index: Int, reset: Boolean) {
        val currentTab = _uiState.value.tabs.getOrNull(index) ?: return
        val requestedSkip = if (reset) 0 else currentTab.nextSkip ?: return
        val currentSource = currentTab.source
        if (
            currentSource?.isTmdb != true &&
            currentSource?.isTrakt != true &&
            currentTab.manifestUrl == null
        ) return

        updateTab(index) { tab ->
            if (reset) {
                tab.copy(
                    items = emptyList(),
                    isLoading = true,
                    isLoadingMore = false,
                    nextSkip = null,
                    consecutiveDuplicatePages = 0,
                    error = null,
                )
            } else {
                tab.copy(
                    isLoadingMore = true,
                    error = null,
                )
            }
        }

        loadJobs.remove(index)?.cancel()
        val job = scope.launch {
            runCatching {
                var currentSkipOrPage = if (reset) {
                    if (currentTab.source?.isTmdb == true || currentTab.source?.isTrakt == true) 1 else 0
                } else {
                    requestedSkip
                }
                val firstPage = fetchTabCatalogPage(
                    tab = currentTab,
                    requestedSkip = currentSkipOrPage,
                    reset = reset,
                )
                var mergedItems = firstPage.items
                var supportsPagination = currentTab.supportsPagination || firstPage.rawItemCount >= CATALOG_PAGE_SIZE
                var paginationState = nextCatalogPaginationState(
                    supportsPagination = supportsPagination,
                    requestedSkip = requestedSkip,
                    page = firstPage,
                    loadedNewItems = true,
                    consecutiveDuplicatePages = if (reset) 0 else currentTab.consecutiveDuplicatePages,
                )
                var pagesFetched = 1
                // Fetch up to MAX_ROW_ITEM_LIMIT + 1 on initial load so desktop/folder rows honor row limits
                // up to 30 and confirm whether additional items actually exist before showing "View All".
                while (
                    reset &&
                    supportsPagination &&
                    paginationState.nextSkip != null &&
                    mergedItems.size <= HomeCatalogSettingsRepository.MAX_ROW_ITEM_LIMIT &&
                    pagesFetched < 3
                ) {
                    val nextCursor = paginationState.nextSkip ?: break
                    val nextPage = fetchTabCatalogPage(
                        tab = currentTab,
                        requestedSkip = nextCursor,
                        reset = false,
                    )
                    pagesFetched++
                    supportsPagination = supportsPagination || nextPage.rawItemCount >= CATALOG_PAGE_SIZE
                    val nextMerged = mergeCatalogItems(mergedItems, nextPage.items)
                    val loadedNewItems = nextMerged.size > mergedItems.size
                    mergedItems = nextMerged
                    if (!loadedNewItems) {
                        paginationState = paginationState.copy(nextSkip = null)
                        break
                    }
                    paginationState = nextCatalogPaginationState(
                        supportsPagination = supportsPagination,
                        requestedSkip = nextCursor,
                        page = nextPage,
                        loadedNewItems = true,
                        consecutiveDuplicatePages = 0,
                    )
                }
                Triple(mergedItems, supportsPagination, paginationState)
            }.onSuccess { (resolvedItems, supportsPagination, paginationState) ->
                val posterPattern = com.nuvio.app.core.poster.CustomPosterUrlRepository.let { repo ->
                    repo.ensureLoaded()
                    repo.patternForScreen(com.nuvio.app.core.poster.CustomPosterScreen.COLLECTIONS)
                }
                updateTab(index) { tab ->
                    val mergedItems = if (reset) {
                        resolvedItems
                    } else {
                        mergeCatalogItems(tab.items, resolvedItems)
                    }.withCustomPosterUrls(posterPattern)
                    val tabSupportsPagination = tab.supportsPagination || supportsPagination
                    val effectivePagination = if (reset) {
                        paginationState
                    } else {
                        val loadedNewItems = mergedItems.size > tab.items.size
                        nextCatalogPaginationState(
                            supportsPagination = tabSupportsPagination,
                            requestedSkip = requestedSkip,
                            page = CatalogPage(
                                items = resolvedItems,
                                rawItemCount = resolvedItems.size,
                                nextSkip = paginationState.nextSkip,
                            ),
                            loadedNewItems = loadedNewItems,
                            consecutiveDuplicatePages = tab.consecutiveDuplicatePages,
                        )
                    }
                    tab.copy(
                        items = mergedItems,
                        supportsPagination = tabSupportsPagination,
                        isLoading = false,
                        isLoadingMore = false,
                        nextSkip = effectivePagination.nextSkip,
                        consecutiveDuplicatePages = effectivePagination.consecutiveDuplicatePages,
                        error = null,
                    )
                }
                rebuildAllTab()
            }.onFailure { error ->
                log.e(error) { "Failed to load source ${currentTab.catalogId}" }
                updateTab(index) { tab ->
                    tab.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        nextSkip = if (reset) null else tab.nextSkip,
                        error = error.message,
                    )
                }
                rebuildAllTab()
            }
        }
        loadJobs[index] = job
    }

    private fun rebuildAllTab() {
        val current = _uiState.value
        if (!current.showAllTab) return
        val sourceTabs = current.tabs.filter { !it.isAllTab }

        // Round-robin merge
        val merged = mutableListOf<MetaPreview>()
        val seenKeys = mutableSetOf<String>()
        val iterators = sourceTabs.map { it.items.iterator() }
        var hasMore = true
        while (hasMore) {
            hasMore = false
            for (iterator in iterators) {
                if (iterator.hasNext()) {
                    val item = iterator.next()
                    if (seenKeys.add(item.stableKey())) {
                        merged.add(item)
                    }
                    hasMore = true
                }
            }
        }

        val updatedTabs = current.tabs.toMutableList()
        val allTabIndex = updatedTabs.indexOfFirst { it.isAllTab }
        if (allTabIndex >= 0) {
            val hasInitialLoads = sourceTabs.any { it.isLoading }
            val hasLoadMore = sourceTabs.any { it.isLoadingMore }
            val errorMessage = sourceTabs.firstOrNull { it.error != null }?.error
            val keepEmptyWhileInitialLoads = hasInitialLoads && updatedTabs[allTabIndex].items.isEmpty()
            updatedTabs[allTabIndex] = updatedTabs[allTabIndex].copy(
                items = if (keepEmptyWhileInitialLoads) emptyList() else merged,
                isLoading = hasInitialLoads,
                isLoadingMore = hasLoadMore,
                error = if (keepEmptyWhileInitialLoads) null else errorMessage.takeIf { merged.isEmpty() },
            )
        }
        _uiState.value = current.copy(tabs = updatedTabs)
    }

    private suspend fun fetchTabCatalogPage(
        tab: FolderTab,
        requestedSkip: Int,
        reset: Boolean,
    ): CatalogPage {
        val source = tab.source
        return when {
            source?.isTmdb == true -> TmdbCollectionSourceResolver.resolve(
                source = source,
                page = if (reset) 1 else requestedSkip,
            )

            source?.isTrakt == true -> TraktPublicListSourceResolver.resolve(
                source = source,
                page = if (reset) 1 else requestedSkip,
            )

            else -> fetchCatalogPage(
                manifestUrl = requireNotNull(tab.manifestUrl),
                type = tab.type,
                catalogId = tab.catalogId,
                genre = tab.genre,
                skip = requestedSkip.takeIf { it > 0 },
            )
        }.withUnreleasedFilter()
    }

    fun getCatalogSectionsForRows(): List<HomeCatalogSection> {
        val current = _uiState.value
        val folder = current.folder ?: return emptyList()
        val collectionId = activeCollectionId ?: return emptyList()

        // Emit only the contiguous loaded prefix of tabs so later faster sources never render
        // above earlier slower sources and push initial scroll/focus halfway down the folder.
        val loadedPrefix = current.tabs
            .filter { !it.isAllTab }
            .takeWhile { !it.isLoading }
            .filter { it.items.isNotEmpty() }

        return loadedPrefix.mapNotNull { tab ->
            val directSource = tab.source?.let { it.isTmdb || it.isTrakt } == true
            val target = if (directSource) {
                val sourceKey = tab.sourceKey ?: return@mapNotNull null
                CatalogTarget.CollectionSource(
                    collectionId = collectionId,
                    folderId = folder.id,
                    sourceKey = sourceKey,
                    contentType = tab.type,
                    supportsPagination = tab.supportsPagination,
                )
            } else {
                val manifestUrl = tab.manifestUrl ?: return@mapNotNull null
                CatalogTarget.Addon(
                    manifestUrl = manifestUrl,
                    contentType = tab.type,
                    catalogId = tab.catalogId,
                    genre = tab.genre,
                    supportsPagination = tab.supportsPagination,
                )
            }
            HomeCatalogSection(
                key = "folder_${folder.id}_${tab.label}",
                title = tab.label,
                subtitle = tab.typeLabel,
                addonName = "",
                target = target,
                items = tab.items,
                availableItemCount = tab.items.size,
                hasMore = tab.canLoadMore,
            )
        }
    }
}

private fun Boolean?.orFalse(): Boolean = this == true

private fun CatalogPage.withUnreleasedFilter(): CatalogPage {
    if (!HomeCatalogSettingsRepository.snapshot().hideUnreleasedContent) return this
    val filteredItems = items.filterReleasedItems(CurrentDateProvider.todayIsoDate())
    return if (filteredItems.size == items.size) this else copy(items = filteredItems)
}

private fun tmdbCatalogId(source: CollectionSource): String =
    buildString {
        append("tmdb_")
        append(source.tmdbSourceType?.lowercase().orEmpty())
        source.tmdbId?.let {
            append("_")
            append(it)
        }
        append("_")
        append(source.mediaType?.lowercase().orEmpty())
    }

private fun traktCatalogId(source: CollectionSource): String =
    listOf(
        "trakt",
        "list",
        source.traktListId?.toString().orEmpty(),
        source.mediaType?.lowercase().orEmpty(),
        TraktListSort.normalize(source.sortBy),
        TraktSortHow.normalize(source.sortHow),
    ).joinToString("_")
