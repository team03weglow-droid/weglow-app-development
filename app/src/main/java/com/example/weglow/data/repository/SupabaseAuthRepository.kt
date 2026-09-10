package com.example.weglow.data.repository

import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.AuthenticationState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SupabaseAuthRepository(
    private val client: SupabaseClient,
) : AuthRepository {

    override val authenticationState: Flow<AuthenticationState> =
        client.auth.sessionStatus.map { status ->
            when (status) {
                SessionStatus.Initializing ->
                    AuthenticationState.INITIALIZING

                is SessionStatus.Authenticated ->
                    AuthenticationState.AUTHENTICATED

                is SessionStatus.NotAuthenticated ->
                    AuthenticationState.NOT_AUTHENTICATED

                is SessionStatus.RefreshFailure ->
                    AuthenticationState.NOT_AUTHENTICATED
            }
        }

    override suspend fun signUp(
        email: String,
        password: String,
    ): Result<Unit> = runCatching {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        Unit
    }

    override suspend fun signIn(
        email: String,
        password: String,
    ): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        Unit
    }

    override suspend fun signInWithGoogle(): Result<Unit> = runCatching {
        client.auth.signInWith(Google)
        Unit
    }

    override suspend fun signOut(): Result<Unit> =
        runCatching {
            client.auth.signOut()
        }

    override fun currentUserId(): String? =
        client.auth.currentUserOrNull()?.id

    override fun hasActiveSession(): Boolean =
        client.auth.currentUserOrNull() != null
}