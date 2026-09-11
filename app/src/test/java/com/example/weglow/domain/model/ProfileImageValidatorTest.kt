package com.example.weglow.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6 validation rules for profile pictures. Pure domain logic: no Android,
 * no Supabase, no network.
 */
class ProfileImageValidatorTest {

    private val jpegHeader = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
        0x00, 0x10, 'J'.code.toByte(), 'F'.code.toByte(),
        'I'.code.toByte(), 'F'.code.toByte(), 0x00, 0x01,
    )
    private val pngHeader = byteArrayOf(
        0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
        0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
    )
    private val webpHeader = byteArrayOf(
        'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
        0x24, 0x00, 0x00, 0x00,
        'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
    )

    @Test
    fun validJpeg_passes() {
        assertEquals(
            ProfileImageValidation.Valid,
            ProfileImageValidator.validate(ProfileImageUpload(jpegHeader, "image/jpeg")),
        )
    }

    @Test
    fun validPngAndWebp_pass() {
        assertEquals(
            ProfileImageValidation.Valid,
            ProfileImageValidator.validate(ProfileImageUpload(pngHeader, "image/png")),
        )
        assertEquals(
            ProfileImageValidation.Valid,
            ProfileImageValidator.validate(ProfileImageUpload(webpHeader, "image/webp")),
        )
    }

    @Test
    fun mimeType_withCharsetSuffix_isAccepted() {
        assertEquals(
            ProfileImageValidation.Valid,
            ProfileImageValidator.validate(ProfileImageUpload(jpegHeader, "image/jpeg; charset=binary")),
        )
    }

    @Test
    fun emptyInput_isRejected() {
        assertEquals(
            ProfileImageValidation.Empty,
            ProfileImageValidator.validate(ProfileImageUpload(ByteArray(0), "image/jpeg")),
        )
    }

    @Test
    fun unsupportedMimeType_isRejected() {
        val result = ProfileImageValidator.validate(ProfileImageUpload(jpegHeader, "image/gif"))
        assertTrue(result is ProfileImageValidation.UnsupportedType)
    }

    @Test
    fun oversizeInput_isRejected() {
        val big = ByteArray(ProfileImageValidator.MAX_BYTES + 1)
        jpegHeader.copyInto(big)
        val result = ProfileImageValidator.validate(ProfileImageUpload(big, "image/jpeg"))
        assertTrue(result is ProfileImageValidation.TooLarge)
    }

    @Test
    fun declaredImageButNonImageBytes_isRejected() {
        val notAnImage = "this is definitely not an image file".toByteArray()
        assertEquals(
            ProfileImageValidation.NotAnImage,
            ProfileImageValidator.validate(ProfileImageUpload(notAnImage, "image/png")),
        )
    }
}
