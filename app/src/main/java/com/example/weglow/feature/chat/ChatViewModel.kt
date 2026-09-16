package com.example.weglow.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weglow.domain.model.ChatTurn
import com.example.weglow.domain.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One displayed bubble in the chat conversation. */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
)

data class ChatUiState(
    val messages: List<ChatMessage> = listOf(GREETING_MESSAGE),
    val isSending: Boolean = false,
    val errorMessage: String? = null,
)

/** How many conversation turns are replayed to the backend per request. */
private const val MAX_HISTORY_TURNS = 20

private val GREETING_MESSAGE = ChatMessage(
    text = "Hi! I'm WeGlow AI ✨\n\nHow can I help you with your beauty and skincare today?",
    isUser = false,
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun sendMessage(rawText: String) {
        val text = rawText.trim()

        if (text.isEmpty()) return
        if (_uiState.value.isSending) return

        val previous = _uiState.value.messages

        _uiState.value = ChatUiState(
            messages = previous + ChatMessage(text = text, isUser = true),
            isSending = true,
        )

        val history = buildHistory(previous)

        viewModelScope.launch {
            chatRepository.sendMessage(
                message = text,
                history = history,
            ).onSuccess { reply ->
                _uiState.value = ChatUiState(
                    messages = _uiState.value.messages + ChatMessage(
                        text = reply,
                        isUser = false,
                    ),
                )
            }.onFailure { error ->
                _uiState.value = ChatUiState(
                    messages = _uiState.value.messages,
                    errorMessage = error.message
                        ?: "WeGlow AI is unavailable. Please try again."
                )
            }
        }
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Turns already-exchanged bubbles into backend history turns. The static
     * greeting bubble is not a real model turn and is excluded. Only the most
     * recent [MAX_HISTORY_TURNS] turns are sent, matching the backend cap.
     */
    private fun buildHistory(messages: List<ChatMessage>): List<ChatTurn> {
        val exchanged = messages.drop(1)
        val start = (exchanged.size - MAX_HISTORY_TURNS).coerceAtLeast(0)

        return exchanged.drop(start).map { message ->
            ChatTurn(
                role = if (message.isUser) "user" else "model",
                text = message.text,
            )
        }
    }
}