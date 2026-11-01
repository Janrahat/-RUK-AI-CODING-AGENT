package com.ruk.ai.data.repository

import com.ruk.ai.data.model.ApiKeyDto
import com.ruk.ai.data.model.ApiKeyRequest
import com.ruk.ai.data.model.KeyTestDto
import com.ruk.ai.data.model.UsageSummaryDto
import com.ruk.ai.data.remote.RukApi
import com.ruk.ai.data.remote.TokenStore

class ProfileRepository(
    private val api: RukApi,
    private val tokenStore: TokenStore
) {

    suspend fun usage(): Result<UsageSummaryDto> = runCatchingResult { api.usage() }

    suspend fun apiKeys(): Result<List<ApiKeyDto>> = runCatchingResult { api.apiKeys() }

    suspend fun saveKey(provider: String, key: String, baseUrl: String?): Result<ApiKeyDto> =
        runCatchingResult { api.saveApiKey(ApiKeyRequest(provider, key, baseUrl)) }

    suspend fun deleteKey(provider: String): Result<Unit> =
        runCatchingResult { api.deleteApiKey(provider) }

    suspend fun testKey(provider: String): Result<KeyTestDto> =
        runCatchingResult { api.testApiKey(provider) }

    fun displayName(): String = tokenStore.displayName ?: "RUK AI user"

    fun email(): String = tokenStore.email ?: ""

    fun plan(): String = tokenStore.plan
}
