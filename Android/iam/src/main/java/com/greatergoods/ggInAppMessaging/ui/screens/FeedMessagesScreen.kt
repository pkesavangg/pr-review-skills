package com.greatergoods.ggInAppMessaging.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greatergoods.ggInAppMessaging.features.common.AppIcon
import com.greatergoods.ggInAppMessaging.features.common.AppIconType
import com.greatergoods.ggInAppMessaging.features.resources.AppIcons
import com.greatergoods.ggInAppMessaging.theme.IamTheme

/**
 * Main Feed Messages Screen Content
 * Displays deals and messages with empty state handling
 * Note: Top navigation bar is provided by the app
 */
@Composable
fun FeedMessagesScreen(
  onSettingsPress: () -> Unit,
  modifier: Modifier = Modifier
) {
  // Global composable approach - automatically recomposes when colors change anywhere
  val colors = IamTheme.colors

  Column(
    modifier = modifier
      .background(colors.primaryBackground)
      .fillMaxWidth()
      .fillMaxHeight(),
  ) {
    // Section Header
    SectionHeader(
      title = "Deals on Goods",
      onSettingsClick = onSettingsPress,
    )
    // Empty State Content (using weight for vertical centering)
    EmptyStateContent()
  }
}

@Composable
fun SectionHeader(
  title: String,
  onSettingsClick: () -> Unit,
) {

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AppIcon(
      id = AppIcons.Logo,
      contentDescription = "Deals Logo",
      tintColor = Color.Unspecified,
      type = AppIconType.Tertiary,
      onClick = { /* action */ },
      modifier = Modifier.size(24.dp),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.weight(1f),
    )
    AppIcon(
      id = AppIcons.Settings, // Using system settings icon
      contentDescription = "Settings",
      type = AppIconType.Primary, // Will automatically use colors.brandWgPrimary
      onClick = onSettingsClick,
      modifier = Modifier.size(24.dp),
    )
  }
}

@Composable
private fun EmptyStateContent() {
  // Use a Box with fillMaxSize to center the column vertically
  androidx.compose.foundation.layout.Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .fillMaxWidth()
      .fillMaxHeight()
      .background(color = IamTheme.colors.primaryBackground),
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(horizontal = 32.dp),
    ) {
      // Primary Empty State Message
      Text(
        text = "Dry on Deals...for Now",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = IamTheme.colors.textHeading,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Secondary Message
      Text(
        text = "check back soon",
        style = MaterialTheme.typography.bodyLarge,
        color = IamTheme.colors.textBody,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      )
    }
  }
}

@Preview
@Composable
fun FeedMessagesScreenPreview() {
  FeedMessagesScreen({})
}
