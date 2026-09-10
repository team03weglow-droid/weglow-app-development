package com.example.weglow.data.repository

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import com.example.weglow.core.image.ScanPhotoDecoder
import com.example.weglow.data.scan.Letterbox
import com.example.weglow.data.scan.YoloPostProcessor
import com.example.weglow.domain.model.AcneScanResult
import com.example.weglow.domain.repository.AcneScanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel

/** Runs the bundled model entirely on the device. No HTTP client, upload, or server fallback. */
class LocalAcneScanRepository(context: Context) : AcneScanRepository {
    private val application = context.applicationContext
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val metadata by lazy {
        application.assets.open("acne/model.json").bufferedReader().use {
            json.decodeFromString<ModelMetadata>(it.readText())
        }.also {
            check(it.input_size == 640 && it.labels.isNotEmpty() && it.prediction_count == 8400) {
                "The installed skin analysis model is invalid. Please reinstall the app."
            }
        }
    }

    override suspend fun analyze(photoReference: String): AcneScanResult = withContext(Dispatchers.Default) {
        mutex.withLock {
            ensureActive()
            try {
                val photo = ScanPhotoDecoder.decode(application.contentResolver, Uri.parse(photoReference))
                try {
                    val geometry = Letterbox(photo.width, photo.height, metadata.input_size)
                    val pixels = prepareInput(photo, geometry)
                    ensureActive()
                    val environment = OrtEnvironment.getEnvironment()
                    environment.setTelemetry(false)
                    // Mapping the uncompressed asset avoids copying the weights into the Java heap.
                    application.assets.openFd("acne/model.onnx").use { asset ->
                        FileInputStream(asset.fileDescriptor).use { stream ->
                            val model = stream.channel.map(FileChannel.MapMode.READ_ONLY, asset.startOffset, asset.declaredLength)
                            OrtSession.SessionOptions().use { options ->
                                options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
                                options.setInterOpNumThreads(1)
                                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                                // Close native sessions and tensors after each scan, including cancellation/error paths.
                                environment.createSession(model, options).use { session ->
                                    val shape = (session.inputInfo.getValue(metadata.input_name).info as TensorInfo).shape
                                    check(shape.contentEquals(longArrayOf(1, 3, 640, 640))) { "The installed skin analysis model is incompatible." }
                                    ensureActive()
                                    OnnxTensor.createTensor(environment, pixels, shape).use { tensor ->
                                        session.run(mapOf(metadata.input_name to tensor)).use { prediction ->
                                            ensureActive()
                                            val output = prediction.get(metadata.output_name).orElseThrow() as OnnxTensor
                                            check(output.info.shape.contentEquals(longArrayOf(1, (4 + metadata.labels.size).toLong(), metadata.prediction_count.toLong()))) {
                                                "The installed skin analysis model is incompatible."
                                            }
                                            @Suppress("UNCHECKED_CAST")
                                            val rows = (output.value as Array<Array<FloatArray>>)[0]
                                            val detections = YoloPostProcessor.decode(
                                                rows, metadata.labels, geometry, metadata.confidence_threshold,
                                                metadata.iou_threshold, metadata.max_detections,
                                            )
                                            AcneScanResult(detections, photo.width, photo.height, metadata.model_sha256.take(12), metadata.confidence_threshold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    photo.recycle()
                }
            } catch (error: OutOfMemoryError) {
                throw IllegalStateException("There is not enough memory to analyze this photo. Close other apps and try again.", error)
            } catch (error: LinkageError) {
                throw IllegalStateException("Skin analysis could not start on this device. Please update or reinstall the app.", error)
            }
        }
    }

    private fun prepareInput(photo: Bitmap, geometry: Letterbox): FloatBuffer {
        val size = geometry.inputSize
        val input = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(input)
            canvas.drawColor(Color.rgb(114, 114, 114))
            canvas.drawBitmap(photo, null, Rect(geometry.left, geometry.top,
                geometry.left + geometry.resizedWidth, geometry.top + geometry.resizedHeight), Paint(Paint.FILTER_BITMAP_FLAG))
            val pixels = IntArray(size * size)
            input.getPixels(pixels, 0, size, 0, 0, size, size)
            val buffer = ByteBuffer.allocateDirect(3 * pixels.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()
            // RGB, channel-first, [0,1], matching YOLO's PyTorch preprocessing.
            for (shift in intArrayOf(16, 8, 0)) for (pixel in pixels) buffer.put(((pixel shr shift) and 255) / 255f)
            buffer.rewind()
            return buffer
        } finally {
            input.recycle()
        }
    }
}

@Serializable
private data class ModelMetadata(
    val input_name: String,
    val output_name: String,
    val input_size: Int,
    val prediction_count: Int,
    val labels: List<String>,
    val model_sha256: String,
    val confidence_threshold: Float,
    val iou_threshold: Float,
    val max_detections: Int,
)
