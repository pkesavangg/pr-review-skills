package com.dmdbrands.gurus.weight.features.deviceDetails.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.WifiMacAddress
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsIntent
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsState
import com.dmdbrands.gurus.weight.features.deviceDetails.strings.WifiMacAddressStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme

@Composable
fun WifiMacAddressScreen(
  state: DeviceDetailsState,
  handleIntent: (DeviceDetailsIntent) -> Unit,
  onClose: () -> Unit,
) {
  BackHandler {
    onClose()
  }
  AppScaffold(
    title = WifiMacAddressStrings.Header,
    navigationIcon = {
      AppIconButton(
        AppIcons.Default.Close,
        onClick = onClose,
        modifier = Modifier.testTag(TestTags.DeviceDetails.WifiMacCloseButton),
      )
    },
  ) {
    Box(modifier = Modifier.testTag(TestTags.DeviceDetails.WifiMacScreenRoot)) {
      WifiMacAddress(
        title = WifiMacAddressStrings.Title,
        macAddress = state.wifiMacAddress ?: "",
        onCopyMacAddress = {
          handleIntent(DeviceDetailsIntent.OnCopyMacAddress(it))
        }
      )
    }
  }
}

@PreviewTheme
@Composable
fun WifiMacAddressScreenPreview() {
  MeAppTheme {
  }
}
