package com.dmdbrands.gurus.weight.features.feedMessages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.feedMessages.strings.FeedMessagesStrings
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
  onBackPress: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AppScaffold(
    title = FeedMessagesStrings.SettingsTitle,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) { onBackPress() }
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
      onBackPress = {},
    )
  }
}
