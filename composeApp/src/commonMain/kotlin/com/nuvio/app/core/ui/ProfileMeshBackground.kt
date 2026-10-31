package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun ProfileMeshBackground(
    profileColor: Color,
    modifier: Modifier = Modifier,
) {
    CinematicBackground(
        modifier = modifier,
        accentColor = profileColor,
        showParticles = true,
    )
}
