package com.ruk.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ruk.ai.data.repository.ProfileRepository
import com.ruk.ai.data.repository.Result
import com.ruk.ai.di.ServiceLocator
import com.ruk.ai.navigation.Routes
import com.ruk.ai.ui.components.RukDivider
import com.ruk.ai.ui.components.RukTopBar
import com.ruk.ai.ui.components.SettingsRow
import com.ruk.ai.ui.theme.RukColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val plan: String = "free",
    val upgrading: Boolean = false,
    val message: String? = null
)

class SettingsViewModel(private val repo: ProfileRepository) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(plan = repo.plan()))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun upgrade() {
        _state.value = _state.value.copy(upgrading = true, message = null)
        viewModelScope.launch {
            when (val result = ServiceLocator.authRepository.upgrade()) {
                is Result.Success -> _state.value = _state.value.copy(
                    upgrading = false,
                    plan = result.data.plan,
                    message = "Pro unlocked on this account."
                )
                is Result.Failure -> _state.value = _state.value.copy(
                    upgrading = false,
                    message = result.message
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(nav: NavController) {
    val vm: SettingsViewModel = viewModel { SettingsViewModel(ServiceLocator.profileRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(RukColors.Black)
            .verticalScroll(rememberScrollState())
    ) {
        RukTopBar(title = "Settings", onBack = { nav.popBackStack() })

        // ------------------------------------------------------- plan card
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(RukColors.Surface)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = if (state.plan == "pro") RukColors.Gold else RukColors.Grey,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.plan == "pro") "Pro Plan" else "Free Plan",
                        style = MaterialTheme.typography.titleMedium,
                        color = RukColors.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (state.plan == "pro") "All models and premium tools unlocked"
                        else "Higher limits on messages, images and videos with Pro",
                        style = MaterialTheme.typography.labelSmall,
                        color = RukColors.Grey
                    )
                }
            }
            if (state.plan != "pro") {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(RukColors.Purple)
                        .clickable(enabled = !state.upgrading) { vm.upgrade() }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (state.upgrading) "Upgrading\u2026" else "Upgrade to Pro",
                        style = MaterialTheme.typography.titleMedium,
                        color = RukColors.White
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "No payment provider is connected yet: this grants Pro on your " +
                        "account directly so the higher limits can be tried out.",
                    style = MaterialTheme.typography.labelSmall,
                    color = RukColors.GreyDim
                )
            }
            state.message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = RukColors.PurpleLight)
            }
        }

        Spacer(Modifier.height(20.dp))

        // --------------------------------------------------- appearance
        Text(
            "APPEARANCE",
            style = MaterialTheme.typography.labelSmall,
            color = RukColors.Grey,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(RukColors.Surface)
        ) {
            SettingsRow(
                Icons.Filled.Palette,
                "Theme",
                value = "Dark (only theme)"
            )
            RukDivider()
            SettingsRow(
                Icons.Filled.Language,
                "Language",
                value = "Follows your input"
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Answers already follow the language you write in and can be translated " +
                "from the Tools tab, so there is no separate interface-language switch yet.",
            style = MaterialTheme.typography.labelSmall,
            color = RukColors.GreyDim,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(20.dp))

        Text(
            "ACCOUNT",
            style = MaterialTheme.typography.labelSmall,
            color = RukColors.Grey,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(RukColors.Surface)
        ) {
            SettingsRow(Icons.Filled.Key, "API Keys") { nav.navigate(Routes.API_KEYS) }
            RukDivider()
            SettingsRow(Icons.Filled.Check, "History & Saved Items") { nav.navigate(Routes.HISTORY) }
            RukDivider()
            SettingsRow(
                Icons.Filled.DeleteSweep,
                "Clear local data",
                tint = RukColors.Error
            ) {
                // Only touches this device: tokens and cached profile fields.
                // Server-side chats are removed from History, one item at a time.
                ServiceLocator.authRepository.signOut()
                nav.navigate(Routes.AUTH) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
            RukDivider()
            SettingsRow(Icons.Filled.Info, "About RUK AI", value = "v1.0.0")
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "WHAT LEAVES YOUR DEVICE",
            style = MaterialTheme.typography.labelSmall,
            color = RukColors.Grey,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Prompts, images you generate and PDFs you upload are sent to the RUK AI " +
                "server, which forwards them to the AI provider you have configured. " +
                "Provider keys are encrypted at rest on the server and are never " +
                "embedded in this app. Chats, documents and generated media are stored " +
                "under your account and can be deleted from History at any time.",
            style = MaterialTheme.typography.labelSmall,
            color = RukColors.GreyDim,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(28.dp))
    }
}
