package com.greatergoods.meapp.features.ScaleModeSettings.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.AppPopup
import com.greatergoods.meapp.features.common.components.AppPopupModal
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.scaleMode.strings.ScaleModeStrings
import com.greatergoods.meapp.theme.MeAppTheme

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
      heading = ScaleModeStrings.BiaModalStrings.Title,
      supportingText = ScaleModeStrings.BiaModalStrings.Messsage,
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
