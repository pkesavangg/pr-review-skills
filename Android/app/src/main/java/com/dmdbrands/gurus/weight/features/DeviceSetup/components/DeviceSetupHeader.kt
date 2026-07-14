package com.dmdbrands.gurus.weight.features.DeviceSetup.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.DeviceSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme

@Composable
fun DeviceSetupHeader(
  sku: String,
  onBack: () -> Unit,
  onHelp: () -> Unit,
  content: @Composable () -> Unit,
) {
  // Grouped model label for display (e.g. 0664 -> "0604/0664", 0022 -> 0383).
  val displaySku = DeviceHelper.listModelLabel(sku)
  val isBabyScale = DeviceHelper.isBabyScale(sku)
  BackHandler {
    onBack()
  }

  AppScaffold(
    title = if (isBabyScale) DeviceSetupStrings.BabyScaleHeader else DeviceSetupStrings.Header(displaySku),
    containerColor = colorScheme.secondaryBackground,
    navigationIcon = {
      // TalkBack: icon-only button needs a spoken label.
      AppIconButton(
        AppIcons.Default.Close,
        modifier = Modifier.testTag(TestTags.DeviceSetup.CloseButton),
        contentDescription = DeviceSetupStrings.accCloseButton,
      ) {
        onBack()
      }
    },
    actions = {
      // TalkBack: icon-only button needs a spoken label.
      AppIconButton(
        AppIcons.Outlined.Help,
        modifier = Modifier.testTag(TestTags.DeviceSetup.HelpButton),
        contentDescription = DeviceSetupStrings.accHelpButton,
      ) {
        onHelp()
      }
    },
  ) {
    content()
  }
}

@PreviewTheme()
@Composable
fun DeviceSetupHeaderPreview() {
  MeAppTheme {
    DeviceSetupHeader(
      sku = "0412",
      onBack = {},
      onHelp = {},
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
          DeviceInfoContent("0412", DeviceSetupType.Wifi) {}
        }
    }
  }
}
