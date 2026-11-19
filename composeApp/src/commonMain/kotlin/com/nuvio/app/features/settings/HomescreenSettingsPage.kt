package com.nuvio.app.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.controller.controllerFocusable
import com.nuvio.app.controller.isControllerNodeFocused
import com.nuvio.app.core.ui.NuvioActionLabel
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.Slider
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.home.HomeCatalogSettingsItem
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeHeroStyle
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import kotlin.math.roundToInt
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.action_reset
import nuvio.composeapp.generated.resources.layout_hide_unreleased
import nuvio.composeapp.generated.resources.layout_hide_unreleased_sub
import nuvio.composeapp.generated.resources.layout_catalog_type
import nuvio.composeapp.generated.resources.layout_catalog_type_sub
import nuvio.composeapp.generated.resources.settings_homescreen_empty_message
import nuvio.composeapp.generated.resources.settings_homescreen_empty_title
import nuvio.composeapp.generated.resources.settings_homescreen_keep_home_focused
import nuvio.composeapp.generated.resources.settings_homescreen_limit_reached
import nuvio.composeapp.generated.resources.settings_homescreen_load_failed_title
import nuvio.composeapp.generated.resources.settings_homescreen_no_sources_selected
import nuvio.composeapp.generated.resources.settings_homescreen_pin_to_move_toast
import nuvio.composeapp.generated.resources.settings_homescreen_section_catalogs
import nuvio.composeapp.generated.resources.settings_homescreen_section_catalogs_collections
import nuvio.composeapp.generated.resources.settings_homescreen_section_collections
import nuvio.composeapp.generated.resources.settings_homescreen_section_hero
import nuvio.composeapp.generated.resources.settings_homescreen_section_hero_sources
import nuvio.composeapp.generated.resources.settings_homescreen_selected_count
import nuvio.composeapp.generated.resources.settings_homescreen_show_hero
import nuvio.composeapp.generated.resources.settings_homescreen_show_hero_description
import nuvio.composeapp.generated.resources.settings_homescreen_summary
import nuvio.composeapp.generated.resources.settings_homescreen_summary_hint
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

