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
import com.greatergoods.ggInAppMessaging.ui.screens.FeedMessagesScreen

/**
 * Feed Messages Screen with TopAppBar
 * Reuses content from IAM package and adds navigation
 */
@Composable
fun AppFeedMessagesScreen(
  onBackPress: () -> Unit,
  onSettingsPress: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AppScaffold(
    title = FeedMessagesStrings.Title,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) { onBackPress() }
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
          onSettingsPress = onSettingsPress
        )
    }
  }
}

@PreviewTheme
@Composable
fun FeedMessagesScreenPreview() {
  MeAppTheme {
    AppFeedMessagesScreen(
      onBackPress = {},
      onSettingsPress = {},
    )
  }
}




