package com.greatergoods.meapp.features.ScaleSetup.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.ScaleSetup.strings.BtWifiScaleSetupStrings
import com.greatergoods.meapp.features.addScale.strings.AddScaleHelpStrings
import com.greatergoods.meapp.features.common.components.AppPopup
import com.greatergoods.meapp.features.common.components.AppPopupImageType
import com.greatergoods.meapp.features.common.components.AppPopupModal
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Modal dialog explaining BIA, matching Figma and AppHelpModal structure.
 * @param onClose Called when the close button is pressed.
 * @param modifier Modifier for styling.
 */
@Composable
fun AccucheckModal(
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AppPopupModal {
    AppPopup(
      visible = true,
      modifier = modifier,
      heading = BtWifiScaleSetupStrings.AccucheckModal.Title,
      supportingText = BtWifiScaleSetupStrings.AccucheckModal.Messsage,
      imageType = AppPopupImageType.DefaultImage(AppIcons.Setup.AccucheckLogo),
      onClose = onClose,
    )
  }
}

@PreviewTheme
@Composable
fun AccucheckModalPreview() {
  MeAppTheme {
    AccucheckModal(onClose = {})
  }
}
