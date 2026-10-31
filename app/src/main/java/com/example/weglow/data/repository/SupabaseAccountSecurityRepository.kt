package com.example.weglow.data.repository

import com.example.weglow.core.config.AppConfig
import com.example.weglow.domain.repository.AccountSecurityRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SupabaseAccountSecurityRepository(
    private val client: SupabaseClient,
    private val http: HttpClient = HttpClient(Android) {
        install(HttpTimeout) { requestTimeoutMillis = 90_000 }
    },
) : AccountSecurityRepository {
    override suspend fun requestPasswordRecovery(email: String): Result<Unit> = runCatching {
        client.auth.resetPasswordForEmail(email)
    }

    override suspend fun resetPassword(email: String, code: String, password: String): Result<Unit> = runCatching {
        // Recovery email template must display {{ .Token }}. A code proves email ownership.
        client.auth.verifyEmailOtp(OtpType.Email.RECOVERY, email, code)
        try {
            client.auth.updateUser { this.password = password }
            client.auth.signOut(SignOutScope.GLOBAL)
        } finally {
            // Never leave a recovery session active, including after an update/revocation failure.
            client.auth.clearSession()
        }
        Unit
    }

    override suspend fun deleteAccount(): Result<String> = runCatching {
        val userId = checkNotNull(client.auth.currentUserOrNull()?.id) { "Sign in to continue." }
        val token = checkNotNull(client.auth.currentAccessTokenOrNull()) { "Sign in to continue." }
        val response = http.post("${AppConfig.supabaseUrl.trimEnd('/')}/functions/v1/delete-account") {
            header("Authorization", "Bearer $token")
            header("apikey", AppConfig.supabasePublishableKey)
            header("Content-Type", "application/json")
            setBody("{\"confirmation\":\"DELETE\"}")
        }
        check(response.status.value in 200..299) {
            if (response.status.value == 403) "Sign out and sign in again before deleting your account."
            else "Could not complete account deletion. Please retry. Some account photos may already have been removed."
        }
        val deleted = Json.parseToJsonElement(response.bodyAsText()).jsonObject["deleted"]?.jsonPrimitive?.content
        check(deleted == "true") { "Account deletion was not confirmed. Please retry." }
        // The server has deleted the user. Do not depend on a second remote logout succeeding.
        client.auth.clearSession()
        userId
    }
}
