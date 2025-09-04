package com.greatergoods.ggInAppMessaging.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greatergoods.ggInAppMessaging.theme.IamTheme

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
  val iamColors = IamTheme.colors

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(iamColors.secondaryBackground),
  ) {
    // Settings Content
    SettingsContent(
      popUpMessagesEnabled = popUpMessagesEnabled,
      notificationBadgesEnabled = notificationBadgesEnabled,
      onPopUpMessagesToggle = onPopUpMessagesToggle,
      onNotificationBadgesToggle = onNotificationBadgesToggle,
    )
  }
}

@Composable
private fun SettingsContent(
  popUpMessagesEnabled: Boolean,
  notificationBadgesEnabled: Boolean,
  onPopUpMessagesToggle: (Boolean) -> Unit,
  onNotificationBadgesToggle: (Boolean) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(IamTheme.colors.secondaryBackground),
  ) {
    // Pop-up Messages Setting
    SettingRow(
      title = "Pop-up Messages",
      isEnabled = popUpMessagesEnabled,
      onToggle = onPopUpMessagesToggle,
    )
    HorizontalDivider(
      modifier = Modifier.fillMaxWidth(),
      thickness = 0.5.dp,
      color = IamTheme.colors.utility,
    )
    // Notification Badges Setting
    SettingRow(
      title = "Notification Badges",
      isEnabled = notificationBadgesEnabled,
      onToggle = onNotificationBadgesToggle,
    )
  }
}

@Composable
private fun SettingRow(
  title: String,
  isEnabled: Boolean,
  onToggle: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Setting Title
    Text(
      text = title,
      color = IamTheme.colors.textBody,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Normal,
      modifier = Modifier.weight(1f),
    )

    // Toggle Switch
    Switch(
      checked = isEnabled,
      onCheckedChange = onToggle,
      // modifier = Modifier.height(56.dp),
      colors = SwitchDefaults.colors(
        checkedThumbColor = IamTheme.colors.inverseAction,
        checkedTrackColor = IamTheme.colors.primaryAction,
        uncheckedThumbColor = IamTheme.colors.iconSecondary,
        uncheckedTrackColor = IamTheme.colors.tertiaryAction,
      ),
    )
  }
}

// Preview removed - @Preview annotation not available in IAM package
@Preview
@Composable
fun SettingsPreview() {
  FeedMessagesSettingsScreen({}, {})
}
