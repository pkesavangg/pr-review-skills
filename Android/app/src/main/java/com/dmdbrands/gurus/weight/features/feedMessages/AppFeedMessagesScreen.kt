package com.dmdbrands.gurus.weight.features.feedMessages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.feedMessages.model.FeedMessagesIntent
import com.dmdbrands.gurus.weight.features.feedMessages.model.FeedMessagesState
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
  val state by viewModel.state.collectAsStateWithLifecycle()

  // Handle back button press
  BackHandler {
    viewModel.handleIntent(FeedMessagesIntent.OnBackPress)
  }

  AppFeedMessagesScreenContent(
    state = state,
    onRefresh = {
      viewModel.handleIntent(FeedMessagesIntent.Refresh)
    },
    handleIntent = viewModel::handleIntent,
    modifier = modifier,
  )
}

@Composable
fun AppFeedMessagesScreenContent(
  state: FeedMessagesState,
  onRefresh: (() -> Unit)? = null,
  handleIntent: (FeedMessagesIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  AppScaffold(
    title = FeedMessagesStrings.Title,
    navigationIcon = {
      AppIconButton(
        AppIcons.Default.Close,
        contentDescription = FeedMessagesStrings.accCloseButton,
      ) {
        handleIntent(FeedMessagesIntent.OnBackPress)
      }
    },
    containerColor = colorScheme.secondaryBackground,
    appBarColor = colorScheme.primaryBackground,
    isRefreshing = state.isRefreshing,
    onRefresh = onRefresh,
    modifier = modifier.fillMaxSize(),
  ) { scaffoldModifier ->
    // Reuse the IAM FeedMessagesScreen content
    Box(modifier = scaffoldModifier.fillMaxSize()) {
      FeedMessagesScreen(
        onSettingsPress = {
          handleIntent(FeedMessagesIntent.OnSettingsPress)
        },
        onNavigateToFeedLanding = { feedItem ->
          handleIntent(FeedMessagesIntent.OnNavigateToFeedLanding(feedItem))
        }
      )
    }
  }
}

@PreviewTheme
@Composable
fun FeedMessagesScreenPreview() {
  MeAppTheme {
    AppFeedMessagesScreenContent(
      state = FeedMessagesState(),
      onRefresh = {},
      handleIntent = {},
    )
  }
}




