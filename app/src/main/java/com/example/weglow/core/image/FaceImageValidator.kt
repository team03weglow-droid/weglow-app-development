package com.example.weglow.core.image

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.Closeable
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

sealed interface FaceValidationResult {
    data object Valid : FaceValidationResult
    data object NoFace : FaceValidationResult
    data object MultipleFaces : FaceValidationResult
    data object FaceTooSmall : FaceValidationResult
    data object FaceTooRotated : FaceValidationResult
    data object FacePartiallyOutsideImage : FaceValidationResult
    data object ProcessingError : FaceValidationResult
}

val FaceValidationResult.errorMessage: String?
    get() = when (this) {
        FaceValidationResult.Valid -> null
        FaceValidationResult.NoFace ->
            "No face was detected. Please choose a clear photo of your face."
        FaceValidationResult.MultipleFaces ->
            "Multiple faces were detected. Please use a photo containing only one person."
        FaceValidationResult.FaceTooSmall ->
            "Your face is too far from the camera. Please move closer and try again."
        FaceValidationResult.FaceTooRotated ->
            "Please look directly at the camera and try again."
        FaceValidationResult.FacePartiallyOutsideImage ->
            "Make sure your entire face is visible inside the photo."
        FaceValidationResult.ProcessingError ->
            "We couldn’t process this image. Please choose another photo."
    }

data class FaceValidationThresholds(
    val detectorMinimumFaceSize: Float = 0.10f,
    val minimumFaceWidthRatio: Float = 0.20f,
    val minimumFaceHeightRatio: Float = 0.20f,
    val maximumYawDegrees: Float = 25f,
    val maximumPitchDegrees: Float = 20f,
    val maximumRollDegrees: Float = 25f,
    val boundaryInsetRatio: Float = 0.02f,
)

interface FaceValidator : Closeable {
    suspend fun validate(photoReference: String): FaceValidationResult
    override fun close() = Unit
}

/** Runs face presence and framing checks locally. No image or face data leaves the device. */
class FaceImageValidator(
    context: Context,
    private val thresholds: FaceValidationThresholds = FaceValidationThresholds(),
) : FaceValidator {
    private val application = context.applicationContext
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(thresholds.detectorMinimumFaceSize)
            .build(),
    )

    override suspend fun validate(photoReference: String): FaceValidationResult =
        withContext(Dispatchers.Default) {
            val bitmap = try {
                ScanPhotoDecoder.decode(application.contentResolver, Uri.parse(photoReference))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OutOfMemoryError) {
                return@withContext FaceValidationResult.ProcessingError
            } catch (_: Exception) {
                return@withContext FaceValidationResult.ProcessingError
            } catch (_: LinkageError) {
                return@withContext FaceValidationResult.ProcessingError
            }

            try {
                // Await on this worker context so the bitmap remains alive until ML Kit has
                // completely finished reading it, including when the coroutine is cancelled.
                val faces = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
                ensureActive()
                evaluateDetectedFaces(
                    faces = faces.map { face ->
                        val box = face.boundingBox
                        DetectedFaceObservation(
                            left = box.left,
                            top = box.top,
                            right = box.right,
                            bottom = box.bottom,
                            pitchDegrees = face.headEulerAngleX,
                            yawDegrees = face.headEulerAngleY,
                            rollDegrees = face.headEulerAngleZ,
                        )
                    },
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    thresholds = thresholds,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OutOfMemoryError) {
                FaceValidationResult.ProcessingError
            } catch (_: Exception) {
                FaceValidationResult.ProcessingError
            } catch (_: LinkageError) {
                FaceValidationResult.ProcessingError
            } finally {
                bitmap.recycle()
            }
        }

    override fun close() = detector.close()
}

internal data class DetectedFaceObservation(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val pitchDegrees: Float,
    val yawDegrees: Float,
    val rollDegrees: Float,
)

internal fun evaluateDetectedFaces(
    faces: List<DetectedFaceObservation>,
    imageWidth: Int,
    imageHeight: Int,
    thresholds: FaceValidationThresholds = FaceValidationThresholds(),
): FaceValidationResult {
    if (imageWidth <= 0 || imageHeight <= 0) return FaceValidationResult.ProcessingError
    if (faces.isEmpty()) return FaceValidationResult.NoFace
    if (faces.size > 1) return FaceValidationResult.MultipleFaces

    val face = faces.single()
    if (
        face.right <= face.left ||
        face.bottom <= face.top ||
        !face.pitchDegrees.isFinite() ||
        !face.yawDegrees.isFinite() ||
        !face.rollDegrees.isFinite()
    ) {
        return FaceValidationResult.ProcessingError
    }
    val faceWidthRatio = (face.right - face.left).toFloat() / imageWidth
    val faceHeightRatio = (face.bottom - face.top).toFloat() / imageHeight
    if (
        faceWidthRatio < thresholds.minimumFaceWidthRatio ||
        faceHeightRatio < thresholds.minimumFaceHeightRatio
    ) {
        return FaceValidationResult.FaceTooSmall
    }

    if (
        abs(face.yawDegrees) > thresholds.maximumYawDegrees ||
        abs(face.pitchDegrees) > thresholds.maximumPitchDegrees ||
        abs(face.rollDegrees) > thresholds.maximumRollDegrees
    ) {
        return FaceValidationResult.FaceTooRotated
    }

    val horizontalInset = imageWidth * thresholds.boundaryInsetRatio
    val verticalInset = imageHeight * thresholds.boundaryInsetRatio
    if (
        face.left < horizontalInset ||
        face.top < verticalInset ||
        face.right > imageWidth - horizontalInset ||
        face.bottom > imageHeight - verticalInset
    ) {
        return FaceValidationResult.FacePartiallyOutsideImage
    }

    return FaceValidationResult.Valid
}
