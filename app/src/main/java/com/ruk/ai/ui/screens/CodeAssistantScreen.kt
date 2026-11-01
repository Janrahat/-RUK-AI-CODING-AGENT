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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ruk.ai.data.model.CodeRunResult
import com.ruk.ai.data.repository.CodeRepository
import com.ruk.ai.data.repository.Result
import com.ruk.ai.di.ServiceLocator
import com.ruk.ai.ui.components.ErrorBanner
import com.ruk.ai.ui.components.GradientButton
import com.ruk.ai.ui.components.MarkdownText
import com.ruk.ai.ui.components.RukPill
import com.ruk.ai.ui.components.RukTopBar
import com.ruk.ai.ui.theme.CodeTextStyle
import com.ruk.ai.ui.theme.RukColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CodeUiState(
    val language: String = "python",
    val code: String = SAMPLE_CODE,
    val busy: Boolean = false,
    val error: String? = null,
    val runResult: CodeRunResult? = null,
    val assistAnswer: String? = null,
    val assistAction: String? = null,
    val executable: Map<String, Boolean> = emptyMap(),
    val execEnabled: Boolean = false
) {
    companion object {
        val SAMPLE_CODE = """
            # Hello World in Python
            for i in range(3):
                print("Hello, RUK AI!")
        """.trimIndent()
    }
}

class CodeViewModel(private val repo: CodeRepository) : ViewModel() {

    private val _state = MutableStateFlow(CodeUiState())
    val state: StateFlow<CodeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            when (val result = repo.languages()) {
                is Result.Success -> _state.value = _state.value.copy(
                    executable = result.data.executable,
                    execEnabled = result.data.execEnabled
                )
                is Result.Failure -> Unit
            }
        }
    }

    fun setLanguage(value: String) { _state.value = _state.value.copy(language = value) }
    fun setCode(value: String) { _state.value = _state.value.copy(code = value) }

    fun run() {
        _state.value = _state.value.copy(busy = true, error = null, assistAnswer = null)
        viewModelScope.launch {
            when (val result = repo.run(_state.value.language, _state.value.code)) {
                is Result.Success -> _state.value = _state.value.copy(busy = false, runResult = result.data)
                is Result.Failure -> _state.value = _state.value.copy(busy = false, error = result.message)
            }
        }
    }

    fun assist(action: String) {
        _state.value = _state.value.copy(busy = true, error = null, assistAction = action)
        viewModelScope.launch {
            when (
                val result = repo.assist(
                    language = _state.value.language,
                    code = _state.value.code,
                    instruction = "",
                    action = action
                )
            ) {
                is Result.Success -> _state.value = _state.value.copy(
                    busy = false,
                    assistAnswer = result.data.answer
                )
                is Result.Failure -> _state.value = _state.value.copy(busy = false, error = result.message)
            }
        }
    }
}

@Composable
fun CodeAssistantScreen(nav: NavController) {
    val vm: CodeViewModel = viewModel { CodeViewModel(ServiceLocator.codeRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var langMenu by remember { mutableStateOf(false) }

    val languages = listOf(
        "python", "javascript", "typescript", "java", "kotlin", "c", "cpp",
        "php", "html", "css", "go", "rust", "sql", "bash"
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(RukColors.Black)
            .verticalScroll(rememberScrollState())
    ) {
        RukTopBar(title = "Code Assistant", onBack = { nav.popBackStack() })

        // ------------------------------------------------ language picker
        Box(Modifier.padding(horizontal = 16.dp)) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(RukColors.Surface)
                    .clickable { langMenu = true }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    state.language,
                    style = MaterialTheme.typography.labelLarge,
                    color = RukColors.White
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = RukColors.Grey,
                    modifier = Modifier.size(16.dp)
                )
            }
            DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                languages.forEach { language ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(language, color = RukColors.White)
                                if (state.executable[language] == true) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "runnable",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RukColors.Success
                                    )
                                }
                            }
                        },
                        onClick = {
                            vm.setLanguage(language)
                            langMenu = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------------------------------------------------- code editor
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(min = 200.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0B0B12))
        ) {
            // Line-number gutter
            Column(
                Modifier
                    .background(RukColors.SurfaceHigh)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                val lineCount = state.code.lines().size.coerceAtLeast(1)
                repeat(lineCount) { index ->
                    Text(
                        "${index + 1}",
                        style = CodeTextStyle.copy(fontSize = 12.sp),
                        color = RukColors.GreyDim
                    )
                }
            }
            BasicTextField(
                value = state.code,
                onValueChange = vm::setCode,
                textStyle = CodeTextStyle.copy(color = Color(0xFFE6E6F0)),
                cursorBrush = SolidColor(RukColors.PurpleLight),
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        GradientButton(
            text = if (state.busy) "Working\u2026" else "Run Code",
            icon = Icons.Filled.PlayArrow,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            onClick = { vm.run() }
        )

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RukPill("Explain", state.assistAction == "explain", leading = Icons.Filled.Lightbulb) { vm.assist("explain") }
            RukPill("Debug", state.assistAction == "debug", leading = Icons.Filled.BugReport) { vm.assist("debug") }
            RukPill("Improve", state.assistAction == "improve", leading = Icons.Filled.TrendingUp) { vm.assist("improve") }
            RukPill("Regenerate", false, leading = Icons.Filled.Refresh) { vm.assist("generate") }
        }

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.padding(horizontal = 16.dp)) { ErrorBanner(it) }
        }

        if (state.busy) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = RukColors.PurpleLight,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // ------------------------------------------------------- output
        state.runResult?.let { result ->
            Spacer(Modifier.height(18.dp))
            Text(
                "Output:",
                style = MaterialTheme.typography.labelSmall,
                color = RukColors.Grey,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0B0B12))
                    .padding(12.dp)
            ) {
                Text(
                    "status: ${result.status}   exit: ${result.exitCode ?: "-"}   ${result.durationMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = when (result.status) {
                        "completed" -> RukColors.Success
                        "timeout" -> RukColors.Warning
                        else -> RukColors.Error
                    }
                )
                if (result.stdout.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(result.stdout.trimEnd(), style = CodeTextStyle, color = Color(0xFFE6E6F0))
                }
                if (result.stderr.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(result.stderr.trimEnd(), style = CodeTextStyle, color = RukColors.Error)
                }
                if (result.truncated) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Output was truncated.",
                        style = MaterialTheme.typography.labelSmall,
                        color = RukColors.Warning
                    )
                }
            }
        }

        state.assistAnswer?.let { answer ->
            Spacer(Modifier.height(18.dp))
            Text(
                "Assistant:",
                style = MaterialTheme.typography.labelSmall,
                color = RukColors.Grey,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RukColors.Surface)
                    .padding(14.dp)
            ) {
                MarkdownText(answer)
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}
