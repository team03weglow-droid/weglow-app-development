package com.example.weglow.data.scan

import com.example.weglow.domain.model.AcneDetection
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/** Geometry of the fixed square letterbox used by the bundled YOLO export. */
data class Letterbox(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val inputSize: Int,
) {
    init { require(sourceWidth > 0 && sourceHeight > 0 && inputSize > 0) }
    val scale = min(inputSize.toFloat() / sourceWidth, inputSize.toFloat() / sourceHeight)
    val resizedWidth = round(sourceWidth * scale).toInt().coerceIn(1, inputSize)
    val resizedHeight = round(sourceHeight * scale).toInt().coerceIn(1, inputSize)
    val left = (inputSize - resizedWidth) / 2
    val top = (inputSize - resizedHeight) / 2
}

object YoloPostProcessor {
    /** YOLOv8 output is [4 + classes, anchors], with xywh in input pixels and no objectness column. */
    fun decode(
        output: Array<FloatArray>,
        labels: List<String>,
        letterbox: Letterbox,
        confidenceThreshold: Float = 0.05f,
        iouThreshold: Float = 0.7f,
        maxDetections: Int = 300,
    ): List<AcneDetection> {
        require(labels.isNotEmpty() && output.size == labels.size + 4)
        require(confidenceThreshold in 0f..1f && iouThreshold in 0f..1f && maxDetections > 0)
        val count = output[0].size
        require(output.all { it.size == count })
        val candidates = ArrayList<AcneDetection>()
        for (index in 0 until count) {
            var bestClass = -1
            var confidence = confidenceThreshold
            for (classIndex in labels.indices) {
                val score = output[classIndex + 4][index]
                if (score.isFinite() && score > confidence && score <= 1f) {
                    confidence = score
                    bestClass = classIndex
                }
            }
            if (bestClass < 0) continue
            val x = output[0][index]
            val y = output[1][index]
            val width = output[2][index]
            val height = output[3][index]
            if (!x.isFinite() || !y.isFinite() || !width.isFinite() || !height.isFinite() || width <= 0 || height <= 0) continue
            // Keep unclipped boxes during NMS, matching Ultralytics. Undo letterbox afterwards.
            candidates += AcneDetection(labels[bestClass], confidence, x - width / 2, y - height / 2, x + width / 2, y + height / 2)
        }
        val kept = ArrayList<AcneDetection>()
        for (candidate in candidates.sortedByDescending { it.confidence }) {
            if (kept.none { it.label == candidate.label && iou(it, candidate) > iouThreshold }) kept += candidate
            if (kept.size == maxDetections) break
        }
        return kept.mapNotNull { detection ->
            val left = ((detection.left - letterbox.left) / letterbox.scale / letterbox.sourceWidth).coerceIn(0f, 1f)
            val top = ((detection.top - letterbox.top) / letterbox.scale / letterbox.sourceHeight).coerceIn(0f, 1f)
            val right = ((detection.right - letterbox.left) / letterbox.scale / letterbox.sourceWidth).coerceIn(0f, 1f)
            val bottom = ((detection.bottom - letterbox.top) / letterbox.scale / letterbox.sourceHeight).coerceIn(0f, 1f)
            if (left >= right || top >= bottom) null else detection.copy(left = left, top = top, right = right, bottom = bottom)
        }
    }

    private fun iou(a: AcneDetection, b: AcneDetection): Float {
        val intersection = max(0f, min(a.right, b.right) - max(a.left, b.left)) *
            max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val union = (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - intersection
        return if (union > 0f) intersection / union else 0f
    }
}
