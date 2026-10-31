package com.example.weglow.data.repository

import com.example.weglow.domain.model.AcneScanResult
import com.example.weglow.domain.model.UserProfile
import com.example.weglow.domain.repository.AcneScanRepository
import com.example.weglow.domain.repository.ProfileRepository
import com.example.weglow.security.SecurityTestAuth
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanAccountOwnershipTest {
    @Test fun scanCannotContinueToPersistenceAfterAnAccountSwitch() = runTest {
        val auth = SecurityTestAuth()
        val gate = CompletableDeferred<Unit>()
        var saved = false
        val onDevice = object : AcneScanRepository {
            override suspend fun analyze(photoReference: String): Result<AcneScanResult> {
                gate.await()
                return Result.success(AcneScanResult(emptyList(), 10, 10, "test", 0.5f))
            }
        }
        val profiles = object : ProfileRepository {
            override suspend fun saveProfile(profile: UserProfile) = Result.success(Unit)
            override suspend fun getProfile(userId: String): Result<UserProfile?> = Result.success(null)
            override suspend fun hasCompletedOnboarding(userId: String) = Result.success(false)
            override suspend fun updateProfileImagePath(userId: String, path: String?) = Result.success(Unit)
            override suspend fun updateFaceShape(userId: String, faceShape: String) = Result.success(Unit)
            override suspend fun updateScanSummary(userId: String, concerns: String, scannedAt: Instant) = Result.success(Unit).also { saved = true }
            override suspend fun updateEnvironment(userId: String, uvIndex: Double, uvCategory: String, humidity: Int, locationName: String) = Result.success(Unit)
            override suspend fun updateGender(userId: String, gender: String) = Result.success(Unit)
        }
        val repository = SupabaseAcneScanRepository(onDevice, auth, profiles)
        val request = async { repository.analyze("private photo") }
        runCurrent()
        auth.switchTo("bob")
        gate.complete(Unit)
        try {
            request.await()
            fail("A scan from a previous account must not proceed to persistence")
        } catch (_: CancellationException) { }
        assertFalse(saved)
    }
}
