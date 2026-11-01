package com.ruk.ai.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.ui.graphics.vector.ImageVector

/** Every destination in the app. */
object Routes {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val HOME = "home"
    const val CHAT = "chat"
    const val TOOLS = "tools"
    const val HISTORY = "history"
    const val PROFILE = "profile"
    const val IMAGE_GEN = "image_gen"
    const val VIDEO_GEN = "video_gen"
    const val CODE = "code"
    const val PDF = "pdf"
    const val TOOL_DETAIL = "tool_detail/{toolId}"
    const val API_KEYS = "api_keys"
    const val SETTINGS = "settings"
    const val SAVED = "saved"

    fun toolDetail(id: String) = "tool_detail/$id"
}

/** The five bottom-bar tabs, in the order shown in the reference design. */
enum class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME(Routes.HOME, "Home", Icons.Filled.Home),
    CHAT(Routes.CHAT, "Chat", Icons.Filled.ChatBubbleOutline),
    TOOLS(Routes.TOOLS, "Tools", Icons.Filled.GridView),
    HISTORY(Routes.HISTORY, "History", Icons.Filled.History),
    PROFILE(Routes.PROFILE, "Profile", Icons.Filled.PersonOutline);

    companion object {
        val routes: Set<String> = entries.map { it.route }.toSet()
        fun forRoute(route: String?): BottomTab? = entries.firstOrNull { it.route == route }
    }
}

/** A dashboard quick-action card. */
data class DashboardCard(
    val title: String,
    val subtitle: String,
    val route: String,
    val icon: ImageVector,
    val accent: androidx.compose.ui.graphics.Color
)

/** A tool in the AI Tools library. */
data class ToolEntry(
    val id: String,
    val title: String,
    val description: String,
    val category: ToolCategory,
    val icon: ImageVector,
    val accent: androidx.compose.ui.graphics.Color
)

enum class ToolCategory(val label: String) {
    ALL("All"),
    WRITING("Writing"),
    MEDIA("Media"),
    CODE("Code"),
    UTILITY("Utility")
}
