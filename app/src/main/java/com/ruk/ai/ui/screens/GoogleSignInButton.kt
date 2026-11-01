package com.ruk.ai.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.ruk.ai.BuildConfig
import com.ruk.ai.ui.theme.RukColors

/**
 * Google Sign-In.
 *
 * The device obtains an ID token from Google Play services and hands it to
 * the backend, which verifies it against Google's tokeninfo endpoint. No
 * client secret is stored in the app.
 *
 * If GOOGLE_WEB_CLIENT_ID was not supplied at build time the button reports
 * that clearly instead of failing silently.
 */
@Composable
fun GoogleSignInButton(
    enabled: Boolean,
    onToken: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val configured = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            onError("Google sign-in was cancelled.")
            return@rememberLauncherForActivityResult
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken.isNullOrBlank()) {
                onError("Google did not return an ID token.")
            } else {
                onToken(idToken)
            }
        } catch (e: ApiException) {
            onError("Google sign-in failed (code ${e.statusCode}).")
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(RukColors.Surface)
            .then(
                Modifier.border(
                    BorderStroke(1.dp, RukColors.SurfaceBorder),
                    RoundedCornerShape(16.dp)
                )
            )
            .clickable(enabled = enabled) {
                if (!configured) {
                    onError(
                        "Google sign-in is not configured for this build. " +
                            "Rebuild with -PrukGoogleClientId=<your web client id>."
                    )
                    return@clickable
                }
                val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .requestEmail()
                    .build()
                val client = GoogleSignIn.getClient(context, options)
                launcher.launch(client.signInIntent)
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            GoogleGlyph()
            Spacer(Modifier.width(10.dp))
            Text(
                "Continue with Google",
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) RukColors.White else RukColors.GreyDim,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** A small four-colour "G" drawn with Compose primitives. */
@Composable
private fun GoogleGlyph() {
    Box(
        Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "G",
            color = Color(0xFF4285F4),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
