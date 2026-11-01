package com.ruk.ai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------- auth

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    @SerialName("display_name") val displayName: String = ""
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class GoogleLoginRequest(@SerialName("id_token") val idToken: String)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    @SerialName("display_name") val displayName: String = "",
    val plan: String = "free",
    @SerialName("created_at") val createdAt: String = ""
) {
    val isPro: Boolean get() = plan.equals("pro", ignoreCase = true)
}

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    val user: UserDto
)

@Serializable
data class TokenPair(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String = "bearer"
)

// ---------------------------------------------------------------- chat

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequest(
    @SerialName("conversation_id") val conversationId: String? = null,
    val model: String,
    val provider: String? = null,
    val messages: List<ChatMessageDto>,
    val stream: Boolean = true,
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int = 2048
)

@Serializable
data class MessageDto(
    val id: String,
    val role: String,
    val content: String,
    val model: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class ConversationDto(
    val id: String,
    val title: String,
    val model: String = "",
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
data class ConversationDetailDto(
    val id: String,
    val title: String,
    val model: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    val messages: List<MessageDto> = emptyList()
)

@Serializable
data class ModelDto(
    val id: String,
    val label: String,
    val provider: String,
    val kind: String = "chat",
    @SerialName("pro_only") val proOnly: Boolean = false
)

// ---------------------------------------------------------- generations

@Serializable
data class ImageRequest(
    val prompt: String,
    @SerialName("aspect_ratio") val aspectRatio: String = "1:1",
    val quality: String = "standard",
    val model: String = "gpt-image-1",
    val n: Int = 1
)

@Serializable
data class VideoRequest(
    val prompt: String,
    @SerialName("aspect_ratio") val aspectRatio: String = "16:9",
    @SerialName("duration_seconds") val durationSeconds: Int = 5,
    @SerialName("image_url") val imageUrl: String? = null
)

@Serializable
data class GenerationDto(
    val id: String,
    val kind: String,
    val status: String,
    val prompt: String = "",
    @SerialName("aspect_ratio") val aspectRatio: String = "1:1",
    val quality: String = "standard",
    @SerialName("asset_url") val assetUrl: String? = null,
    val error: String? = null,
    @SerialName("created_at") val createdAt: String = ""
) {
    val isDone: Boolean get() = status == "completed"
    val isFailed: Boolean get() = status == "failed"
    val isRunning: Boolean get() = status == "pending" || status == "running"
}

// ----------------------------------------------------------------- code

@Serializable
data class CodeRequest(
    val language: String,
    val code: String,
    val stdin: String = ""
)

@Serializable
data class CodeRunResult(
    val status: String,
    @SerialName("exit_code") val exitCode: Int? = null,
    val stdout: String = "",
    val stderr: String = "",
    @SerialName("duration_ms") val durationMs: Int = 0,
    val truncated: Boolean = false
)

@Serializable
data class CodeAssistRequest(
    val language: String,
    val code: String = "",
    val instruction: String = "",
    val action: String = "explain"
)

@Serializable
data class CodeAssistResponse(
    val action: String,
    val language: String,
    val answer: String,
    val model: String = ""
)

@Serializable
data class LanguagesDto(
    val editor: List<String> = emptyList(),
    val executable: Map<String, Boolean> = emptyMap(),
    @SerialName("exec_enabled") val execEnabled: Boolean = false
)

// ------------------------------------------------------------------ pdf

@Serializable
data class DocumentDto(
    val id: String,
    val filename: String,
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    @SerialName("page_count") val pageCount: Int = 0,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class PdfAskRequest(val question: String, val model: String = "gpt-4o-mini")

@Serializable
data class PdfAnswerDto(
    @SerialName("document_id") val documentId: String,
    val question: String = "",
    val answer: String
)

@Serializable
data class PdfSummaryDto(
    @SerialName("document_id") val documentId: String,
    val summary: String
)

// ---------------------------------------------------------------- tools

@Serializable
data class ToolDto(
    val id: String,
    val label: String,
    val description: String = "",
    val options: List<String> = emptyList()
)

@Serializable
data class ToolRequest(
    val tool: String,
    val input: String,
    val options: Map<String, String> = emptyMap(),
    val model: String = "gpt-4o-mini"
)

@Serializable
data class ToolResponse(
    val tool: String,
    val output: String,
    val model: String = ""
)

// ------------------------------------------------------------- api keys

@Serializable
data class ApiKeyRequest(
    val provider: String,
    @SerialName("api_key") val apiKey: String,
    @SerialName("base_url") val baseUrl: String? = null
)

@Serializable
data class ApiKeyDto(
    val provider: String,
    @SerialName("key_hint") val keyHint: String = "",
    @SerialName("base_url") val baseUrl: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("last_used_at") val lastUsedAt: String? = null
)

@Serializable
data class KeyTestDto(
    val ok: Boolean,
    val provider: String = "",
    val model: String = "",
    val reply: String = "",
    val detail: String = ""
)

// ---------------------------------------------------------------- usage

@Serializable
data class UsageSummaryDto(
    val plan: String = "free",
    @SerialName("messages_today") val messagesToday: Int = 0,
    @SerialName("daily_limit") val dailyLimit: Int = 0,
    @SerialName("images_this_month") val imagesThisMonth: Int = 0,
    @SerialName("videos_this_month") val videosThisMonth: Int = 0,
    @SerialName("total_tokens_in") val totalTokensIn: Long = 0,
    @SerialName("total_tokens_out") val totalTokensOut: Long = 0,
    @SerialName("remaining_today") val remainingToday: Int = 0
)

// --------------------------------------------------------------- errors

@Serializable
data class ApiErrorDto(val detail: String = "")
