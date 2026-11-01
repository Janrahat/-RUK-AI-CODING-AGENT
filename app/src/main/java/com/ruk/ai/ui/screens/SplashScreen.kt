package com.ruk.ai.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ruk.ai.ui.components.RukLogo
import com.ruk.ai.ui.theme.RukColors
import kotlinx.coroutines.delay

/**
 * Animated startup screen: the logo scales and fades in over a purple glow,
 * then the caller navigates on.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.7f,
        animationSpec = tween(700),
        label = "logoScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(700),
        label = "logoAlpha"
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(1400)
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(RukColors.PurpleDeep.copy(alpha = 0.55f), RukColors.Black),
                    radius = 900f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            RukLogo(size = 132, modifier = Modifier.scale(scale).alpha(alpha))
            Spacer(Modifier.height(22.dp))
            Text(
                "RUK AI",
                style = MaterialTheme.typography.displayLarge,
                color = RukColors.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(alpha)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "All-in-One AI Assistant",
                style = MaterialTheme.typography.bodyMedium,
                color = RukColors.Grey,
                modifier = Modifier.alpha(alpha)
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "Chat \u2022 Create \u2022 Generate \u2022 Code \u2022 Analyze",
                style = MaterialTheme.typography.labelSmall,
                color = RukColors.PurpleLight,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(alpha)
                    .padding(horizontal = 24.dp)
            )
        }
    }
}
