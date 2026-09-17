package com.example.weglow.data.repository

import com.example.weglow.domain.model.AcneScanResult
import com.example.weglow.domain.repository.AcneScanRepository
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.ProfileRepository
import java.time.Instant

/**
 * Runs the on-device acne model via [onDevice] and then mirrors the detected concerns to the
 * authenticated user's profile (see [SupabaseHairstyleRepository]) so the chat AI and
 * recommendations can personalize on the latest skin scan.
 *
 * Persistence is deliberately best-effort rather than failing the scan: the model output is the
 * source of truth for the UI and is fully local, so a transient network error must never discard
 * a finished scan. If the summary write fails, the caller still receives the local result.
 */
class SupabaseAcneScanRepository(
    private val onDevice: AcneScanRepository,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : AcneScanRepository {

    override suspend fun analyze(photoReference: String): Result<AcneScanResult> {
        return onDevice.analyze(photoReference).onSuccess { result ->
            val summary = concernsSummary(result)

            if (summary.isNotEmpty()) {
                authRepository.currentUserId()?.let { userId ->
                    runCatching {
                        profileRepository.updateScanSummary(
                            userId,
                            summary,
                            Instant.now(),
                        )
                    }
                    // Best-effort: failures are ignored so the local scan still surfaces to the user.
                }
            }
        }
    }

    private fun concernsSummary(result: AcneScanResult): String =
        result.detections
            .groupBy { it.label }
            .map { (label, detections) -> "$label (${detections.size})" }
            .joinToString(", ")
}