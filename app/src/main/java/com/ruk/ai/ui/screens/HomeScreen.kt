package com.ruk.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ruk.ai.navigation.DashboardCard
import com.ruk.ai.navigation.Routes
import com.ruk.ai.ui.components.ProBadge
import com.ruk.ai.ui.components.RukLogo
import com.ruk.ai.ui.theme.RukColors

/**
 * Main dashboard, laid out to match the reference design:
 * header (menu / logo / Pro) → hero (logo, title, tagline, capabilities)
 * → ask-anything bar → 2x3 card grid.
 */
@Composable
fun HomeScreen(nav: NavController) {
    var query by remember { mutableStateOf("") }

    val cards = listOf(
        DashboardCard("AI Chat", "GPT, Gemini, etc.", Routes.CHAT, Icons.Filled.AutoAwesome, RukColors.AccentBlue),
        DashboardCard("Image Gen", "Create with AI", Routes.IMAGE_GEN, Icons.Filled.Image, RukColors.AccentLavender),
        DashboardCard("Video Gen", "Text to Video", Routes.VIDEO_GEN, Icons.Filled.Movie, RukColors.AccentPink),
        DashboardCard("Code AI", "Write & Debug", Routes.CODE, Icons.Filled.Code, RukColors.AccentCyan),
        DashboardCard("PDF Chat", "Read & Analyze", Routes.PDF, Icons.Filled.Description, RukColors.AccentRed),
        DashboardCard("AI Tools", "50+ Tools", Routes.TOOLS, Icons.Filled.GridView, RukColors.Purple)
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(RukColors.Black)
            .verticalScroll(rememberScrollState())
    ) {
        // ---------------------------------------------------------- header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { nav.navigate(Routes.PROFILE) }) {
                Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = RukColors.White)
            }
            Spacer(Modifier.weight(1f))
            RukLogo(size = 30)
            Spacer(Modifier.width(8.dp))
            Text(
                "RUK AI",
                style = MaterialTheme.typography.titleMedium,
                color = RukColors.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            ProBadge(modifier = Modifier.clickable { nav.navigate(Routes.PROFILE) })
        }

        // ------------------------------------------------------------ hero
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(RukColors.NavyDeep, RukColors.Navy, RukColors.Black)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Purple horizon glow behind the logo.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(RukColors.Purple.copy(alpha = 0.35f), Color.Transparent),
                            radius = 620f
                        )
                    )
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                RukLogo(size = 104)
                Spacer(Modifier.height(14.dp))
                Text(
                    "RUK AI",
                    style = MaterialTheme.typography.headlineLarge,
                    color = RukColors.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "All-in-One AI Assistant",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RukColors.Grey
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Chat \u2022 Create \u2022 Generate \u2022 Code \u2022 Analyze",
                    style = MaterialTheme.typography.labelSmall,
                    color = RukColors.PurpleLight,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ------------------------------------------------- ask-anything bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(50))
                .background(RukColors.Surface)
                .clickable { nav.navigate(Routes.CHAT) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = RukColors.PurpleLight,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Ask anything\u2026",
                style = MaterialTheme.typography.bodyMedium,
                color = RukColors.Grey,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(RukColors.Purple, RukColors.PurpleLight))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = "Send message",
                    tint = RukColors.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ------------------------------------------------------- card grid
        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            cards.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { card ->
                        DashboardTile(
                            card = card,
                            modifier = Modifier.weight(1f),
                            onClick = { nav.navigate(card.route) }
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "RUK AI can make mistakes. Verify important information.",
            style = MaterialTheme.typography.labelSmall,
            color = RukColors.GreyDim,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DashboardTile(
    card: DashboardCard,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .aspectRatio(1.15f)
            .clip(RoundedCornerShape(18.dp))
            .background(RukColors.Surface)
            .clickable { onClick() }
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(card.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(card.icon, contentDescription = null, tint = card.accent, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(
                card.title,
                style = MaterialTheme.typography.titleMedium,
                color = RukColors.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                card.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = RukColors.Grey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
