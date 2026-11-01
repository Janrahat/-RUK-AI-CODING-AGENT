package com.ruk.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

private val Bg = Color(0xFF05060B)
private val Panel = Color(0xFF111522)
private val Panel2 = Color(0xFF171D2B)
private val Purple = Color(0xFF8B35F5)
private val Purple2 = Color(0xFFB04BFF)
private val White = Color(0xFFF7F5FC)
private val Muted = Color(0xFFA6A8B8)
private val Line = Color(0xFF303648)
private val client = OkHttpClient.Builder().callTimeout(150, TimeUnit.SECONDS).build()

private enum class Page { HOME, CHAT, TOOLS, HISTORY, PROFILE, IMAGE, VIDEO, CODE, PDF, TOOL_DETAIL }
private data class ChatLine(val role: String, val text: String)
private data class ToolItem(val id: String, val title: String, val description: String, val category: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 6, 11)
        window.navigationBarColor = android.graphics.Color.rgb(5, 6, 11)
        setContent { RukAiApp() }
    }
}

@Composable
private fun RukLogo(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 76.dp) {
    Box(modifier.size(size).clip(CircleShape).background(Bg).border(2.dp, White, CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(7.dp)) {
            val c = center
            val w = this.size.width * .44f
            val h = this.size.height * .20f
            for (angle in listOf(0f, 60f, 120f)) {
                rotate(angle) { drawOval(White.copy(alpha = .94f), topLeft = Offset(c.x-w, c.y-h), size = androidx.compose.ui.geometry.Size(w*2, h*2), style = Stroke(width = 2.2.dp.toPx())) }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("RUK AI", color = White, fontSize = (size.value * .13f).sp, fontWeight = FontWeight.Bold)
            Box(Modifier.padding(top = 2.dp).width(size * .18f).height(2.dp).clip(CircleShape).background(White))
            Box(Modifier.padding(top = 2.dp).width(size * .13f).height(2.dp).clip(CircleShape).background(White))
        }
    }
}

@Composable
private fun RukAiApp() {
    var page by remember { mutableStateOf(Page.HOME) }
    var apiBase by remember { mutableStateOf("https://your-ruk-ai-server.com") }
    var token by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf("login") }
    var authStatus by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("Rahatullah") }
    var plan by remember { mutableStateOf("Free Plan") }
    var selectedTool by remember { mutableStateOf<ToolItem?>(null) }
    var filter by remember { mutableStateOf("All") }
    var search by remember { mutableStateOf("") }
    var chatInput by remember { mutableStateOf("") }
    var chatBusy by remember { mutableStateOf(false) }
    var conversationId by remember { mutableStateOf<String?>(null) }
    val chatLines = remember { mutableStateListOf<ChatLine>() }
    var imagePrompt by remember { mutableStateOf("") }
    var imageResult by remember { mutableStateOf("") }
    var videoPrompt by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("print(\"Hello, RUK AI!\")") }
    var codeLanguage by remember { mutableStateOf("python") }
    var codeResult by remember { mutableStateOf("") }
    var toolInput by remember { mutableStateOf("") }
    var toolResult by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("openrouter") }
    var providerKey by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val tools = remember {
        listOf(
            ToolItem("writer", "AI Writer", "Write articles, blogs and content", "Writing", Icons.Default.Edit),
            ToolItem("summarizer", "AI Summarizer", "Summarize text and documents", "Writing", Icons.Default.Description),
            ToolItem("translator", "AI Translator", "Translate languages instantly", "Writing", Icons.Default.Translate),
            ToolItem("seo", "AI SEO Tools", "Create SEO titles and keywords", "Writing", Icons.Default.TrendingUp),
            ToolItem("research", "AI Research", "Explore topics and organize findings", "Writing", Icons.Default.Search),
            ToolItem("math", "AI Math Solver", "Solve and explain math problems", "Utility", Icons.Default.Calculate),
            ToolItem("voice", "AI Voice Assistant", "Generate voice-ready scripts", "Media", Icons.Default.Mic)
        )
    }

    suspend fun api(path: String, method: String = "GET", payload: JSONObject? = null): JSONObject {
        val url = apiBase.trimEnd('/') + path
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        if (payload != null) {
            builder.header("Content-Type", "application/json")
            builder.method(method, payload.toString().toRequestBody("application/json".toMediaType()))
        } else if (method != "GET") builder.method(method, null)
        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(body).optString("detail") }.getOrDefault("")
                throw IllegalStateException(if (detail.isNotBlank()) detail else "Server error HTTP ${response.code}")
            }
            return if (body.isBlank()) JSONObject() else JSONObject(body)
        }
    }

    fun authenticate() {
        scope.launch {
            authStatus = "Connecting…"
            try {
                val body = JSONObject().put("email", email.trim()).put("password", password)
                if (authMode == "register") body.put("display_name", displayName)
                val result = withContext(Dispatchers.IO) {
                    api("/api/v1/auth/${if (authMode == "register") "register" else "login"}", "POST", body)
                }
                token = result.optString("access_token")
                userName = result.optJSONObject("user")?.optString("display_name")?.ifBlank { email.substringBefore("@") } ?: email.substringBefore("@")
                plan = (result.optJSONObject("user")?.optString("plan") ?: "free").replaceFirstChar { it.uppercase() } + " Plan"
                authStatus = "Signed in successfully."
            } catch (e: Exception) { authStatus = e.message ?: "Sign in failed." }
        }
    }

    fun sendChat() {
        val question = chatInput.trim()
        if (question.isEmpty() || chatBusy) return
        if (token.isBlank()) { page = Page.PROFILE; authStatus = "Sign in to use AI chat."; return }
        chatInput = ""
        chatLines.add(ChatLine("user", question))
        chatBusy = true
        scope.launch {
            try {
                val msgs = JSONArray()
                chatLines.takeLast(30).forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.text)) }
                val payload = JSONObject().put("model", "openai/gpt-4o-mini").put("messages", msgs).put("stream", true)
                if (conversationId != null) payload.put("conversation_id", conversationId)
                val answer = withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(apiBase.trimEnd('/') + "/api/v1/chat/completions")
                        .header("Authorization", "Bearer $token")
                        .header("Content-Type", "application/json")
                        .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
                    client.newCall(request).execute().use { response ->
                        val raw = response.body?.string().orEmpty()
                        if (!response.isSuccessful) throw IllegalStateException("Chat failed: HTTP ${response.code}")
                        val out = StringBuilder()
                        var conv: String? = null
                        raw.lineSequence().filter { it.startsWith("data: ") }.forEach { line ->
                            val json = runCatching { JSONObject(line.removePrefix("data: ")) }.getOrNull() ?: return@forEach
                            when {
                                json.has("conversation_id") -> conv = json.optString("conversation_id")
                                json.has("text") -> out.append(json.optString("text"))
                                json.has("message") -> throw IllegalStateException(json.optString("message"))
                            }
                        }
                        conversationId = conv ?: conversationId
                        out.toString().ifBlank { "The server returned no response text." }
                    }
                }
                chatLines.add(ChatLine("assistant", answer))
            } catch (e: Exception) { chatLines.add(ChatLine("assistant", "Error: ${e.message ?: "Request failed"}")) }
            finally { chatBusy = false }
        }
    }

    fun runTool(item: ToolItem) {
        if (token.isBlank()) { page = Page.PROFILE; authStatus = "Sign in to use AI tools."; return }
        if (toolInput.isBlank()) { toolResult = "Enter some text first."; return }
        scope.launch {
            toolResult = "Working…"
            try {
                val payload = JSONObject().put("tool", item.id).put("input", toolInput)
                val result = withContext(Dispatchers.IO) { api("/api/v1/tools/run", "POST", payload) }
                toolResult = result.optString("output", "No result returned.")
            } catch (e: Exception) { toolResult = "Error: ${e.message}" }
        }
    }

    fun generateImage() {
        if (token.isBlank()) { page = Page.PROFILE; authStatus = "Sign in to generate images."; return }
        if (imagePrompt.isBlank()) return
        scope.launch {
            imageResult = "Generating image…"
            try {
                val payload = JSONObject().put("prompt", imagePrompt).put("aspect_ratio", "1:1").put("quality", "standard")
                val result = withContext(Dispatchers.IO) { api("/api/v1/generations/image", "POST", payload) }
                imageResult = result.optString("asset_url").ifBlank { "Generation status: ${result.optString("status")}" }
            } catch (e: Exception) { imageResult = "Error: ${e.message}" }
        }
    }

    fun generateVideo() {
        if (token.isBlank()) { page = Page.PROFILE; authStatus = "Sign in to generate videos."; return }
        if (videoPrompt.isBlank()) return
        scope.launch {
            status = "Submitting video job…"
            try {
                val payload = JSONObject().put("prompt", videoPrompt).put("aspect_ratio", "16:9").put("duration_seconds", 5)
                val result = withContext(Dispatchers.IO) { api("/api/v1/generations/video", "POST", payload) }
                status = "Video job: ${result.optString("status")} • ID ${result.optString("id")}"
            } catch (e: Exception) { status = "Error: ${e.message}" }
        }
    }

    fun runCode(action: String) {
        if (token.isBlank()) { page = Page.PROFILE; authStatus = "Sign in to use Code Assistant."; return }
        scope.launch {
            codeResult = "Working…"
            try {
                val payload = JSONObject().put("language", codeLanguage).put("code", code).put("action", action)
                    .put("instruction", if (action == "generate") code else "")
                val result = withContext(Dispatchers.IO) { api("/api/v1/code/assist", "POST", payload) }
                codeResult = result.optString("answer", "No output.")
            } catch (e: Exception) { codeResult = "Error: ${e.message}" }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Purple2, secondary = Purple, background = Bg, surface = Panel, onPrimary = White, onBackground = White, onSurface = White)) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                if (page in listOf(Page.HOME, Page.CHAT, Page.TOOLS, Page.HISTORY, Page.PROFILE)) {
                    NavigationBar(containerColor = Color(0xFF0D111B), contentColor = White) {
                        val items = listOf(Triple(Page.HOME, "Home", Icons.Default.Home), Triple(Page.CHAT, "Chat", Icons.Default.Chat), Triple(Page.TOOLS, "Tools", Icons.Default.GridView), Triple(Page.HISTORY, "History", Icons.Default.History), Triple(Page.PROFILE, "Profile", Icons.Default.Person))
                        items.forEach { (target, label, icon) ->
                            NavigationBarItem(selected = page == target, onClick = { page = target }, icon = { Icon(icon, null) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Purple2, selectedTextColor = Purple2, indicatorColor = Purple.copy(alpha = .18f), unselectedIconColor = Muted, unselectedTextColor = Muted))
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    Page.HOME -> HomeScreen(onOpen = { page = it })
                    Page.CHAT -> ChatScreen(chatLines, chatInput, { chatInput = it }, chatBusy, ::sendChat, { chatLines.clear(); conversationId = null })
                    Page.TOOLS -> ToolsScreen(tools, filter, { filter = it }, search, { search = it }, { selectedTool = it; toolInput = ""; toolResult = ""; page = Page.TOOL_DETAIL }, { page = it })
                    Page.PROFILE -> ProfileScreen(apiBase, { apiBase = it }, email, { email = it }, password, { password = it }, displayName, { displayName = it }, authMode, { authMode = it }, token.isNotBlank(), userName, plan, authStatus, ::authenticate, provider, { provider = it }, providerKey, { providerKey = it }, {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { api("/api/v1/keys", "PUT", JSONObject().put("provider", provider).put("api_key", providerKey)) }
                                authStatus = "Provider key securely saved."
                                providerKey = ""
                            } catch (e: Exception) { authStatus = e.message ?: "Could not save key." }
                        }
                    })
                    Page.HISTORY -> HistoryScreen(token.isNotBlank())
                    Page.IMAGE -> ImageScreen(imagePrompt, { imagePrompt = it }, imageResult, ::generateImage, { page = Page.HOME })
                    Page.VIDEO -> VideoScreen(videoPrompt, { videoPrompt = it }, status, ::generateVideo, { page = Page.HOME })
                    Page.CODE -> CodeScreen(code, { code = it }, codeLanguage, { codeLanguage = it }, codeResult, ::runCode, { page = Page.HOME })
                    Page.PDF -> PdfScreen({ page = Page.HOME })
                    Page.TOOL_DETAIL -> ToolDetailScreen(selectedTool, toolInput, { toolInput = it }, toolResult, { selectedTool?.let(::runTool) }, { page = Page.TOOLS })
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onOpen: (Page) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(top = 14.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = White, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(12.dp))
                RukLogo(size = 38.dp)
                Spacer(Modifier.width(9.dp))
                Text("RUK AI", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = White)
                Spacer(Modifier.weight(1f))
                Surface(color = Color(0xFF1B1725), shape = RoundedCornerShape(30.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB58B26))) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFFFD45A), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Pro", color = Color(0xFFFFD45A), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                RukLogo(size = 148.dp)
                Text("RUK AI", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = White)
                Text("All-in-One AI Assistant", color = Muted, fontSize = 16.sp)
                Text("Chat  •  Create  •  Generate  •  Code  •  Analyze", color = White, fontSize = 11.sp)
            }
        }
        item {
            Surface(Modifier.fillMaxWidth().clickable { onOpen(Page.CHAT) }, shape = RoundedCornerShape(30.dp), color = Panel2, border = androidx.compose.foundation.BorderStroke(1.dp, Purple.copy(alpha = .8f))) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = White)
                    Spacer(Modifier.width(12.dp)); Text("Ask anything…", color = Muted, modifier = Modifier.weight(1f))
                    Surface(color = Purple, shape = CircleShape) { Icon(Icons.Default.Send, null, tint = White, modifier = Modifier.padding(9.dp).size(18.dp)) }
                }
            }
        }
        item { Text("Explore AI", color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        item {
            val cards = listOf(Triple("AI Chat", Icons.Default.Chat, Page.CHAT), Triple("Image Gen", Icons.Default.Image, Page.IMAGE), Triple("Video Gen", Icons.Default.PlayCircle, Page.VIDEO), Triple("Code AI", Icons.Default.Code, Page.CODE), Triple("PDF Chat", Icons.Default.PictureAsPdf, Page.PDF), Triple("AI Tools", Icons.Default.GridView, Page.TOOLS))
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth().height(246.dp), userScrollEnabled = false, horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(cards) { (label, icon, target) ->
                    Surface(Modifier.fillMaxWidth().height(116.dp).clickable { onOpen(target) }, color = Panel, shape = RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(icon, null, tint = Purple2, modifier = Modifier.size(27.dp))
                            Spacer(Modifier.height(8.dp)); Text(label, color = White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text(when (target) { Page.CHAT -> "Ask AI"; Page.IMAGE -> "Create images"; Page.VIDEO -> "Text to video"; Page.CODE -> "Write & debug"; Page.PDF -> "Read & analyze"; else -> "50+ tools" }, color = Muted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Purple2, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp)); Text("Discover 50+ AI tools", color = White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onOpen(Page.TOOLS) }) { Text("Explore", color = Purple2) }
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(lines: List<ChatLine>, input: String, onInput: (String) -> Unit, busy: Boolean, send: () -> Unit, newChat: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = newChat) { Icon(Icons.Default.Add, null, tint = White) }; Text("AI Chat", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("GPT / Gemini", color = Muted, fontSize = 11.sp) }
        if (lines.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { RukLogo(size = 92.dp); Text("How can I help you today?", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Ask, create, explain or explore an idea.", color = Muted) }
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(lines.size) { i -> val line = lines[i]; Surface(Modifier.fillMaxWidth(), color = if (line.role == "user") Color(0xFF281A40) else Panel, shape = RoundedCornerShape(16.dp)) { Text(line.text, Modifier.padding(14.dp), color = White, fontSize = 14.sp) } } }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Purple2, trackColor = Panel2)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Panel2).border(1.dp, Line, RoundedCornerShape(28.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(value = input, onValueChange = onInput, modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 10.dp), textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 14.sp), decorationBox = { inner -> Box { if (input.isEmpty()) Text("Type a message…", color = Muted); inner() } })
            IconButton(onClick = send, enabled = input.isNotBlank() && !busy, modifier = Modifier.clip(CircleShape).background(Purple)) { Icon(Icons.Default.Send, null, tint = White) }
        }
    }
}

@Composable
private fun ToolsScreen(tools: List<ToolItem>, filter: String, onFilter: (String) -> Unit, search: String, onSearch: (String) -> Unit, onTool: (ToolItem) -> Unit, onOpen: (Page) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { onOpen(Page.HOME) }) { Icon(Icons.Default.ArrowBack, null, tint = White) }; Text("AI Tools", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("♛ Pro", color = Color(0xFFFFD45A), fontSize = 12.sp) }
        OutlinedTextField(search, onSearch, Modifier.fillMaxWidth(), placeholder = { Text("Search AI tools…", color = Muted) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }, singleLine = true, shape = RoundedCornerShape(15.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("All", "Writing", "Media", "Code", "Utility").forEach { item -> FilterChip(selected = filter == item, onClick = { onFilter(item) }, label = { Text(item) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Purple, selectedLabelColor = White, labelColor = White)) } }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            val filtered = tools.filter { (filter == "All" || it.category == filter) && (search.isBlank() || it.title.contains(search, true) || it.description.contains(search, true)) }
            items(filtered.size) { i ->
                val item = filtered[i]
                Surface(Modifier.fillMaxWidth().clickable { onTool(item) }, color = Panel, shape = RoundedCornerShape(15.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Purple.copy(alpha = .22f), shape = RoundedCornerShape(12.dp)) { Icon(item.icon, null, tint = Purple2, modifier = Modifier.padding(11.dp).size(25.dp)) }
                        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(item.title, color = White, fontWeight = FontWeight.Bold); Text(item.description, color = Muted, fontSize = 11.sp) }; Icon(Icons.Default.ChevronRight, null, tint = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolDetailScreen(item: ToolItem?, input: String, onInput: (String) -> Unit, result: String, run: () -> Unit, back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, null, tint = White) }; Text(item?.title ?: "AI Tool", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
        Text(item?.description ?: "", color = Muted)
        OutlinedTextField(input, onInput, Modifier.fillMaxWidth().weight(.35f), placeholder = { Text("Enter your text or request…") }, shape = RoundedCornerShape(14.dp))
        Button(onClick = run, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Purple), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Run AI Tool") }
        if (result.isNotBlank()) Surface(Modifier.fillMaxWidth().weight(.65f), color = Panel, shape = RoundedCornerShape(14.dp)) { Text(result, Modifier.padding(14.dp), color = White) }
    }
}

@Composable
private fun ImageScreen(prompt: String, onPrompt: (String) -> Unit, result: String, generate: () -> Unit, back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, null, tint = White) }; Text("Image Generator", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
        OutlinedTextField(prompt, onPrompt, Modifier.fillMaxWidth(), minLines = 4, placeholder = { Text("Describe the image you want to create…") }, shape = RoundedCornerShape(14.dp))
        Text("Aspect ratio: 1:1  •  Quality: Standard", color = Muted, fontSize = 12.sp)
        Button(onClick = generate, enabled = prompt.isNotBlank(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Generate ✨") }
        if (result.isNotBlank()) Surface(Modifier.fillMaxWidth().weight(1f), color = Panel, shape = RoundedCornerShape(14.dp)) { Text(result, Modifier.padding(14.dp), color = White) }
    }
}

@Composable
private fun VideoScreen(prompt: String, onPrompt: (String) -> Unit, status: String, generate: () -> Unit, back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, null, tint = White) }; Text("Video Generator", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
        OutlinedTextField(prompt, onPrompt, Modifier.fillMaxWidth(), minLines = 4, placeholder = { Text("Describe the video scene…") }, shape = RoundedCornerShape(14.dp))
        Text("5 seconds  •  16:9", color = Muted, fontSize = 12.sp)
        Button(onClick = generate, enabled = prompt.isNotBlank(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Generate Video ▶") }
        if (status.isNotBlank()) Text(status, color = White)
    }
}

@Composable
private fun CodeScreen(code: String, onCode: (String) -> Unit, language: String, onLanguage: (String) -> Unit, result: String, run: (String) -> Unit, back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, null, tint = White) }; Text("Code Assistant", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(language.uppercase(), color = White); Icon(Icons.Default.ExpandMore, null, tint = White) }
            DropdownMenu(expanded, { expanded = false }) { listOf("python","javascript","typescript","java","kotlin","c","cpp","php","html","css","go","rust","sql","bash").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { onLanguage(it); expanded = false }) } }
        }
        OutlinedTextField(code, onCode, Modifier.fillMaxWidth().weight(.5f), minLines = 8, textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF9BE36A), fontSize = 13.sp), shape = RoundedCornerShape(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { run("explain") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Explain") }; Button(onClick = { run("debug") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Debug") } }
        Button(onClick = { run("improve") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Panel2)) { Text("Improve Code") }
        if (result.isNotBlank()) Surface(Modifier.fillMaxWidth().weight(.5f), color = Panel, shape = RoundedCornerShape(14.dp)) { LazyColumn(Modifier.padding(12.dp)) { item { Text(result, color = White, fontSize = 13.sp) } } }
    }
}

