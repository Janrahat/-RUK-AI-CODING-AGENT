package com.ruk.ai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ruk.ai.data.model.DocumentDto
import com.ruk.ai.data.repository.PdfRepository
import com.ruk.ai.data.repository.Result
import com.ruk.ai.di.ServiceLocator
import com.ruk.ai.ui.components.EmptyState
import com.ruk.ai.ui.components.ErrorBanner
import com.ruk.ai.ui.components.GradientButton
import com.ruk.ai.ui.components.MarkdownText
import com.ruk.ai.ui.components.RukTopBar
import com.ruk.ai.ui.theme.RukColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PdfUiState(
    val documents: List<DocumentDto> = emptyList(),
    val selected: DocumentDto? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val summary: String? = null,
    val answer: String? = null,
    val question: String = ""
)

class PdfViewModel(private val repo: PdfRepository) : ViewModel() {

    private val _state = MutableStateFlow(PdfUiState())
    val state: StateFlow<PdfUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = repo.documents()) {
                is Result.Success -> _state.value = _state.value.copy(documents = result.data)
                is Result.Failure -> Unit
            }
        }
    }

    fun upload(bytes: ByteArray, filename: String) {
        _state.value = _state.value.copy(busy = true, error = null, summary = null, answer = null)
        viewModelScope.launch {
            when (val result = repo.upload(bytes, filename)) {
                is Result.Success -> {
                    _state.value = _state.value.copy(
                        busy = false,
                        selected = result.data,
                        documents = listOf(result.data) + _state.value.documents
                    )
                }
                is Result.Failure ->
                    _state.value = _state.value.copy(busy = false, error = result.message)
            }
        }
    }

    fun select(document: DocumentDto) {
        _state.value = _state.value.copy(selected = document, summary = null, answer = null)
    }

    fun summarize() {
        val document = _state.value.selected ?: return
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            when (val result = repo.summarize(document.id)) {
                is Result.Success -> _state.value = _state.value.copy(busy = false, summary = result.data)
                is Result.Failure -> _state.value = _state.value.copy(busy = false, error = result.message)
            }
        }
    }

    fun setError(message: String) {
        _state.value = _state.value.copy(error = message, busy = false)
    }

    fun setQuestion(value: String) { _state.value = _state.value.copy(question = value) }

    fun ask() {
        val document = _state.value.selected ?: return
        val question = _state.value.question.trim()
        if (question.isEmpty()) return
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            when (val result = repo.ask(document.id, question)) {
                is Result.Success -> _state.value = _state.value.copy(busy = false, answer = result.data)
                is Result.Failure -> _state.value = _state.value.copy(busy = false, error = result.message)
            }
        }
    }
}

@Composable
fun PdfAssistantScreen(nav: NavController) {
    val vm: PdfViewModel = viewModel { PdfViewModel(ServiceLocator.pdfRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
            if (bytes == null) {
                vm.setError("Could not read that file.")
            } else {
                vm.upload(bytes, name)
            }
        }.onFailure { vm.setError(it.message ?: "Could not read that file.") }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(RukColors.Black)
            .verticalScroll(rememberScrollState())
    ) {
        RukTopBar(title = "PDF Assistant", onBack = { nav.popBackStack() })

        GradientButton(
            text = "Upload PDF",
            icon = Icons.Filled.UploadFile,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            onClick = { picker.launch("application/pdf") }
        )

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.padding(horizontal = 16.dp)) { ErrorBanner(it) }
        }

        if (state.busy) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(
                    color = RukColors.PurpleLight,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        state.selected?.let { document ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(RukColors.Surface)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = null,
                        tint = RukColors.AccentRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            document.filename,
                            style = MaterialTheme.typography.titleMedium,
                            color = RukColors.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${document.pageCount} pages \u2022 ${document.sizeBytes / 1024} KB",
                            style = MaterialTheme.typography.labelSmall,
                            color = RukColors.Grey
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                GradientButton(
                    text = "Summarize document",
                    icon = Icons.Filled.Summarize,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { vm.summarize() }
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = state.question,
                    onValueChange = vm::setQuestion,
                    placeholder = { Text("Ask about this document\u2026", color = RukColors.GreyDim) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RukColors.Purple,
                        unfocusedBorderColor = RukColors.SurfaceBorder,
                        focusedTextColor = RukColors.White,
                        unfocusedTextColor = RukColors.White,
                        cursorColor = RukColors.PurpleLight
                    )
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(RukColors.Purple)
                        .clickable(enabled = !state.busy && state.question.isNotBlank()) { vm.ask() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = RukColors.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            state.summary?.let { summary ->
                Spacer(Modifier.height(16.dp))
                Text(
                    "SUMMARY",
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
                ) { MarkdownText(summary) }
            }

            state.answer?.let { answer ->
                Spacer(Modifier.height(16.dp))
                Text(
                    "ANSWER",
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
                ) { MarkdownText(answer) }
            }
        } ?: run {
            if (state.documents.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Description,
                    title = "No document loaded",
                    subtitle = "Upload a PDF to summarise it and ask questions about its contents."
                )
            }
        }

        if (state.documents.size > 1) {
            Spacer(Modifier.height(20.dp))
            Text(
                "YOUR DOCUMENTS",
                style = MaterialTheme.typography.labelSmall,
                color = RukColors.Grey,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            state.documents.forEach { document ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RukColors.Surface)
                        .clickable { vm.select(document) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = null,
                        tint = RukColors.Grey,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        document.filename,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RukColors.White,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${document.pageCount}p",
                        style = MaterialTheme.typography.labelSmall,
                        color = RukColors.Grey
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}
