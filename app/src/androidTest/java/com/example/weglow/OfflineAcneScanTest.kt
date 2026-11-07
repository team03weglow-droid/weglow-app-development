package com.example.weglow

import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weglow.data.repository.LocalAcneScanRepository
import com.example.weglow.domain.model.ScanFailure
import com.example.weglow.domain.model.ScanFailureException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uses the actual bundled weights and Android JNI runtime; requires no backend or account. */
@RunWith(AndroidJUnit4::class)
class OfflineAcneScanTest {
    @Test fun bundledModelRejectsAFacelessPhotoWithoutSavingFalseZeroConcerns() = runBlocking {
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
            // A single repository instance is reused for repeated scans in the real app
            // (see AppContainer); analyzing twice on the same instance proves the lazily
            // built OrtEnvironment/OrtSession survive and keep working across repeated
            // calls, instead of only ever being exercised once.
            val repository = LocalAcneScanRepository(context)
            repeat(2) {
                val result = repository.analyze(Uri.fromFile(image).toString())
                assertTrue(result.isFailure)
                assertEquals(ScanFailure.InvalidImage, (result.exceptionOrNull() as? ScanFailureException)?.failure)
            }
        } finally {
            bitmap.recycle()
            image.delete()
        }
    }
}
