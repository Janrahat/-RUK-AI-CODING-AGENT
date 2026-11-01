package com.ruk.ai.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ruk.ai.R
import com.ruk.ai.ui.theme.PurpleGradient
import com.ruk.ai.ui.theme.RukColors

/** The RUK AI mark, loaded from the launcher asset extracted from the brand sheet. */
@Composable
fun RukLogo(size: Int = 40, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.ruk_logo),
        contentDescription = "RUK AI logo",
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
    )
}

/** Rounded dark card with an optional glowing purple border. */
@Composable
fun RukCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val base = Modifier
        .clip(shape)
        .background(RukColors.Surface)
        .then(
            if (glow) Modifier.border(
                BorderStroke(1.dp, Brush.linearGradient(listOf(RukColors.Purple, RukColors.PurpleLight))),
                shape
            ) else Modifier.border(BorderStroke(1.dp, RukColors.SurfaceBorder), shape)
        )
    Box(modifier = if (onClick != null) base.clickable { onClick() } else base) { content() }
}

/** Full-width purple gradient button. */
@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(if (enabled) PurpleGradient else Brush.linearGradient(listOf(RukColors.SurfaceHigh, RukColors.SurfaceHigh)))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = RukColors.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = if (enabled) RukColors.White else RukColors.GreyDim,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/** Gold "Pro" pill used in headers and on the profile upsell. */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFFB8860B), RukColors.Gold)),
                    RoundedCornerShape(50)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = RukColors.Black,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "Pro",
                color = RukColors.Black,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Screen header: optional back arrow, title, optional trailing content. */
@Composable
fun RukTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Go back",
                    tint = RukColors.White
                )
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = RukColors.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = RukColors.Grey,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

/** Three pulsing dots used while waiting for the first streamed token. */
@Composable
fun TypingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 180),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(7.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(RukColors.PurpleLight)
            )
        }
    }
}

/** Inline error strip with an optional retry action. */
@Composable
fun ErrorBanner(message: String, onRetry: (() -> Unit)? = null) {
    Surface(
        color = RukColors.Error.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                message,
                color = RukColors.Error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            if (onRetry != null) {
                Text(
                    "Retry",
                    color = RukColors.PurpleLight,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clickable { onRetry() }
                        .padding(start = 12.dp)
                )
            }
        }
    }
}

/** Small pill used for filters and selectors. */
@Composable
fun RukPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) RukColors.Purple else RukColors.SurfaceHigh)
            .border(
                BorderStroke(1.dp, if (selected) RukColors.PurpleLight else RukColors.SurfaceBorder),
                shape
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = RukColors.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            color = if (selected) RukColors.White else RukColors.Grey,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}

/** Empty-state block used by history, saved items and search results. */
@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = RukColors.GreyDim, modifier = Modifier.size(44.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = RukColors.White)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = RukColors.Grey,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
