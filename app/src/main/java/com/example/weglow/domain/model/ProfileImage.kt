package com.example.weglow.domain.model

/**
 * Neutral, backend- and Android-agnostic representation of an image the user
 * picked for their profile picture.
 *
 * The Android layer is responsible for reading the picked content `Uri` and
 * normalising it into these raw bytes before it crosses into the domain/data
 * layers. No `android.net.Uri` (or any Android/Supabase type) is allowed here.
 */
class ProfileImageUpload(
    val bytes: ByteArray,
    val mimeType: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProfileImageUpload) return false
        return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * mimeType.hashCode() + bytes.contentHashCode()
}
