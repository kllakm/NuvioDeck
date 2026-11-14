package com.nuvio.app.core.ui
import androidx.compose.runtime.collectAsState
import com.nuvio.app.controller.ControllerManager
import com.nuvio.app.controller.controllerFocusable
import com.nuvio.app.controller.isControllerNodeFocused

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nuvio.app.isDesktop
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_view_all
import nuvio.composeapp.generated.resources.poster_logo_content_description
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

enum class NuvioPosterShape {
    Poster,
    Square,
    Landscape,
}

enum class NuvioViewAllPillSize {
    Default,
    Compact,
}

@Composable
fun <T> NuvioShelfSection(
    title: String,
    entries: List<T>,
    modifier: Modifier = Modifier,
    rowModifier: Modifier = Modifier,
    headerHorizontalPadding: Dp = 0.dp,
    rowContentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 10.dp,
    onViewAllClick: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    viewAllPillSize: NuvioViewAllPillSize = NuvioViewAllPillSize.Default,
    groupKey: String? = null,
    key: ((T) -> Any)? = null,
    animatePlacement: Boolean = false,
    state: LazyListState = rememberLazyListState(),
    itemContent: @Composable (T) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val duplicateSafeEntries = remember(entries, key) {
        key?.let { entries.withDuplicateSafeLazyKeys(it) }
    }
    val effectiveGroupKey = remember(groupKey, title) {
        groupKey ?: if (title.isNotBlank()) "shelf_${title.replace(" ", "_").lowercase()}" else null
    }

    ScreenActivityEffect(state) { active ->
        if (!active) state.stopScroll()
    }
    com.nuvio.app.controller.ProvideControllerGroupKey(effectiveGroupKey) {
    com.nuvio.app.controller.ProvideControllerRowLazyListState(state) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap + NuvioTokens.Space.s2),
    ) {
        if (title.isNotBlank()) {
            NuvioShelfSectionHeader(
                title = title,
                modifier = Modifier.padding(horizontal = headerHorizontalPadding),
                onViewAllClick = onViewAllClick,
                onTitleClick = onTitleClick,
                viewAllPillSize = viewAllPillSize,
            )
        }
        LazyRow(
            state = state,
            modifier = rowModifier
                .nuvioHorizontalScrollBleed(LocalVerticalBarPadding.current)
                .nuvioDesktopDragScroll(state),
            contentPadding = PaddingValues(
                start = rowContentPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                top = maxOf(rowContentPadding.calculateTopPadding(), 8.dp),
                end = rowContentPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                bottom = maxOf(rowContentPadding.calculateBottomPadding(), 12.dp),
            ).withVerticalBar(),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            if (duplicateSafeEntries != null) {
                itemsIndexed(
                    items = duplicateSafeEntries,
                    key = { _, entry -> entry.lazyKey },
                    contentType = { _, _ -> "poster" },
                ) { index, keyedEntry ->
                    com.nuvio.app.controller.ProvideControllerRowItemInfo(
                        index = index,
                        totalCount = duplicateSafeEntries.size,
                    ) {
                        if (animatePlacement) {
                            Box(modifier = Modifier.animateItem()) { itemContent(keyedEntry.value) }
                        } else {
                            itemContent(keyedEntry.value)
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = entries,
                    contentType = { _, _ -> "poster" },
                ) { index, entry ->
                    com.nuvio.app.controller.ProvideControllerRowItemInfo(
                        index = index,
                        totalCount = entries.size,
                    ) {
                        if (animatePlacement) {
                            Box(modifier = Modifier.animateItem()) { itemContent(entry) }
                        } else {
                            itemContent(entry)
                        }
                    }
                }
            }
            if (onViewAllClick != null) {
                item(
                    key = "shelf_see_all_card_$title",
                    contentType = "see_all",
                ) {
                    NuvioSeeAllPosterCard(
                        title = title,
                        onClick = onViewAllClick,
                    )
                }
            }
        }
    }
    }
    }
}

internal fun Modifier.nuvioDesktopDragScroll(
    state: LazyListState,
): Modifier {
    if (!isDesktop) return this

    return pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            var totalDx = 0f
            var totalDy = 0f
            var dragging = false

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                val delta = change.position - change.previousPosition
                totalDx += delta.x
                totalDy += delta.y

                if (!dragging) {
                    val horizontalDrag =
                        abs(totalDx) > viewConfiguration.touchSlop && abs(totalDx) > abs(totalDy)
                    val verticalDrag =
                        abs(totalDy) > viewConfiguration.touchSlop && abs(totalDy) > abs(totalDx)

                    when {
                        verticalDrag -> break
                        horizontalDrag -> dragging = true
                        else -> continue
                    }
                }

                state.dispatchRawDelta(-delta.x)
                change.consume()
            }
        }
    }
}

