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

    suspend fun signIn(
        email: String,
        password: String,
    ): Result<Unit>

    suspend fun signInWithGoogle(): Result<Unit>

    suspend fun signOut(): Result<Unit>

    fun currentUserId(): String?

    fun hasActiveSession(): Boolean
}