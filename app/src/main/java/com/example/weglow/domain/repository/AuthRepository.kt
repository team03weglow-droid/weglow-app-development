package com.example.weglow.domain.repository

import kotlinx.coroutines.flow.Flow

enum class AuthenticationState {
    INITIALIZING,
    AUTHENTICATED,
    NOT_AUTHENTICATED,
}

interface AuthRepository {

    val authenticationState: Flow<AuthenticationState>

    suspend fun signUp(
        email: String,
        password: String,
    ): Result<Unit>

    /** Keep the name with Auth signup when confirmation delays profile creation. */
    suspend fun signUpWithName(fullName: String, email: String, password: String): Result<Unit> =
        signUp(email, password)

    suspend fun signIn(
        email: String,
        password: String,
    ): Result<Unit>

    suspend fun signInWithGoogle(): Result<Unit>

    suspend fun signOut(): Result<Unit>

    fun currentUserId(): String?

    fun hasActiveSession(): Boolean

    /**
     * Best-effort display name taken from the authenticated identity provider's
     * user metadata (e.g. Google or the name recorded at signup). Null when
     * unavailable; never use a cached anonymous form from another account.
     */
    fun currentUserDisplayName(): String?

    /**
     * The authenticated identity's own account email, straight from the auth
     * session (never a value supplied by the UI). Read-only display use only -
     * this repository has no email-change operation, since the existing
     * authentication architecture does not support changing it.
     */
    fun currentUserEmail(): String?
}
