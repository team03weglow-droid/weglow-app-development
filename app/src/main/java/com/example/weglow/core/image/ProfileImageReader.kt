package com.example.weglow.core.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.example.weglow.domain.model.ProfileImageUpload
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max

/**
 * Android-facing boundary that turns a picked content [Uri] into a neutral
 * [ProfileImageUpload].
 *
 * It decodes, corrects for the source image's EXIF orientation, down-scales and
 * re-encodes the picture to JPEG so that the bytes crossing into the domain/data
 * layers are bounded in size, upright regardless of how the camera or gallery
 * app tagged the original, and provably a real raster image. A file that cannot
 * be decoded yields `null`. No `Uri` ever leaves this object.
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

        // The EXIF tag is read from the same already-downsampled-scale-independent bytes,
        // so a camera photo (always tagged) and a gallery photo (tagged only sometimes)
        // are both normalized to upright pixels before anything downstream ever sees them.
        val oriented = applyExifOrientation(decoded, readExifOrientation(source))
        if (oriented !== decoded) decoded.recycle()

        val scaled = downscale(oriented)

        val out = ByteArrayOutputStream()
        val ok = scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (scaled !== oriented) scaled.recycle()
        oriented.recycle()
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

    /** Reads the standard EXIF orientation tag straight out of the decoded bytes. */
    private fun readExifOrientation(source: ByteArray): Int =
        try {
            ExifInterface(ByteArrayInputStream(source)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }

    /**
     * Rotates/mirrors [bitmap] so its pixels are upright, covering every orientation the
     * EXIF spec defines (normal, 90/180/270 rotation, and the mirrored/transposed variants
     * some camera and scanner apps emit). Returns [bitmap] unchanged when no correction is
     * needed so callers can tell whether a new bitmap was allocated.
     */
    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
