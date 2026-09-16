package com.example.weglow.domain.repository

import com.example.weglow.domain.model.ChatTurn

/**
 * Conversations with the WEGlow AI chat backend.
 *
 * Implementations are responsible for transport details, including attaching
 * the caller's authenticated session so the backend can personalize replies
 * from the user's WEGlow profile.
 */
interface ChatRepository {

    /**
     * Sends a new [message] together with the prior [history] of the
     * conversation. Returns the assistant's reply text on success.
     */
    suspend fun sendMessage(
        message: String,
        history: List<ChatTurn>,
    ): Result<String>
}