package com.nuvio.app.controller

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.HoverPreviewAssetPrefetcher
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.PosterZoomOverlayCoordinator
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.details.components.ImdbRatingSourceLabel
import com.nuvio.app.features.details.components.ImdbYellow
import com.nuvio.app.features.details.components.formatOneDecimal
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.poster_logo_content_description
import org.jetbrains.compose.resources.stringResource

private val ControllerInlineCardBackground = Color(0xFF0E1017)
private var activeExpandedDwellFocusId by mutableStateOf<String?>(null)

internal data class ControllerPosterDwellState(
    val item: MetaPreview,
    val isWatched: Boolean,
    val focusId: String?,
    val isLandscapeCard: Boolean,
    val isFocused: Boolean,
    val isExpanded: Boolean,
)

internal val LocalControllerPosterDwellState = compositionLocalOf<ControllerPosterDwellState?> { null }

/**
 * Returns true while controller focus has dwelled long enough to expand [focusId] inline.
 * Works both inside a poster subtree (via CompositionLocal) and in parent row containers
 * such as grid rows that animate column weights when a portrait tile expands.
 */
@Composable
fun isControllerPosterDwellExpanded(focusId: String?): Boolean {
    val state = LocalControllerPosterDwellState.current
    if (state != null) {
        return state.isExpanded && (focusId == null || state.focusId == focusId)
    }
    return !focusId.isNullOrEmpty() && activeExpandedDwellFocusId == focusId
}

/**
 * Provides dwell-expansion state for controller-focused poster cards after waiting for
 * the user's configured hover delay, collapsing immediately on navigation or defocus.
 */
@Composable
internal fun ControllerPosterDwellHost(
    item: MetaPreview,
    isWatched: Boolean,
    focusId: String?,
    isLandscapeCard: Boolean,
    hoverPreviewEnabled: Boolean,
    openDelayMillis: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val isControllerActive by ControllerManager.isControllerActive.collectAsState()
    val isFocused = isControllerActive && !focusId.isNullOrBlank() && isControllerNodeFocused(focusId)
    val zoomVisible = PosterZoomOverlayCoordinator.isVisible
    var isExpanded by remember(item.type, item.id, focusId) { mutableStateOf(false) }

    val platformContext = LocalPlatformContext.current
    LaunchedEffect(isFocused, hoverPreviewEnabled, item.id, item.banner, item.logo, item.poster) {
        if (!isFocused || !hoverPreviewEnabled) return@LaunchedEffect
        HoverPreviewAssetPrefetcher.prefetchBackground(
            context = platformContext,
            bannerUrl = item.banner,
            logoUrl = item.logo,
            posterUrl = item.poster,
        )
    }

    LaunchedEffect(isFocused, hoverPreviewEnabled, openDelayMillis, zoomVisible, item.id, item.type) {
        if (isFocused && hoverPreviewEnabled && !zoomVisible) {
            delay(openDelayMillis.toLong())
            isExpanded = true
        } else {
            isExpanded = false
        }
    }

    LaunchedEffect(isExpanded, focusId) {
        if (isExpanded && !focusId.isNullOrBlank()) {
            activeExpandedDwellFocusId = focusId
        } else if (activeExpandedDwellFocusId == focusId) {
            activeExpandedDwellFocusId = null
        }
    }

    DisposableEffect(focusId) {
        onDispose {
            if (!focusId.isNullOrBlank() && activeExpandedDwellFocusId == focusId) {
                activeExpandedDwellFocusId = null
            }
        }
    }

    val state = ControllerPosterDwellState(
        item = item,
        isWatched = isWatched,
        focusId = focusId,
        isLandscapeCard = isLandscapeCard,
        isFocused = isFocused,
        isExpanded = isExpanded,
    )

    CompositionLocalProvider(LocalControllerPosterDwellState provides state) {
        Box(modifier = modifier) {
            content(Modifier)
        }
    }
}

/**
 * Wraps the poster card artwork in an inline expandable frame on controller dwell:
 * - Landscape cards extend downward inline inside the same rounded container.
 * - Portrait cards extend horizontally to the right inline, pushing row neighbors across.
 */
