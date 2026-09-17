package com.example.weglow.data.remote.chat

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit interface for the WEGlow AI chat backend.
 *
 * The backend is a hosted Supabase Edge Function
 * (`supabase/functions/chat`, via `.../functions/v1/chat/message`). Its
 * request/response shape mirrors the original FastAPI server's
 * `ChatRequest`/`ChatResponse` pydantic models.
 */
interface ChatApi {

    /**
     * @param authorization the caller's Supabase access token as
     *   `Bearer <token>`. The function verifies it against Supabase so it
     *   can load the user's profile for personalization.
     * @param request the new message plus the trimmed conversation history.
     */
    @POST("chat/message")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest,
    ): ChatResponse
}

data class ChatTurn(
    val role: String,
    val text: String,
)

data class ChatRequest(
    val message: String,
    val history: List<ChatTurn>,
)

data class ChatResponse(
    val response: String,
)