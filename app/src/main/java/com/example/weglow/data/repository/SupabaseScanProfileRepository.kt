package com.example.weglow.data.repository

import com.example.weglow.data.model.toScanProfileInsert
import com.example.weglow.domain.model.AcneScanResult
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.ProfileRepository
import com.example.weglow.domain.repository.ScanProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant

class SupabaseScanProfileRepository(
    private val client: SupabaseClient,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ScanProfileRepository {
    override suspend fun saveScanResult(result: AcneScanResult, scannedAt: Instant): Result<Unit> = runCatching {
        val authenticatedUserId = checkNotNull(authRepository.currentUserId()) {
            "Your session is no longer active."
        }
        val profile = profileRepository.getProfile(authenticatedUserId).getOrThrow()
            ?: error("No profile is available for the current user.")
        check(profile.id == authenticatedUserId) { "The current profile does not match the signed-in user." }

        client.postgrest[SCAN_PROFILES_TABLE].insert(
            result.toScanProfileInsert(
                userProfileId = profile.id,
                dateTime = scannedAt.toString(),
            ),
        )
        Unit
    }

    private companion object {
        const val SCAN_PROFILES_TABLE = "Scan-profiles"
    }
}
