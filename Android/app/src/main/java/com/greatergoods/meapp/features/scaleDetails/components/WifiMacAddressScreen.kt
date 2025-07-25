package com.greatergoods.meapp.features.scaleDetails.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.WifiMacAddress
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleDetailsIntent
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleDetailsState
import com.greatergoods.meapp.features.scaleDetails.strings.WifiMacAddressStrings
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme

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
      macAddress = state.scale?.device?.macAddress ?: "",
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
