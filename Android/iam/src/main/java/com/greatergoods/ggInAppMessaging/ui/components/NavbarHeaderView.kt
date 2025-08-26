package com.greatergoods.ggInAppMessaging.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Navigation header component
 * Android equivalent of iOS NavbarHeaderView
 */
@Composable
fun NavbarHeaderView(
    title: String,
    leadingIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    trailingIcon: ImageVector = Icons.Default.Settings,
    onLeadingTap: () -> Unit = {},
    onTrailingTap: () -> Unit = {},
    canShowBorder: Boolean = false
) {
    val borderModifier = if (canShowBorder) {
        Modifier.border(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .then(borderModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Leading icon
        Icon(
            imageVector = leadingIcon,
            contentDescription = "Back",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable { onLeadingTap() }
                .padding(4.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )

        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center)
        )

        // Trailing icon
        Icon(
            imageVector = trailingIcon,
            contentDescription = "Settings",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clickable { onTrailingTap() }
                .padding(4.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview
@Composable
private fun NavbarHeaderViewPreview() {
    MaterialTheme {
        NavbarHeaderView(
            title = "Messages",
            canShowBorder = true
        )
    }
}
