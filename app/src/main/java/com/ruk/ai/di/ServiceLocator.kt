package com.ruk.ai.di

import android.content.Context
import com.ruk.ai.BuildConfig
import com.ruk.ai.data.remote.ChatStreamClient
import com.ruk.ai.data.remote.NetworkModule
import com.ruk.ai.data.remote.RukApi
import com.ruk.ai.data.remote.TokenStore
import com.ruk.ai.data.repository.AuthRepository
import com.ruk.ai.data.repository.ChatRepository
import com.ruk.ai.data.repository.CodeRepository
import com.ruk.ai.data.repository.GenerationRepository
import com.ruk.ai.data.repository.PdfRepository
import com.ruk.ai.data.repository.ProfileRepository
import com.ruk.ai.data.repository.ToolsRepository
import okhttp3.OkHttpClient

/**
 * Minimal hand-rolled service locator.
 *
 * A DI framework would add build complexity for a graph this small; every
 * dependency is a singleton created once in [init].
 */
object ServiceLocator {

    lateinit var tokenStore: TokenStore
        private set
    lateinit var api: RukApi
        private set
    lateinit var httpClient: OkHttpClient
        private set
    lateinit var chatStream: ChatStreamClient
        private set

    lateinit var authRepository: AuthRepository
        private set
    lateinit var chatRepository: ChatRepository
        private set
    lateinit var generationRepository: GenerationRepository
        private set
    lateinit var codeRepository: CodeRepository
        private set
    lateinit var pdfRepository: PdfRepository
        private set
    lateinit var toolsRepository: ToolsRepository
        private set
    lateinit var profileRepository: ProfileRepository
        private set

    /** Invoked when a refresh fails, so the UI can return to sign-in. */
    var onSessionExpired: () -> Unit = {}

    private var initialised = false

    fun init(context: Context) {
        if (initialised) return
        initialised = true

        tokenStore = TokenStore(context.applicationContext)
        httpClient = NetworkModule.buildClient(tokenStore) { onSessionExpired() }
        api = NetworkModule.buildApi(httpClient)
        chatStream = ChatStreamClient(
            client = httpClient,
            baseUrl = BuildConfig.API_BASE_URL,
            json = NetworkModule.json
        )

        authRepository = AuthRepository(api, tokenStore)
        chatRepository = ChatRepository(api, chatStream, tokenStore)
        generationRepository = GenerationRepository(api)
        codeRepository = CodeRepository(api)
        pdfRepository = PdfRepository(api)
        toolsRepository = ToolsRepository(api)
        profileRepository = ProfileRepository(api, tokenStore)
    }
}
