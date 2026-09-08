package com.example.weglow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val WeGlowColors = lightColorScheme(
    primary = Ink,
    onPrimary = Cream,
    secondary = Clay,
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = CreamDim,
    onSurfaceVariant = InkSoft,
)

@Composable
fun WeGlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WeGlowColors, typography = Typography, content = content)
}