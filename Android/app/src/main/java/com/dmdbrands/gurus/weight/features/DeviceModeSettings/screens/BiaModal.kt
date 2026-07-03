package com.dmdbrands.gurus.weight.features.DeviceModeSettings.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.common.components.AppPopup
import com.dmdbrands.gurus.weight.features.common.components.AppPopupModal
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.deviceMode.strings.DeviceModeStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme

/**
 * Modal dialog explaining BIA, matching Figma and AppHelpModal structure.
 * @param onClose Called when the close button is pressed.
 * @param modifier Modifier for styling.
 */
@Composable
fun BiaModal(
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
    AppPopupModal {
      AppPopup(
        visible = true,
        modifier = modifier,
        heading = DeviceModeStrings.BiaModalStrings.Title,
        supportingText = DeviceModeStrings.BiaModalStrings.Messsage,
        onClose = onClose,
      )
    }
}

@PreviewTheme
@Composable
fun BiaModalPreview() {
  MeAppTheme {
    BiaModal(onClose = {})
  }
}