@Composable
fun ControllerPosterDwellCardFrame(
    focusId: String?,
    cornerRadiusDp: Int,
    isLandscape: Boolean,
    cardWidth: Dp? = null,
    posterAspectRatio: Float,
    modifier: Modifier = Modifier,
    posterContent: @Composable (Shape) -> Unit,
) {
    val state = LocalControllerPosterDwellState.current?.takeIf {
        focusId == null || it.focusId == focusId
    }
    val outerRadius = cornerRadiusDp.dp
    val outerShape = RoundedCornerShape(outerRadius)

    if (state == null) {
        posterContent(outerShape)
        return
    }

    val isExpanded = state.isExpanded
    val isFocused = state.isFocused
    val innerPosterShape = when {
        !isExpanded -> outerShape
        isLandscape -> RoundedCornerShape(
            topStart = outerRadius,
            topEnd = outerRadius,
            bottomStart = 0.dp,
            bottomEnd = 0.dp,
        )
        else -> RoundedCornerShape(
            topStart = outerRadius,
            bottomStart = outerRadius,
            topEnd = 0.dp,
            bottomEnd = 0.dp,
        )
    }

    val density = LocalDensity.current
    var measuredPosterWidthPx by remember { mutableIntStateOf(0) }
    var measuredPosterHeightPx by remember { mutableIntStateOf(0) }

    val effectivePosterWidth = cardWidth ?: if (measuredPosterWidthPx > 0) {
        with(density) { measuredPosterWidthPx.toDp() }
    } else if (isLandscape) {
        260.dp
    } else {
        176.dp
    }
    val posterHeight = if (cardWidth == null && measuredPosterHeightPx > 0) {
        with(density) { measuredPosterHeightPx.toDp() }
    } else {
        effectivePosterWidth / posterAspectRatio
    }
    val detailPanelWidth = if (isLandscape) {
        (effectivePosterWidth * 0.96f).coerceIn(230.dp, 280.dp)
    } else {
        (effectivePosterWidth * 1.42f).coerceIn(210.dp, 268.dp)
    }
    val detailPanelWidthPx = with(density) { detailPanelWidth.toPx() }
    val rowState = LocalControllerRowLazyListState.current

    LaunchedEffect(isExpanded, focusId, detailPanelWidthPx) {
        if (isExpanded && !focusId.isNullOrBlank()) {
            ControllerFocusEngine.activeDwellExpandedNodeId = focusId
            ControllerFocusEngine.activeDwellExpandedWidthPx = detailPanelWidthPx
            if (rowState != null) {
                delay(60L)
                val focusedNode = ControllerFocusEngine.getFocusedNode()
                if (focusedNode != null && focusedNode.id == focusId) {
                    val expandedRight = focusedNode.unclippedBoundsInRoot.right + detailPanelWidthPx
                    val viewportWidth = rowState.layoutInfo.viewportSize.width.toFloat()
                        .takeIf { it > 100f } ?: 1920f
                    val safeRight = (viewportWidth - maxOf(rowState.layoutInfo.afterContentPadding.toFloat(), 96f))
                        .coerceAtLeast(240f)
                    if (expandedRight > safeRight && rowState.canScrollForward) {
                        runCatching {
                            rowState.animateScrollBy(
                                expandedRight - safeRight + 24f,
                                tween(220, easing = NuvioTokens.Motion.decelerate),
                            )
                        }
                    }
                }
            }
        } else if (!isExpanded && ControllerFocusEngine.activeDwellExpandedNodeId == focusId) {
            ControllerFocusEngine.activeDwellExpandedNodeId = null
            ControllerFocusEngine.activeDwellExpandedWidthPx = 0f
        }
    }

    DisposableEffect(focusId) {
        onDispose {
            if (!focusId.isNullOrBlank() && ControllerFocusEngine.activeDwellExpandedNodeId == focusId) {
                ControllerFocusEngine.activeDwellExpandedNodeId = null
                ControllerFocusEngine.activeDwellExpandedWidthPx = 0f
            }
        }
    }

    Row(
        modifier = modifier
            .then(if (cardWidth == null && !isExpanded) Modifier.fillMaxWidth() else Modifier)
            .controllerPortraitOverflowGuard(enabled = isExpanded)
            .controllerUnifiedCardFocusRing(
                isFocused = isFocused && isExpanded,
                cornerRadius = outerRadius,
            )
            .clip(outerShape)
            .background(if (isExpanded) ControllerInlineCardBackground else Color.Transparent),
        verticalAlignment = Alignment.Top,
    ) {
        val posterSlotModifier = when {
            cardWidth != null -> Modifier.width(cardWidth)
            isExpanded -> Modifier.width(effectivePosterWidth)
            else -> Modifier.fillMaxWidth()
        }
        Box(
            modifier = posterSlotModifier.onSizeChanged { size ->
                if (!isExpanded && size.width > 0 && size.height > 0) {
                    measuredPosterWidthPx = size.width
                    measuredPosterHeightPx = size.height
                }
            },
        ) {
            posterContent(innerPosterShape)
            if (isExpanded) {
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(posterHeight)
                        .align(Alignment.CenterEnd)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, ControllerInlineCardBackground),
                            ),
                        ),
                )
            }
        }
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandHorizontally(
                expandFrom = Alignment.Start,
                animationSpec = tween(
                    durationMillis = 220,
                    easing = NuvioTokens.Motion.emphasized,
                ),
            ) + fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = shrinkHorizontally(
                shrinkTowards = Alignment.Start,
                animationSpec = tween(durationMillis = 140),
            ) + fadeOut(animationSpec = tween(durationMillis = 100)),
        ) {
            ControllerInlineDetails(
                item = state.item,
                width = detailPanelWidth,
                height = posterHeight,
            )
        }
    }
}

