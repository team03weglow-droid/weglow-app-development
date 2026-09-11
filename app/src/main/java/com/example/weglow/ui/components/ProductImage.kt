package com.example.weglow.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import com.example.weglow.R
import com.example.weglow.ui.theme.PillGray

/**
 * The single reliable way to render a real catalog product's image anywhere in the app
 * (Discover, Recommendations, Routines, ...). A null, blank, or malformed [imageUrl], or a
 * failed network load, all resolve to the same intentional WeGlow placeholder rather than an
 * unexplained blank/white square - Coil's `fallback` and `error` painters are always wired up,
 * never bypassed with a manual null-check that skips [AsyncImage] entirely.
 */
@Composable
fun WeGlowProductImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    // Coil only treats a genuinely null model as "no image" (triggering `fallback`); a blank
    // string would otherwise be attempted as a real request and surface as a slower `error`
    // case instead, so blank strings are normalized to null up front.
    val sanitizedUrl = imageUrl?.trim()?.takeIf(String::isNotEmpty)

    AsyncImage(
        model = sanitizedUrl,
        contentDescription = contentDescription,
        placeholder = painterResource(R.drawable.weglow_logo),
        error = painterResource(R.drawable.weglow_logo),
        fallback = painterResource(R.drawable.weglow_logo),
        contentScale = ContentScale.Crop,
        modifier = modifier.background(PillGray),
    )
}
