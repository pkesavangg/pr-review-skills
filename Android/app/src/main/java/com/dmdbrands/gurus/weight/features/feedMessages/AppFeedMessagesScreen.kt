package com.dmdbrands.gurus.weight.features.feedMessages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
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
import com.dmdbrands.gurus.weight.features.feedMessages.model.FeedMessagesState
import com.dmdbrands.gurus.weight.features.feedMessages.strings.FeedMessagesStrings
import com.dmdbrands.gurus.weight.features.feedMessages.viewmodel.FeedMessagesViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.greatergoods.ggInAppMessaging.ui.screens.FeedMessagesScreen
import android.util.Log

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
  val isRefreshing = state.isRefreshing

  // Debug logging
  android.util.Log.d("AppFeedMessagesScreen", "Current state - isRefreshing: $isRefreshing, isLoading: ${state.isLoading}")

  // Handle back button press
  BackHandler {
    viewModel.handleIntent(FeedMessagesIntent.OnBackPress)
  }

  // Initialize the screen
  LaunchedEffect(Unit) {
    viewModel.handleIntent(FeedMessagesIntent.Initialize)
  }

  AppFeedMessagesScreenContent(
    state = state,
    isRefreshing = state.isRefreshing,
    onRefresh = {
      android.util.Log.d("AppFeedMessagesScreen", "onRefresh callback triggered!")
      viewModel.handleIntent(FeedMessagesIntent.Refresh)
    },
    handleIntent = viewModel::handleIntent,
    modifier = modifier,
  )
}

@Composable
fun AppFeedMessagesScreenContent(
  state: FeedMessagesState,
  isRefreshing: Boolean = false,
  onRefresh: (() -> Unit)? = null,
  handleIntent: (FeedMessagesIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  AppScaffold(
    title = FeedMessagesStrings.Title,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) {
        handleIntent(FeedMessagesIntent.OnBackPress)
      }
    },
    containerColor = colorScheme.secondaryBackground,
    appBarColor = colorScheme.primaryBackground,
    isRefreshing = state.isRefreshing,
    onRefresh = {
      Log.i("CHECKING" , "triggered")
    },
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
      isRefreshing = false,
      onRefresh = {},
      handleIntent = {},
    )
  }
}




