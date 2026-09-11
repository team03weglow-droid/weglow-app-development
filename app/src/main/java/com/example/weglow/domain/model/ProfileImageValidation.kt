package com.example.weglow.domain.model

/** Outcome of validating a candidate profile picture before it is uploaded. */
sealed interface ProfileImageValidation {
    data object Valid : ProfileImageValidation
    data object Empty : ProfileImageValidation
    data class TooLarge(val sizeBytes: Int, val maxBytes: Int) : ProfileImageValidation
    data class UnsupportedType(val mimeType: String) : ProfileImageValidation
    data object NotAnImage : ProfileImageValidation
}

/**
 * Pure, framework-free validation for profile pictures. Keeping it in the domain
 * layer means the rules are unit-testable without Android or Supabase and are
 * enforced on-device before any byte is uploaded.
 */
object ProfileImageValidator {

    /** Mirrors the Storage bucket `file_size_limit` in the Phase 6 migration. */
    const val MAX_BYTES: Int = 5 * 1024 * 1024

    val ALLOWED_MIME_TYPES: Set<String> = setOf("image/jpeg", "image/png", "image/webp")

    fun validate(upload: ProfileImageUpload): ProfileImageValidation {
        if (upload.bytes.isEmpty()) return ProfileImageValidation.Empty
        if (upload.bytes.size > MAX_BYTES) {
            return ProfileImageValidation.TooLarge(upload.bytes.size, MAX_BYTES)
        }
        val normalisedType = upload.mimeType.substringBefore(';').trim().lowercase()
        if (normalisedType !in ALLOWED_MIME_TYPES) {
            return ProfileImageValidation.UnsupportedType(upload.mimeType)
        }
        if (!looksLikeSupportedImage(upload.bytes)) return ProfileImageValidation.NotAnImage
        return ProfileImageValidation.Valid
    }

    /**
     * Magic-byte sniff so a renamed or mislabelled file cannot pass as an image
     * even when its declared MIME type is in the allow-list.
     */
    private fun looksLikeSupportedImage(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        fun b(i: Int) = bytes[i].toInt() and 0xFF

        val jpeg = b(0) == 0xFF && b(1) == 0xD8 && b(2) == 0xFF
        val png = b(0) == 0x89 && b(1) == 'P'.code && b(2) == 'N'.code && b(3) == 'G'.code
        val webp = b(0) == 'R'.code && b(1) == 'I'.code && b(2) == 'F'.code && b(3) == 'F'.code &&
            b(8) == 'W'.code && b(9) == 'E'.code && b(10) == 'B'.code && b(11) == 'P'.code
        return jpeg || png || webp
    }
}
