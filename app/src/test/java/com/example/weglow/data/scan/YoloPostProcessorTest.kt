package com.example.weglow.data.scan

import org.junit.Assert.*
import org.junit.Test

class YoloPostProcessorTest {
    private val labels = listOf("papules", "whiteheads")

    @Test fun removesLetterboxPaddingForLandscapeAndPortraitPhotos() {
        for (geometry in listOf(Letterbox(640, 320, 640), Letterbox(320, 640, 640))) {
            val width = geometry.resizedWidth / 2f
            val height = geometry.resizedHeight / 2f
            val detection = YoloPostProcessor.decode(output(floatArrayOf(320f, 320f, width, height, 0.9f, 0.1f)), labels, geometry).single()
            assertEquals(0.25f, detection.left, 0.0001f)
            assertEquals(0.25f, detection.top, 0.0001f)
            assertEquals(0.75f, detection.right, 0.0001f)
            assertEquals(0.75f, detection.bottom, 0.0001f)
        }
    }

    @Test fun suppressesOverlappingBoxesOnlyWithinTheSameClass() {
        val detections = YoloPostProcessor.decode(output(
            floatArrayOf(320f, 320f, 100f, 100f, 0.95f, 0.1f),
            floatArrayOf(321f, 320f, 100f, 100f, 0.8f, 0.1f),
            floatArrayOf(320f, 320f, 100f, 100f, 0.1f, 0.9f),
        ), labels, Letterbox(640, 640, 640))
        assertEquals(listOf("papules", "whiteheads"), detections.map { it.label })
        assertEquals(0.95f, detections.first().confidence, 0.0001f)
    }

    @Test fun ignoresBelowThresholdInvalidAndPaddingOnlyDetections() {
        // The bundled model's confidence_threshold is 0.15 (see acne/model.json), lowered
        // deliberately from an earlier 0.25 default so weak-but-real detections still surface.
        // This case must stay below the *current* default to keep testing rejection, not pass.
        val detections = YoloPostProcessor.decode(output(
            floatArrayOf(320f, 320f, 100f, 100f, 0.1f, 0.05f),
            floatArrayOf(Float.NaN, 320f, 100f, 100f, 0.9f, 0.1f),
            floatArrayOf(320f, 30f, 20f, 20f, 0.9f, 0.1f),
        ), labels, Letterbox(640, 320, 640))
        assertTrue(detections.isEmpty())
    }

    @Test fun selectsHighestClassScoreWithoutAnObjectnessColumn() {
        val result = YoloPostProcessor.decode(output(floatArrayOf(320f, 320f, 100f, 100f, 0.4f, 0.8f)), labels, Letterbox(640, 640, 640)).single()
        assertEquals("whiteheads", result.label)
        assertEquals(0.8f, result.confidence, 0f)
    }

    @Test fun clipsBoxesToImageAndLimitsResultsByConfidence() {
        val detections = YoloPostProcessor.decode(output(
            floatArrayOf(20f, 20f, 100f, 100f, 0.9f, 0.1f),
            floatArrayOf(500f, 500f, 50f, 50f, 0.8f, 0.1f),
        ), labels, Letterbox(640, 640, 640), maxDetections = 1)
        assertEquals(1, detections.size)
        assertEquals(0f, detections.single().left, 0f)
        assertEquals(0f, detections.single().top, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIncorrectTensorShape() {
        YoloPostProcessor.decode(arrayOf(floatArrayOf(1f)), labels, Letterbox(640, 640, 640))
    }

    private fun output(vararg anchors: FloatArray): Array<FloatArray> =
        Array(6) { channel -> FloatArray(anchors.size) { index -> anchors[index][channel] } }
}