@Composable
private fun ControllerInlineDetails(
    item: MetaPreview,
    width: Dp,
    height: Dp,
) {
    val tokens = MaterialTheme.nuvio
    val isCompactHeight = height < 175.dp
    var logoLoadError by remember(item.type, item.id, item.logo) { mutableStateOf(false) }
    val logoUrl = item.logo?.takeIf { it.isNotBlank() && !logoLoadError }

    Column(
        modifier = Modifier
            .requiredWidth(width)
            .height(height)
            .padding(
                horizontal = if (isCompactHeight) NuvioTokens.Space.s12 else NuvioTokens.Space.s14,
                vertical = if (isCompactHeight) NuvioTokens.Space.s10 else NuvioTokens.Space.s12,
            ),
        verticalArrangement = Arrangement.spacedBy(
            if (isCompactHeight) NuvioTokens.Space.s4 else NuvioTokens.Space.s6,
        ),
    ) {
        if (logoUrl != null) {
            NuvioAsyncImage(
                model = logoUrl,
                contentDescription = stringResource(Res.string.poster_logo_content_description, item.name),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompactHeight) 26.dp else 34.dp),
                alignment = Alignment.CenterStart,
                contentScale = ContentScale.Fit,
                clipToBounds = false,
                onError = { logoLoadError = true },
            )
        } else {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = tokens.colors.textPrimary,
                maxLines = if (isCompactHeight) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val metaPrefix = item.controllerDwellMetadataPrefix()
        val formattedImdb = item.imdbRating
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> raw.toDoubleOrNull()?.let(::formatOneDecimal) ?: raw }
        if (metaPrefix.isNotBlank() || formattedImdb != null) {
            val metaTextStyle = MaterialTheme.typography.labelMedium
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (metaPrefix.isNotBlank()) {
                    Text(
                        text = metaPrefix,
                        style = metaTextStyle,
                        color = tokens.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (formattedImdb != null) {
                    if (metaPrefix.isNotBlank()) {
                        Text(
                            text = " • ",
                            style = metaTextStyle,
                            color = tokens.colors.textSecondary,
                        )
                    }
                    ImdbRatingSourceLabel(storeTextStyle = metaTextStyle)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = formattedImdb,
                        style = metaTextStyle,
                        color = ImdbYellow,
                        maxLines = 1,
                    )
                }
            }
        }

        item.genres
            .take(3)
            .joinToString(" • ")
            .takeIf { it.isNotBlank() }
            ?.let { genres ->
                Text(
                    text = genres,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

        item.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textPrimary.copy(alpha = 0.88f),
                maxLines = if (isCompactHeight) 3 else 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Modifier.controllerPortraitOverflowGuard(enabled: Boolean): Modifier {
    if (!enabled) return this
    return layout { measurable, constraints ->
        val unconstrained = measurable.measure(
            constraints.copy(
                minWidth = 0,
                maxWidth = Constraints.Infinity,
            ),
        )
        val layoutWidth = if (constraints.hasBoundedWidth) {
            unconstrained.width.coerceAtMost(constraints.maxWidth)
        } else {
            unconstrained.width
        }
        val layoutHeight = if (constraints.hasBoundedHeight) {
            unconstrained.height.coerceAtMost(constraints.maxHeight)
        } else {
            unconstrained.height
        }
        layout(layoutWidth, layoutHeight) {
            unconstrained.placeRelative(0, 0)
        }
    }
}

private fun Modifier.controllerUnifiedCardFocusRing(
    isFocused: Boolean,
    cornerRadius: Dp,
): Modifier {
    if (!isFocused) return this
    return drawWithContent {
        drawContent()
        val radiusPx = cornerRadius.toPx()
        val cardShape = RoundedCornerShape(cornerRadius)
        val sheenPath = Path().apply {
            addOutline(cardShape.createOutline(size, layoutDirection, this@drawWithContent))
        }
        clipPath(sheenPath) {
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White.copy(alpha = 0.14f),
                        0.28f to Color.White.copy(alpha = 0.05f),
                        0.55f to Color.Transparent,
                        1.0f to Color.White.copy(alpha = 0.03f),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
        }

        val shadowStrokePx = 5.dp.toPx()
        val glowStrokePx = 3.5f.dp.toPx()
        val whiteBorderStrokePx = 1.6f.dp.toPx()

        drawRoundRect(
            color = Color.Black.copy(alpha = 0.50f),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(radiusPx, radiusPx),
            style = Stroke(width = shadowStrokePx),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.45f),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(radiusPx, radiusPx),
            style = Stroke(width = glowStrokePx),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(radiusPx, radiusPx),
            style = Stroke(width = whiteBorderStrokePx),
        )
    }
}

private fun MetaPreview.controllerDwellMetadataPrefix(): String =
    buildList {
        type.trim().takeIf { it.isNotEmpty() }?.let {
            add(it.replaceFirstChar(Char::uppercase))
        }
        releaseInfo
            ?.takeIf { it.isNotBlank() }
            ?.let(::formatReleaseDateForDisplay)
            ?.takeIf { it.isNotBlank() }
            ?.let(::add)
    }.joinToString(" • ")
