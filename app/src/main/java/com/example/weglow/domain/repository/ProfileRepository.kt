package com.example.weglow.domain.repository

import com.example.weglow.domain.model.UserProfile
import java.time.Instant

interface ProfileRepository {
    suspend fun saveProfile(profile: UserProfile): Result<Unit>

    suspend fun getProfile(userId: String): Result<UserProfile?>

    suspend fun hasCompletedOnboarding(userId: String): Result<Boolean>

    suspend fun updateProfileImagePath(
        userId: String,
        path: String?,
    ): Result<Unit>

    suspend fun updateFaceShape(
        userId: String,
        faceShape: String,
    ): Result<Unit>

    /**
     * Stores the latest on-device skin scan summary (e.g. "Blackheads (2), Pimples (1)")
     * for the authenticated profile only, so the chat AI and recommendations can
     * personalize on the most recent acne findings. Touches no other columns.
     */
    suspend fun updateScanSummary(
        userId: String,
        concerns: String,
        scannedAt: Instant,
    ): Result<Unit>

    /**
     * Stores the latest environment/UV reading for the authenticated profile only,
     * so the chat AI can personalize sunscreen and UV advice. Touches no other columns.
     */
    suspend fun updateEnvironment(
        userId: String,
        uvIndex: Double,
        uvCategory: String,
        humidity: Int,
        locationName: String,
    ): Result<Unit>

    /**
     * Persists [gender] (one of [com.example.weglow.domain.model.Gender.OPTIONS]) into the
     * authenticated user's profile row, touching no other column. This is the same
     * `profiles.gender` value set during onboarding, so a Profile-initiated change here is
     * the single source of truth every other screen (in particular hairstyle recommendations)
     * reads from afterwards - no separate gender field or cache is ever created.
     */
    suspend fun updateGender(
        userId: String,
        gender: String,
    ): Result<Unit>
}