package com.example.weglow.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import com.example.weglow.R
import com.example.weglow.core.image.ScanPhotoDecoder
import com.example.weglow.domain.model.HairstyleRecommendation
import com.example.weglow.domain.model.HairstyleResult
import com.example.weglow.domain.repository.HairstyleRepository
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import kotlin.math.roundToInt

/** Runs the bundled Keras-derived LiteRT classifier entirely on the Android device. */
class LocalHairstyleRepository(context: Context) : HairstyleRepository {
    private val application = context.applicationContext
    private val mutex = Mutex()

    private val interpreter: Interpreter by lazy {
        val model = application.assets.openFd(MODEL_ASSET).use { asset ->
            FileInputStream(asset.fileDescriptor).use { stream ->
                stream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    asset.startOffset,
                    asset.declaredLength,
                )
            }
        }
        Interpreter(
            model,
            Interpreter.Options().apply {
                setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
            },
        )
    }

    override suspend fun analyze(photoReference: String, gender: String?): HairstyleResult =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                ensureActive()
                try {
                    val photo = ScanPhotoDecoder.decode(
                        application.contentResolver,
                        Uri.parse(photoReference),
                    )
                    try {
                        val inputValues = prepareInput(photo)
                        ensureActive()
                        check(
                            interpreter.inputTensorCount == 1 &&
                                interpreter.outputTensorCount == 1 &&
                                interpreter.getInputTensor(0).shape().contentEquals(
                                    intArrayOf(1, INPUT_SIZE, INPUT_SIZE, 3),
                                ) &&
                                interpreter.getOutputTensor(0).shape().contentEquals(
                                    intArrayOf(1, FACE_SHAPES.size),
                                ),
                        ) {
                            "The installed face-shape model is incompatible."
                        }
                        val output = Array(1) { FloatArray(FACE_SHAPES.size) }
                        interpreter.run(inputValues, output)
                        ensureActive()
                        val probabilities = output.single()
                        check(probabilities.all(Float::isFinite)) {
                            "The installed face-shape model returned an invalid result."
                        }
                        val bestIndex = probabilities.indices.maxBy { probabilities[it] }
                        val confidence = (probabilities[bestIndex] * 100f).roundToInt().coerceIn(0, 100)
                        recommendationResult(FACE_SHAPES[bestIndex], confidence, gender)
                    } finally {
                        photo.recycle()
                    }
                } catch (error: OutOfMemoryError) {
                    throw IllegalStateException(
                        "There is not enough memory to analyze this photo. Close other apps and try again.",
                        error,
                    )
                } catch (error: LinkageError) {
                    throw IllegalStateException(
                        "Face-shape analysis could not start on this device. Please update or reinstall the app.",
                        error,
                    )
                }
            }
        }

    /**
     * The EfficientNet model contains its own 1/255 rescaling layer, so its float input must
     * remain in the training range 0..255. A centered square crop matches the camera face guide.
     */
    private fun prepareInput(photo: Bitmap): ByteBuffer {
        val side = minOf(photo.width, photo.height)
        val left = (photo.width - side) / 2
        val top = (photo.height - side) / 2
        val resized = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        try {
            Canvas(resized).drawBitmap(
                photo,
                Rect(left, top, left + side, top + side),
                Rect(0, 0, INPUT_SIZE, INPUT_SIZE),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            return ByteBuffer.allocateDirect(pixels.size * 3 * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .also { values ->
                    for (pixel in pixels) {
                        values.putFloat(((pixel shr 16) and 0xff).toFloat())
                        values.putFloat(((pixel shr 8) and 0xff).toFloat())
                        values.putFloat((pixel and 0xff).toFloat())
                    }
                    values.rewind()
                }
        } finally {
            resized.recycle()
        }
    }

    private companion object {
        const val MODEL_ASSET = "hairstyle/face_shape.tflite"
        const val INPUT_SIZE = 224

        // The training dataset's alphabetical class_indices order. If the training mapping
        // changes, update this list and assets/hairstyle/model.json together.
        val FACE_SHAPES = listOf("Heart", "Oblong", "Oval", "Round", "Square")
    }
}

internal fun recommendationResult(
    faceShape: String,
    confidencePercent: Int,
    gender: String?,
): HairstyleResult {
    val profile = FACE_SHAPE_PROFILES.getValue(faceShape)
    val baseStyles = if (gender.equals("Male", ignoreCase = true)) maleStyles() else femaleStyles()
    val order = if (gender.equals("Male", ignoreCase = true)) profile.maleOrder else profile.femaleOrder
    val scores = listOf(97, 94, 91, 88)
    val recommendations = order.mapIndexed { rank, index ->
        baseStyles[index].copy(matchPercent = scores[rank])
    }
    return HairstyleResult(
        faceShape = faceShape,
        confidencePercent = confidencePercent,
        traits = profile.traits,
        description = profile.description,
        recommendations = recommendations,
    )
}

private data class FaceShapeProfile(
    val traits: List<String>,
    val description: String,
    val maleOrder: List<Int>,
    val femaleOrder: List<Int>,
)

private val FACE_SHAPE_PROFILES = mapOf(
    "Heart" to FaceShapeProfile(
        listOf("Wider Forehead", "Defined Cheekbones", "Tapered Chin"),
        "Your heart-shaped proportions pair well with styles that soften the forehead and add balance around the jaw.",
        listOf(2, 0, 1, 3), listOf(1, 2, 3, 0),
    ),
    "Oblong" to FaceShapeProfile(
        listOf("Longer Profile", "Even Width", "Soft Jawline"),
        "Your elongated proportions suit styles with side volume and texture that create a balanced, broader silhouette.",
        listOf(2, 1, 0, 3), listOf(1, 3, 2, 0),
    ),
    "Oval" to FaceShapeProfile(
        listOf("Balanced Proportions", "Defined Cheekbones", "Tapered Jaw"),
        "Your balanced proportions allow broad styling flexibility, from structured cuts to soft face-framing layers.",
        listOf(0, 1, 2, 3), listOf(0, 1, 2, 3),
    ),
    "Round" to FaceShapeProfile(
        listOf("Soft Contours", "Full Cheeks", "Similar Width & Length"),
        "Your soft, rounded contours are complemented by height, angles, and layers that visually lengthen the face.",
        listOf(3, 0, 1, 2), listOf(0, 3, 2, 1),
    ),
    "Square" to FaceShapeProfile(
        listOf("Strong Jawline", "Broad Forehead", "Angular Contours"),
        "Your angular proportions work beautifully with texture and movement that highlight or gently soften a defined jawline.",
        listOf(2, 0, 3, 1), listOf(3, 2, 0, 1),
    ),
)

private fun maleStyles() = listOf(
    HairstyleRecommendation(0, "HIGH VOLUME", "Fringe Up", "Crown height creates a clean, lengthened silhouette.", R.drawable.hairstyle_male_fringe_up),
    HairstyleRecommendation(0, "CLEAN EDGES", "Modern Undercut", "A sharp side taper adds structure and definition.", R.drawable.hairstyle_male_undercut),
    HairstyleRecommendation(0, "LOW MAINTENANCE", "Textured Crop", "Layered texture brings balance without heavy volume.", R.drawable.hairstyle_male_textured_crop),
    HairstyleRecommendation(0, "STRUCTURED", "Classic Pompadour", "Swept-back volume emphasizes confident proportions.", R.drawable.hairstyle_male_pompadour),
)

private fun femaleStyles() = listOf(
    HairstyleRecommendation(0, "HIGH VOLUME", "Butterfly Cut", "Face-framing layers add lift and natural movement.", R.drawable.hairstyle_female_butterfly_cut),
    HairstyleRecommendation(0, "BALANCED WAVES", "Curtain Bangs Bob", "A parted fringe softly balances the upper face.", R.drawable.hairstyle_female_curtain_bangs),
    HairstyleRecommendation(0, "PARISIAN CHIC", "Textured French Lob", "Collarbone-length waves create balanced side volume.", R.drawable.hairstyle_female_french_lob),
    HairstyleRecommendation(0, "MODERN EDGE", "Soft Layered Shag", "Feathered texture softens angles and adds dimension.", R.drawable.hairstyle_female_layered_shag),
)
