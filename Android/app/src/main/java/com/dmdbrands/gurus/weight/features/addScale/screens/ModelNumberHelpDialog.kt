package com.dmdbrands.gurus.weight.features.addScale.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dmdbrands.gurus.weight.features.addScale.strings.AddScaleHelpStrings
import com.dmdbrands.gurus.weight.features.common.components.AppPopup
import com.dmdbrands.gurus.weight.features.common.components.AppPopupImageType
import com.dmdbrands.gurus.weight.features.common.components.AppPopupModal
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Dialog to help users find their scale's model number, matching Figma MCP spec.
 * Uses AppPopup and MEImage for pixel-perfect design.
 *
 * @param visible Whether the dialog is visible
 * @param onClose Handler for closing the dialog
 */
@Composable
fun ModelNumberHelpDialog(
    visible: Boolean,
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
            visible = visible,
            supportingText = AddScaleHelpStrings.SupportingText,
            onClose = onClose,
            imageType = AppPopupImageType.CustomImage(
              image = {
                Image(
                  painter = painterResource(AppIcons.Default.ModalNumber),
                  contentDescription = AddScaleHelpStrings.ImageContentDescription,
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .height(120.dp),
                )
              },
            ),
            modifier = modifier,
          )
        }
      }
    }
  }
}
