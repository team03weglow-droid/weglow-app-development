package com.example.weglow.data.repository

import com.example.weglow.domain.model.ProfileImageUpload
import com.example.weglow.domain.repository.ProfileImageRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType

/**
 * Supabase Storage implementation of [ProfileImageRepository].
 *
 * All objects live in a private bucket ([BUCKET]) under a per-user folder keyed
 * by the authenticated user id: `<userId>/<timestamp>.<ext>`. The matching
 * `storage.objects` RLS policies (see the Phase 6 migration) enforce that a user
 * can only read, write, or delete inside their own folder — the Kotlin side is
 * defence in depth, not the security boundary.
 *
 * Each upload uses a fresh timestamped name rather than a fixed `profile.jpg`
 * so the ViewModel can persist the new reference first and only then delete the
 * previous object, which keeps failures recoverable and avoids orphan build-up.
 */
class SupabaseProfileImageRepository(
    private val client: SupabaseClient,
) : ProfileImageRepository {

    override suspend fun uploadProfileImage(
        userId: String,
        upload: ProfileImageUpload,
    ): Result<String> = runCatching {
        require(userId.isNotBlank()) { "A blank user id cannot own a storage object." }
        val objectPath = "$userId/${System.currentTimeMillis()}.${extensionFor(upload.mimeType)}"
        client.storage.from(BUCKET).upload(objectPath, upload.bytes) {
            upsert = false
            contentType = runCatching { ContentType.parse(upload.mimeType) }.getOrNull()
        }
        objectPath
    }

    override suspend fun downloadProfileImage(path: String): Result<ByteArray> = runCatching {
        client.storage.from(BUCKET).downloadAuthenticated(path)
    }

    override suspend fun deleteProfileImage(path: String): Result<Unit> = runCatching {
        client.storage.from(BUCKET).delete(path)
    }

    private fun extensionFor(mimeType: String): String =
        when (mimeType.substringBefore(';').trim().lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }

    private companion object {
        const val BUCKET = "profile-images"
    }
}
