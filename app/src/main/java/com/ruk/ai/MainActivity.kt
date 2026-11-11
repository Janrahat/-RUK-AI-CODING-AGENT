package com.ruk.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val BG = Color(0xFF080B14)
private val PANEL = Color(0xFF12182A)
private val PURPLE = Color(0xFF8B5CF6)
private val FG = Color(0xFFF4F2FF)
private val MUTED = Color(0xFFA7A9BC)
private val http = OkHttpClient.Builder().callTimeout(120, TimeUnit.SECONDS).build()
private data class Provider(val id: String, val name: String, val configured: Boolean, val model: String)
private data class ChatMessage(val role: String, val text: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 11, 20)
        window.navigationBarColor = android.graphics.Color.rgb(8, 11, 20)
        setContent { RukAiApp() }
    }
}

@Composable
private fun RukAiApp() {
    var server by remember { mutableStateOf("http://127.0.0.1:8000") }
    var providers by remember { mutableStateOf(listOf<Provider>()) }
    var provider by remember { mutableStateOf("openrouter") }
    var model by remember { mutableStateOf("openai/gpt-4.1-mini") }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Connect to your RUK AI backend") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()

    suspend fun callApi(path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(server.trim().trimEnd('/') + path).header("Accept", "application/json")
        if (body == null) builder.get() else builder.header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        http.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(raw).optString("detail") }.getOrDefault("")
                throw IllegalStateException(detail.ifBlank { "HTTP ${response.code}: ${raw.take(220)}" })
            }
            JSONObject(raw)
        }
    }

    fun refreshProviders() {
        scope.launch {
            status = "Connecting…"
            try {
                val result = callApi("/api/providers")
                val array = result.optJSONArray("providers") ?: JSONArray()
                val list = buildList {
                    for (i in 0 until array.length()) {
                        val p = array.optJSONObject(i) ?: continue
                        add(Provider(p.optString("id"), p.optString("name"), p.optBoolean("configured"), p.optString("default_model")))
                    }
                }
                providers = list
                val chosen = list.firstOrNull { it.id == provider } ?: list.firstOrNull { it.configured } ?: list.firstOrNull()
                if (chosen != null) { provider = chosen.id; model = chosen.model }
                status = "Backend connected • ${list.count { it.configured }} provider(s) configured"
            } catch (e: Exception) { status = "Connection failed: ${e.message}" }
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        if (providers.none { it.id == provider && it.configured }) {
            status = "No API key configured for this provider. Add it to backend/.env."
            return
        }
        input = ""
        messages.add(ChatMessage("user", text))
        busy = true
        scope.launch {
            try {
                val history = JSONArray()
                messages.dropLast(1).takeLast(20).forEach { history.put(JSONObject().put("role", it.role).put("content", it.text)) }
                val body = JSONObject().put("provider", provider).put("model", model.trim())
                    .put("message", text).put("history", history)
                val result = callApi("/api/chat", body)
                messages.add(ChatMessage("assistant", result.optString("reply", "Empty AI response")))
                status = "${result.optString("provider", provider)} • ${result.optString("model", model)}"
            } catch (e: Exception) {
                messages.add(ChatMessage("assistant", "Request failed: ${e.message}"))
                status = "Chat request failed"
            } finally { busy = false }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = PURPLE, background = BG, surface = PANEL, onBackground = FG, onSurface = FG)) {
        Column(Modifier.fillMaxSize().background(BG).imePadding().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = PURPLE, shape = RoundedCornerShape(12.dp)) {
                    Text("R", Modifier.padding(horizontal = 13.dp, vertical = 8.dp), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("RUK AI GLOBAL", color = FG, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("Native Android Application", color = MUTED, fontSize = 11.sp)
                }
                IconButton(onClick = { refreshProviders() }) { Icon(Icons.Default.Refresh, "Refresh", tint = FG) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(server, { server = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Backend URL") },
                supportingText = { Text("Same phone + Termux: http://127.0.0.1:8000", color = MUTED) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                var expanded by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth()) {
                        Text(providers.firstOrNull { it.id == provider }?.name ?: provider, maxLines = 1)
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        providers.forEach { p -> DropdownMenuItem(
                            text = { Text(p.name + if (p.configured) " ✓" else " • No key") },
                            onClick = { provider = p.id; model = p.model; expanded = false }
                        ) }
                    }
                }
                Spacer(Modifier.width(7.dp))
                OutlinedTextField(model, { model = it }, Modifier.weight(1.3f), singleLine = true, label = { Text("Model ID") })
            }
            Text(status, color = if (status.startsWith("Connection failed") || status == "Chat request failed") Color(0xFFFF8A80) else MUTED,
                fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
            HorizontalDivider(color = Color(0xFF283047))
            if (messages.isEmpty()) {
                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("✦", color = PURPLE, fontSize = 56.sp)
                    Text("How can I help you today?", color = FG, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("Choose an AI provider and start chatting.", color = MUTED, fontSize = 13.sp)
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(messages) { item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.role == "user") Arrangement.End else Arrangement.Start) {
                            Surface(color = if (item.role == "user") Color(0xFF292047) else PANEL, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(.92f)) {
                                Column(Modifier.padding(13.dp)) {
                                    Text(if (item.role == "user") "YOU" else "RUK AI", color = PURPLE, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(5.dp))
                                    Text(item.text, color = FG, fontSize = 14.sp, lineHeight = 21.sp)
                                }
                            }
                        }
                    }
                    if (busy) item { Text("RUK AI is thinking…", color = MUTED, fontSize = 12.sp) }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Message RUK AI…") }, maxLines = 4)
                Spacer(Modifier.width(8.dp))
                Button(onClick = { send() }, enabled = input.isNotBlank() && !busy, contentPadding = PaddingValues(14.dp)) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("API keys stay on your backend. Never share them in chat.", color = MUTED, fontSize = 10.sp)
        }
    }
}
