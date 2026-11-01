package com.ruk.ai.data.repository

import com.ruk.ai.data.model.ChatMessageDto
import com.ruk.ai.data.model.ConversationDetailDto
import com.ruk.ai.data.model.ConversationDto
import com.ruk.ai.data.model.ModelDto
import com.ruk.ai.data.remote.ChatStreamClient
import com.ruk.ai.data.remote.ChatStreamEvent
import com.ruk.ai.data.remote.RukApi
import com.ruk.ai.data.remote.TokenStore
import com.ruk.ai.data.remote.chatRequest
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val api: RukApi,
    private val streamClient: ChatStreamClient,
    private val tokenStore: TokenStore
) {

    suspend fun models(): Result<List<ModelDto>> = runCatchingResult { api.models("chat") }

    suspend fun conversations(): Result<List<ConversationDto>> =
        runCatchingResult { api.conversations() }

    suspend fun conversation(id: String): Result<ConversationDetailDto> =
        runCatchingResult { api.conversation(id) }

    suspend fun deleteConversation(id: String): Result<Unit> =
        runCatchingResult { api.deleteConversation(id) }

    suspend fun regenerate(conversationId: String, model: String): Result<Unit> =
        runCatchingResult { api.regenerate(conversationId, model); Unit }

    /**
     * Streams an answer. The caller collects [ChatStreamEvent]s and appends
     * [ChatStreamEvent.Delta] text to the visible message.
     */
    fun stream(
        model: String,
        messages: List<ChatMessageDto>,
        conversationId: String?,
        provider: String? = null
    ): Flow<ChatStreamEvent> = streamClient.stream(
        request = chatRequest(model, messages, conversationId, provider),
        token = tokenStore.accessToken.value
    )
}
