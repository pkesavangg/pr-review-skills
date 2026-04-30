package com.dmdbrands.gurus.weight.features.ScaleSetup.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.theme.MeAppTheme

/**
 * Parent composable for the WiFi password step that wraps SetupForm
 * and handles form reset using DisposableEffect when the composable is disposed.
 *
 * @param state The current BtWifiScaleSetupState
 * @param onIntent Callback for handling intents
 * @param modifier Modifier to be applied to the root component
 */
@Composable
fun WifiPasswordForm(
  state: BtWifiScaleSetupState,
  onIntent: (BtWifiScaleSetupIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  // // Reset the WiFi password form when the composable is disposed
  DisposableEffect(Unit) {
    onDispose {
      onIntent(BtWifiScaleSetupIntent.ClearWifiPasswordForm)
    }
  }

  SetupForm(
    formControl = state.wifiPasswordForm.password,
    title = BtWifiScaleSetupStrings.WifiPassword.Title,
    label = BtWifiScaleSetupStrings.WifiPassword.PasswordLabel,
    subtitle = BtWifiScaleSetupStrings.WifiPassword.Subtitle,
    subtitleAnnotatedText = state.wifiPasswordForm.ssid.value,
    hasToggle = true,
    toggleLabel = BtWifiScaleSetupStrings.WifiPassword.NetworkPasswordToggleLabel,
    toggleChecked = state.wifiPasswordForm.noPasswordNetwork.value,
    onToggleChanged = {
      state.wifiPasswordForm.noPasswordNetwork.onValueChange(it)
      state.wifiPasswordForm.password.reset()
      onIntent(BtWifiScaleSetupIntent.HandlePasswordNetworkStatus)
    },
    inputType = AppInputType.PASSWORD,
    modifier = modifier,
  )
}

@PreviewTheme
@Composable
fun WifiPasswordFormPreview() {
  MeAppTheme {
    // This is just a preview - in real usage, the state would come from the ViewModel
    WifiPasswordForm(
      state = BtWifiScaleSetupState(),
      onIntent = {},
    )
  }
}
