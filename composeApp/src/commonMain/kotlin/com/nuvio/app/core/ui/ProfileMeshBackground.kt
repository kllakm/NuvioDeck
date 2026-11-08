package com.nuvio.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun ProfileMeshBackground(
    profileColor: Color,
    modifier: Modifier = Modifier,
) {
    val animatedAccentColor by animateColorAsState(
        targetValue = profileColor,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "profile_backdrop_accent_color",
    )

    CinematicBackground(
        modifier = modifier,
        accentColor = animatedAccentColor,
        showParticles = false,
    )
}
