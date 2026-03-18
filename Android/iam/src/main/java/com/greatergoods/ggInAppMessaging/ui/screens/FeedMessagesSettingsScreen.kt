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
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.ggInAppMessaging.theme.IamTheme
import com.greatergoods.ggInAppMessaging.ui.strings.FeedMessagesSettingsStrings
import com.greatergoods.ggInAppMessaging.ui.viewmodel.FeedMessagesIntent
import com.greatergoods.ggInAppMessaging.ui.viewmodel.FeedMessagesViewModel

/**
 * Feed Messages Settings Screen Content
 * Allows users to configure pop-up messages and notification badges
 * Note: Top navigation bar is provided by the app
 */
@Composable
fun FeedMessagesSettingsScreen(
  modifier: Modifier = Modifier
) {
  val viewModel: FeedMessagesViewModel = hiltViewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()

  // Load settings when screen is first displayed
  LaunchedEffect(Unit) {
    viewModel.handleIntent(FeedMessagesIntent.LoadFeedSettings)
  }

  FeedMessagesSettingsContent(
    state = state,
    handleIntent = viewModel::handleIntent,
    modifier = modifier
  )
}

@Composable
private fun FeedMessagesSettingsContent(
  state: com.greatergoods.ggInAppMessaging.ui.viewmodel.FeedMessagesState,
  handleIntent: (FeedMessagesIntent) -> Unit,
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
      popUpMessagesEnabled = state.popUpMessagesEnabled,
      notificationBadgesEnabled = state.notificationBadgesEnabled,
      onPopUpMessagesToggle = { enabled ->
        handleIntent(FeedMessagesIntent.TogglePopUpMessages(enabled))
      },
      onNotificationBadgesToggle = { enabled ->
        handleIntent(FeedMessagesIntent.ToggleNotificationBadges(enabled))
      },
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
      title = FeedMessagesSettingsStrings.PopUpMessagesTitle,
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
      title = FeedMessagesSettingsStrings.NotificationBadgesTitle,
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
      colors = SwitchDefaults.colors(
        checkedThumbColor = IamTheme.colors.primaryBackground,
        checkedTrackColor = IamTheme.colors.primaryAction,
        uncheckedThumbColor = IamTheme.colors.iconSecondary,
        uncheckedTrackColor = IamTheme.colors.utility,
        disabledCheckedThumbColor = IamTheme.colors.secondaryActionDisabled,
        disabledCheckedTrackColor = IamTheme.colors.secondaryActionDisabled,
        disabledUncheckedThumbColor = IamTheme.colors.secondaryActionDisabled,
        disabledUncheckedTrackColor = IamTheme.colors.secondaryActionDisabled,
        checkedBorderColor = IamTheme.colors.primaryAction,
        uncheckedBorderColor = IamTheme.colors.iconSecondary,
        disabledCheckedBorderColor = IamTheme.colors.secondaryActionDisabled,
        disabledUncheckedBorderColor = IamTheme.colors.secondaryActionDisabled,
      ),
    )
  }
}

// Preview removed - @Preview annotation not available in IAM package
@Preview
@Composable
fun FeedMessagesSettingsScreenPreview() {
  // Note: Preview requires a mock ViewModel which is not available in IAM package
  // This preview is for demonstration purposes only
  val dummyState = com.greatergoods.ggInAppMessaging.ui.viewmodel.FeedMessagesState(
    popUpMessagesEnabled = true,
    notificationBadgesEnabled = true
  )

  FeedMessagesSettingsContent(
    state = dummyState,
    handleIntent = {},
  )
}
