package com.dmdbrands.gurus.weight.features.feedMessages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.feedMessages.model.FeedMessagesIntent
import com.dmdbrands.gurus.weight.features.feedMessages.strings.FeedMessagesStrings
import com.dmdbrands.gurus.weight.features.feedMessages.viewmodel.FeedMessagesViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.greatergoods.ggInAppMessaging.ui.screens.FeedMessagesScreen

/**
 * Feed Messages Screen with TopAppBar
 * Reuses content from IAM package and adds navigation
 * Follows MVI pattern with ViewModel
 */
@Composable
fun AppFeedMessagesScreen(
  modifier: Modifier = Modifier,
) {
  val viewModel: FeedMessagesViewModel = hiltViewModel()
  val state by viewModel.state.collectAsState()

  // Initialize the screen
  LaunchedEffect(Unit) {
    viewModel.handleIntent(FeedMessagesIntent.Initialize)
  }

  AppScaffold(
    title = FeedMessagesStrings.Title,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) {
        viewModel.handleIntent(FeedMessagesIntent.OnBackPress)
      }
    },
    containerColor = colorScheme.secondaryBackground,
    appBarColor = colorScheme.primaryBackground,
    modifier = modifier.fillMaxSize(),
  ) { scaffoldModifier ->
    // Reuse the IAM FeedMessagesScreen content
    Column(
      modifier = Modifier
        .background(colorScheme.primaryBackground)
        .fillMaxSize()
        // .padding(horizontal = 16.dp),
    ) {
      FeedMessagesScreen(
        onSettingsPress = {
          viewModel.handleIntent(FeedMessagesIntent.OnSettingsPress)
        },
        onNavigateToFeedLanding = { feedItem ->
          viewModel.handleIntent(FeedMessagesIntent.OnNavigateToFeedLanding(feedItem))
        }
      )
    }
  }
}

@PreviewTheme
@Composable
fun FeedMessagesScreenPreview() {
  MeAppTheme {
    AppFeedMessagesScreen()
  }
}




