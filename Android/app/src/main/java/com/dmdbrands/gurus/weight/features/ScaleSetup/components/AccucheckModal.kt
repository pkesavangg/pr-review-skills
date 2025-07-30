package com.dmdbrands.gurus.weight.features.ScaleSetup.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.addScale.strings.AddScaleHelpStrings
import com.dmdbrands.gurus.weight.features.common.components.AppPopup
import com.dmdbrands.gurus.weight.features.common.components.AppPopupImageType
import com.dmdbrands.gurus.weight.features.common.components.AppPopupModal
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme

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
