package com.dmdbrands.gurus.weight.features.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.feed.model.FeedLandingIntent
import com.dmdbrands.gurus.weight.features.feed.strings.FeedStrings
import com.dmdbrands.gurus.weight.features.feed.viewmodel.FeedLandingViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.greatergoods.ggInAppMessaging.ui.screens.IamFeedLandingScreen

/**
 * MeApp Feed Landing Screen
 * Wraps the IAM package FeedLandingScreen composable
 */
@Composable
fun FeedLandingScreen() {
  val viewModel: FeedLandingViewModel = hiltViewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()

  // Handle back button press
  BackHandler {
    viewModel.handleIntent(FeedLandingIntent.OnBackPress)
  }

  AppScaffold(
    title = state.feedItem?.landingPage?.titleText ?: state.feedItem?.titleText ?: "Feed",
    navigationIcon = {
      AppIconButton(
        AppIcons.Default.Close,
        contentDescription = FeedStrings.accCloseButton,
      ) {
        viewModel.handleIntent(FeedLandingIntent.OnBackPress)
      }
    },
    actions = {
      AppIconButton(
        AppIcons.Outlined.Help,
        contentDescription = FeedStrings.accFaqButton,
      ) { viewModel.handleIntent(FeedLandingIntent.OpenFAQ) }
    },
  ) { scaffoldModifier ->
    Column(
      modifier = scaffoldModifier
        .fillMaxSize().verticalScroll(rememberScrollState())
        .padding(top = spacing.md, bottom = spacing.md),
    ) {
      when {
        state.isLoading -> {
        }

        state.feedItem != null -> {
          val feedItem = state.feedItem
          if (feedItem != null) {
            IamFeedLandingScreen(
              feedItem = feedItem,
            )
          }
        }

        else -> {
        }
      }
    }
  }
}
