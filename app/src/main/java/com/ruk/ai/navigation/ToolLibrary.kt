package com.ruk.ai.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.TrendingUp
import com.ruk.ai.ui.theme.RukColors

/**
 * The AI Tools library. Each entry maps to a real backend tool id
 * (`/api/v1/tools/run`) or to a dedicated screen.
 */
val ToolLibrary: List<ToolEntry> = listOf(
    ToolEntry(
        id = "writer",
        title = "AI Writing Assistant",
        description = "Write articles, blogs, emails and content in any tone.",
        category = ToolCategory.WRITING,
        icon = Icons.Filled.Edit,
        accent = RukColors.Purple
    ),
    ToolEntry(
        id = "image",
        title = "AI Image Generator",
        description = "Create stunning images from a text prompt.",
        category = ToolCategory.MEDIA,
        icon = Icons.Filled.Image,
        accent = RukColors.AccentBlue
    ),
    ToolEntry(
        id = "video",
        title = "AI Video Generator",
        description = "Turn text or a still image into a short video.",
        category = ToolCategory.MEDIA,
        icon = Icons.Filled.Movie,
        accent = RukColors.AccentMagenta
    ),
    ToolEntry(
        id = "code",
        title = "AI Code Assistant",
        description = "Write, explain, debug and run code in 14 languages.",
        category = ToolCategory.CODE,
        icon = Icons.Filled.Code,
        accent = RukColors.AccentCyan
    ),
    ToolEntry(
        id = "summarizer",
        title = "AI Summarizer",
        description = "Summarise long text, articles and reports.",
        category = ToolCategory.WRITING,
        icon = Icons.Filled.Summarize,
        accent = RukColors.AccentGreen
    ),
    ToolEntry(
        id = "translator",
        title = "AI Translator",
        description = "Translate any language instantly and faithfully.",
        category = ToolCategory.WRITING,
        icon = Icons.Filled.Language,
        accent = RukColors.AccentBlue
    ),
    ToolEntry(
        id = "pdf",
        title = "AI PDF Assistant",
        description = "Upload a PDF, summarise it and ask questions.",
        category = ToolCategory.UTILITY,
        icon = Icons.Filled.Description,
        accent = RukColors.AccentRed
    ),
    ToolEntry(
        id = "voice",
        title = "AI Voice Generator",
        description = "Dictate a prompt and hear answers read back.",
        category = ToolCategory.MEDIA,
        icon = Icons.Filled.RecordVoiceOver,
        accent = RukColors.AccentLavender
    ),
    ToolEntry(
        id = "math",
        title = "AI Math Solver",
        description = "Step-by-step solutions with full working shown.",
        category = ToolCategory.UTILITY,
        icon = Icons.Filled.Calculate,
        accent = RukColors.AccentCyan
    ),
    ToolEntry(
        id = "seo",
        title = "AI SEO Tools",
        description = "Titles, meta descriptions, keywords and structure.",
        category = ToolCategory.UTILITY,
        icon = Icons.Filled.TrendingUp,
        accent = RukColors.AccentGreen
    ),
    ToolEntry(
        id = "research",
        title = "AI Research Assistant",
        description = "Structured briefs that separate fact from uncertainty.",
        category = ToolCategory.UTILITY,
        icon = Icons.Filled.Science,
        accent = RukColors.AccentLavender
    ),
    ToolEntry(
        id = "chat",
        title = "AI Chatbot",
        description = "Open a full conversation with any supported model.",
        category = ToolCategory.UTILITY,
        icon = Icons.Filled.AutoAwesome,
        accent = RukColors.PurpleLight
    )
)

fun toolById(id: String): ToolEntry? = ToolLibrary.firstOrNull { it.id == id }
