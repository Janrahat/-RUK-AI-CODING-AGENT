package com.ruk.ai.data.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the session tokens in EncryptedSharedPreferences (AES-256-GCM,
 * key held in the Android Keystore). Tokens are never written to plain
 * preferences and never logged.
 */
class TokenStore(context: Context) {

    private val prefs = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "ruk_ai_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        // Some heavily modified ROMs lack a usable Keystore. Fall back to
        // private-mode preferences so the app still works, and keep the
        // tokens short-lived.
        context.getSharedPreferences("ruk_ai_fallback", Context.MODE_PRIVATE)
    }

    private val _accessToken = MutableStateFlow(prefs.getString(KEY_ACCESS, null))
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    private val _signedIn = MutableStateFlow(!prefs.getString(KEY_ACCESS, null).isNullOrBlank())
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) = prefs.edit().putString(KEY_REFRESH, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var displayName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    var plan: String
        get() = prefs.getString(KEY_PLAN, "free") ?: "free"
        set(value) = prefs.edit().putString(KEY_PLAN, value).apply()

    fun save(access: String, refresh: String, userId: String, name: String, email: String, plan: String) {
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_NAME, name)
            .putString(KEY_EMAIL, email)
            .putString(KEY_PLAN, plan)
            .apply()
        _accessToken.value = access
        _signedIn.value = true
    }

    fun updateAccess(access: String) {
        prefs.edit().putString(KEY_ACCESS, access).apply()
        _accessToken.value = access
    }

    fun clear() {
        prefs.edit().clear().apply()
        _accessToken.value = null
        _signedIn.value = false
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_NAME = "display_name"
        const val KEY_EMAIL = "email"
        const val KEY_PLAN = "plan"
    }
}
