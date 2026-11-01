package com.ruk.ai.data.remote

import com.ruk.ai.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the shared OkHttp + Retrofit stack.
 *
 * The auth interceptor attaches the bearer token and, on a 401, performs a
 * single refresh attempt before giving up — so an expired access token does
 * not surface as a spurious error to the user.
 */
object NetworkModule {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }

    fun buildClient(
        tokenStore: TokenStore,
        onSessionExpired: () -> Unit
    ): OkHttpClient {
        val auth = Interceptor { chain ->
            val original = chain.request()
            val token = tokenStore.accessToken.value
            val isAuthRoute = original.url.encodedPath.contains("/auth/")
            val request = if (token.isNullOrBlank() || isAuthRoute) {
                original
            } else {
                original.newBuilder().header("Authorization", "Bearer $token").build()
            }
            val response = chain.proceed(request)
            if (response.code != 401 || token.isNullOrBlank() || isAuthRoute) {
                return@Interceptor response
            }

            // One refresh attempt, then replay the original request once.
            val refreshed = TokenRefresher.refresh(tokenStore)
            if (refreshed == null) {
                onSessionExpired()
                return@Interceptor response
            }
            response.close()
            chain.proceed(
                original.newBuilder()
                    .header("Authorization", "Bearer $refreshed")
                    .build()
            )
        }

        val logging = HttpLoggingInterceptor().apply {
            // BASIC only: request/response bodies can contain user prompts,
            // documents and provider keys, so they are never logged.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)   // long streaming answers
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(240, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun buildApi(client: OkHttpClient): RukApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(RukApi::class.java)
    }
}

/**
 * Performs the refresh call with a bare client so the auth interceptor
 * cannot recurse into itself.
 */
private object TokenRefresher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun refresh(tokenStore: TokenStore): String? {
        val refreshToken = tokenStore.refreshToken ?: return null
        val payload = JsonObject(
            mapOf("refresh_token" to kotlinx.serialization.json.JsonPrimitive(refreshToken))
        ).toString()

        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/v1/auth/refresh")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val text = response.body?.string() ?: return null
                val obj = NetworkModule.json.parseToJsonElement(text) as? JsonObject ?: return null
                val access = obj["access_token"]?.jsonPrimitive?.content
                val newRefresh = obj["refresh_token"]?.jsonPrimitive?.content
                if (access.isNullOrBlank()) return null
                tokenStore.updateAccess(access)
                if (!newRefresh.isNullOrBlank()) tokenStore.refreshToken = newRefresh
                access
            }
        }.getOrNull()
    }
}
