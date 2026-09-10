package com.example.weglow.data.scan

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Actual model + native Java runtime smoke test, independent of HTTP and Android services. */
class BundledAcneModelTest {
    @Test fun bundledCheckpointRunsAndMatchesItsManifest() {
        val directory = File(requireNotNull(System.getProperty("weglow.model.assets")))
        val manifest = Json.parseToJsonElement(File(directory, "model.json").readText()).jsonObject
        val modelFile = File(directory, "model.onnx")
        val digest = MessageDigest.getInstance("SHA-256")
        modelFile.inputStream().use { input ->
            val chunk = ByteArray(8192)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                digest.update(chunk, 0, read)
            }
        }
        assertEquals(manifest.getValue("model_sha256").jsonPrimitive.content, digest.digest().joinToString("") { "%02x".format(it) })
        val labels = manifest.getValue("labels").jsonArray.map { it.jsonPrimitive.content }
        val inputName = manifest.getValue("input_name").jsonPrimitive.content
        val outputName = manifest.getValue("output_name").jsonPrimitive.content
        val environment = OrtEnvironment.getEnvironment()
        environment.setTelemetry(false)
        FileInputStream(modelFile).use { file ->
            val model = file.channel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length())
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2)
                environment.createSession(model, options).use { session ->
                    val buffer = ByteBuffer.allocateDirect(3 * 640 * 640 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                    repeat(3 * 640 * 640) { buffer.put(1f) }
                    buffer.rewind()
                    OnnxTensor.createTensor(environment, buffer, longArrayOf(1, 3, 640, 640)).use { tensor ->
                        session.run(mapOf(inputName to tensor)).use { result ->
                            val prediction = result.get(outputName).get() as OnnxTensor
                            assertArrayEquals(longArrayOf(1, 14, 8400), prediction.info.shape)
                            @Suppress("UNCHECKED_CAST")
                            val rows = (prediction.value as Array<Array<FloatArray>>)[0]
                            assertTrue(rows.all { row -> row.all { it.isFinite() } })
                            assertTrue(YoloPostProcessor.decode(rows, labels, Letterbox(640, 640, 640)).isEmpty())
                        }
                    }
                }
            }
        }
    }
}
