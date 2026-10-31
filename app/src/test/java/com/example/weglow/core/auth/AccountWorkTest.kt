package com.example.weglow.core.auth

import com.example.weglow.security.SecurityTestAuth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountWorkTest {
    @Test fun lateResponseCannotPublishAfterAccountSwitchEvenIfCancellationIsSwallowed() = runTest {
        val auth = SecurityTestAuth()
        val gate = CompletableDeferred<String>()
        var visible: String? = null
        val work = AccountWork(auth, backgroundScope) { visible = null }
        work.launch { visible = await { withContext(NonCancellable) { gate.await() } } }
        runCurrent()
        auth.switchTo("bob")
        runCurrent()
        gate.complete("alice's private response")
        runCurrent()
        assertNull(visible)
        work.launch { visible = await { "bob's response" } }
        runCurrent()
        assertEquals("bob's response", visible)
    }

    @Test fun logoutAndLoginToSameAccountStillInvalidatesPreviousGeneration() = runTest {
        val auth = SecurityTestAuth()
        val gate = CompletableDeferred<String>()
        var visible: String? = null
        val work = AccountWork(auth, backgroundScope) { visible = null }
        work.launch { visible = await { withContext(NonCancellable) { gate.await() } } }
        runCurrent()
        auth.switchTo(null)
        runCurrent()
        auth.switchTo("alice")
        runCurrent()
        gate.complete("old session")
        runCurrent()
        assertNull(visible)
    }
}
