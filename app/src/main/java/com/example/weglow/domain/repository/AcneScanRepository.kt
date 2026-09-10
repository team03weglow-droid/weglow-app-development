package com.example.weglow.domain.repository

import com.example.weglow.domain.model.AcneScanResult

interface AcneScanRepository {
    suspend fun analyze(photoReference: String): AcneScanResult
}
