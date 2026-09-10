package com.example.weglow.data.repository

import com.example.weglow.data.model.ProfileRow
import com.example.weglow.data.model.toProfileRow
import com.example.weglow.data.model.toUserProfile
import com.example.weglow.domain.model.UserProfile
import com.example.weglow.domain.repository.ProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

class SupabaseProfileRepository(
    private val client: SupabaseClient,
) : ProfileRepository {
    override suspend fun saveProfile(profile: UserProfile): Result<Unit> = runCatching {
        client.postgrest["profiles"].upsert(profile.toProfileRow())
        Unit
    }

    override suspend fun getProfile(userId: String): Result<UserProfile?> = runCatching {
        client.postgrest["profiles"]
            .select { filter { eq("id", userId) } }
            .decodeList<ProfileRow>()
            .firstOrNull()
            ?.toUserProfile()
    }

    override suspend fun hasCompletedOnboarding(userId: String): Result<Boolean> = runCatching {
        client.postgrest["profiles"]
            .select { filter { eq("id", userId) } }
            .decodeList<ProfileRow>()
            .isOnboardingCompleted()
    }
}

/**
 * Decides completion from the actual [ProfileRow.onboarding_completed] flag, never from
 * row existence. Kept as a pure function so the rule is unit-testable without a Supabase client.
 */
internal fun List<ProfileRow>.isOnboardingCompleted(): Boolean =
    firstOrNull()?.onboarding_completed == true
