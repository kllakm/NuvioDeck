package com.nuvio.app.features.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.zIndex
import com.nuvio.app.controller.ControllerManager
import com.nuvio.app.controller.LocalControllerGroupKey
import com.nuvio.app.controller.isControllerNodeFocused
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.NuvioPosterCard
import com.nuvio.app.core.ui.NuvioPosterShape
import com.nuvio.app.core.ui.desktopCatalogShelfPosterBaseWidthDp
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.home.stableKey
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.netflix_sans_bold
import org.jetbrains.compose.resources.Font

@Composable
fun HomePosterCard(
    item: MetaPreview,
    modifier: Modifier = Modifier,
    useLandscapeBackdropMode: Boolean = false,
    forcePortrait: Boolean = false,
    isWatched: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    showLandscapeOverlay: Boolean = true,
    focusId: String? = null,
) {
    val ambientGroupKey = com.nuvio.app.controller.LocalControllerGroupKey.current
    val effectiveFocusId = focusId ?: remember(ambientGroupKey, item.id, item.type) {
        if (!ambientGroupKey.isNullOrEmpty()) "poster_${ambientGroupKey}_${item.stableKey()}"
        else "poster_shelf_${item.stableKey()}"
    }
    val posterCardStyle = rememberPosterCardStyleUiState()
    val isLandscapeMode = !forcePortrait && (useLandscapeBackdropMode || posterCardStyle.catalogLandscapeModeEnabled)
    val effectiveLandscapePoster = if (posterCardStyle.alwaysShowLandscapeClearlogo) null else item.landscapePoster
    val cleanLandscapePoster = effectiveLandscapePoster?.trim()?.takeIf { it.isNotEmpty() }
    val cleanBanner = item.banner?.trim()?.takeIf { it.isNotEmpty() }
    val cleanPoster = item.poster?.trim()?.takeIf { it.isNotEmpty() }
    val cleanRawPoster = item.rawPosterUrl?.trim()?.takeIf { it.isNotEmpty() }
    val metahubFallback = remember(item.id, isLandscapeMode) {
        com.nuvio.app.core.poster.metahubFallbackArtworkUrl(item.id, isLandscape = isLandscapeMode)
    }
    val imageUrl = if (isLandscapeMode) {
        cleanLandscapePoster ?: cleanBanner ?: cleanPoster ?: metahubFallback
    } else {
        cleanPoster ?: cleanRawPoster ?: metahubFallback
    }
    val fallbackImageUrl = if (isLandscapeMode && cleanLandscapePoster != null) {
        // Landscape custom poster -> fall back to original backdrop, then portrait
        cleanBanner ?: cleanPoster ?: cleanRawPoster ?: metahubFallback
    } else if (isLandscapeMode && cleanBanner != null && cleanPoster != null && cleanPoster != cleanBanner) {
        cleanPoster
    } else {
        cleanRawPoster ?: metahubFallback
    }

    HomePosterHoverPreview(
        item = item,
        isWatched = isWatched,
        onClick = onClick,
        onLongClick = onLongClick,
    ) { hoverModifier ->
        NuvioPosterCard(
            title = item.name,
            imageUrl = imageUrl,
            modifier = modifier.then(hoverModifier),
            fallbackImageUrl = fallbackImageUrl,
            secondaryFallbackImageUrl = metahubFallback,
            basePosterWidthDp = desktopCatalogShelfPosterBaseWidthDp(posterCardStyle.widthDp),
            shape = if (isLandscapeMode) NuvioPosterShape.Landscape else if (forcePortrait) NuvioPosterShape.Poster else item.posterShape.toNuvioPosterShape(),
            detailLine = if (isLandscapeMode || posterCardStyle.hideLabelsEnabled) null else item.releaseInfo?.let { formatReleaseDateForDisplay(it) },
            showTitleBelow = !posterCardStyle.hideLabelsEnabled,
            bottomLeftLogoUrl = if (isLandscapeMode && showLandscapeOverlay && cleanLandscapePoster == null) item.logo else null,
            bottomLeftText = if (isLandscapeMode && showLandscapeOverlay && cleanLandscapePoster == null && item.logo.isNullOrBlank() && !posterCardStyle.hideLabelsEnabled) item.name else null,
            isWatched = isWatched,
            onClick = onClick,
            onLongClick = onLongClick,
            focusId = effectiveFocusId,
        )
    }
}

