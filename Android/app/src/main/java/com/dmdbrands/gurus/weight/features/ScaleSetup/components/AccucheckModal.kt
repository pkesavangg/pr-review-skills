package com.dmdbrands.gurus.weight.features.ScaleSetup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.AppPopup
import com.dmdbrands.gurus.weight.features.common.components.AppPopupImageType
import com.dmdbrands.gurus.weight.features.common.components.AppPopupModal
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

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
  Dialog(
    onDismissRequest = onClose,
    properties = DialogProperties(
      dismissOnBackPress = true,
      dismissOnClickOutside = true,
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MeTheme.colorScheme.glow)
        .clickable { onClose() },
    ) {
      Box(modifier = Modifier.align(Alignment.Center)) {
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
    }
  }
}

@PreviewTheme
@Composable
fun AccucheckModalPreview() {
  MeAppTheme {
    AccucheckModal(onClose = {})
  }
}
