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
import com.greatergoods.ggInAppMessaging.theme.ColorTokens
import com.greatergoods.ggInAppMessaging.theme.rememberIAMColors

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
  // Get ColorTokens with automatic recomposition on theme changes
  val colors = rememberIAMColors()

  // Show nothing if colors are not yet initialized
  if (colors == null) {
    return
  }

  Column(
    modifier = modifier
      .background(colors.primaryBackground)
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
  ) {
    // Section Header
    SectionHeader(
      title = "Deals on Goods",
      onSettingsClick = onSettingsPress,
      colors = colors,
    )

    // Empty State Content
    EmptyStateContent(colors = colors)
  }
}

@Composable
private fun SectionHeader(
  title: String,
  onSettingsClick: () -> Unit,
  colors: ColorTokens
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(colors.secondaryBackground)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Deals Icon (stylized 'G' or price tag)
    Box(
      modifier = Modifier
        .size(24.dp)
        .background(
          color = colors.brandWgPrimary,
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
      color = colors.textHeading,
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
        tint = colors.brandWgPrimary,
        modifier = Modifier.size(24.dp),
      )
    }
  }
}

@Composable
private fun EmptyStateContent(
  colors: ColorTokens
) {
  Box(
    modifier = Modifier
      .fillMaxWidth().background(color = colors.primaryBackground),
    contentAlignment = Alignment.Center,
  ) {
    Text("${colors.primaryBackground}")
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(32.dp),
    ) {
      // Primary Empty State Message
      Text(
        text = "Dry on Deals...for Now",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = colors.textHeading,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Secondary Message
      Text(
        text = "check back soon",
        style = MaterialTheme.typography.bodyLarge,
        color = colors.textBody,
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