@Composable
fun HomeTop10PosterCard(
    rank: Int,
    item: MetaPreview,
    modifier: Modifier = Modifier,
    isWatched: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    focusId: String? = null,
) {
    val ambientGroupKey = LocalControllerGroupKey.current
    val effectiveFocusId = focusId ?: remember(ambientGroupKey, item.id, item.type, rank) {
        if (!ambientGroupKey.isNullOrEmpty()) "poster_${ambientGroupKey}_${item.stableKey()}"
        else "poster_shelf_${item.stableKey()}"
    }
    val isControllerActive by ControllerManager.isControllerActive.collectAsState()
    val isFocused = isControllerActive && isControllerNodeFocused(effectiveFocusId)

    val posterCardStyle = rememberPosterCardStyleUiState()
    val cardWidth: Dp = desktopCatalogShelfPosterBaseWidthDp(posterCardStyle.widthDp).dp
    val cardHeight: Dp = cardWidth / 0.675f
    val rankLeadWidth: Dp = if (rank >= 10) cardWidth * 0.88f else cardWidth * 0.56f
    val totalWidth: Dp = cardWidth + rankLeadWidth
    val rankFontFamily = FontFamily(
        Font(Res.font.netflix_sans_bold, FontWeight.Bold, FontStyle.Normal),
    )
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val rankText = rank.toString()
    val fontSize = with(density) { (cardHeight * if (rank >= 10) 0.78f else 0.88f).toSp() }
    val letterSpacing = if (rank >= 10) (-0.14).em else (-0.03).em
    val textLayoutResult = textMeasurer.measure(
        text = rankText,
        style = TextStyle(
            fontFamily = rankFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            letterSpacing = letterSpacing,
        ),
    )
    val posterCornerShape = RoundedCornerShape(posterCardStyle.cornerRadiusDp.dp)

    Box(
        modifier = modifier
            .width(totalWidth)
            .zIndex(if (isFocused) 15f else 0f),
    ) {
        Canvas(
            modifier = Modifier
                .width(rankLeadWidth + cardWidth * 0.5f)
                .height(cardHeight)
                .align(Alignment.TopStart)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                },
        ) {
            val rankLeadPx = rankLeadWidth.toPx()
            val x = (rankLeadPx + cardWidth.toPx() * 0.14f - textLayoutResult.size.width).coerceAtLeast(0f)
            val y = (size.height - textLayoutResult.size.height) * 0.5f
            drawText(
                textLayoutResult = textLayoutResult,
                color = Color(0xFF787C88),
                topLeft = Offset(x, y),
                drawStyle = Stroke(
                    width = 3.6.dp.toPx(),
                    join = StrokeJoin.Round,
                ),
            )
            drawText(
                textLayoutResult = textLayoutResult,
                color = Color(0xFF0A0B10),
                topLeft = Offset(x, y),
            )
            val fadeStartX = (rankLeadPx * 0.65f).coerceAtLeast(0f)
            val fadeMidX = (rankLeadPx * 0.92f).coerceAtLeast(fadeStartX + 1f)
            val fadeEndX = (rankLeadPx + 2.dp.toPx()).coerceAtLeast(fadeMidX + 1f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color.Black,
                        (fadeStartX / size.width).coerceIn(0f, 1f) to Color.Black,
                        (fadeMidX / size.width).coerceIn(0f, 1f) to Color.Black.copy(alpha = 0.35f),
                        (fadeEndX / size.width).coerceIn(0f, 1f) to Color.Transparent,
                        1f to Color.Transparent,
                    ),
                    startX = 0f,
                    endX = size.width,
                ),
                blendMode = BlendMode.DstIn,
            )
        }

        Box(
            modifier = Modifier
                .padding(start = rankLeadWidth)
                .width(cardWidth)
                .height(cardHeight)
                .clip(posterCornerShape)
                .background(Color(0xFF0A0B10)),
        )

        HomePosterCard(
            item = item,
            modifier = Modifier.padding(start = rankLeadWidth),
            useLandscapeBackdropMode = false,
            forcePortrait = true,
            isWatched = isWatched,
            onClick = onClick,
            onLongClick = onLongClick,
            showLandscapeOverlay = false,
            focusId = effectiveFocusId,
        )
    }
}

private fun PosterShape.toNuvioPosterShape(): NuvioPosterShape =
    when (this) {
        PosterShape.Poster -> NuvioPosterShape.Poster
        PosterShape.Square -> NuvioPosterShape.Square
        PosterShape.Landscape -> NuvioPosterShape.Landscape
    }
