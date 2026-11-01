package com.ruk.ai

import android.app.Application
import com.ruk.ai.di.ServiceLocator

/**
 * Application entry point. Builds the dependency graph once so every
 * ViewModel shares a single HTTP client and token store.
 */
class RukAiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
