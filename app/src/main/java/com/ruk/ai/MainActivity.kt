package com.ruk.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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

private val Gold = Color(0xFFD6AD60)
private val Ink = Color(0xFF101010)
private val Panel = Color(0xFF1C1C1C)
private val client = OkHttpClient.Builder().callTimeout(120, TimeUnit.SECONDS).build()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RukApp() }
    }
}

@Composable
fun RukApp() {
    var baseUrl by remember { mutableStateOf("https://openrouter.ai/api/v1") }
    var model by remember { mutableStateOf("openai/gpt-4.1-mini") }
    var apiKey by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Add your API key to begin.") }
    val messages = remember { mutableStateListOf<Pair<String, String>>() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    fun sendMessage() {
        val question = prompt.trim()
        if (question.isEmpty() || apiKey.isBlank() || busy) return
        prompt = ""
        messages.add("You" to question)
        busy = true
        status = "RUK is working…"
        scope.launch {
            try {
                val answer = withContext(Dispatchers.IO) {
                    callChat(baseUrl, model, apiKey, question, messages.toList())
                }
                messages.add("RUK AI" to answer)
                status = "Response received."
            } catch (e: Exception) {
                status = "Error: ${e.message ?: "Request failed"}"
            } finally {
                busy = false
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Gold, secondary = Gold, background = Ink, surface = Panel,
        onPrimary = Ink, onBackground = Color.White, onSurface = Color.White
    )) {
        Column(
            Modifier.fillMaxSize().background(Ink).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("RUK AI", style = MaterialTheme.typography.headlineLarge, color = Gold)
                    Text("YOUR AI CODING WORKSPACE", color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = { showSettings = !showSettings }) {
                    Text(if (showSettings) "Hide setup" else "API setup", color = Gold)
                }
            }

            if (showSettings) {
                OutlinedTextField(
                    value = baseUrl, onValueChange = { baseUrl = it },
                    label = { Text("API Base URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("Model ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it },
                    label = { Text("API Key • memory only") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, visualTransformation = PasswordVisualTransformation()
                )
            }

            HorizontalDivider(color = Gold.copy(alpha = 0.4f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("WORKSPACE", color = Gold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    messages.clear()
                    status = "Conversation cleared."
                }, enabled = messages.isNotEmpty() && !busy) { Text("New chat", color = Gold) }
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Welcome to RUK AI", color = Gold, style = MaterialTheme.typography.titleLarge)
                                Text("Ask me to write code, explain errors, design an app, or plan a project.", color = Color.White)
                                Text("Choose a starter task below to begin.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                itemsIndexed(messages) { index, (role, body) ->
                    Surface(
                        color = if (role == "You") Color(0xFF29251C) else Panel,
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (role == "You") "YOU" else "RUK AI", color = Gold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                if (role != "You") {
                                    TextButton(onClick = { clipboard.setText(AnnotatedString(body)) }) {
                                        Text("Copy", color = Gold)
                                    }
                                }
                            }
                            SelectionContainer { Text(body, color = Color.White, style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                }
                if (busy) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(color = Gold, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("RUK is preparing your answer…", color = Color.LightGray)
                        }
                    }
                }
            }

            Text("QUICK START", color = Gold, style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Write Python code", "Debug my code", "Build an Android app").forEach { starter ->
                    AssistChip(
                        onClick = { prompt = starter },
                        label = { Text(starter, maxLines = 1) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = Color.White,
                            containerColor = Panel
                        )
                    )
                }
            }

            OutlinedTextField(
                value = prompt, onValueChange = { prompt = it },
                label = { Text("Tell RUK what to build or fix") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
                enabled = !busy
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { sendMessage() },
                    enabled = !busy && apiKey.isNotBlank() && prompt.isNotBlank(),
                    modifier = Modifier.height(48.dp)
                ) { Text(if (busy) "Working…" else "Send") }
                Text(status, color = if (status.startsWith("Error")) Color(0xFFFF8A80) else Color.LightGray,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
            Text(
                "API key stays in memory only. Requests are sent to the provider URL above.",
                color = Color.Gray, style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun callChat(
    base: String, model: String, key: String, question: String,
    history: List<Pair<String, String>>
): String {
    val msgs = JSONArray()
    val systemPrompt = """
        You are RUK AI, a skilled and practical software engineering assistant.
        Help with planning, writing, reviewing, and debugging code across common programming languages.
        When asked to create a project, provide a clear file tree and complete code for every requested file.
        Explain setup, dependencies, run commands, and tests. Never claim you created files, ran commands,
        accessed a device, or changed a repository unless a connected tool actually did so.
        Ask a focused clarification only when essential. Prefer secure, maintainable code.
        Refuse harmful cyber abuse; support authorized defensive security and learning.
    """.trimIndent()
    msgs.put(JSONObject().put("role", "system").put("content", systemPrompt))
    history.dropLast(1).takeLast(16).forEach { (role, text) ->
        msgs.put(JSONObject().put("role", if (role == "You") "user" else "assistant").put("content", text))
    }
    msgs.put(JSONObject().put("role", "user").put("content", question))
    val payload = JSONObject()
        .put("model", model)
        .put("messages", msgs)
        .put("temperature", 0.2)
        .put("max_tokens", 4096)
    val url = base.trimEnd('/') + "/chat/completions"
    val request = Request.Builder().url(url)
        .header("Authorization", "Bearer $key")
        .header("Content-Type", "application/json")
        .post(payload.toString().toRequestBody("application/json".toMediaType()))
        .build()
    client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: ${body.take(500)}")
        val json = JSONObject(body)
        return json.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content", "No response text.")
    }
}
