package com.example.weglow.domain.repository

interface AccountSecurityRepository {
    suspend fun requestPasswordRecovery(email: String): Result<Unit>
    suspend fun verifyPasswordRecovery(tokenHash: String): Result<Unit>
    suspend fun resetPassword(password: String): Result<Unit>
    suspend fun cancelPasswordRecovery(): Result<Unit>
    /** Returns the deleted identity so device-local data can be removed after session cleanup. */
    suspend fun deleteAccount(): Result<String>
}
