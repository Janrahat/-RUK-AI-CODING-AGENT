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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.ruk.ai.data.model.ConversationDto
import com.ruk.ai.data.model.GenerationDto
import com.ruk.ai.data.repository.ChatRepository
import com.ruk.ai.data.repository.GenerationRepository
import com.ruk.ai.data.repository.Result
import com.ruk.ai.di.ServiceLocator
import com.ruk.ai.navigation.Routes
import com.ruk.ai.ui.components.EmptyState
import com.ruk.ai.ui.components.RukPill
import com.ruk.ai.ui.components.RukTopBar
import com.ruk.ai.ui.theme.RukColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class HistoryTab(val label: String) {
    CHATS("Chats"),
    IMAGES("Images"),
    VIDEOS("Videos")
}

data class HistoryUiState(
    val tab: HistoryTab = HistoryTab.CHATS,
    val conversations: List<ConversationDto> = emptyList(),
    val generations: List<GenerationDto> = emptyList(),
    val loading: Boolean = false
)

class HistoryViewModel(
    private val chatRepo: ChatRepository,
    private val genRepo: GenerationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        load(HistoryTab.CHATS)
    }

    fun load(tab: HistoryTab) {
        _state.value = _state.value.copy(tab = tab, loading = true)
        viewModelScope.launch {
            when (tab) {
                HistoryTab.CHATS -> when (val result = chatRepo.conversations()) {
                    is Result.Success -> _state.value = _state.value.copy(
                        conversations = result.data,
                        loading = false
                    )
                    is Result.Failure -> _state.value = _state.value.copy(loading = false)
                }
                HistoryTab.IMAGES -> when (val result = genRepo.history("image")) {
                    is Result.Success -> _state.value = _state.value.copy(
                        generations = result.data,
                        loading = false
                    )
                    is Result.Failure -> _state.value = _state.value.copy(loading = false)
                }
                HistoryTab.VIDEOS -> when (val result = genRepo.history("video")) {
                    is Result.Success -> _state.value = _state.value.copy(
                        generations = result.data,
                        loading = false
                    )
                    is Result.Failure -> _state.value = _state.value.copy(loading = false)
                }
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            chatRepo.deleteConversation(id)
            load(HistoryTab.CHATS)
        }
    }

    fun deleteGeneration(id: String) {
        viewModelScope.launch {
            genRepo.delete(id)
            load(_state.value.tab)
        }
    }
}

@Composable
fun HistoryScreen(nav: NavController) {
    val vm: HistoryViewModel = viewModel {
        HistoryViewModel(ServiceLocator.chatRepository, ServiceLocator.generationRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(RukColors.Black)
    ) {
        RukTopBar(title = "History", onBack = { nav.popBackStack() })

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HistoryTab.entries.forEach { tab ->
                RukPill(tab.label, state.tab == tab) { vm.load(tab) }
            }
        }

        Spacer(Modifier.height(12.dp))

        when (state.tab) {
            HistoryTab.CHATS -> {
                if (state.conversations.isEmpty() && !state.loading) {
                    EmptyState(
                        icon = Icons.Filled.History,
                        title = "No conversations yet",
                        subtitle = "Your chats will appear here once you start talking to RUK AI."
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, end = 16.dp, bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.conversations) { conversation ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(RukColors.Surface)
                                    .clickable { nav.navigate(Routes.CHAT) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = RukColors.PurpleLight,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        conversation.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = RukColors.White,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${conversation.messageCount} messages \u2022 ${conversation.model}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RukColors.Grey
                                    )
                                }
                                IconButton(onClick = { vm.deleteConversation(conversation.id) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete conversation",
                                        tint = RukColors.GreyDim,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                if (state.generations.isEmpty() && !state.loading) {
                    EmptyState(
                        icon = if (state.tab == HistoryTab.IMAGES) Icons.Filled.Image else Icons.Filled.Movie,
                        title = "Nothing here yet",
                        subtitle = "Generated ${state.tab.label.lowercase()} will be saved here."
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, end = 16.dp, bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.generations) { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(RukColors.Surface)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(RukColors.SurfaceHigh)
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
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.prompt,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = RukColors.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        item.status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (item.status) {
                                            "succeeded" -> RukColors.Success
                                            "failed" -> RukColors.Error
                                            else -> RukColors.Warning
                                        }
                                    )
                                }
                                IconButton(onClick = { vm.deleteGeneration(item.id) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete item",
                                        tint = RukColors.GreyDim,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
