package com.example.weglow.domain.model

/**
 * One archived conversational turn replayed to the AI backend so that the
 * model has context from earlier in the conversation. [role] is either
 * "user" (the person chatting) or "model" (a previous WEGlow AI reply).
 * Fields intentionally mirror the backend's `ChatTurn` request model —
 * this is a backend-agnostic view of a conversational exchange.
 */
data class ChatTurn(
    val role: String,
    val text: String,
)