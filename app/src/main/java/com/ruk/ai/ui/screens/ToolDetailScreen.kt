package com.ruk.ai.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ruk.ai.data.repository.Result
import com.ruk.ai.data.repository.ToolsRepository
import com.ruk.ai.di.ServiceLocator
import com.ruk.ai.navigation.Routes
import com.ruk.ai.navigation.toolById
import com.ruk.ai.ui.components.ErrorBanner
import com.ruk.ai.ui.components.GradientButton
import com.ruk.ai.ui.components.MarkdownText
import com.ruk.ai.ui.components.RukTopBar
import com.ruk.ai.ui.theme.RukColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ToolDetailUiState(
    val input: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val output: String? = null
)

class ToolDetailViewModel(private val repo: ToolsRepository) : ViewModel() {

    private val _state = MutableStateFlow(ToolDetailUiState())
    val state: StateFlow<ToolDetailUiState> = _state.asStateFlow()

    fun setError(message: String) {
        _state.value = _state.value.copy(error = message, busy = false)
    }

    fun setInput(value: String) { _state.value = _state.value.copy(input = value) }

    fun run(toolId: String) {
        val input = _state.value.input.trim()
        if (input.isEmpty()) {
            _state.value = _state.value.copy(error = "Enter some text first.")
            return
        }
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            when (val result = repo.run(toolId, input)) {
                is Result.Success -> _state.value = _state.value.copy(busy = false, output = result.data)
                is Result.Failure -> _state.value = _state.value.copy(busy = false, error = result.message)
            }
        }
    }
}

/**
 * Detail page for a library tool. Tools that have a dedicated screen
 * (image, video, code, pdf, chat) route there instead of running inline.
 */
@Composable
fun ToolDetailScreen(nav: NavController, toolId: String) {
    val tool = toolById(toolId)
    val vm: ToolDetailViewModel = viewModel { ToolDetailViewModel(ServiceLocator.toolsRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    val dedicatedRoute = when (toolId) {
        "image" -> Routes.IMAGE_GEN
        "video" -> Routes.VIDEO_GEN
        "code" -> Routes.CODE
        "pdf" -> Routes.PDF
        "chat" -> Routes.CHAT
        else -> null
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(RukColors.Black)
            .verticalScroll(rememberScrollState())
    ) {
        RukTopBar(title = tool?.title ?: "Tool", onBack = { nav.popBackStack() })

        if (tool == null) {
            Text(
                "That tool does not exist.",
                color = RukColors.Grey,
                modifier = Modifier.padding(16.dp)
            )
            return@Column
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tool.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(tool.icon, contentDescription = null, tint = tool.accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tool.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = RukColors.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = RukColors.Grey
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        if (dedicatedRoute != null) {
            Text(
                "This tool has a dedicated screen with extra options.",
                style = MaterialTheme.typography.bodyMedium,
                color = RukColors.Grey,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(14.dp))
            GradientButton(
                text = "Open ${tool.title}",
                icon = Icons.Filled.AutoAwesome,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                onClick = { nav.navigate(dedicatedRoute) }
            )
            return@Column
        }

        OutlinedTextField(
            value = state.input,
            onValueChange = vm::setInput,
            placeholder = { Text("Paste your text here\u2026", color = RukColors.GreyDim) },
            minLines = 5,
            maxLines = 12,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RukColors.Purple,
                unfocusedBorderColor = RukColors.SurfaceBorder,
                focusedTextColor = RukColors.White,
                unfocusedTextColor = RukColors.White,
                cursorColor = RukColors.PurpleLight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(14.dp))

        GradientButton(
            text = if (state.busy) "Working\u2026" else "Run ${tool.title}",
            icon = Icons.Filled.AutoAwesome,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            onClick = { vm.run(tool.id) }
        )

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.padding(horizontal = 16.dp)) { ErrorBanner(it) }
        }

        if (state.busy) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(
                    color = RukColors.PurpleLight,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        state.output?.let { output ->
            Spacer(Modifier.height(18.dp))
            Text(
                "RESULT",
                style = MaterialTheme.typography.labelSmall,
                color = RukColors.Grey,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(RukColors.Surface)
                    .padding(14.dp)
            ) { MarkdownText(output) }
        }

        Spacer(Modifier.height(28.dp))
    }
}
