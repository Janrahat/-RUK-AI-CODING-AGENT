package com.ruk.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ruk.ai.navigation.Routes
import com.ruk.ai.navigation.ToolCategory
import com.ruk.ai.navigation.ToolEntry
import com.ruk.ai.navigation.ToolLibrary
import com.ruk.ai.ui.components.ProBadge
import com.ruk.ai.ui.components.RukPill
import com.ruk.ai.ui.components.RukTopBar
import com.ruk.ai.ui.theme.RukColors

/** The AI Tools library: search, category filters and a list of tool cards. */
@Composable
fun ToolsScreen(nav: NavController) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ToolCategory.ALL) }

    val filtered = ToolLibrary.filter { tool ->
        val matchesCategory = category == ToolCategory.ALL || tool.category == category
        val matchesQuery = query.isBlank() ||
            tool.title.contains(query, ignoreCase = true) ||
            tool.description.contains(query, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(RukColors.Black)
    ) {
        RukTopBar(
            title = "AI Tools",
            onBack = { nav.popBackStack() },
            trailing = { ProBadge() }
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search tools\u2026", color = RukColors.GreyDim) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = RukColors.Grey) },
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RukColors.Purple,
                unfocusedBorderColor = RukColors.SurfaceBorder,
                focusedTextColor = RukColors.White,
                unfocusedTextColor = RukColors.White,
                cursorColor = RukColors.PurpleLight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToolCategory.entries.forEach { entry ->
                RukPill(
                    text = entry.label,
                    selected = entry == category,
                    onClick = { category = entry }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "No tools match \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RukColors.Grey,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            }
            items(filtered) { tool ->
                ToolRow(tool) { nav.navigate(Routes.toolDetail(tool.id)) }
            }
        }
    }
}

@Composable
private fun ToolRow(tool: ToolEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RukColors.Surface)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tool.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(tool.icon, contentDescription = null, tint = tool.accent, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tool.title,
                style = MaterialTheme.typography.titleMedium,
                color = RukColors.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = RukColors.Grey,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = RukColors.GreyDim,
            modifier = Modifier.size(20.dp)
        )
    }
}