@Composable
private fun PdfScreen(back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, null, tint = White) }; Text("PDF Assistant", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
        Icon(Icons.Default.PictureAsPdf, null, tint = Purple2, modifier = Modifier.size(54.dp))
        Text("Upload and analyze PDF files", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("PDF upload and document Q&A are available in the backend. The Android file-picker workflow is the next integration step.", color = Muted)
    }
}

@Composable
private fun HistoryScreen(signedIn: Boolean) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("History", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(if (signedIn) "Your saved conversations are stored securely on the RUK AI server. Conversation history sync is the next UI integration step." else "Sign in to sync your conversation history.", color = Muted)
    }
}

@Composable
private fun ProfileScreen(base: String, onBase: (String) -> Unit, email: String, onEmail: (String) -> Unit, password: String, onPassword: (String) -> Unit, displayName: String, onName: (String) -> Unit, mode: String, onMode: (String) -> Unit, signedIn: Boolean, name: String, plan: String, message: String, auth: () -> Unit, provider: String, onProvider: (String) -> Unit, key: String, onKey: (String) -> Unit, saveKey: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Profile", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) { RukLogo(size = 68.dp); Spacer(Modifier.width(13.dp)); Column { Text(if (signedIn) name else "Welcome to RUK AI", color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(if (signedIn) plan else "Free Plan • Sign in to continue", color = Muted) } }
        }
        item { Text("Backend URL", color = White, fontWeight = FontWeight.Bold); OutlinedTextField(base, onBase, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("https://your-ruk-ai-server.com") }); Text("For a physical phone, use your deployed HTTPS backend URL. 10.0.2.2 is for Android emulator only.", color = Muted, fontSize = 11.sp) }
        if (!signedIn) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = mode == "login", onClick = { onMode("login") }, label = { Text("Login") }); FilterChip(selected = mode == "register", onClick = { onMode("register") }, label = { Text("Register") }) } }
            if (mode == "register") item { OutlinedTextField(displayName, onName, Modifier.fillMaxWidth(), label = { Text("Display name") }, singleLine = true) }
            item { OutlinedTextField(email, onEmail, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)) }
            item { OutlinedTextField(password, onPassword, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation()) }
            item { Button(onClick = auth, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text(if (mode == "register") "Create Account" else "Sign In") } }
        } else {
            item { Text("Provider API Keys", color = White, fontWeight = FontWeight.Bold) }
            item { var expanded by remember { mutableStateOf(false) }; Box { OutlinedButton(onClick = { expanded = true }) { Text(provider, color = White); Icon(Icons.Default.ExpandMore, null, tint = White) }; DropdownMenu(expanded, { expanded = false }) { listOf("openrouter","openai","gemini","together","stability","fal").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { onProvider(it); expanded = false }) } } } }
            item { OutlinedTextField(key, onKey, Modifier.fillMaxWidth(), label = { Text("API Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation()) }
            item { Button(onClick = saveKey, enabled = key.length >= 8, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Save API Key Securely") } }
            item { Text("Keys are encrypted by the backend and are never returned in plaintext.", color = Muted, fontSize = 11.sp) }
        }
        if (message.isNotBlank()) item { Text(message, color = if (message.contains("Error", true)) Color(0xFFFF8A80) else Purple2, fontSize = 12.sp) }
    }
}
