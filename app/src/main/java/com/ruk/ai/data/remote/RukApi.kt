package com.ruk.ai.data.remote

import com.ruk.ai.data.model.ApiKeyDto
import com.ruk.ai.data.model.ApiKeyRequest
import com.ruk.ai.data.model.AuthResponse
import com.ruk.ai.data.model.ChatRequest
import com.ruk.ai.data.model.CodeAssistRequest
import com.ruk.ai.data.model.CodeAssistResponse
import com.ruk.ai.data.model.CodeRequest
import com.ruk.ai.data.model.CodeRunResult
import com.ruk.ai.data.model.ConversationDetailDto
import com.ruk.ai.data.model.ConversationDto
import com.ruk.ai.data.model.DocumentDto
import com.ruk.ai.data.model.GenerationDto
import com.ruk.ai.data.model.GoogleLoginRequest
import com.ruk.ai.data.model.ImageRequest
import com.ruk.ai.data.model.KeyTestDto
import com.ruk.ai.data.model.LanguagesDto
import com.ruk.ai.data.model.LoginRequest
import com.ruk.ai.data.model.ModelDto
import com.ruk.ai.data.model.PdfAnswerDto
import com.ruk.ai.data.model.PdfAskRequest
import com.ruk.ai.data.model.PdfSummaryDto
import com.ruk.ai.data.model.RefreshRequest
import com.ruk.ai.data.model.RegisterRequest
import com.ruk.ai.data.model.TokenPair
import com.ruk.ai.data.model.ToolDto
import com.ruk.ai.data.model.ToolRequest
import com.ruk.ai.data.model.ToolResponse
import com.ruk.ai.data.model.UsageSummaryDto
import com.ruk.ai.data.model.UserDto
import com.ruk.ai.data.model.VideoRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit description of the RUK AI backend. Every method maps to a real
 * route in backend/app/routers — nothing here is a stub.
 */
interface RukApi {

    // ------------------------------------------------------------- auth
    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/v1/auth/google")
    suspend fun googleLogin(@Body body: GoogleLoginRequest): AuthResponse

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenPair

    @GET("api/v1/auth/me")
    suspend fun me(): UserDto

    @POST("api/v1/auth/upgrade")
    suspend fun upgrade(): UserDto

    // ------------------------------------------------------------- chat
    @GET("api/v1/chat/models")
    suspend fun models(@Query("kind") kind: String = "chat"): List<ModelDto>

    @GET("api/v1/chat/conversations")
    suspend fun conversations(): List<ConversationDto>

    @GET("api/v1/chat/conversations/{id}")
    suspend fun conversation(@Path("id") id: String): ConversationDetailDto

    @DELETE("api/v1/chat/conversations/{id}")
    suspend fun deleteConversation(@Path("id") id: String)

    @POST("api/v1/chat/regenerate/{id}")
    suspend fun regenerate(
        @Path("id") id: String,
        @Query("model") model: String? = null
    ): Map<String, String>

    // ------------------------------------------------------ generations
    @GET("api/v1/generations")
    suspend fun generations(
        @Query("kind") kind: String? = null,
        @Query("limit") limit: Int = 30
    ): List<GenerationDto>

    @POST("api/v1/generations/image")
    suspend fun generateImage(@Body body: ImageRequest): GenerationDto

    @POST("api/v1/generations/video")
    suspend fun generateVideo(@Body body: VideoRequest): GenerationDto

    @GET("api/v1/generations/{id}")
    suspend fun generation(
        @Path("id") id: String,
        @Query("refresh") refresh: Boolean = false
    ): GenerationDto

    @DELETE("api/v1/generations/{id}")
    suspend fun deleteGeneration(@Path("id") id: String)

    // ------------------------------------------------------------- code
    @GET("api/v1/code/languages")
    suspend fun languages(): LanguagesDto

    @POST("api/v1/code/run")
    suspend fun runCode(@Body body: CodeRequest): CodeRunResult

    @POST("api/v1/code/assist")
    suspend fun codeAssist(@Body body: CodeAssistRequest): CodeAssistResponse

    // -------------------------------------------------------------- pdf
    @Multipart
    @POST("api/v1/pdf/upload")
    suspend fun uploadPdf(@Part file: MultipartBody.Part): DocumentDto

    @GET("api/v1/pdf/documents")
    suspend fun documents(): List<DocumentDto>

    @POST("api/v1/pdf/documents/{id}/summarize")
    suspend fun summarizePdf(@Path("id") id: String): PdfSummaryDto

    @POST("api/v1/pdf/documents/{id}/ask")
    suspend fun askPdf(@Path("id") id: String, @Body body: PdfAskRequest): PdfAnswerDto

    @DELETE("api/v1/pdf/documents/{id}")
    suspend fun deleteDocument(@Path("id") id: String)

    // ------------------------------------------------------------ tools
    @GET("api/v1/tools")
    suspend fun tools(): List<ToolDto>

    @POST("api/v1/tools/run")
    suspend fun runTool(@Body body: ToolRequest): ToolResponse

    // --------------------------------------------------------- api keys
    @GET("api/v1/keys")
    suspend fun apiKeys(): List<ApiKeyDto>

    @PUT("api/v1/keys")
    suspend fun saveApiKey(@Body body: ApiKeyRequest): ApiKeyDto

    @DELETE("api/v1/keys/{provider}")
    suspend fun deleteApiKey(@Path("provider") provider: String)

    @POST("api/v1/keys/{provider}/test")
    suspend fun testApiKey(@Path("provider") provider: String): KeyTestDto

    // ------------------------------------------------------------ usage
    @GET("api/v1/usage/summary")
    suspend fun usage(): UsageSummaryDto
}
