package com.dmdbrands.gurus.weight.features.feedMessages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.greatergoods.ggInAppMessaging.ui.screens.FeedMessagesSettingsScreen

/**
 * Feed Messages Settings Screen with TopAppBar
 * Reuses content from IAM package and adds navigation
 */
@Composable
fun AppFeedMessagesSettingsScreen(
  modifier: Modifier = Modifier,
) {
  val viewModel: FeedMessagesViewModel = hiltViewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()

  // Handle back button press
  BackHandler {
    viewModel.handleIntent(FeedMessagesIntent.OnBackPress)
  }

  AppScaffold(
    title = FeedMessagesStrings.SettingsTitle,
    navigationIcon = {
      AppIconButton(
        AppIcons.Default.Close,
        contentDescription = FeedMessagesStrings.accCloseButton,
      ) {
        viewModel.handleIntent(FeedMessagesIntent.OnBackPress)
      }
    },
    containerColor = colorScheme.secondaryBackground,
    appBarColor = colorScheme.primaryBackground,
    modifier = modifier.fillMaxSize(),
  ) { scaffoldModifier ->
    // Reuse the IAM FeedMessagesSettingsScreen content
    Column(
      modifier = Modifier
        .background(colorScheme.secondaryBackground)
        .fillMaxSize(),
    ) {
      FeedMessagesSettingsScreen()
    }
  }
}

@PreviewTheme
@Composable
fun FeedMessagesSettingsScreenPreview() {
  MeAppTheme {
    AppFeedMessagesSettingsScreen(
    )
  }
}
