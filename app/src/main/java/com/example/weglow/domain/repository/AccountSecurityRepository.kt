package com.example.weglow.domain.repository

interface AccountSecurityRepository {
    suspend fun requestPasswordRecovery(email: String): Result<Unit>
    suspend fun resetPassword(email: String, code: String, password: String): Result<Unit>
    /** Returns the deleted identity so device-local data can be removed after session cleanup. */
    suspend fun deleteAccount(): Result<String>
}
