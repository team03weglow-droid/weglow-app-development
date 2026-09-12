package com.example.weglow

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weglow.core.image.FaceImageValidator
import com.example.weglow.core.image.FaceValidationResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the bundled ML Kit detector without network access or a stored user image. */
@RunWith(AndroidJUnit4::class)
class OfflineFaceImageValidatorTest {
    @Test fun blankImageReturnsNoFace() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = File.createTempFile("face-validation-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val validator = FaceImageValidator(context)
        try {
            bitmap.eraseColor(Color.WHITE)
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            assertEquals(
                FaceValidationResult.NoFace,
                validator.validate(Uri.fromFile(image).toString()),
            )
        } finally {
            validator.close()
            bitmap.recycle()
            image.delete()
        }
    }

    @Test fun missingImageReturnsProcessingError() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val validator = FaceImageValidator(context)
        try {
            assertEquals(
                FaceValidationResult.ProcessingError,
                validator.validate(Uri.fromFile(File(context.cacheDir, "missing-image.jpg")).toString()),
            )
        } finally {
            validator.close()
        }
    }
}
