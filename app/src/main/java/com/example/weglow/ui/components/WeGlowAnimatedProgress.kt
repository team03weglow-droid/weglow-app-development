package com.example.weglow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Animate measured values only; labels and chart points retain their actual values. */
@Composable
fun WeGlowAnimatedProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color,
    trackColor: Color,
) {
    val displayed = animateFloatAsState(
        targetValue = progress().coerceIn(0f, 1f),
        animationSpec = tween(450),
        label = "Progress update",
    )
    LinearProgressIndicator(
        progress = { displayed.value },
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}
