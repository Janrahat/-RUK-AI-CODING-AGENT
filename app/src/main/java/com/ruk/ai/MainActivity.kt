package com.ruk.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ruk.ai.di.ServiceLocator
import com.ruk.ai.navigation.Routes
import com.ruk.ai.navigation.RukNavGraph
import com.ruk.ai.ui.theme.RukAiTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RukAiTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    color = Color.Black
                ) {
                    RukApp()
                }
            }
        }
    }
}

@Composable
private fun RukApp() {
    val signedIn by ServiceLocator.tokenStore.signedIn.collectAsStateWithLifecycle()
    var startRoute by remember { mutableStateOf<String?>(null) }

    // Choose the entry point once, after the encrypted token store is read.
    LaunchedEffect(signedIn) {
        if (startRoute == null) {
            startRoute = if (signedIn) Routes.HOME else Routes.AUTH
        }
    }

    // A failed token refresh clears the session so the UI returns to sign-in.
    LaunchedEffect(Unit) {
        ServiceLocator.onSessionExpired = {
            ServiceLocator.tokenStore.clear()
        }
    }

    startRoute?.let { RukNavGraph(startDestination = it) }
}
