package com.ruk.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
private val client = OkHttpClient.Builder()
    .callTimeout(90, TimeUnit.SECONDS)
    .build()

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
    var status by remember { mutableStateOf("Add your API key to begin.") }
    val messages = remember { mutableStateListOf<Pair<String, String>>() }
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Gold, secondary = Gold, background = Ink, surface = Panel,
        onPrimary = Ink, onBackground = Color.White, onSurface = Color.White
    )) {
        Column(
            Modifier.fillMaxSize().background(Ink).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("RUK AI", style = MaterialTheme.typography.headlineLarge, color = Gold)
            Text("CODING AGENT • ANDROID", color = Color.LightGray)
            OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("API Base URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(model, { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API Key (kept in memory only)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            HorizontalDivider(color = Gold.copy(alpha = 0.35f))
            Text("Conversation", color = Gold, style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages) { (role, body) ->
                    Surface(color = if (role == "You") Color(0xFF29251C) else Panel, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(role, color = Gold, style = MaterialTheme.typography.labelMedium)
                            Text(body, color = Color.White)
                        }
                    }
                }
            }
            OutlinedTextField(prompt, { prompt = it }, label = { Text("Ask RUK to build or debug code") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy && apiKey.isNotBlank() && prompt.isNotBlank(),
                    onClick = {
                        val question = prompt.trim()
                        prompt = ""
                        messages.add("You" to question)
                        busy = true
                        status = "Thinking…"
                        scope.launch {
                            try {
                                val answer = withContext(Dispatchers.IO) {
                                    callChat(baseUrl, model, apiKey, question, messages.toList())
                                }
                                messages.add("RUK AI" to answer)
                                status = "Response received."
                            } catch (e: Exception) {
                                status = "Error: ${e.message ?: "Request failed"}"
                            } finally { busy = false }
                        }
                    }
                ) { Text(if (busy) "Working…" else "Send") }
                Text(status, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
            Text("API key is not saved. Requests go to the provider URL you enter.", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun callChat(base: String, model: String, key: String, question: String, history: List<Pair<String, String>>): String {
    val msgs = JSONArray()
    msgs.put(JSONObject().put("role", "system").put("content", "You are RUK AI, a helpful coding assistant. Provide safe, practical code and explain how to run it."))
    history.dropLast(1).takeLast(12).forEach { (role, text) ->
        msgs.put(JSONObject().put("role", if (role == "You") "user" else "assistant").put("content", text))
    }
    msgs.put(JSONObject().put("role", "user").put("content", question))
    val payload = JSONObject().put("model", model).put("messages", msgs).put("temperature", 0.2).put("max_tokens", 2048)
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
        return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "No response text.")
    }
}