internal fun Modifier.nuvioDesktopDragScroll(
    state: ScrollState,
): Modifier {
    if (!isDesktop) return this

    return pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            var totalDx = 0f
            var totalDy = 0f
            var dragging = false

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                val delta = change.position - change.previousPosition
                totalDx += delta.x
                totalDy += delta.y

                if (!dragging) {
                    val horizontalDrag =
                        abs(totalDx) > viewConfiguration.touchSlop && abs(totalDx) > abs(totalDy)
                    val verticalDrag =
                        abs(totalDy) > viewConfiguration.touchSlop && abs(totalDy) > abs(totalDx)

                    when {
                        verticalDrag -> break
                        horizontalDrag -> dragging = true
                        else -> continue
                    }
                }

                state.dispatchRawDelta(-delta.x)
                change.consume()
            }
        }
    }
}

@Composable
fun NuvioPosterCard(
    title: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    basePosterWidthDp: Int? = null,
    fallbackImageUrl: String? = null,
    secondaryFallbackImageUrl: String? = null,
    shape: NuvioPosterShape = NuvioPosterShape.Poster,
    detailLine: String? = null,
    showTitleBelow: Boolean = true,
    bottomLeftLogoUrl: String? = null,
    bottomLeftText: String? = null,
    isWatched: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    focusId: String? = null,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val tokens = MaterialTheme.nuvio
    val effectiveBasePosterWidthDp = basePosterWidthDp ?: posterCardStyle.widthDp
    val cardWidth = shape.cardWidth(basePosterWidthDp = effectiveBasePosterWidthDp)
    val cardShape = RoundedCornerShape(posterCardStyle.cornerRadiusDp.dp)
    val catalogLogoOverlaySize = catalogLogoOverlaySize(
        basePosterWidthDp = effectiveBasePosterWidthDp,
        shape = shape,
    )
    val shouldShowTitleBelow = showTitleBelow && !posterCardStyle.hideLabelsEnabled
    val normalizedPrimary = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedFallback = fallbackImageUrl?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedSecondary = secondaryFallbackImageUrl?.trim()?.takeIf { it.isNotEmpty() }
    val resolvedPrimaryUrl = normalizedPrimary ?: normalizedFallback ?: normalizedSecondary
    val imageModel = com.nuvio.app.core.poster.rememberArtworkImageModel(
        primaryUrl = normalizedPrimary,
        fallbackUrl = normalizedFallback,
        secondaryFallbackUrl = normalizedSecondary,
    )
    var imageLoadFailed by remember(resolvedPrimaryUrl, normalizedFallback, normalizedSecondary) {
        mutableStateOf(false)
    }
    val showFallbackArtwork = resolvedPrimaryUrl == null || imageLoadFailed
    val effectiveFocusId = focusId ?: remember(title, imageUrl) { "poster_${title.hashCode()}_${imageUrl?.hashCode() ?: 0}" }

    Column(
        modifier = Modifier
            .desktopPosterHoverScale(focusId = effectiveFocusId)
            .then(modifier)
            .width(cardWidth),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(shape.aspectRatio)
                .posterCardClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    zoomImageUrl = if (showFallbackArtwork) null else resolvedPrimaryUrl,
                    zoomCornerRadius = posterCardStyle.cornerRadiusDp.dp,
                    hoverScaleEnabled = false,
                    focusId = effectiveFocusId,
                )
                .clip(cardShape)
                .background(tokens.colors.surface)
                .nuvioCardDepth(
                    shape = cardShape,
                    surface = NuvioCardDepthSurface.Posters,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (imageModel != null && !imageLoadFailed) {
                NuvioAsyncImage(
                    model = imageModel,
                    contentDescription = title,
                    modifier = Modifier.matchParentSize(),
                    onSuccess = { imageLoadFailed = false },
                    onError = { imageLoadFailed = true },
                    contentScale = ContentScale.Crop,
                    desktopAnimation = posterCardStyle.animatedArtworkMode.toDesktopAnimation(),
                )
            }
            if (showFallbackArtwork) {
                NuvioFallbackPosterArtwork(
                    title = title,
                    modifier = Modifier.matchParentSize(),
                    showTitle = true,
                    isLandscape = shape == NuvioPosterShape.Landscape,
                )
            }

            val hasLogo = !bottomLeftLogoUrl.isNullOrBlank()
            val hasBottomText = !showFallbackArtwork && !bottomLeftText.isNullOrBlank() && !hasLogo
            if (hasLogo || hasBottomText) {
                // Gradient scrim for readability — matching NuvioTV Modern Home style
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawWithCache {
                            val gradient = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.58f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.75f)
                                )
                            )
                            onDrawBehind { drawRect(gradient) }
                        }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s10),
                ) {
                    if (hasLogo) {
                        NuvioAsyncImage(
                            model = bottomLeftLogoUrl,
                            contentDescription = stringResource(Res.string.poster_logo_content_description, title),
                            modifier = Modifier
                                .width(catalogLogoOverlaySize.width)
                                .height(catalogLogoOverlaySize.height),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                        )
                    } else {
                        Text(
                            text = bottomLeftText.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = catalogLogoOverlaySize.textMaxWidth),
                        )
                    }
                }
            }

            NuvioPosterWatchedOverlay(isWatched = isWatched)
        }
        if (shouldShowTitleBelow) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detailLine.isNullOrBlank()) {
                Text(
                    text = detailLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Box(modifier = Modifier.height(NuvioTokens.Space.none))
            }
        } else {
            Box(modifier = Modifier.height(NuvioTokens.Space.none))
        }
    }
}

