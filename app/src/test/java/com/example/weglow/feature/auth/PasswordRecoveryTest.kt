package com.example.weglow.feature.auth

import org.junit.Assert.*
import org.junit.Test

class PasswordRecoveryTest {
    private val hash = "a".repeat(64)

    @Test
    fun acceptsOnlyDedicatedRecoveryTokenLinks() {
        assertEquals(hash, PasswordRecovery.tokenHash("weglow://password-reset?token_hash=$hash&type=recovery"))
        assertEquals(hash, PasswordRecovery.tokenHash("weglow://password-reset?type=recovery&token_hash=$hash"))
    }

    @Test
    fun rejectsLoginCallbacksAmbiguousAndMalformedLinks() {
        listOf(
            "weglow://login-callback?token_hash=$hash&type=recovery",
            "weglow://password-reset?token_hash=$hash&type=signup",
            "weglow://password-reset?token_hash=$hash&type=magiclink",
            "weglow://password-reset?code=$hash&type=recovery",
            "weglow://password-reset?token_hash=$hash&type=recovery&type=recovery",
            "weglow://password-reset?token_hash=$hash&type=recovery&access_token=secret",
            "weglow://password-reset?token_hash=$hash&type=recovery#access_token=secret",
            "weglow://password-reset/path?token_hash=$hash&type=recovery",
            "weglow://password-reset?token_hash=&type=recovery",
            "weglow://password-reset?token_hash=%ZZ&type=recovery",
            "weglow://password-reset?token_hash=${"a".repeat(4096)}&type=recovery",
            "https://password-reset?token_hash=$hash&type=recovery",
            "weglow://user@password-reset?token_hash=$hash&type=recovery",
            "weglow://password-reset:80?token_hash=$hash&type=recovery",
            "not a URI",
        ).forEach { assertNull(PasswordRecovery.tokenHash(it)) }
    }
}
