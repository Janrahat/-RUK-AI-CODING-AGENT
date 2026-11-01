package com.ruk.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
// Material icons are extension properties, so they must be *imported* — a
// fully-qualified `Icons.AutoMirrored.Filled.X` expression does not resolve.
// KeyboardArrowRight ships in material-icons-core (not the extended set).
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ruk.ai.navigation.BottomTab
import com.ruk.ai.ui.theme.RukColors

/** Five-item bottom navigation bar matching the reference design. */
@Composable
fun RukBottomBar(current: BottomTab, onSelect: (BottomTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(RukColors.Black)
            .height(64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BottomTab.entries.forEach { tab ->
            val selected = tab == current
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    tab.icon,
                    contentDescription = tab.label,
                    tint = if (selected) RukColors.PurpleLight else RukColors.GreyDim,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) RukColors.PurpleLight else RukColors.GreyDim
                )
            }
        }
    }
}

/** Thin divider used between list rows. */
@Composable
fun RukDivider(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RukColors.SurfaceBorder)
    ) {}
}

/** A settings-style row: icon, label, optional value, chevron. */
@Composable
fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String? = null,
    tint: Color = RukColors.White,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = RukColors.White,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        )
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodySmall, color = RukColors.Grey)
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = RukColors.GreyDim,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(18.dp)
            )
        }
    }
}
