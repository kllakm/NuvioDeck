package com.nuvio.app.features.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.controller.LocalControllerRowItemIndex
import com.nuvio.app.controller.LocalControllerRowTotalCount
import com.nuvio.app.controller.controllerFocusable

@Composable
internal fun AvatarPicker(
    avatars: List<AvatarCatalogItem>,
    selectedAvatarId: String?,
    onAvatarSelected: (AvatarCatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (avatars.isEmpty()) return
    val spacing = 10.dp
    val minAvatarSize = 58.dp
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = ((maxWidth + spacing) / (minAvatarSize + spacing)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            avatars.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    row.forEachIndexed { colIndex, avatar ->
                        CompositionLocalProvider(
                            LocalControllerRowItemIndex provides colIndex,
                            LocalControllerRowTotalCount provides row.size,
                        ) {
                            AvatarChoiceItem(
                                avatar = avatar,
                                rowIndex = rowIndex,
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                                isSelected = avatar.id == selectedAvatarId,
                                onClick = { onAvatarSelected(avatar) },
                            )
                        }
                    }
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarChoiceItem(
    avatar: AvatarCatalogItem,
    rowIndex: Int,
    modifier: Modifier,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .controllerFocusable(
                id = "profile_avatar_${avatar.id}",
                groupKey = "profile_edit_avatars_row_$rowIndex",
                shape = CircleShape,
                focusScale = 1.08f,
                selectLabel = avatar.displayName,
                onSelect = onClick,
            )
            .clip(CircleShape)
            .background(
                avatar.bgColor?.let(::parseHexColor)
                    ?: MaterialTheme.colorScheme.surfaceVariant,
            )
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = avatarImageUrl(avatar),
            contentDescription = avatar.displayName,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}
