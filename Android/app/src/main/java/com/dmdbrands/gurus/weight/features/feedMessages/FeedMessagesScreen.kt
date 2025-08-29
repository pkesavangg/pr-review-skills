package com.dmdbrands.gurus.weight.features.feedMessages

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.feedMessages.viewmodel.FeedMessagesViewModel
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.greatergoods.ggInAppMessaging.ui.screens.FeedMessagesScreen

/**
 * Feed Messages Screen with TopAppBar
 * Reuses content from IAM package and adds navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedMessagesScreen(
  onBackPress: () -> Unit,
  onSettingsPress: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: FeedMessagesViewModel = hiltViewModel()
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Messages",
            style = MeTheme.typography.body1,
            fontWeight = FontWeight.Bold,
          )
        },
        navigationIcon = {
          IconButton(onClick = onBackPress) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = MeTheme.colorScheme.primaryBackground,
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MeTheme.colorScheme.primaryBackground,
          titleContentColor = MeTheme.colorScheme.primaryBackground,
        ),
      )
    },
    modifier = modifier.fillMaxSize(),
  ) { paddingValues ->
    // Reuse the IAM FeedMessagesScreen content
    FeedMessagesScreen(
      onSettingsPress = onSettingsPress,
      modifier = Modifier.padding(paddingValues),
    )
  }
}

@PreviewTheme
@Composable
fun FeedMessagesScreenPreview() {
  MeAppTheme {
    FeedMessagesScreen(
      onBackPress = {},
      onSettingsPress = {},
    )
  }
}