private data class FallbackPosterPalette(
    val topColor: Color,
    val bottomColor: Color,
    val glowColor: Color,
    val secondaryGlowColor: Color,
)

private val FallbackGlassPalettes = listOf(
    FallbackPosterPalette(
        topColor = Color(0xFF4A1525),
        bottomColor = Color(0xFF140910),
        glowColor = Color(0xFFE11D48),
        secondaryGlowColor = Color(0xFFFB7185),
    ),
    FallbackPosterPalette(
        topColor = Color(0xFF13294B),
        bottomColor = Color(0xFF090F1E),
        glowColor = Color(0xFF2563EB),
        secondaryGlowColor = Color(0xFF38BDF8),
    ),
    FallbackPosterPalette(
        topColor = Color(0xFF0E3B33),
        bottomColor = Color(0xFF071716),
        glowColor = Color(0xFF10B981),
        secondaryGlowColor = Color(0xFF34D399),
    ),
    FallbackPosterPalette(
        topColor = Color(0xFF321A4C),
        bottomColor = Color(0xFF12091E),
        glowColor = Color(0xFF8B5CF6),
        secondaryGlowColor = Color(0xFFC084FC),
    ),
    FallbackPosterPalette(
        topColor = Color(0xFF472711),
        bottomColor = Color(0xFF180D07),
        glowColor = Color(0xFFF59E0B),
        secondaryGlowColor = Color(0xFFFBBF24),
    ),
    FallbackPosterPalette(
        topColor = Color(0xFF0D3849),
        bottomColor = Color(0xFF06151E),
        glowColor = Color(0xFF06B6D4),
        secondaryGlowColor = Color(0xFF22D3EE),
    ),
    FallbackPosterPalette(
        topColor = Color(0xFF46183D),
        bottomColor = Color(0xFF160817),
        glowColor = Color(0xFFD946EF),
        secondaryGlowColor = Color(0xFFF472B6),
    ),
    FallbackPosterPalette(
        topColor = Color(0xFF1E224D),
        bottomColor = Color(0xFF0A0D20),
        glowColor = Color(0xFF6366F1),
        secondaryGlowColor = Color(0xFF818CF8),
    ),
    FallbackPosterPalette(
        topColor = Color(0xFF173D22),
        bottomColor = Color(0xFF08170D),
        glowColor = Color(0xFF22C55E),
        secondaryGlowColor = Color(0xFF4ADE80),
    ),
    FallbackPosterPalette(
        topColor = Color(0xFF4B1D1B),
        bottomColor = Color(0xFF1A0909),
        glowColor = Color(0xFFF97316),
        secondaryGlowColor = Color(0xFFFB923C),
    ),
)

