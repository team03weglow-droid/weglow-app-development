package com.example.weglow.domain.repository

import com.example.weglow.domain.model.UserProfile

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
     * Persists [gender] (one of [com.example.weglow.domain.model.Gender.OPTIONS]) into the
     * authenticated user's profile row, touching no other column. This is the same
     * `profiles.gender` value set during onboarding, so a Profile-initiated change here is
     * the single source of truth every other screen (in particular hairstyle recommendations)
     * reads from afterwards - no separate gender field or cache is ever created.
     */
    suspend fun updateGender(userId: String, gender: String): Result<Unit>
}
