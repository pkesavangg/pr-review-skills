package com.dmdbrands.gurus.weight.features.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
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
  AppScaffold(
    title = "FAQ",
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) {
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
