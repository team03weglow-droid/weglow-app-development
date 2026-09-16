package com.example.weglow.data.repository

import com.example.weglow.data.model.ProfileRow
import com.example.weglow.data.model.toProfileRow
import com.example.weglow.data.model.toUserProfile
import com.example.weglow.domain.model.UserProfile
import com.example.weglow.domain.repository.ProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant

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

    override suspend fun updateProfileImagePath(userId: String, path: String?): Result<Unit> = runCatching {
        client.postgrest["profiles"].update(
            update = { set("profile_image_url", path) },
        ) {
            filter { eq("id", userId) }
        }
        Unit
    }

    override suspend fun updateFaceShape(userId: String, faceShape: String): Result<Unit> = runCatching {
        client.postgrest["profiles"].update(
            update = { set("face_shape", faceShape) },
        ) {
            filter { eq("id", userId) }
        }
        Unit
    }

    override suspend fun updateScanSummary(userId: String, concerns: String, scannedAt: Instant): Result<Unit> = runCatching {
        client.postgrest["profiles"].update(
            update = {
                set("skin_concerns", concerns)
                set("last_scan_at", scannedAt.toString())
            },
        ) {
            filter { eq("id", userId) }
        }
        Unit
    }

    override suspend fun updateEnvironment(userId: String, uvIndex: Double, uvCategory: String, humidity: Int, locationName: String): Result<Unit> = runCatching {
        client.postgrest["profiles"].update(
            update = {
                set("uv_index", uvIndex)
                set("uv_category", uvCategory)
                set("humidity", humidity)
                set("location_name", locationName)
            },
        ) {
            filter { eq("id", userId) }
        }
        Unit
    }
}

internal fun List<ProfileRow>.isOnboardingCompleted(): Boolean =
    firstOrNull()?.onboarding_completed == true