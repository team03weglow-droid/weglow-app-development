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
}
