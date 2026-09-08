package com.example.weglow.domain.repository

/** Contract consumed by future authentication presentation/domain code. */
interface AuthRepository {
    suspend fun signUp(email: String, password: String): Result<Unit>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
    fun currentUserId(): String?
    fun hasActiveSession(): Boolean
}