@Composable
fun NuvioFallbackPosterArtwork(
    title: String,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    isLandscape: Boolean = false,
) {
    val palette = remember(title) {
        val hash = title.trim().lowercase().hashCode() and 0x7fffffff
        FallbackGlassPalettes[hash % FallbackGlassPalettes.size]
    }
    Box(
        modifier = modifier.drawWithCache {
            val baseGradient = Brush.linearGradient(
                colors = listOf(palette.topColor, palette.bottomColor),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
            val topGlow = Brush.radialGradient(
                colors = listOf(
                    palette.glowColor.copy(alpha = 0.42f),
                    palette.glowColor.copy(alpha = 0.14f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.18f, size.height * 0.22f),
                radius = maxOf(size.width, size.height) * 0.78f,
            )
            val bottomGlow = Brush.radialGradient(
                colors = listOf(
                    palette.secondaryGlowColor.copy(alpha = 0.24f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.85f, size.height * 0.82f),
                radius = maxOf(size.width, size.height) * 0.68f,
            )
            val glassSheen = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.03f),
                    Color.Black.copy(alpha = 0.34f),
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
            val rimColor = Color.White.copy(alpha = 0.12f)
            val strokePx = 1.dp.toPx()
            onDrawBehind {
                drawRect(baseGradient)
                drawRect(topGlow)
                drawRect(bottomGlow)
                drawRect(glassSheen)
                drawRect(
                    color = rimColor,
                    style = Stroke(width = strokePx),
                )
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (showTitle && title.isNotBlank()) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s8),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White.copy(alpha = 0.95f),
                textAlign = TextAlign.Center,
                maxLines = if (isLandscape) 2 else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NuvioShelfSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onViewAllClick: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    viewAllPillSize: NuvioViewAllPillSize = NuvioViewAllPillSize.Default,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier),
                style = MaterialTheme.nuvioTypeScale.titleMd.copy(fontWeight = FontWeight.Bold),
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NuvioSeeAllPosterCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val tokens = MaterialTheme.nuvio
    val effectiveBasePosterWidthDp = desktopCatalogShelfPosterBaseWidthDp(posterCardStyle.widthDp)
    val shape = if (posterCardStyle.catalogLandscapeModeEnabled) {
        NuvioPosterShape.Landscape
    } else {
        NuvioPosterShape.Poster
    }
    val cardWidth = shape.cardWidth(basePosterWidthDp = effectiveBasePosterWidthDp)
    val cardShape = RoundedCornerShape(posterCardStyle.cornerRadiusDp.dp)
    val viewAllText = stringResource(Res.string.home_view_all)
    val cardId = remember(title) { "see_all_card_${title.hashCode()}" }

    Column(
        modifier = Modifier
            .desktopPosterHoverScale()
            .then(modifier)
            .width(cardWidth),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(shape.aspectRatio)
                .controllerFocusable(
                    id = cardId,
                    groupKey = remember(title) { "shelf_${title.hashCode()}" },
                    shape = cardShape,
                    cornerRadius = posterCardStyle.cornerRadiusDp.dp,
                    focusScale = 1.07f,
                    selectLabel = viewAllText,
                    onSelect = onClick,
                )
                .clickable(onClick = onClick)
                .clip(cardShape)
                .background(tokens.colors.surface.copy(alpha = 0.48f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.14f),
                    shape = cardShape,
                )
                .nuvioCardDepth(
                    shape = cardShape,
                    surface = NuvioCardDepthSurface.Posters,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.12f),
                            shape = CircleShape,
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.22f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = viewAllText,
                        tint = tokens.colors.textPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = viewAllText,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = tokens.colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!posterCardStyle.hideLabelsEnabled) {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun NuvioViewAllPill(
    onClick: (() -> Unit)?,
    size: NuvioViewAllPillSize,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val actionSize = if (size == NuvioViewAllPillSize.Compact) NuvioTokens.Space.s32 else NuvioTokens.Space.s40
    val iconSize = if (size == NuvioViewAllPillSize.Compact) NuvioTokens.Icon.sm else tokens.icons.md
    val viewAllText = stringResource(Res.string.home_view_all)

    Box(
        modifier = modifier
            .size(actionSize)
            .background(
                color = tokens.colors.surface,
                shape = RoundedCornerShape(NuvioTokens.Radius.xl),
            )
            .then(
                if (onClick != null) {
                    val pillId = remember { "view_all_pill_${onClick.hashCode()}" }
                    Modifier
                        .controllerFocusable(
                            id = pillId,
                            cornerRadius = NuvioTokens.Radius.xl,
                            selectLabel = viewAllText,
                            onSelect = onClick,
                        )
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = viewAllText,
            tint = tokens.colors.textMuted,
            modifier = Modifier.size(iconSize),
        )
    }
}

internal const val NuvioDesktopCatalogShelfPosterScale = 1.4f
private const val DesktopPosterHoverScale = 1.045f

internal fun desktopCatalogShelfPosterBaseWidthDp(
    basePosterWidthDp: Int,
): Int =
    if (isDesktop) {
        (basePosterWidthDp * NuvioDesktopCatalogShelfPosterScale).roundToInt()
    } else {
        basePosterWidthDp
    }

internal fun Modifier.nuvioShelfHoverOverdraw(inset: Dp): Modifier {
    if (inset == 0.dp) return this

    // Expand the measured viewport, then place it negatively so edge items keep
    // their visual alignment while desktop hover scale can draw into the gutter.
    return layout { measurable, constraints ->
        val insetPx = inset.roundToPx()
        val horizontalInset = insetPx * 2
        val verticalInset = insetPx * 2
        val expandedMaxWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth + horizontalInset
        } else {
            constraints.maxWidth
        }
        val expandedMinWidth = if (constraints.hasBoundedWidth) {
            (constraints.minWidth + horizontalInset).coerceAtMost(expandedMaxWidth)
        } else {
            constraints.minWidth
        }
        val expandedMaxHeight = if (constraints.hasBoundedHeight) {
            constraints.maxHeight + verticalInset
        } else {
            constraints.maxHeight
        }
        val expandedConstraints = constraints.copy(
            minWidth = expandedMinWidth,
            maxWidth = expandedMaxWidth,
            minHeight = 0,
            maxHeight = expandedMaxHeight,
        )
        val placeable = measurable.measure(expandedConstraints)
        val width = (placeable.width - horizontalInset)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (placeable.height - verticalInset)
            .coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(width, height) {
            placeable.placeRelative(-insetPx, -insetPx)
        }
    }
}

private val NuvioPosterShape.aspectRatio: Float
    get() = when (this) {
        NuvioPosterShape.Poster -> 0.675f
        NuvioPosterShape.Square -> 1f
        NuvioPosterShape.Landscape -> PosterLandscapeAspectRatio
    }

private data class CatalogLogoOverlaySize(
    val width: Dp,
    val height: Dp,
    val textMaxWidth: Dp,
)

private fun catalogLogoOverlaySize(
    basePosterWidthDp: Int,
    shape: NuvioPosterShape,
): CatalogLogoOverlaySize =
    if (shape == NuvioPosterShape.Landscape) {
        when {
            basePosterWidthDp <= 108 -> CatalogLogoOverlaySize(width = 92.dp, height = 24.dp, textMaxWidth = 120.dp)
            basePosterWidthDp <= 120 -> CatalogLogoOverlaySize(width = 104.dp, height = 28.dp, textMaxWidth = 132.dp)
            basePosterWidthDp <= 132 -> CatalogLogoOverlaySize(width = 116.dp, height = 30.dp, textMaxWidth = 144.dp)
            else -> CatalogLogoOverlaySize(width = 128.dp, height = 34.dp, textMaxWidth = 156.dp)
        }
    } else {
        when {
            basePosterWidthDp <= 108 -> CatalogLogoOverlaySize(width = 72.dp, height = 18.dp, textMaxWidth = 92.dp)
            basePosterWidthDp <= 120 -> CatalogLogoOverlaySize(width = 80.dp, height = 20.dp, textMaxWidth = 104.dp)
            basePosterWidthDp <= 132 -> CatalogLogoOverlaySize(width = 88.dp, height = 22.dp, textMaxWidth = 112.dp)
            else -> CatalogLogoOverlaySize(width = 96.dp, height = 24.dp, textMaxWidth = 124.dp)
        }
    }

private fun NuvioPosterShape.cardWidth(basePosterWidthDp: Int): Dp =
    when (this) {
        NuvioPosterShape.Poster -> basePosterWidthDp.dp
        NuvioPosterShape.Square -> basePosterWidthDp.dp
        NuvioPosterShape.Landscape -> landscapePosterWidth(basePosterWidthDp)
    }
@Composable
internal fun Modifier.desktopPosterHoverScale(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    focusId: String? = null,
    controllerFocusScale: Float = 1.07f,
): Modifier {
    if (!enabled || !isDesktop) return this

    val isControllerActive by ControllerManager.isControllerActive.collectAsState()
    val isControllerFocused = isControllerActive && !focusId.isNullOrEmpty() && isControllerNodeFocused(focusId)
    val rawHovered by interactionSource.collectIsHoveredAsState()
    val hovered = rawHovered && !isControllerActive
    val targetScale = when {
        isControllerFocused -> controllerFocusScale
        hovered -> DesktopPosterHoverScale
        else -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "desktop_poster_hover_scale",
    )

    val isScaling = isControllerFocused || hovered || scale != 1f

    return this
        .then(
            if (isScaling) {
                Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .zIndex(if (isControllerFocused) 15f else 1f)
            } else {
                Modifier
            },
        )
        .hoverable(interactionSource)
}
