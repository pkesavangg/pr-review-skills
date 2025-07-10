package com.greatergoods.meapp.features.ScaleSetup.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing

@Composable
fun ScaleSetupHeader(
  sku: String,
  onBack: () -> Unit,
  onHelp: () -> Unit,
  content: @Composable () -> Unit,
) {
  BackHandler {
    onBack()
  }

  AppScaffold(
    title = ScaleSetupStrings.Header(sku),
    containerColor = colorScheme.secondaryBackground,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) {
        onBack()
      }
    },
    actions = {
      AppIconButton(AppIcons.Outlined.Help) {
        onHelp()
      }
    },
  ) {
    content()
  }
}

@PreviewTheme()
@Composable
fun ScaleSetupHeaderPreview() {
  MeAppTheme {
    ScaleSetupHeader(
      sku = "0412",
      onBack = {},
      onHelp = {},
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
          ScaleInfo("0412")
        }
    }
  }
}
