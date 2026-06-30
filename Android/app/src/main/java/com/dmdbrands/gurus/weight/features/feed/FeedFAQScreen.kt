package com.dmdbrands.gurus.weight.features.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.greatergoods.ggInAppMessaging.theme.IamTheme
import com.greatergoods.ggInAppMessaging.ui.components.FAQComponent

/**
 * MeApp Feed FAQ Screen
 * Displays FAQ content using the IAM FAQComponent
 */
@Composable
fun FeedFAQScreen(
) {
  val viewModel: FeedLandingViewModel = hiltViewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()
  BackHandler {
    viewModel.handleIntent(FeedLandingIntent.OnBackPress)
  }

  AppScaffold(
    title = "FAQ",
    navigationIcon = {
      AppIconButton(
        AppIcons.Default.Close,
        contentDescription = FeedStrings.accCloseButton,
      ) {
        viewModel.handleIntent(FeedLandingIntent.OnBackPress)
      }
    },
  ) { scaffoldModifier ->
    Column(
      modifier = scaffoldModifier
        .fillMaxSize()
        .background(color = IamTheme.colors.secondaryBackground)
        .padding(top = spacing.md, bottom = spacing.md),
    ) {
      FAQComponent()
    }
  }
}
