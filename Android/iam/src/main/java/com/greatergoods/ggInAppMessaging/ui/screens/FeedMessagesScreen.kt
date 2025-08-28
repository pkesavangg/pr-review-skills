package com.greatergoods.ggInAppMessaging.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greatergoods.ggInAppMessaging.theme.getIAMColors

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
  val iamColors = getIAMColors()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(iamColors.backgroundPrimary)
      .verticalScroll(rememberScrollState()),
  ) {
    // Section Header
    SectionHeader(
      title = "Deals on Goods",
      onSettingsClick = onSettingsPress,
      iamColors = iamColors,
    )

    // Empty State Content
    EmptyStateContent(iamColors = iamColors)
  }
}

@Composable
private fun SectionHeader(
  title: String,
  onSettingsClick: () -> Unit,
  iamColors: com.greatergoods.ggInAppMessaging.theme.IAMColorScheme
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(iamColors.backgroundSecondary)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Deals Icon (stylized 'G' or price tag)
    Box(
      modifier = Modifier
        .size(24.dp)
        .background(
          color = iamColors.brandWgPrimary,
          shape = MaterialTheme.shapes.small,
        ),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = "G",
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
      )
    }

    Spacer(modifier = Modifier.width(12.dp))

    // Section Title
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = iamColors.textHeading,
      modifier = Modifier.weight(1f),
    )

    // Settings Icon
    IconButton(
      onClick = onSettingsClick,
      modifier = Modifier.size(40.dp),
    ) {
      Icon(
        imageVector = Icons.Default.Settings,
        contentDescription = "Settings",
        tint = iamColors.brandWgPrimary,
        modifier = Modifier.size(24.dp),
      )
    }
  }
}

@Composable
private fun EmptyStateContent(
  iamColors: com.greatergoods.ggInAppMessaging.theme.IAMColorScheme
) {
  Box(
    modifier = Modifier
      .fillMaxWidth(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(32.dp),
    ) {
      // Primary Empty State Message
      Text(
        text = "Dry on Deals...for Now",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = iamColors.textHeading,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Secondary Message
      Text(
        text = "check back soon",
        style = MaterialTheme.typography.bodyLarge,
        color = iamColors.textBody,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      )
    }
  }
}


@Preview
@Composable
fun FeedMessagesScreenPreview(){
  FeedMessagesScreen({})
}
