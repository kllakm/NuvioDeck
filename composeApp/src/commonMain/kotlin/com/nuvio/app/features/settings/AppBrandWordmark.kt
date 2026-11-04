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
import androidx.compose.foundation.layout.height
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
                Spacer(modifier = Modifier.width(effectiveHeight * 0.12f))
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
    val badgeHeight = (height * 0.52f).coerceAtLeast(14.dp)
    val fontSize = (badgeHeight.value * 0.58f).coerceIn(8.5f, 13f).sp
    val cornerRadius = (badgeHeight * 0.22f).coerceIn(3.dp, 6.dp)
    val hPad = (badgeHeight * 0.40f).coerceIn(5.dp, 10.dp)

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .height(badgeHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(primaryColor.copy(alpha = 0.16f))
            .border(
                width = 1.dp,
                color = primaryColor.copy(alpha = 0.50f),
                shape = RoundedCornerShape(cornerRadius),
            )
            .padding(horizontal = hPad),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "DECK",
            color = primaryColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            lineHeight = fontSize,
        )
    }
}
