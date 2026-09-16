package com.example.weglow.domain.repository

import com.example.weglow.domain.model.UserProfile
import java.time.Instant

/** Contract for profile persistence. UI code must not depend on Supabase-specific models. */
interface ProfileRepository {
    suspend fun saveProfile(profile: UserProfile): Result<Unit>

    /** Reads the persisted profile for [userId], or null when no row exists yet. */
    suspend fun getProfile(userId: String): Result<UserProfile?>

    /**
     * True only when the persisted profile has [UserProfile.onboardingCompleted] set.
     * The mere existence of a profile row is not treated as completion.
     */
    suspend fun hasCompletedOnboarding(userId: String): Result<Boolean>

    /**
     * Persists [path] (a Supabase Storage object path, or null to clear it) into
     * the authenticated user's profile row, touching no other column. [userId]
     * must be the authenticated identity resolved by the caller, never a value
     * supplied by the UI.
     */
    suspend fun updateProfileImagePath(userId: String, path: String?): Result<Unit>

    /** Stores the last detected face shape for the authenticated profile only. */
    suspend fun updateFaceShape(userId: String, faceShape: String): Result<Unit>

    /**
     * Stores the latest on-device skin scan summary (e.g. "Blackheads (2), Pimples (1)")
     * for the authenticated profile only, so the chat AI and recommendations can
     * personalize on the most recent acne findings. Touches no other columns.
     */
    suspend fun updateScanSummary(userId: String, concerns: String, scannedAt: Instant): Result<Unit>

    /**
     * Stores the latest environment/UV reading for the authenticated profile only,
     * so the chat AI can personalize sunscreen and UV advice. Touches no other columns.
     */
    suspend fun updateEnvironment(userId: String, uvIndex: Double, uvCategory: String, humidity: Int, locationName: String): Result<Unit>
}