package com.example.weglow.core.auth

import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.AuthenticationState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Owns work for one account generation, including logout followed by login to the same account. */
class AccountWork(
    private val auth: AuthRepository,
    private val parent: CoroutineScope,
    private val resetState: () -> Unit,
) {
    private var owner = auth.currentUserId()
    private var generation = 0L
    private var work = SupervisorJob(parent.coroutineContext[Job])

    init {
        parent.launch {
            auth.authenticationState.collect { state ->
                if (state != AuthenticationState.INITIALIZING) {
                    val next = if (state == AuthenticationState.AUTHENTICATED) auth.currentUserId() else null
                    if (next != owner) clear()
                }
            }
        }
    }

    fun clear() {
        generation++
        work.cancel()
        work = SupervisorJob(parent.coroutineContext[Job])
        owner = auth.currentUserId().takeIf { auth.hasActiveSession() }
        resetState()
    }

    fun launch(block: suspend Request.() -> Unit): Job {
        val userId = auth.currentUserId()
        if (owner != userId) clear()
        val request = Request(userId, generation)
        return CoroutineScope(parent.coroutineContext + work).launch {
            request.checkCurrent()
            request.block()
        }
    }

    inner class Request internal constructor(private val userId: String?, private val version: Long) {
        suspend fun checkCurrent() {
            currentCoroutineContext().ensureActive()
            if (userId == null || !auth.hasActiveSession() || auth.currentUserId() != userId || version != generation) {
                throw CancellationException("Account changed")
            }
        }

        /** Recheck even when a repository catches CancellationException inside Result. */
        suspend fun <T> await(operation: suspend () -> T): T {
            checkCurrent()
            val result = operation()
            checkCurrent()
            return result
        }
    }
}
