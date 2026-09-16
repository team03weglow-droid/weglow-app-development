package com.example.weglow.data.repository

import com.example.weglow.domain.model.ProfileImageUpload
import com.example.weglow.domain.repository.ProfileImageRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage

private const val PROFILE_IMAGES_BUCKET = "profile-images"

class SupabaseProfileImageRepository(
    private val client: SupabaseClient,
) : ProfileImageRepository {

    override suspend fun uploadProfileImage(userId: String, upload: ProfileImageUpload): Result<String> = runCatching {
        val path = "$userId/${objectNameFor(upload.mimeType)}"
        client.storage[PROFILE_IMAGES_BUCKET].upload(path, upload.bytes) {
            upsert = true
        }
        path
    }

    override suspend fun downloadProfileImage(path: String): Result<ByteArray> = runCatching {
        client.storage[PROFILE_IMAGES_BUCKET].downloadAuthenticated(path)
    }

    override suspend fun deleteProfileImage(path: String): Result<Unit> = runCatching {
        client.storage[PROFILE_IMAGES_BUCKET].delete(listOf(path))
        Unit
    }

    private fun objectNameFor(mimeType: String): String = when (mimeType) {
        "image/png" -> "profile.png"
        "image/webp" -> "profile.webp"
        else -> "profile.jpg"
    }
}