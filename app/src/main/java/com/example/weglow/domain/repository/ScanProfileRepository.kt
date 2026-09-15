package com.example.weglow.domain.repository

import com.example.weglow.domain.model.AcneScanResult
import java.time.Instant

/** Persists final skin-concern scan results for the currently authenticated profile. */
interface ScanProfileRepository {
    suspend fun saveScanResult(result: AcneScanResult, scannedAt: Instant): Result<Unit>
}
