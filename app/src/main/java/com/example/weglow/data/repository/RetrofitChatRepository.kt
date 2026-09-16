package com.example.weglow.data.repository

import com.example.weglow.data.remote.chat.ChatApi
import com.example.weglow.data.remote.chat.ChatRequest
import com.example.weglow.data.remote.chat.ChatTurn
import com.example.weglow.domain.model.ChatTurn as DomainChatTurn
import com.example.weglow.domain.repository.ChatRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.SupabaseClient

/**
 * Talks to the WEGlow AI chat backend (hosted Supabase Edge Function) for the
 * authenticated user.
 *
 * The caller's Supabase access token is attached as `Authorization: Bearer`
 * so the endpoint can verify the session and personalize replies from the
 * user's own profile row (and, server-side, their routine/scan/catalog data).
 */
class RetrofitChatRepository(
    private val apiProvider: () -> ChatApi,
    private val supabaseClient: SupabaseClient,
) : ChatRepository {

    private val api: ChatApi by lazy {
        apiProvider()
    }

    override suspend fun sendMessage(
        message: String,
        history: List<DomainChatTurn>,
    ): Result<String> = runCatching {

        val accessToken = supabaseClient.auth.currentAccessTokenOrNull()

        val response = api.sendMessage(
            authorization = "Bearer $accessToken",
            request = ChatRequest(
                message = message,
                history = history.map { turn ->
                    ChatTurn(
                        role = turn.role,
                        text = turn.text,
                    )
                },
            ),
        )

        response.response.trim()
    }
}