package com.ruk.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ImageGenUiState(
    val prompt: String = "",
    val aspectRatio: String = "1:1",
    val quality: String = "standard",
    val busy: Boolean = false,
    val error: String? = null,
    val current: GenerationDto? = null,
    val history: List<GenerationDto> = emptyList()
)

class ImageGenViewModel(private val repo: GenerationRepository) : ViewModel() {

    private val _state = MutableStateFlow(ImageGenUiState())
    val state: StateFlow<ImageGenUiState> = _state.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            when (val result = repo.history("image")) {
                is Result.Success -> _state.value = _state.value.copy(history = result.data)
                is Result.Failure -> Unit
            }
        }
    }

    fun setPrompt(value: String) { _state.value = _state.value.copy(prompt = value) }
    fun setAspect(value: String) { _state.value = _state.value.copy(aspectRatio = value) }
    fun setQuality(value: String) { _state.value = _state.value.copy(quality = value) }

    fun generate() {
        val prompt = _state.value.prompt.trim()
        if (prompt.isEmpty()) {
            _state.value = _state.value.copy(error = "Describe the image you want first.")
            return
        }
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            when (
                val result = repo.image(
                    prompt = prompt,
                    aspectRatio = _state.value.aspectRatio,
                    quality = _state.value.quality,
                    model = "gpt-image-1"
                )
            ) {
                is Result.Success -> {
                    _state.value = _state.value.copy(
                        busy = false,
                        current = result.data,
                        history = listOf(result.data) + _state.value.history
                    )
                }
                is Result.Failure ->
                    _state.value = _state.value.copy(busy = false, error = result.message)
            }
        }
    }

    fun select(item: GenerationDto) {
        _state.value = _state.value.copy(current = item, prompt = item.prompt)
    }
}

@Composable
fun ImageGeneratorScreen(nav: NavController) {
    val vm: ImageGenViewModel = viewModel { ImageGenViewModel(ServiceLocator.generationRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(RukColors.Black)
            .verticalScroll(rememberScrollState())
    ) {
        RukTopBar(title = "Image Generator", onBack = { nav.popBackStack() })

        OutlinedTextField(
            value = state.prompt,
            onValueChange = vm::setPrompt,
            placeholder = {
                Text(
                    "A futuristic hacker setup with neon lights, 4K, realistic",
                    color = RukColors.GreyDim
                )
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

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("Aspect Ratio", style = MaterialTheme.typography.labelSmall, color = RukColors.Grey)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("1:1", "16:9", "9:16", "4:3").forEach { ratio ->
                        RukPill(
                            text = ratio,
                            selected = state.aspectRatio == ratio,
                            onClick = { vm.setAspect(ratio) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Quality", style = MaterialTheme.typography.labelSmall, color = RukColors.Grey)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("standard", "hd").forEach { quality ->
                    RukPill(
                        text = quality.uppercase(),
                        selected = state.quality == quality,
                        onClick = { vm.setQuality(quality) }
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        GradientButton(
            text = if (state.busy) "Generating\u2026" else "Generate",
            icon = Icons.Filled.AutoAwesome,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            onClick = { vm.generate() }
        )

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.padding(horizontal = 16.dp)) {
                ErrorBanner(it, onRetry = { vm.generate() })
            }
        }

        Spacer(Modifier.height(18.dp))

        // ------------------------------------------------------- preview
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(RukColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            val current = state.current
            when {
                state.busy -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = RukColors.PurpleLight, strokeWidth = 2.dp)
                    Spacer(Modifier.height(10.dp))
                    Text("Creating your image\u2026", color = RukColors.Grey, style = MaterialTheme.typography.bodySmall)
                }
                current?.assetUrl != null -> AsyncImage(
                    model = current.assetUrl,
                    contentDescription = "Generated image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                current?.isFailed == true -> Text(
                    current.error ?: "Generation failed.",
                    color = RukColors.Error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(24.dp)
                )
                else -> EmptyState(
                    icon = Icons.Filled.Image,
                    title = "No image yet",
                    subtitle = "Describe an image above and tap Generate."
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
                ActionPill("Download", Icons.Filled.Download, Modifier.weight(1f)) {}
                ActionPill("Share", Icons.Filled.Share, Modifier.weight(1f)) {}
                ActionPill("Regenerate", Icons.Filled.Refresh, Modifier.weight(1f)) { vm.generate() }
            }
        }

        if (state.history.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text(
                "HISTORY",
                style = MaterialTheme.typography.labelSmall,
                color = RukColors.Grey,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.history) { item ->
                    Box(
                        Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RukColors.SurfaceHigh)
                            .clickable { vm.select(item) }
                    ) {
                        if (item.assetUrl != null) {
                            AsyncImage(
                                model = item.assetUrl,
                                contentDescription = item.prompt,
                                contentScale = ContentScale.Crop,
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
private fun ActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(RukColors.Surface)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = RukColors.White, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = RukColors.White)
    }
}
