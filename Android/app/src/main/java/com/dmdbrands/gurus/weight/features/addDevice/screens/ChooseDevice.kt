package com.dmdbrands.gurus.weight.features.addDevice.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.features.addDevice.reducer.AddDeviceIntent
import com.dmdbrands.gurus.weight.features.addDevice.strings.ChooseScaleStrings
import com.dmdbrands.gurus.weight.features.addDevice.viewmodel.AddDeviceViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.DeviceList
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import kotlinx.coroutines.launch

@Composable
fun ChooseScaleScreen(viewModel: AddDeviceViewModel = hiltViewModel()) {
  ChooseScaleScreenContent(viewModel::handleIntent)
}

@Composable
fun ChooseScaleScreenContent(handleIntent: (AddDeviceIntent) -> Unit = {}) {
  val backStack = LocalNavBackStack.current
  val coroutineScope = rememberCoroutineScope()

  AppScaffold(
    title = ChooseScaleStrings.Header,
    navigationIcon = {
      AppIconButton(
        AppIcons.Default.Close,
        modifier = Modifier.testTag(TestTags.AddDevice.ChooseCloseButton),
      ) {
        coroutineScope.launch {
          backStack.removeLast()
        }
      }
    },
  ) {
    Column(
      modifier =
        Modifier
          .padding(vertical = spacing.md)
          .testTag(TestTags.AddDevice.ChooseScreenRoot),
    ) {
      DeviceList(
        onScaleSelected = { scaleInfo ->
          handleIntent(AddDeviceIntent.OpenSelectedScaleSetup(scaleInfo.sku))
        },
      )
    }
  }
}

@PreviewTheme
@Composable
fun ChooseScaleScreenPreview() {
  MeAppTheme {
    ChooseScaleScreenContent(
      handleIntent = {},
    )
  }
}
