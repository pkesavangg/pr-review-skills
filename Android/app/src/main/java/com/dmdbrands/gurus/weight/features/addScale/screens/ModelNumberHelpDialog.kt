package com.dmdbrands.gurus.weight.features.addScale.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.addScale.strings.AddScaleHelpStrings
import com.dmdbrands.gurus.weight.features.common.components.AppPopup
import com.dmdbrands.gurus.weight.features.common.components.AppPopupImageType
import com.dmdbrands.gurus.weight.features.common.components.AppPopupModal
import com.dmdbrands.gurus.weight.resources.AppIcons

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
