package com.dmdbrands.gurus.weight.features.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.feed.model.FeedLandingIntent
import com.dmdbrands.gurus.weight.features.feed.viewmodel.FeedLandingViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.ui.screens.FeedLandingScreen as IAMFeedLandingScreen

/**
 * MeApp Feed Landing Screen
 * Wraps the IAM package FeedLandingScreen composable
 */
@Composable
fun FeedLandingScreen(
  onNavigateBack: () -> Unit,
  onNavigateToProduct: (String, Int?) -> Unit = { _, _ -> },
  onNavigateToFeedLanding: (FeedItem) -> Unit = { _ -> },
) {
  val viewModel: FeedLandingViewModel = hiltViewModel()
  val state by viewModel.state.collectAsState()

  LaunchedEffect(onNavigateToFeedLanding) {
    viewModel.setNavigationCallback(onNavigateToFeedLanding)
  }

  AppScaffold(
    title = state.feedItem?.landingPage?.titleText ?: state.feedItem?.titleText ?: "Feed",
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) {
        onNavigateBack()
      }
    },
    actions = {
      AppIconButton(AppIcons.Outlined.Help) { viewModel.handleIntent(FeedLandingIntent.OpenFAQ) }
    },
  ) { scaffoldModifier ->
    Column(
      modifier = scaffoldModifier
        .fillMaxSize()
        .padding(top = spacing.md, bottom = spacing.md),
    ) {
      when {
        state.isLoading -> {
        }

        state.feedItem != null -> {
          IAMFeedLandingScreen(
            feedItem = state.feedItem!!,
            onPromoCodeClick = { promoCode ->
              viewModel.onPromoCodeClick(promoCode)
            },
            onShopNowClick = { link ->
              viewModel.onShopNowClick(link)
            },
            onProductClick = { link, variationId ->
              viewModel.onProductClick(link, variationId)
            },
          )
        }

        else -> {
        }
      }
    }
  }
}
