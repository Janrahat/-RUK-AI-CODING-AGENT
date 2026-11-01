package com.ruk.ai.data.remote

import com.ruk.ai.data.model.ChatMessageDto
import com.ruk.ai.data.model.ChatRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/** Events emitted while a chat answer streams in. */
sealed interface ChatStreamEvent {
    data class Meta(val conversationId: String, val model: String, val provider: String) : ChatStreamEvent
    data class Delta(val text: String) : ChatStreamEvent
    data class Done(val conversationId: String) : ChatStreamEvent
    data class Failure(val message: String) : ChatStreamEvent
}

/**
 * Server-Sent Events client for `POST /api/v1/chat/completions`.
 *
 * OkHttp's SSE support is not used directly because the endpoint is a POST
 * with a JSON body; the stream is parsed by hand, which also keeps the
 * dependency surface small.
 */
class ChatStreamClient(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val json: Json
) {

    fun stream(request: ChatRequest, token: String?): Flow<ChatStreamEvent> = callbackFlow {
        val body = json.encodeToString(ChatRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())

        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/api/v1/chat/completions")
            .header("Accept", "text/event-stream")
            .post(body)
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        val call = client.newCall(builder.build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                trySend(ChatStreamEvent.Failure(e.message ?: "Network error"))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    if (!res.isSuccessful) {
                        val detail = res.body?.string().orEmpty()
                        trySend(ChatStreamEvent.Failure(parseError(res.code, detail)))
                        close()
                        return
                    }
                    val source = res.body?.source() ?: run {
                        trySend(ChatStreamEvent.Failure("Empty response body"))
                        close()
                        return
                    }
                    var event = ""
                    try {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            when {
                                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                                line.startsWith("data:") -> {
                                    val payload = line.removePrefix("data:").trim()
                                    if (payload.isNotEmpty()) {
                                        handleEvent(event, payload)?.let { trySend(it) }
                                    }
                                }
                                // Blank line terminates an SSE frame.
                                line.isBlank() -> event = ""
                            }
                        }
                    } catch (e: IOException) {
                        trySend(ChatStreamEvent.Failure(e.message ?: "Stream interrupted"))
                    }
                    close()
                }
            }
        })

        awaitClose { call.cancel() }
    }

    private fun handleEvent(event: String, payload: String): ChatStreamEvent? {
        val obj = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
            ?: return null
        return when (event) {
            "meta" -> ChatStreamEvent.Meta(
                conversationId = obj["conversation_id"]?.jsonPrimitive?.content.orEmpty(),
                model = obj["model"]?.jsonPrimitive?.content.orEmpty(),
                provider = obj["provider"]?.jsonPrimitive?.content.orEmpty()
            )
            "delta" -> ChatStreamEvent.Delta(obj["text"]?.jsonPrimitive?.content.orEmpty())
            "done" -> ChatStreamEvent.Done(
                obj["conversation_id"]?.jsonPrimitive?.content.orEmpty()
            )
            "error" -> ChatStreamEvent.Failure(
                obj["message"]?.jsonPrimitive?.content ?: "The model returned an error."
            )
            else -> null
        }
    }

    private fun parseError(code: Int, body: String): String {
        val detail = runCatching {
            (json.parseToJsonElement(body) as? JsonObject)
                ?.get("detail")?.jsonPrimitive?.content
        }.getOrNull()
        return when {
            !detail.isNullOrBlank() -> detail
            code == 401 -> "Your session expired. Please sign in again."
            code == 402 -> "Daily limit reached. Upgrade to Pro for more messages."
            code == 429 -> "Too many requests. Please wait a moment."
            else -> "Request failed (HTTP $code)."
        }
    }
}

/** Convenience for building the request body from a plain message list. */
fun chatRequest(
    model: String,
    messages: List<ChatMessageDto>,
    conversationId: String? = null,
    provider: String? = null
) = ChatRequest(
    conversationId = conversationId,
    model = model,
    provider = provider,
    messages = messages,
    stream = true
)
