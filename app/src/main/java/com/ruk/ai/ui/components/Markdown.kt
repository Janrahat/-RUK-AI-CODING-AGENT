package com.ruk.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ruk.ai.ui.theme.CodeTextStyle
import com.ruk.ai.ui.theme.RukColors

/**
 * A compact Markdown renderer for chat answers.
 *
 * Supports fenced code blocks (with a language label and horizontal
 * scroll), ATX headings, bullet and numbered lists, block quotes, bold,
 * italic and inline code. It is intentionally small: the app renders model
 * output, not arbitrary documents, and this avoids pulling a large
 * rendering dependency into the APK.
 */
private sealed interface Block {
    data class Code(val language: String, val body: String) : Block
    data class Heading(val level: Int, val text: String) : Block
    data class Bullet(val text: String, val ordered: Boolean, val index: Int) : Block
    data class Quote(val text: String) : Block
    data class Paragraph(val text: String) : Block
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = RukColors.White
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    Column(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Block.Code -> CodeBlock(block.language, block.body)
                is Block.Heading -> Text(
                    text = inline(block.text, textColor),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    color = textColor
                )
                is Block.Bullet -> Row(Modifier.fillMaxWidth()) {
                    Text(
                        if (block.ordered) "${block.index}." else "\u2022",
                        color = RukColors.PurpleLight,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.width(22.dp)
                    )
                    SelectionContainer {
                        Text(inline(block.text, textColor), style = MaterialTheme.typography.bodyLarge, color = textColor)
                    }
                }
                is Block.Quote -> Row(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(RukColors.Purple)
                    ) {}
                    Spacer(Modifier.width(10.dp))
                    SelectionContainer {
                        Text(
                            inline(block.text, RukColors.Grey),
                            style = MaterialTheme.typography.bodyMedium,
                            color = RukColors.Grey,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
                is Block.Paragraph -> SelectionContainer {
                    Text(inline(block.text, textColor), style = MaterialTheme.typography.bodyLarge, color = textColor)
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(language: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B0B12))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(RukColors.SurfaceHigh)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                language.ifBlank { "code" }.lowercase(),
                style = MaterialTheme.typography.labelSmall,
                color = RukColors.PurpleLight
            )
        }
        SelectionContainer {
            Text(
                text = body.trimEnd(),
                style = CodeTextStyle,
                color = Color(0xFFE6E6F0),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            )
        }
    }
}

private fun parseBlocks(markdown: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val lines = markdown.replace("\r\n", "\n").split("\n")
    var index = 0
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) {
            blocks += Block.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
    }

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        when {
            trimmed.startsWith("```") -> {
                flushParagraph()
                val language = trimmed.removePrefix("```").trim()
                val body = StringBuilder()
                index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    body.appendLine(lines[index])
                    index++
                }
                blocks += Block.Code(language, body.toString())
            }
            trimmed.startsWith("#") -> {
                flushParagraph()
                val level = trimmed.takeWhile { it == '#' }.length.coerceAtMost(3)
                blocks += Block.Heading(level, trimmed.drop(level).trim())
            }
            trimmed.startsWith("> ") -> {
                flushParagraph()
                blocks += Block.Quote(trimmed.removePrefix("> ").trim())
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                blocks += Block.Bullet(trimmed.drop(2).trim(), ordered = false, index = 0)
            }
            Regex("^\\d+\\.\\s").containsMatchIn(trimmed) -> {
                flushParagraph()
                val number = trimmed.takeWhile { it.isDigit() }.toIntOrNull() ?: 1
                blocks += Block.Bullet(trimmed.dropWhile { it.isDigit() }.drop(1).trim(), ordered = true, index = number)
            }
            trimmed.isEmpty() -> flushParagraph()
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
        index++
    }
    flushParagraph()
    return blocks
}

/** Renders **bold**, *italic* and `inline code` spans. */
private fun inline(text: String, baseColor: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > 0) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = RukColors.PurpleLight,
                            background = Color(0xFF1A1A24)
                        )
                    ) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > 0) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            else -> {
                append(text[i]); i++
            }
        }
    }
}
