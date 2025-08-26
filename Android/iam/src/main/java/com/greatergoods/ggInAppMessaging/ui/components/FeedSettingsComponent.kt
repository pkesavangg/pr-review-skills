package com.greatergoods.ggInAppMessaging.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greatergoods.ggInAppMessaging.domain.constants.FeedStrings
import com.greatergoods.ggInAppMessaging.domain.models.FeedSetting
import com.greatergoods.ggInAppMessaging.core.service.GGInAppMessagingService
import kotlinx.coroutines.launch

/**
 * Feed settings component for managing user preferences
 * Android equivalent of Angular feed-settings component
 */
@Composable
fun FeedSettingsComponent(
    feedSetting: FeedSetting? = null,
    onDismiss: () -> Unit = {},
    onSettingsChanged: (FeedSetting) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var togglePopup by remember { mutableStateOf(feedSetting?.showPopupMessage ?: true) }
    var toggleBadges by remember { mutableStateOf(feedSetting?.showNotificationBadge ?: true) }

    val scope = rememberCoroutineScope()

    // Update local state when feedSetting changes
    LaunchedEffect(feedSetting) {
        feedSetting?.let {
            togglePopup = it.showPopupMessage
            toggleBadges = it.showNotificationBadge
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp)
                ) {
                    Text(
                        text = FeedStrings.Feeds.MESSAGE_SETTINGS,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Content
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Popup Messages Toggle
                    SettingsToggleItem(
                        title = FeedStrings.Feeds.POPUP_MESSAGE,
                        description = "Show popup messages for new feeds",
                        checked = togglePopup,
                        onCheckedChange = { isChecked ->
                            togglePopup = isChecked
                            scope.launch {
                                val newSettings = FeedSetting(
                                    showPopupMessage = isChecked,
                                    showNotificationBadge = toggleBadges
                                )
                                onSettingsChanged(newSettings)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Notification Badges Toggle
                    SettingsToggleItem(
                        title = FeedStrings.Feeds.NOTIFICATION,
                        description = "Show notification badges for unread feeds",
                        checked = toggleBadges,
                        onCheckedChange = { isChecked ->
                            toggleBadges = isChecked
                            scope.launch {
                                val newSettings = FeedSetting(
                                    showPopupMessage = togglePopup,
                                    showNotificationBadge = isChecked
                                )
                                onSettingsChanged(newSettings)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Info text
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Settings Information",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "These settings control how you receive feed notifications and messages. Changes are saved automatically.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FeedSettingsComponentPreview() {
    val sampleFeedSetting = FeedSetting(
        showPopupMessage = true,
        showNotificationBadge = false
    )

    MaterialTheme {
        FeedSettingsComponent(
            feedSetting = sampleFeedSetting,
            modifier = Modifier.padding(16.dp)
        )
    }
}
