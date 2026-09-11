package com.example.weglow.domain.repository

import com.example.weglow.domain.model.ProfileImageUpload

/**
 * Contract for storing and retrieving the binary profile-picture asset.
 *
 * The implementation lives in the data layer and talks to Supabase Storage;
 * callers only ever see neutral types (byte arrays and opaque storage paths).
 * UI and feature code must depend on this interface, never on the Supabase
 * Storage client.
 */
interface ProfileImageRepository {

    /**
     * Uploads [upload] for the authenticated [userId] into that user's own
     * private storage folder and returns the stable storage object path that
     * should be persisted in the profile.
     *
     * Implementations must derive the object path from [userId] and must never
     * trust a path supplied by the UI. [userId] is the authenticated identity
     * resolved by the caller from the auth session.
     */
    suspend fun uploadProfileImage(userId: String, upload: ProfileImageUpload): Result<String>

    /** Downloads the bytes of the private storage object at [path] using the caller's session. */
    suspend fun downloadProfileImage(path: String): Result<ByteArray>

    /** Best-effort removal of a storage object, used for orphan cleanup and rollback. */
    suspend fun deleteProfileImage(path: String): Result<Unit>
}
