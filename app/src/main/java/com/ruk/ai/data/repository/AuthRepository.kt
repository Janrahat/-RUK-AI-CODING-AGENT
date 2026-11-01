package com.ruk.ai.data.repository

import com.ruk.ai.data.model.AuthResponse
import com.ruk.ai.data.model.GoogleLoginRequest
import com.ruk.ai.data.model.LoginRequest
import com.ruk.ai.data.model.RegisterRequest
import com.ruk.ai.data.model.UserDto
import com.ruk.ai.data.remote.RukApi
import com.ruk.ai.data.remote.TokenStore
import kotlinx.coroutines.flow.StateFlow

/** Result wrapper so ViewModels never have to catch raw exceptions. */
sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Failure(val message: String) : Result<Nothing>
}

// `inline` lets suspend callers use this from a coroutine body. Because it is
// public-inline, `describeHttp` must not be private — hence @PublishedApi on it.
inline fun <T> runCatchingResult(block: () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: retrofit2.HttpException) {
    Result.Failure(describeHttp(e))
} catch (e: java.io.IOException) {
    Result.Failure("No connection. Check your network and try again.")
} catch (e: Exception) {
    Result.Failure(e.message ?: "Something went wrong.")
}

@PublishedApi
internal fun describeHttp(e: retrofit2.HttpException): String {
    val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
    val detail = body?.let {
        runCatching {
            (com.ruk.ai.data.remote.NetworkModule.json
                .parseToJsonElement(it) as? kotlinx.serialization.json.JsonObject)
                ?.get("detail")?.let { d -> d.toString().trim('"') }
        }.getOrNull()
    }
    return when {
        !detail.isNullOrBlank() -> detail
        e.code() == 401 -> "Email or password is incorrect."
        e.code() == 409 -> "An account with that email already exists."
        e.code() == 429 -> "Too many attempts. Please wait a moment."
        else -> "Request failed (HTTP ${e.code()})."
    }
}

class AuthRepository(
    private val api: RukApi,
    private val tokenStore: TokenStore
) {

    val signedIn: StateFlow<Boolean> = tokenStore.signedIn

    suspend fun register(email: String, password: String, name: String): Result<UserDto> =
        runCatchingResult {
            persist(api.register(RegisterRequest(email.trim(), password, name.trim())))
        }

    suspend fun login(email: String, password: String): Result<UserDto> =
        runCatchingResult {
            persist(api.login(LoginRequest(email.trim(), password)))
        }

    suspend fun google(idToken: String): Result<UserDto> =
        runCatchingResult {
            persist(api.googleLogin(GoogleLoginRequest(idToken)))
        }

    suspend fun me(): Result<UserDto> = runCatchingResult { api.me() }

    suspend fun upgrade(): Result<UserDto> = runCatchingResult {
        val user = api.upgrade()
        tokenStore.plan = user.plan
        user
    }

    fun signOut() = tokenStore.clear()

    fun currentName(): String = tokenStore.displayName ?: "RUK AI user"

    fun currentEmail(): String = tokenStore.email ?: ""

    fun currentPlan(): String = tokenStore.plan

    private fun persist(response: AuthResponse): UserDto {
        tokenStore.save(
            access = response.accessToken,
            refresh = response.refreshToken,
            userId = response.user.id,
            name = response.user.displayName,
            email = response.user.email,
            plan = response.user.plan
        )
        return response.user
    }
}
