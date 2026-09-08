package com.example.weglow.domain.repository

import com.example.weglow.domain.model.UserProfile

/** Contract for profile persistence. UI code must not depend on Supabase-specific models. */
interface ProfileRepository {
    suspend fun saveProfile(profile: UserProfile): Result<Unit>
    suspend fun hasCompletedOnboarding(userId: String): Result<Boolean>
}
