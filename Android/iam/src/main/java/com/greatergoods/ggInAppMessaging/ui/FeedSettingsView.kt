package com.greatergoods.ggInAppMessaging.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.ggInAppMessaging.ui.viewmodels.FeedSettingsViewModel
import com.greatergoods.ggInAppMessaging.ui.strings.FeedSettingsStrings

/**
 * Feed settings dialog
 * Android equivalent of iOS FeedSettingsView
 */
@Composable
fun FeedSettingsView(
    onDismiss: () -> Unit,
    viewModel: FeedSettingsViewModel = hiltViewModel()
) {
    val feedSetting by viewModel.feedSetting.collectAsState()
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Title
                Text(
                    text = FeedSettingsStrings.MessageSettings,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Settings options
                feedSetting?.let { setting ->
                    SettingRow(
                        title = FeedSettingsStrings.ShowPopupMessage,
                        description = FeedSettingsStrings.ShowPopupMessageDescription,
                        isEnabled = setting.showPopupMessage,
                        onToggle = { enabled ->
                            viewModel.updatePopupMessageSetting(enabled)
                        }
                    )
                    
                    SettingRow(
                        title = FeedSettingsStrings.ShowNotificationBadge,
                        description = FeedSettingsStrings.ShowNotificationBadgeDescription,
                        isEnabled = setting.showNotificationBadge,
                        onToggle = { enabled ->
                            viewModel.updateNotificationBadgeSetting(enabled)
                        }
                    )
                }
                
                // Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(FeedSettingsStrings.Done)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle
        )
    }
}

@Preview
@Composable
private fun FeedSettingsViewPreview() {
    MaterialTheme {
        FeedSettingsView(onDismiss = {})
    }
}