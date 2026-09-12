package com.example.weglow

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weglow.data.repository.LocalHairstyleRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the bundled face-shape model through the same native runtime used by the app. */
@RunWith(AndroidJUnit4::class)
class OfflineHairstyleScanTest {
    @Test fun bundledModelRunsOnDeviceWithoutCrashing() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = File.createTempFile("offline-hairstyle-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }

            val result = LocalHairstyleRepository(context).analyze(
                Uri.fromFile(image).toString(),
                "Female",
            )

            assertTrue(result.faceShape in listOf("Heart", "Oblong", "Oval", "Round", "Square"))
            assertTrue(result.confidencePercent in 0..100)
            assertEquals(4, result.recommendations.size)
        } finally {
            bitmap.recycle()
            image.delete()
        }
    }
}
