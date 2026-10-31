package com.example.weglow.security

import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.AuthenticationState
import kotlinx.coroutines.flow.MutableSharedFlow

class SecurityTestAuth(var user: String? = "alice") : AuthRepository {
    private val events = MutableSharedFlow<AuthenticationState>(replay = 1, extraBufferCapacity = 10)
    override val authenticationState = events
    init { switchTo(user) }
    fun switchTo(id: String?) {
        user = id
        events.tryEmit(if (id == null) AuthenticationState.NOT_AUTHENTICATED else AuthenticationState.AUTHENTICATED)
    }
    override suspend fun signUp(email: String, password: String) = Result.success(Unit)
    override suspend fun signIn(email: String, password: String) = Result.success(Unit).also { switchTo("alice") }
    override suspend fun signInWithGoogle() = Result.success(Unit)
    override suspend fun signOut() = Result.success(Unit).also { switchTo(null) }
    override fun currentUserId() = user
    override fun hasActiveSession() = user != null
    override fun currentUserDisplayName(): String? = null
    override fun currentUserEmail(): String? = null
}
