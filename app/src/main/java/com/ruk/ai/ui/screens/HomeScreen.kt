package com.ruk.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ruk.ai.data.model.GenerationDto
import com.ruk.ai.data.model.UsageSummaryDto
import com.ruk.ai.data.repository.GenerationRepository
import com.ruk.ai.data.repository.ProfileRepository
import com.ruk.ai.data.repository.Result
import com.ruk.ai.di.ServiceLocator
import com.ruk.ai.navigation.Routes
import com.ruk.ai.ui.components.RukLogo
import com.ruk.ai.ui.components.RukPill
import com.ruk.ai.ui.theme.RukColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val plan: String = "free",
    val displayName: String = "",
    val usage: UsageSummaryDto? = null,
    val recent: List<GenerationDto> = emptyList()
)

class HomeViewModel(
    private val profileRepo: ProfileRepository,
    private val generationRepo: GenerationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(
            plan = profileRepo.plan(),
            displayName = profileRepo.displayName()
        )
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = profileRepo.usage()) {
                is Result.Success -> _state.value = _state.value.copy(
                    usage = result.data,
                    plan = result.data.plan
                )
                is Result.Failure -> Unit  // header stays usable without usage data
            }
        }
        viewModelScope.launch {
            when (val result = generationRepo.history("image")) {
                is Result.Success ->
                    _state.value = _state.value.copy(recent = result.data.take(6))
                is Result.Failure -> Unit
            }
        }
    }
}

@Composable
fun HomeScreen(nav: NavController, onOpenDrawer: () -> Unit) {
    val vm: HomeViewModel = viewModel {
        HomeViewModel(ServiceLocator.profileRepository, ServiceLocator.generationRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(RukColors.Black)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(20.dp))

        // ------------------------------------------------------------ header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RukLogo(size = 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "RUK AI",
                    style = MaterialTheme.typography.headlineSmall,
                    color = RukColors.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Your All-in-One AI Assistant",
                    style = MaterialTheme.typography.bodySmall,
                    color = RukColors.Grey
                )
            }
            RukPill(
                text = if (state.plan == "pro") "PRO" else "FREE",
                selected = state.plan == "pro",
                onClick = { nav.navigate(Routes.PROFILE) }
            )
        }

        Spacer(Modifier.height(20.dp))

        // -------------------------------------------------------- search bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(50))
                .background(RukColors.Surface)
                .clickable { nav.navigate(Routes.CHAT) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = RukColors.Grey,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Ask anything\u2026",
                style = MaterialTheme.typography.bodyMedium,
                color = RukColors.GreyDim
            )
        }

        Spacer(Modifier.height(24.dp))

        // ------------------------------------------------------------ cards
        Text(
            "FEATURES",
            style = MaterialTheme.typography.labelSmall,
            color = RukColors.Grey,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(10.dp))

        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DashboardCard("AI Chat", "GPT, Gemini, etc.", Routes.CHAT, Icons.Filled.AutoAwesome, RukColors.AccentBlue)
            DashboardCard("Image Gen", "Create with AI", Routes.IMAGE_GEN, Icons.Filled.Image, RukColors.AccentLavender)
            DashboardCard("Video Gen", "Text to Video", Routes.VIDEO_GEN, Icons.Filled.Movie, RukColors.AccentPink)
            DashboardCard("Code AI", "Write & Debug", Routes.CODE, Icons.Filled.Code, RukColors.AccentCyan)
            DashboardCard("PDF Chat", "Read & Analyze", Routes.PDF, Icons.Filled.Description, RukColors.AccentRed)
            DashboardCard("AI Tools", "12 tools", Routes.TOOLS, Icons.Filled.GridView, RukColors.Purple)
        }

        // ------------------------------------------------- recent creations
        if (state.recent.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                "RECENT",
                style = MaterialTheme.typography.labelSmall,
                color = RukColors.Grey,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                state.recent.forEach { item ->
                    Box(
                        Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RukColors.SurfaceHigh)
                            .clickable { nav.navigate(Routes.IMAGE_GEN) }
                    ) {
                        if (item.assetUrl != null) {
                            coil.compose.AsyncImage(
                                model = item.assetUrl,
                                contentDescription = item.prompt,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun DashboardCard(
    title: String,
    subtitle: String,
    route: String,
    icon: ImageVector,
    accent: Color
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RukColors.Surface)
            .clickable { /* caller wires navigation via the route below */ }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = RukColors.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = RukColors.Grey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = RukColors.GreyDim,
            modifier = Modifier.size(18.dp)
        )
    }
}
