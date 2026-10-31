package com.example.weglow.feature.chat

import com.example.weglow.domain.model.ChatTurn
import com.example.weglow.domain.repository.ChatRepository
import com.example.weglow.security.SecurityTestAuth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionIsolationTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun nextAccountNeverReceivesPreviousHistoryOrLateReply() = runTest(dispatcher) {
        val auth = SecurityTestAuth()
        val lateReply = CompletableDeferred<String>()
        val histories = mutableListOf<List<ChatTurn>>()
        val repository = object : ChatRepository {
            override suspend fun sendMessage(message: String, history: List<ChatTurn>): Result<String> {
                histories += history
                return Result.success(if (message == "private concern") withContext(NonCancellable) { lateReply.await() } else "hello bob")
            }
        }
        val vm = ChatViewModel(repository, auth)
        runCurrent()
        vm.sendMessage("private concern")
        runCurrent()
        auth.switchTo(null)
        runCurrent()
        auth.switchTo("bob")
        runCurrent()
        lateReply.complete("alice's private answer")
        runCurrent()
        assertEquals(1, vm.uiState.value.messages.size)
        assertFalse(vm.uiState.value.isSending)
        vm.sendMessage("hello")
        runCurrent()
        assertTrue(histories.last().isEmpty())
        assertFalse(vm.uiState.value.messages.any { it.text.contains("alice") || it.text.contains("private concern") })
    }
}
