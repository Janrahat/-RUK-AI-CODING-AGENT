package com.ruk.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ruk.ai.data.model.UsageSummaryDto
import com.ruk.ai.data.repository.ProfileRepository
import com.ruk.ai.data.repository.Result
import com.ruk.ai.di.ServiceLocator
import com.ruk.ai.navigation.Routes
import com.ruk.ai.ui.components.RukDivider
import com.ruk.ai.ui.components.RukLogo
import com.ruk.ai.ui.components.SettingsRow
import com.ruk.ai.ui.theme.RukColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val name: String = "",
    val email: String = "",
    val plan: String = "free",
    val usage: UsageSummaryDto? = null
)

class ProfileViewModel(private val repo: ProfileRepository) : ViewModel() {

    private val _state = MutableStateFlow(
        ProfileUiState(
            name = repo.displayName(),
            email = repo.email(),
            plan = repo.plan()
        )
    )
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = repo.usage()) {
                is Result.Success -> _state.value = _state.value.copy(
                    usage = result.data,
                    plan = result.data.plan
                )
                is Result.Failure -> Unit
            }
        }
    }
}

@Composable
fun ProfileScreen(nav: NavController) {
    val vm: ProfileViewModel = viewModel { ProfileViewModel(ServiceLocator.profileRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(RukColors.Black)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(28.dp))

        // ------------------------------------------------------- identity
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RukLogo(size = 88)
            Spacer(Modifier.height(12.dp))
            Text(
                state.name.ifBlank { "RUK AI user" },
                style = MaterialTheme.typography.headlineMedium,
                color = RukColors.White,
                fontWeight = FontWeight.Bold
            )
            if (state.email.isNotBlank()) {
                Text(
                    state.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = RukColors.Grey
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (state.plan == "pro") "Pro Plan" else "Free Plan",
                style = MaterialTheme.typography.labelLarge,
                color = if (state.plan == "pro") RukColors.Gold else RukColors.Grey
            )
        }

        Spacer(Modifier.height(22.dp))

        // ---------------------------------------------------- usage stats
        state.usage?.let { usage ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile("Messages today", "${usage.messagesToday}/${usage.dailyLimit}", Modifier.weight(1f))
                StatTile("Remaining", "${usage.remainingToday}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile("Images", "${usage.imagesThisMonth}", Modifier.weight(1f))
                StatTile("Videos", "${usage.videosThisMonth}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
        }

        // --------------------------------------------------- pro upsell
        if (state.plan != "pro") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(listOf(RukColors.PurpleDeep, RukColors.Purple)))
                    .clickable { nav.navigate(Routes.SETTINGS) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = RukColors.White,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Upgrade to Pro",
                        style = MaterialTheme.typography.titleMedium,
                        color = RukColors.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Unlock all AI models, higher limits and premium tools",
                        style = MaterialTheme.typography.labelSmall,
                        color = RukColors.White.copy(alpha = 0.85f)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ------------------------------------------------------- menu
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(RukColors.Surface)
        ) {
            SettingsRow(Icons.Filled.History, "My History") { nav.navigate(Routes.HISTORY) }
            RukDivider()
            SettingsRow(Icons.Filled.Star, "Saved Items") { nav.navigate(Routes.HISTORY) }
            RukDivider()
            SettingsRow(Icons.Filled.Key, "API Keys") { nav.navigate(Routes.API_KEYS) }
            RukDivider()
            SettingsRow(Icons.Filled.Settings, "Settings") { nav.navigate(Routes.SETTINGS) }
        }

        Spacer(Modifier.height(16.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(RukColors.Surface)
        ) {
            SettingsRow(
                Icons.Filled.Logout,
                "Sign out",
                tint = RukColors.Error
            ) {
                // Clearing the token alone would leave the user on a protected
                // screen whose every request now 401s. Drop the whole back
                // stack so there is no way back into the app without signing in.
                ServiceLocator.authRepository.signOut()
                nav.navigate(Routes.AUTH) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "RUK AI v1.0.0",
            style = MaterialTheme.typography.labelSmall,
            color = RukColors.GreyDim,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 0.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(RukColors.Surface)
            .padding(14.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = RukColors.White)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = RukColors.Grey)
    }
}
