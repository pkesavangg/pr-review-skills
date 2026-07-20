package com.dmdbrands.gurus.weight.features.help.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.model.BABY_SCALES
import com.dmdbrands.gurus.weight.features.common.model.BPM_DEVICES
import com.dmdbrands.gurus.weight.features.help.components.AppVersionContent
import com.dmdbrands.gurus.weight.features.help.components.ContactUsContent
import com.dmdbrands.gurus.weight.features.help.components.DeviceCatalogSection
import com.dmdbrands.gurus.weight.features.help.components.WeightScaleCatalogSection
import com.dmdbrands.gurus.weight.features.help.model.HelpIntent
import com.dmdbrands.gurus.weight.features.help.strings.HelpScreenStrings
import com.dmdbrands.gurus.weight.features.help.viewmodel.HelpViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.delay

@Composable
fun HelpScreen() {
  val viewModel: HelpViewModel = hiltViewModel()

  BackHandler {
    viewModel.handleIntent(HelpIntent.OnBack)
  }

  HelpContent(viewModel, viewModel::handleIntent)
}

@Composable
private fun HelpContent(
  viewModel: HelpViewModel,
  handleIntent: (HelpIntent) -> Unit
) {
  var debugTaps by remember { mutableIntStateOf(0) }
  LaunchedEffect(debugTaps) {
    if (debugTaps > 0) {
      delay(10000)
      debugTaps = 0
    }
  }
  AppScaffold(
    title = HelpScreenStrings.Title,
    enable = true,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close, modifier = Modifier.testTag(TestTags.Help.CloseButton), contentDescription = HelpScreenStrings.accCloseLabel) {
        handleIntent(HelpIntent.OnBack)
      }
    },
    appBarOnclick = {
      debugTaps++
      if (debugTaps >= 5) {
        viewModel.onOpenDebugMenu()
        debugTaps = 0
      }
    },
  ) { scaffoldModifier ->
    Column(
      modifier = scaffoldModifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .testTag(TestTags.Help.ScreenRoot),
    ) {
      ContactUsContent(handleIntent)
      Spacer(Modifier.height(MeTheme.spacing.xl))
      HelpDeviceCatalog(handleIntent)
      AppVersionContent()
    }
  }
}

@Composable
private fun HelpDeviceCatalog(handleIntent: (HelpIntent) -> Unit) {
  DeviceCatalogSection(
    title = HelpScreenStrings.BabyScale,
    iconId = AppIcons.Setup.BabyScale,
    iconTint = MeTheme.colorScheme.baby,
    devices = BABY_SCALES,
    onDeviceSelected = { device ->
      val url = "${AppConfig.PRODUCT_URL}/${device.sku}"
      handleIntent(HelpIntent.OpenUrl(url))
    },
  )
  DeviceCatalogSection(
    title = HelpScreenStrings.BloodPressureMonitor,
    iconId = AppIcons.Setup.BpmScale,
    iconTint = MeTheme.colorScheme.secondarySuccess,
    devices = BPM_DEVICES,
    onDeviceSelected = { device ->
      val url = "${AppConfig.PRODUCT_URL}/${device.sku}"
      handleIntent(HelpIntent.OpenUrl(url))
    },
  )
  WeightScaleCatalogSection(
    onScaleSelected = { scale ->
      val url = "${AppConfig.PRODUCT_URL}/${scale.sku}"
      handleIntent(HelpIntent.OpenUrl(url))
    },
  )
}

@PreviewTheme
@Composable
private fun HelpScreenPreview() {
  MeAppTheme {
    // HelpContent(
    //     handleIntent = {},
    // )
  }
}
