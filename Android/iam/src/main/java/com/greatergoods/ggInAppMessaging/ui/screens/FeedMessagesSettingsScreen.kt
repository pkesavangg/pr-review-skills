package com.greatergoods.ggInAppMessaging.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greatergoods.ggInAppMessaging.theme.getIAMColors

/**
 * Feed Messages Settings Screen Content
 * Allows users to configure pop-up messages and notification badges
 * Note: Top navigation bar is provided by the app
 */
@Composable
fun FeedMessagesSettingsScreen(
  onPopUpMessagesToggle: (Boolean) -> Unit,
  onNotificationBadgesToggle: (Boolean) -> Unit,
  popUpMessagesEnabled: Boolean = true,
  notificationBadgesEnabled: Boolean = true,
  modifier: Modifier = Modifier
) {
  val iamColors = getIAMColors()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(iamColors.backgroundPrimary),
  ) {
    // Settings Content
    SettingsContent(
      popUpMessagesEnabled = popUpMessagesEnabled,
      notificationBadgesEnabled = notificationBadgesEnabled,
      onPopUpMessagesToggle = onPopUpMessagesToggle,
      onNotificationBadgesToggle = onNotificationBadgesToggle,
      iamColors = iamColors,
    )
  }
}

@Composable
private fun SettingsContent(
  popUpMessagesEnabled: Boolean,
  notificationBadgesEnabled: Boolean,
  onPopUpMessagesToggle: (Boolean) -> Unit,
  onNotificationBadgesToggle: (Boolean) -> Unit,
  iamColors: com.greatergoods.ggInAppMessaging.theme.IAMColorScheme
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
  ) {
    // Pop-up Messages Setting
    SettingRow(
      title = "Pop-up Messages",
      isEnabled = popUpMessagesEnabled,
      onToggle = onPopUpMessagesToggle,
      iamColors = iamColors,
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Notification Badges Setting
    SettingRow(
      title = "Notification Badges",
      isEnabled = notificationBadgesEnabled,
      onToggle = onNotificationBadgesToggle,
      iamColors = iamColors,
    )
  }
}

@Composable
private fun SettingRow(
  title: String,
  isEnabled: Boolean,
  onToggle: (Boolean) -> Unit,
  iamColors: com.greatergoods.ggInAppMessaging.theme.IAMColorScheme
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(
        color = iamColors.backgroundCard,
        shape = MaterialTheme.shapes.medium,
      )
      .padding(horizontal = 16.dp, vertical = 20.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Setting Title
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Medium,
      color = iamColors.textHeading,
      modifier = Modifier.weight(1f),
    )

    // Toggle Switch
    Switch(
      checked = isEnabled,
      onCheckedChange = onToggle,
      colors = SwitchDefaults.colors(
        checkedThumbColor = iamColors.actionInverse,
        checkedTrackColor = iamColors.actionPrimary,
        uncheckedThumbColor = iamColors.iconSecondary,
        uncheckedTrackColor = iamColors.backgroundTertiary,
      ),
    )
  }
}

// Preview removed - @Preview annotation not available in IAM package
