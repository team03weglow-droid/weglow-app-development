package com.example.weglow.domain.repository

import com.example.weglow.domain.model.AcneScanResult

interface AcneScanRepository {
    /** Classifies a skin photo. Failure is returned as a typed [com.example.weglow.domain.model.ScanFailure]. */
    suspend fun analyze(photoReference: String): Result<AcneScanResult>
}
