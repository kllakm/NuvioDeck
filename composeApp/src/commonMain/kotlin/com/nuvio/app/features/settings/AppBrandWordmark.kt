package com.nuvio.app.features.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.appTheme
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun AppBrandWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    icon: AppIconOption? = null,
    showDeckMoniker: Boolean = true,
) {
    val state by remember {
        AppIconRepository.ensureLoaded()
        AppIconRepository.state
    }.collectAsStateWithLifecycle()

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val effectiveHeight = if (maxHeight.isSpecified && maxHeight != Dp.Infinity && maxHeight > 0.dp) {
            maxHeight
        } else {
            40.dp
        }

        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(
                    icon?.wordmarkResource ?: MaterialTheme.appTheme.wordmarkResource(state.selected),
                ),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxHeight(),
                contentScale = ContentScale.Fit,
            )
            if (showDeckMoniker) {
                Spacer(modifier = Modifier.width(effectiveHeight * 0.16f))
                NuvioDeckMonikerBadge(height = effectiveHeight)
            }
        }
    }
}

@Composable
private fun NuvioDeckMonikerBadge(
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val h = height.value
    val fontSize = (h * 0.28f).coerceIn(9f, 16f).sp
    val cornerRadius = (h * 0.12f).coerceIn(3f, 8f).dp
    val hPad = (h * 0.18f).coerceIn(4f, 10f).dp
    val vPad = (h * 0.04f).coerceIn(1.5f, 4f).dp

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(primaryColor.copy(alpha = 0.16f))
            .border(
                width = 1.dp,
                color = primaryColor.copy(alpha = 0.50f),
                shape = RoundedCornerShape(cornerRadius),
            )
            .padding(horizontal = hPad, vertical = vPad),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "DECK",
            color = primaryColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
        )
    }
}
