package com.dmdbrands.gurus.weight.features.ScaleSetup.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme

@Composable
fun ScaleSetupHeader(
  sku: String,
  onBack: () -> Unit,
  onHelp: () -> Unit,
  content: @Composable () -> Unit,
) {
  // Map SKU for display (e.g., 0022 -> 0383)
  val displaySku = DeviceHelper.mapSkuForDisplay(sku)
  val isBabyScale = DeviceHelper.isBabyScale(sku)
  BackHandler {
    onBack()
  }

  AppScaffold(
    title = if (isBabyScale) ScaleSetupStrings.BabyScaleHeader else ScaleSetupStrings.Header(displaySku),
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
          ScaleInfo("0412", ScaleSetupType.Wifi) {}
        }
    }
  }
}
