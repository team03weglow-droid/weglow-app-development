package com.example.weglow.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.example.weglow.ui.theme.CardGray
import com.example.weglow.ui.theme.SoftGray

/** Decodes persisted profile-picture bytes into an [ImageBitmap], cached per byte array. */
@Composable
fun rememberDecodedBitmap(bytes: ByteArray?): ImageBitmap? =
    remember(bytes) {
        bytes?.takeIf { it.isNotEmpty() }?.let {
            runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull()
        }
    }

/**
 * Circular profile avatar. Shows [image] when present, otherwise a neutral
 * placeholder so a user without a profile picture still sees a sensible avatar.
 */
@Composable
fun ProfileAvatar(
    image: ImageBitmap?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = "Profile picture",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(CardGray),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "No profile picture",
                tint = SoftGray,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}
