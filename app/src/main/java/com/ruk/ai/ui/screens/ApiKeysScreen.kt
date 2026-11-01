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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ruk.ai.data.model.ApiKeyDto
import com.ruk.ai.data.repository.ProfileRepository
import com.ruk.ai.data.repository.Result
import com.ruk.ai.di.ServiceLocator
import com.ruk.ai.ui.components.ErrorBanner
import com.ruk.ai.ui.components.GradientButton
import com.ruk.ai.ui.components.RukPill
import com.ruk.ai.ui.components.RukTopBar
import com.ruk.ai.ui.theme.RukColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Every provider the backend accepts a key for (`ApiKeyIn.provider`). */
private val SUPPORTED_PROVIDERS = listOf(
    "openai", "gemini", "openrouter", "together", "stability", "fal"
)

data class ApiKeysUiState(
    val keys: List<ApiKeyDto> = emptyList(),
    val provider: String = "openai",
    val value: String = "",
    val baseUrl: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null
)

class ApiKeysViewModel(private val repo: ProfileRepository) : ViewModel() {

    private val _state = MutableStateFlow(ApiKeysUiState())
    val state: StateFlow<ApiKeysUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = repo.apiKeys()) {
                is Result.Success -> _state.value = _state.value.copy(keys = result.data)
                is Result.Failure -> _state.value = _state.value.copy(error = result.message)
            }
        }
    }

    fun setProvider(value: String) { _state.value = _state.value.copy(provider = value) }
    fun setValue(value: String) { _state.value = _state.value.copy(value = value) }
    fun setBaseUrl(value: String) { _state.value = _state.value.copy(baseUrl = value) }

    fun save() {
        val key = _state.value.value.trim()
        if (key.isEmpty()) {
            _state.value = _state.value.copy(error = "Paste your API key first.")
            return
        }
        _state.value = _state.value.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            when (
                val result = repo.saveKey(
                    provider = _state.value.provider,
                    key = key,
                    baseUrl = _state.value.baseUrl.trim().ifBlank { null }
                )
            ) {
                is Result.Success -> {
                    _state.value = _state.value.copy(
                        busy = false,
                        value = "",
                        notice = "Key saved and encrypted.",
                        keys = _state.value.keys.filterNot { it.provider == result.data.provider } + result.data
                    )
                }
                is Result.Failure ->
                    _state.value = _state.value.copy(busy = false, error = result.message)
            }
        }
    }

    fun test(provider: String) {
        _state.value = _state.value.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            when (val result = repo.testKey(provider)) {
                is Result.Success -> _state.value = _state.value.copy(
                    busy = false,
                    notice = if (result.data.ok) "Key works: ${result.data.detail}"
                    else "Key rejected: ${result.data.detail}"
                )
                is Result.Failure ->
                    _state.value = _state.value.copy(busy = false, error = result.message)
            }
        }
    }

    fun delete(provider: String) {
        viewModelScope.launch {
            repo.deleteKey(provider)
            refresh()
        }
    }
}

@Composable
fun ApiKeysScreen(nav: NavController) {
    val vm: ApiKeysViewModel = viewModel { ApiKeysViewModel(ServiceLocator.profileRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var reveal by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(RukColors.Black)
            .verticalScroll(rememberScrollState())
    ) {
        RukTopBar(title = "API Keys", onBack = { nav.popBackStack() })

        Text(
            "Bring your own provider key. Keys are encrypted on the server and are " +
                "never stored in the app or returned in full.",
            style = MaterialTheme.typography.bodySmall,
            color = RukColors.Grey,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SUPPORTED_PROVIDERS.forEach { provider ->
                RukPill(provider, state.provider == provider) { vm.setProvider(provider) }
            }
        }

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = state.value,
            onValueChange = vm::setValue,
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(
                        if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (reveal) "Hide key" else "Show key",
                        tint = RukColors.Grey
                    )
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RukColors.Purple,
                unfocusedBorderColor = RukColors.SurfaceBorder,
                focusedTextColor = RukColors.White,
                unfocusedTextColor = RukColors.White,
                focusedLabelColor = RukColors.PurpleLight,
                unfocusedLabelColor = RukColors.Grey,
                cursorColor = RukColors.PurpleLight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = vm::setBaseUrl,
            label = { Text("Base URL (optional, for self-hosted)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RukColors.Purple,
                unfocusedBorderColor = RukColors.SurfaceBorder,
                focusedTextColor = RukColors.White,
                unfocusedTextColor = RukColors.White,
                focusedLabelColor = RukColors.PurpleLight,
                unfocusedLabelColor = RukColors.Grey,
                cursorColor = RukColors.PurpleLight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(16.dp))

        GradientButton(
            text = if (state.busy) "Saving\u2026" else "Save key",
            icon = Icons.Filled.Key,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            onClick = { vm.save() }
        )

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.padding(horizontal = 16.dp)) { ErrorBanner(it) }
        }
        state.notice?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = RukColors.Success,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (state.busy) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(
                    color = RukColors.PurpleLight,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (state.keys.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                "SAVED KEYS",
                style = MaterialTheme.typography.labelSmall,
                color = RukColors.Grey,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            state.keys.forEach { key ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RukColors.Surface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = RukColors.Success,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            key.provider,
                            style = MaterialTheme.typography.titleMedium,
                            color = RukColors.White,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            key.keyHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = RukColors.Grey
                        )
                    }
                    Text(
                        "Test",
                        style = MaterialTheme.typography.labelLarge,
                        color = RukColors.PurpleLight,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickable { vm.test(key.provider) }
                    )
                    IconButton(onClick = { vm.delete(key.provider) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete key",
                            tint = RukColors.GreyDim,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}
