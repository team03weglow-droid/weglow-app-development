package com.example.weglow.domain.model

data class AcneDetection(
    val label: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class AcneScanResult(
    val detections: List<AcneDetection>,
    val imageWidth: Int,
    val imageHeight: Int,
    val modelVersion: String,
    val confidenceThreshold: Float,
)
