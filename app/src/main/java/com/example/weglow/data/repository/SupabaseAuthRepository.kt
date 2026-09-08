package com.example.weglow.data.repository

import com.example.weglow.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

class SupabaseAuthRepository(
    private val client: SupabaseClient,
) : AuthRepository {
    override suspend fun signUp(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        Unit
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        Unit
    }

    override suspend fun signOut(): Result<Unit> = runCatching { client.auth.signOut() }
    override fun currentUserId(): String? = client.auth.currentUserOrNull()?.id
    override fun hasActiveSession(): Boolean = client.auth.currentUserOrNull() != null
}
