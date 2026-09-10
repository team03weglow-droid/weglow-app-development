package com.example.weglow

import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weglow.data.repository.LocalAcneScanRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uses the actual bundled weights and Android JNI runtime; requires no backend or account. */
@RunWith(AndroidJUnit4::class)
class OfflineAcneScanTest {
    @Test fun bundledModelAnalyzesAnOrientedPhotoWithoutNetwork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = File.createTempFile("offline-scan-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            ExifInterface(image.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val result = LocalAcneScanRepository(context).analyze(Uri.fromFile(image).toString())
            assertEquals(480, result.imageWidth)
            assertEquals(640, result.imageHeight)
            assertEquals(12, result.modelVersion.length)
            assertEquals(0.05f, result.confidenceThreshold, 0f)
            assertTrue(result.detections.all { it.confidence > 0.05f && it.left in 0f..1f && it.bottom in 0f..1f })
        } finally {
            bitmap.recycle()
            image.delete()
        }
    }
}
