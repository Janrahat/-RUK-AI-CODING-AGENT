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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ruk.ai.data.model.ChatMessageDto
import com.ruk.ai.data.model.ModelDto
import com.ruk.ai.data.remote.ChatStreamEvent
import com.ruk.ai.data.repository.ChatRepository
import com.ruk.ai.data.repository.Result
import com.ruk.ai.di.ServiceLocator
import com.ruk.ai.ui.components.ErrorBanner
import com.ruk.ai.ui.components.MarkdownText
import com.ruk.ai.ui.components.RukLogo
import com.ruk.ai.ui.components.RukTopBar
import com.ruk.ai.ui.components.TypingDots
import com.ruk.ai.ui.theme.RukColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiMessage(
    val role: String,
    val content: String,
    val streaming: Boolean = false
)

data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val models: List<ModelDto> = emptyList(),
    val selectedModel: String = "gpt-4o-mini",
    val conversationId: String? = null,
    val streaming: Boolean = false,
    val error: String? = null
)

class ChatViewModel(private val repo: ChatRepository) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            when (val result = repo.models()) {
                is Result.Success -> {
                    val chatModels = result.data.filter { it.kind == "chat" }
                    _state.value = _state.value.copy(
                        models = chatModels,
                        selectedModel = chatModels.firstOrNull()?.id ?: _state.value.selectedModel
                    )
                }
                is Result.Failure -> Unit // model list is optional; keep the default
            }
        }
    }

    fun selectModel(model: String) {
        _state.value = _state.value.copy(selectedModel = model)
    }

    fun newChat() {
        streamJob?.cancel()
        _state.value = _state.value.copy(
            messages = emptyList(),
            conversationId = null,
            error = null,
            streaming = false
        )
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _state.value.streaming) return

        val history = _state.value.messages
            .filter { !it.streaming }
            .map { ChatMessageDto(it.role, it.content) } + ChatMessageDto("user", prompt)

        _state.value = _state.value.copy(
            messages = _state.value.messages +
                UiMessage("user", prompt) +
                UiMessage("assistant", "", streaming = true),
            streaming = true,
            error = null
        )

        streamJob = viewModelScope.launch {
            repo.stream(
                model = _state.value.selectedModel,
                messages = history,
                conversationId = _state.value.conversationId
            ).collect { event ->
                when (event) {
                    is ChatStreamEvent.Meta -> _state.value =
                        _state.value.copy(conversationId = event.conversationId)
                    is ChatStreamEvent.Delta -> appendDelta(event.text)
                    is ChatStreamEvent.Done -> finishStream()
                    is ChatStreamEvent.Failure -> _state.value = _state.value.copy(
                        streaming = false,
                        error = event.message,
                        messages = _state.value.messages.filterNot { it.streaming }
                    )
                }
            }
        }
    }

    private fun appendDelta(text: String) {
        val updated = _state.value.messages.map { message ->
            if (message.streaming) message.copy(content = message.content + text) else message
        }
        _state.value = _state.value.copy(messages = updated)
    }

    private fun finishStream() {
        val updated = _state.value.messages.map { message ->
            if (message.streaming) message.copy(streaming = false) else message
        }
        _state.value = _state.value.copy(messages = updated, streaming = false)
    }

    fun regenerate() {
        val conversationId = _state.value.conversationId ?: return
        if (_state.value.streaming) return
        val lastUser = _state.value.messages.lastOrNull { it.role == "user" } ?: return
        val withoutLastAnswer = _state.value.messages.dropLastWhile { it.role == "assistant" }
        _state.value = _state.value.copy(messages = withoutLastAnswer)
        viewModelScope.launch {
            repo.regenerate(conversationId, _state.value.selectedModel)
            send(lastUser.content)
        }
    }
}

@Composable
fun ChatScreen(nav: NavController) {
    val vm: ChatViewModel = viewModel { ChatViewModel(ServiceLocator.chatRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var modelMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content?.length) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(RukColors.Black)
            .imePadding()
    ) {
        RukTopBar(
            title = "AI Chat",
            onBack = { nav.popBackStack() },
            trailing = {
                IconButton(onClick = { vm.newChat() }) {
                    Icon(Icons.Filled.Add, contentDescription = "New chat", tint = RukColors.White)
                }
            }
        )

        // ------------------------------------------------- model selector
        Box(Modifier.padding(horizontal = 16.dp)) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(RukColors.Surface)
                    .clickable { modelMenu = true }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RukLogo(size = 18)
                Spacer(Modifier.width(8.dp))
                Text(
                    state.selectedModel,
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
            DropdownMenu(
                expanded = modelMenu,
                onDismissRequest = { modelMenu = false }
            ) {
                if (state.models.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No models available") },
                        onClick = { modelMenu = false }
                    )
                }
                state.models.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(model.id, color = RukColors.White)
                                Text(
                                    model.provider,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RukColors.Grey
                                )
                            }
                        },
                        onClick = {
                            vm.selectModel(model.id)
                            modelMenu = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------------------------------------------------- message list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.messages.isEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RukLogo(size = 64)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "How can I help you today?",
                            style = MaterialTheme.typography.titleLarge,
                            color = RukColors.White
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Ask a question, paste code, or describe what you want to build.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RukColors.Grey
                        )
                    }
                }
            }
            items(state.messages) { message ->
                MessageBubble(
                    message = message,
                    onCopy = { clipboard.setText(AnnotatedString(message.content)) },
                    onRegenerate = { vm.regenerate() }
                )
            }
        }

        state.error?.let {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                ErrorBanner(it)
            }
        }

        // ---------------------------------------------------- input area
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Type a message\u2026", color = RukColors.GreyDim) },
                modifier = Modifier.weight(1f),
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
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
                    .clip(CircleShape)
                    .background(
                        if (state.streaming || input.isBlank())
                            Brush.linearGradient(listOf(RukColors.SurfaceHigh, RukColors.SurfaceHigh))
                        else Brush.linearGradient(listOf(RukColors.Purple, RukColors.PurpleLight))
                    )
                    .clickable(enabled = !state.streaming && input.isNotBlank()) {
                        vm.send(input)
                        input = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                if (state.streaming) {
                    CircularProgressIndicator(
                        color = RukColors.PurpleLight,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = RukColors.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: UiMessage,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit
) {
    val isUser = message.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            RukLogo(size = 26, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(
            Modifier
                .weight(1f, fill = false)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
                .background(
                    if (isUser) Brush.linearGradient(listOf(RukColors.PurpleDeep, RukColors.Purple))
                    else Brush.linearGradient(listOf(RukColors.Surface, RukColors.Surface))
                )
                .padding(14.dp)
        ) {
            if (message.content.isEmpty() && message.streaming) {
                TypingDots()
            } else if (isUser) {
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = RukColors.White
                )
            } else {
                MarkdownText(message.content)
            }

            if (!isUser && !message.streaming && message.content.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "Copy response",
                            tint = RukColors.GreyDim,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(onClick = onRegenerate, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Regenerate response",
                            tint = RukColors.GreyDim,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(onClick = {}, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Filled.ThumbUp,
                            contentDescription = null,
                            tint = RukColors.GreyDim,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(onClick = {}, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Filled.ThumbDown,
                            contentDescription = null,
                            tint = RukColors.GreyDim,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