internal fun LazyListScope.homescreenSettingsContent(
    isTablet: Boolean,
    heroEnabled: Boolean,
    showCatalogType: Boolean,
    hideUnreleasedContent: Boolean,
    items: List<HomeCatalogSettingsItem>,
    isCatalogLoading: Boolean,
    catalogErrorMessage: String?,
) {
    val selectedHeroSourceCount = items.count { it.heroSourceEnabled }
    val enabledCatalogCount = items.count { it.enabled }
    item {
        HomescreenSummaryCard(
            isTablet = isTablet,
            enabledCatalogCount = enabledCatalogCount,
            totalCatalogCount = items.size,
            selectedHeroSourceCount = selectedHeroSourceCount,
        )
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_homescreen_section_hero),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                val homescreenState by HomeCatalogSettingsRepository.uiState.collectAsState()
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_homescreen_show_hero),
                    description = stringResource(Res.string.settings_homescreen_show_hero_description),
                    checked = heroEnabled,
                    isDefaultFocus = true,
                    isTablet = isTablet,
                    onMoveUp = { true },
                    onCheckedChange = HomeCatalogSettingsRepository::setHeroEnabled,
                )
                SettingsGroupDivider(isTablet = isTablet)
                HomeHeroStyleSelector(
                    isTablet = isTablet,
                    selectedStyle = homescreenState.heroStyle,
                    onStyleSelected = HomeCatalogSettingsRepository::setHeroStyle,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.layout_catalog_type),
                    description = stringResource(Res.string.layout_catalog_type_sub),
                    checked = showCatalogType,
                    isTablet = isTablet,
                    onCheckedChange = HomeCatalogSettingsRepository::setShowCatalogType,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.layout_hide_unreleased),
                    description = stringResource(Res.string.layout_hide_unreleased_sub),
                    checked = hideUnreleasedContent,
                    isTablet = isTablet,
                    onCheckedChange = HomeCatalogSettingsRepository::setHideUnreleasedContent,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = "My List",
                    description = "Show your Library as a My List row at the top of the home screen.",
                    checked = homescreenState.showMyListRow,
                    isTablet = isTablet,
                    onCheckedChange = HomeCatalogSettingsRepository::setShowMyListRow,
                )
                SettingsGroupDivider(isTablet = isTablet)
                val minLimit = HomeCatalogSettingsRepository.MIN_ROW_ITEM_LIMIT.toFloat()
                val maxLimit = HomeCatalogSettingsRepository.MAX_ROW_ITEM_LIMIT.toFloat()
                val rowItemLimit = homescreenState.rowItemLimit.coerceIn(
                    HomeCatalogSettingsRepository.MIN_ROW_ITEM_LIMIT,
                    HomeCatalogSettingsRepository.MAX_ROW_ITEM_LIMIT,
                )
                val hapticFeedback = LocalHapticFeedback.current
                var sliderValue by remember(rowItemLimit) { mutableFloatStateOf(rowItemLimit.toFloat()) }
                var lastHapticStep by remember(rowItemLimit) { mutableStateOf(rowItemLimit.toFloat()) }
                val shownLimit = sliderValue.roundToInt().coerceIn(
                    HomeCatalogSettingsRepository.MIN_ROW_ITEM_LIMIT,
                    HomeCatalogSettingsRepository.MAX_ROW_ITEM_LIMIT,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .controllerSliderFocus(
                            id = "settings_homescreen_row_item_limit",
                            label = "Row Poster Limit",
                            onDecrement = {
                                val next = (sliderValue.roundToInt() - 1).coerceIn(
                                    HomeCatalogSettingsRepository.MIN_ROW_ITEM_LIMIT,
                                    HomeCatalogSettingsRepository.MAX_ROW_ITEM_LIMIT,
                                )
                                sliderValue = next.toFloat()
                                HomeCatalogSettingsRepository.setRowItemLimit(next)
                            },
                            onIncrement = {
                                val next = (sliderValue.roundToInt() + 1).coerceIn(
                                    HomeCatalogSettingsRepository.MIN_ROW_ITEM_LIMIT,
                                    HomeCatalogSettingsRepository.MAX_ROW_ITEM_LIMIT,
                                )
                                sliderValue = next.toFloat()
                                HomeCatalogSettingsRepository.setRowItemLimit(next)
                            },
                        )
                        .padding(horizontal = if (isTablet) 18.dp else 16.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Row Poster Limit",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Maximum posters shown per catalog row before the See All card (10–30)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ValueBox(text = "$shownLimit posters", modifier = Modifier.wrapContentWidth())
                    }
                    Slider(
                        value = sliderValue.coerceIn(minLimit, maxLimit),
                        onValueChange = {
                            val snapped = snapToStep(it, 1f).coerceIn(minLimit, maxLimit)
                            sliderValue = snapped

                            if (snapped != lastHapticStep) {
                                lastHapticStep = snapped
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onValueChangeFinished = {
                            val limit = sliderValue.roundToInt().coerceIn(
                                HomeCatalogSettingsRepository.MIN_ROW_ITEM_LIMIT,
                                HomeCatalogSettingsRepository.MAX_ROW_ITEM_LIMIT,
                            )
                            HomeCatalogSettingsRepository.setRowItemLimit(limit)
                        },
                        valueRange = minLimit..maxLimit,
                        steps = calculateSteps(minLimit, maxLimit, 1f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    item {
        val catalogOnlyItems = items.filter { !it.isCollection }
        if (heroEnabled && catalogOnlyItems.isNotEmpty()) {
            var heroSourcesExpanded by remember { mutableStateOf(false) }
            SettingsSection(
                title = stringResource(Res.string.settings_homescreen_section_hero_sources),
                isTablet = isTablet,
            ) {
                HeroSourcesDropdown(
                    isTablet = isTablet,
                    items = catalogOnlyItems,
                    selectedHeroSourceCount = selectedHeroSourceCount,
                    expanded = heroSourcesExpanded,
                    onExpandedChange = { heroSourcesExpanded = it },
                )
            }
        }
    }
    item {
        if (isCatalogLoading && items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                NuvioLoadingIndicator(
                    modifier = Modifier.size(28.dp),
                )
            }
        } else if (catalogErrorMessage != null && items.isEmpty()) {
            HomeEmptyStateCard(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.settings_homescreen_load_failed_title),
                message = catalogErrorMessage,
                actionLabel = stringResource(Res.string.action_retry),
                onActionClick = AddonRepository::refreshAll,
            )
        } else if (items.isEmpty()) {
            HomeEmptyStateCard(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.settings_homescreen_empty_title),
                message = stringResource(Res.string.settings_homescreen_empty_message),
            )
        } else {
            val catalogCount = items.count { !it.isCollection }
            val collectionCount = items.count { it.isCollection }
            val sectionTitle = when {
                collectionCount > 0 && catalogCount > 0 -> stringResource(Res.string.settings_homescreen_section_catalogs_collections)
                collectionCount > 0 -> stringResource(Res.string.settings_homescreen_section_collections)
                else -> stringResource(Res.string.settings_homescreen_section_catalogs)
            }
            SettingsSection(
                title = sectionTitle,
                isTablet = isTablet,
                actions = {
                    NuvioActionLabel(
                        text = stringResource(Res.string.action_reset),
                        onClick = HomeCatalogSettingsRepository::resetToDefaults,
                    )
                },
            ) {
                val hapticFeedback = LocalHapticFeedback.current
                val pinToMoveToast = stringResource(Res.string.settings_homescreen_pin_to_move_toast)

                HomescreenCatalogList(
                    isTablet = isTablet,
                    items = items,
                    onPinnedDragAttempt = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        NuvioToastController.show(pinToMoveToast)
                    },
                )
            }
        }
    }
}

