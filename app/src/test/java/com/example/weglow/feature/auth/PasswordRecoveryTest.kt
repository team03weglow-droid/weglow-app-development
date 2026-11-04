package com.example.weglow.feature.auth

import com.example.weglow.core.config.AppConfig

import org.junit.Assert.*
import org.junit.Test

class PasswordRecoveryTest {
    private val hash = "a".repeat(64)

    @Test
    fun acceptsOnlyDedicatedRecoveryTokenLinks() {
        assertEquals(hash, PasswordRecovery.tokenHash("${AppConfig.authScheme}://password-reset?token_hash=$hash&type=recovery"))
        assertEquals(hash, PasswordRecovery.tokenHash("${AppConfig.authScheme}://password-reset?type=recovery&token_hash=$hash"))
    }

    @Test
    fun rejectsLoginCallbacksAmbiguousAndMalformedLinks() {
        listOf(
            "${AppConfig.authScheme}://login-callback?token_hash=$hash&type=recovery",
            "${AppConfig.authScheme}://password-reset?token_hash=$hash&type=signup",
            "${AppConfig.authScheme}://password-reset?token_hash=$hash&type=magiclink",
            "${AppConfig.authScheme}://password-reset?code=$hash&type=recovery",
            "${AppConfig.authScheme}://password-reset?token_hash=$hash&type=recovery&type=recovery",
            "${AppConfig.authScheme}://password-reset?token_hash=$hash&type=recovery&access_token=secret",
            "${AppConfig.authScheme}://password-reset?token_hash=$hash&type=recovery#access_token=secret",
            "${AppConfig.authScheme}://password-reset/path?token_hash=$hash&type=recovery",
            "${AppConfig.authScheme}://password-reset?token_hash=&type=recovery",
            "${AppConfig.authScheme}://password-reset?token_hash=%ZZ&type=recovery",
            "${AppConfig.authScheme}://password-reset?token_hash=${"a".repeat(4096)}&type=recovery",
            "https://password-reset?token_hash=$hash&type=recovery",
            "${AppConfig.authScheme}://user@password-reset?token_hash=$hash&type=recovery",
            "${AppConfig.authScheme}://password-reset:80?token_hash=$hash&type=recovery",
            "not a URI",
        ).forEach { assertNull(PasswordRecovery.tokenHash(it)) }
    }
}
