package com.example.weglow.core.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.weglow.domain.model.ProfileImageUpload
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Android-facing boundary that turns a picked content [Uri] into a neutral
 * [ProfileImageUpload].
 *
 * It decodes, down-scales and re-encodes the picture to JPEG so that the bytes
 * crossing into the domain/data layers are bounded in size, free of surprising
 * metadata, and provably a real raster image. A file that cannot be decoded
 * yields `null`. No `Uri` ever leaves this object.
 */
object ProfileImageReader {

    private const val MAX_DIMENSION = 1024
    private const val JPEG_QUALITY = 85

    fun read(resolver: ContentResolver, uri: Uri): ProfileImageUpload? {
        val source = runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        if (source.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = BitmapFactory.decodeByteArray(source, 0, source.size, options) ?: return null
        val scaled = downscale(decoded)

        val out = ByteArrayOutputStream()
        val ok = scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        if (!ok) return null

        val bytes = out.toByteArray()
        return if (bytes.isEmpty()) null else ProfileImageUpload(bytes = bytes, mimeType = "image/jpeg")
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (max(width, height) / sample > MAX_DIMENSION * 2) sample *= 2
        return sample
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_DIMENSION) return bitmap
        val ratio = MAX_DIMENSION.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }
}