@Composable
private fun HeroSourcesDropdown(
    isTablet: Boolean,
    items: List<HomeCatalogSettingsItem>,
    selectedHeroSourceCount: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val noSourcesSelected = stringResource(Res.string.settings_homescreen_no_sources_selected)
    val isHeroHeaderFocused = isControllerNodeFocused("settings_homescreen_hero_sources_header")
    val heroHeaderBg = if (isHeroHeaderFocused) tokens.colors.accent.copy(alpha = 0.32f) else Color.Transparent
    SettingsGroup(isTablet = isTablet) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroHeaderBg, RoundedCornerShape(12.dp))
                .controllerFocusable(
                    id = "settings_homescreen_hero_sources_header",
                    groupKey = "settings_content",
                    cornerRadius = 12.dp,
                    focusScale = 1.0f,
                    hideFocusRing = true,
                    selectLabel = if (expanded) "Collapse" else "Expand",
                    onSelect = { onExpandedChange(!expanded) },
                )
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(
                        Res.string.settings_homescreen_selected_count,
                        selectedHeroSourceCount,
                        HomeCatalogSettingsRepository.HERO_SOURCE_SELECTION_LIMIT,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = items.filter { it.heroSourceEnabled }
                        .joinToString(separator = ", ") { it.displayTitle }
                        .ifBlank { noSourcesSelected },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                SettingsGroupDivider(isTablet = isTablet)
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        SettingsGroupDivider(isTablet = isTablet)
                    }
                    SettingsSwitchRow(
                        title = item.displayTitle,
                        description = if (!item.heroSourceEnabled &&
                            selectedHeroSourceCount >= HomeCatalogSettingsRepository.HERO_SOURCE_SELECTION_LIMIT
                        ) {
                            stringResource(
                                Res.string.settings_homescreen_limit_reached,
                                item.addonName,
                                HomeCatalogSettingsRepository.HERO_SOURCE_SELECTION_LIMIT,
                            )
                        } else {
                            item.addonName
                        },
                        checked = item.heroSourceEnabled,
                        enabled = item.heroSourceEnabled ||
                            selectedHeroSourceCount < HomeCatalogSettingsRepository.HERO_SOURCE_SELECTION_LIMIT,
                        isTablet = isTablet,
                        onCheckedChange = { HomeCatalogSettingsRepository.setHeroSourceEnabled(item.key, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomescreenSummaryCard(
    isTablet: Boolean,
    enabledCatalogCount: Int,
    totalCatalogCount: Int,
    selectedHeroSourceCount: Int,
) {
    SettingsGroup(isTablet = isTablet) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_homescreen_keep_home_focused),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    Res.string.settings_homescreen_summary,
                    enabledCatalogCount,
                    totalCatalogCount,
                    selectedHeroSourceCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.settings_homescreen_summary_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomescreenCatalogList(
    isTablet: Boolean,
    items: List<HomeCatalogSettingsItem>,
    onPinnedDragAttempt: () -> Unit,
) {
    var expandedKey by remember { mutableStateOf<String?>(null) }
    val hapticFeedback = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
    ) { from, to ->
        val fromItem = items.getOrNull(from.index)
        val toItem = items.getOrNull(to.index)
        if (fromItem?.isPinnedToTop == true || toItem?.isPinnedToTop == true) {
            return@rememberReorderableLazyListState
        }
        HomeCatalogSettingsRepository.moveByIndex(from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    SettingsGroup(isTablet = isTablet) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (items.size * 240 + 240).dp),
            state = lazyListState,
            userScrollEnabled = false,
        ) {
            itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                ReorderableItem(
                    reorderableLazyListState,
                    key = item.key,
                    enabled = !item.isPinnedToTop,
                ) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)

                    Surface(shadowElevation = elevation) {
                        Column {
                            if (index > 0) {
                                SettingsGroupDivider(isTablet = isTablet)
                            }
                            HomescreenCatalogRow(
                                item = item,
                                isTablet = isTablet,
                                expanded = expandedKey == item.key,
                                onExpandedChange = { shouldExpand ->
                                    expandedKey = if (shouldExpand) item.key else null
                                },
                                onTitleChange = { HomeCatalogSettingsRepository.setCustomTitle(item.key, it) },
                                onEnabledChange = { HomeCatalogSettingsRepository.setEnabled(item.key, it) },
                                dragHandleScope = this@ReorderableItem,
                                onPinnedDragAttempt = onPinnedDragAttempt,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeroStyleSelector(
    isTablet: Boolean,
    selectedStyle: HomeHeroStyle,
    onStyleSelected: (HomeHeroStyle) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = if (isTablet) 18.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Hero Section",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Choose how the featured banner is rendered at the top of the home screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeHeroStyle.entries.forEach { style ->
                Box(modifier = Modifier.weight(1f)) {
                    HomeHeroStyleOption(
                        style = style,
                        selected = selectedStyle == style,
                        isTablet = isTablet,
                        onClick = { onStyleSelected(style) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeroStyleOption(
    style: HomeHeroStyle,
    selected: Boolean,
    isTablet: Boolean,
    onClick: () -> Unit,
) {
    val focusId = "settings_homescreen_hero_style_${style.name}"
    val isFocused = isControllerNodeFocused(focusId)
    val tokens = MaterialTheme.nuvio
    val containerColor = when {
        isFocused -> tokens.colors.accent.copy(alpha = 0.24f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderStroke = if (isFocused) {
        BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f))
    } else {
        null
    }
    val title = when (style) {
        HomeHeroStyle.Default -> "Default"
        HomeHeroStyle.Poster -> "Poster"
    }
    val description = when (style) {
        HomeHeroStyle.Default -> "Edge-to-edge top fade banner"
        HomeHeroStyle.Poster -> "Framed full-width landscape card"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .controllerFocusable(
                id = focusId,
                groupKey = "settings_content",
                cornerRadius = 12.dp,
                focusScale = 1.0f,
                hideFocusRing = true,
                selectLabel = "$title Style",
                onSelect = onClick,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        color = containerColor,
        border = borderStroke,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp),
                contentAlignment = Alignment.Center,
            ) {
                HomeHeroStylePreview(style = style)
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd).size(if (isTablet) 24.dp else 18.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = if (isTablet) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeHeroStylePreview(
    style: HomeHeroStyle,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (style) {
            HomeHeroStyle.Default -> {
                Box(
                    modifier = Modifier
                        .width(136.dp)
                        .height(86.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)),
                ) {
                    // Full-bleed edge-to-edge backdrop fading into lower content
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                    // Minimal title + pill bars aligned on lower-left fade
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(42.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)),
                        )
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
                        )
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)),
                        )
                    }
                    // Bottom shelf peek
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                            )
                        }
                    }
                }
            }

            HomeHeroStyle.Poster -> {
                Column(
                    modifier = Modifier
                        .width(136.dp)
                        .height(86.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    // Simulated top nav bar above framed card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)),
                        )
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
                        )
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
                        )
                    }
                    // Framed rounded landscape poster card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f),
                                shape = RoundedCornerShape(7.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .align(Alignment.BottomCenter)
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.36f)),
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 7.dp, bottom = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(42.dp)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)),
                            )
                            Box(
                                modifier = Modifier
                                    .width(58.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
                            )
                            Box(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f)),
                            )
                        }
                    }
                    // Shelf row peeking underneath poster frame
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
                            )
                        }
                    }
                }
            }
        }
    }
}
