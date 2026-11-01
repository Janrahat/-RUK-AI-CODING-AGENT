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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ruk.ai.data.model.GenerationDto
import com.ruk.ai.data.repository.GenerationRepository
import com.ruk.ai.data.repository.Result
import com.ruk.ai.di.ServiceLocator
import com.ruk.ai.ui.components.EmptyState
import com.ruk.ai.ui.components.ErrorBanner
import com.ruk.ai.ui.components.GradientButton
import com.ruk.ai.ui.components.RukPill
import com.ruk.ai.ui.components.RukTopBar
import com.ruk.ai.ui.theme.RukColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VideoGenUiState(
    val prompt: String = "",
    val aspectRatio: String = "16:9",
    val duration: Int = 5,
    val busy: Boolean = false,
    val error: String? = null,
    val current: GenerationDto? = null,
    val history: List<GenerationDto> = emptyList()
)

class VideoGenViewModel(private val repo: GenerationRepository) : ViewModel() {

    private val _state = MutableStateFlow(VideoGenUiState())
    val state: StateFlow<VideoGenUiState> = _state.asStateFlow()
    private var pollJob: Job? = null

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            when (val result = repo.history("video")) {
                is Result.Success -> _state.value = _state.value.copy(history = result.data)
                is Result.Failure -> Unit
            }
        }
    }

    fun setPrompt(value: String) { _state.value = _state.value.copy(prompt = value) }
    fun setAspect(value: String) { _state.value = _state.value.copy(aspectRatio = value) }
    fun setDuration(value: Int) { _state.value = _state.value.copy(duration = value) }

    fun generate() {
        val prompt = _state.value.prompt.trim()
        if (prompt.isEmpty()) {
            _state.value = _state.value.copy(error = "Describe the video you want first.")
            return
        }
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            when (
                val result = repo.video(
                    prompt = prompt,
                    aspectRatio = _state.value.aspectRatio,
                    durationSeconds = _state.value.duration
                )
            ) {
                is Result.Success -> {
                    _state.value = _state.value.copy(
                        busy = false,
                        current = result.data,
                        history = listOf(result.data) + _state.value.history
                    )
                    if (result.data.isRunning) startPolling(result.data.id)
                }
                is Result.Failure ->
                    _state.value = _state.value.copy(busy = false, error = result.message)
            }
        }
    }

    /** Video jobs are asynchronous: poll until the provider finishes. */
    private fun startPolling(id: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            repeat(60) {
                delay(5_000)
                when (val result = repo.poll(id)) {
                    is Result.Success -> {
                        _state.value = _state.value.copy(current = result.data)
                        if (!result.data.isRunning) return@launch
                    }
                    is Result.Failure -> {
                        _state.value = _state.value.copy(error = result.message)
                        return@launch
                    }
                }
            }
            _state.value = _state.value.copy(
                error = "The video is taking longer than expected. Check History later."
            )
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}

@Composable
fun VideoGeneratorScreen(nav: NavController) {
    val vm: VideoGenViewModel = viewModel { VideoGenViewModel(ServiceLocator.generationRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(RukColors.Black)
            .verticalScroll(rememberScrollState())
    ) {
        RukTopBar(title = "Video Generator", onBack = { nav.popBackStack() })

        OutlinedTextField(
            value = state.prompt,
            onValueChange = vm::setPrompt,
            placeholder = {
                Text("A drone shot flying over a neon city at night", color = RukColors.GreyDim)
            },
            minLines = 3,
            maxLines = 6,
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

        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Aspect Ratio", style = MaterialTheme.typography.labelSmall, color = RukColors.Grey)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("16:9", "9:16", "1:1").forEach { ratio ->
                    RukPill(
                        text = ratio,
                        selected = state.aspectRatio == ratio,
                        onClick = { vm.setAspect(ratio) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Duration", style = MaterialTheme.typography.labelSmall, color = RukColors.Grey)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 10).forEach { seconds ->
                    RukPill(
                        text = "${seconds}s",
                        selected = state.duration == seconds,
                        onClick = { vm.setDuration(seconds) }
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        GradientButton(
            text = if (state.busy) "Submitting\u2026" else "Generate video",
            icon = Icons.Filled.Movie,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            onClick = { vm.generate() }
        )

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.padding(horizontal = 16.dp)) { ErrorBanner(it) }
        }

        Spacer(Modifier.height(18.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp))
                .background(RukColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            val current = state.current
            when {
                current?.isRunning == true -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        "Rendering your video\u2026",
                        color = RukColors.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        color = RukColors.PurpleLight,
                        trackColor = RukColors.SurfaceHigh,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "This usually takes 30\u201390 seconds. You can leave this screen.",
                        color = RukColors.Grey,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                current?.assetUrl != null -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(RukColors.NavyDeep, RukColors.Black))),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Play video",
                            tint = RukColors.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Video ready",
                            color = RukColors.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            current.assetUrl,
                            color = RukColors.Grey,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                current?.isFailed == true -> Text(
                    current.error ?: "Generation failed.",
                    color = RukColors.Error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(24.dp)
                )
                else -> EmptyState(
                    icon = Icons.Filled.Movie,
                    title = "No video yet",
                    subtitle = "Describe a scene above and tap Generate."
                )
            }
        }

        if (state.current?.assetUrl != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(RukColors.Surface)
                        .clickable {}
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, tint = RukColors.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download", style = MaterialTheme.typography.labelLarge, color = RukColors.White)
                }
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(RukColors.Surface)
                        .clickable { vm.generate() }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = RukColors.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Regenerate", style = MaterialTheme.typography.labelLarge, color = RukColors.White)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}
