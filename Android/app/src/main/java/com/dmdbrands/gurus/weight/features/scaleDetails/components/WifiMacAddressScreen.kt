package com.dmdbrands.gurus.weight.features.scaleDetails.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.WifiMacAddress
import com.dmdbrands.gurus.weight.features.scaleDetails.reducer.ScaleDetailsIntent
import com.dmdbrands.gurus.weight.features.scaleDetails.reducer.ScaleDetailsState
import com.dmdbrands.gurus.weight.features.scaleDetails.strings.WifiMacAddressStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme

@Composable
fun WifiMacAddressScreen(
  state: ScaleDetailsState,
  handleIntent: (ScaleDetailsIntent) -> Unit,
  onClose: () -> Unit,
) {
  BackHandler {
    onClose()
  }
  AppScaffold(
    title = WifiMacAddressStrings.Header,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close, onClick = onClose)
    },
  ) {
    WifiMacAddress(
      title = WifiMacAddressStrings.Title,
      macAddress = state.wifiMacAddress ?: "",
      onCopyMacAddress = {
        handleIntent(ScaleDetailsIntent.OnCopyMacAddress(it))
      }
    )
  }
}

@PreviewTheme
@Composable
fun WifiMacAddressScreenPreview() {
  MeAppTheme {
  }
}
