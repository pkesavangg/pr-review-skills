package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.dmdbrands.gurus.weight.features.common.strings.AppHelpModalStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import android.annotation.SuppressLint
import android.content.Intent

/**
 * Help modal matching Figma (node 14585-12302): shows support phone/email and info.
 *
 * @param onClose Called when the close button is pressed.
 * @param modifier Modifier for styling.
 */
@SuppressLint("UseKtx")
@Composable
fun AppHelpModal(
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
  showGuide: Boolean = false,
  onGuideClick: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  ModalDialog(
    onDismiss = onClose,
    config = ModalConfigs.Informational, // Perfect for help/support modals
  ) {
    AppPopupModal {
      AppPopup(
        true,
        modifier = Modifier,
        AppHelpModalStrings.Title,
        supportingText = AppHelpModalStrings.SupportingText,
        onClose = {
          onClose()
        },
        imageType = AppPopupImageType.DefaultImage(AppIcons.Default.ggLogo),
      ) {
        // Parent AppPopup column applies sm (16dp) spacedBy; add another sm so the
        // gap between the supporting text and the first action button reaches lg (32dp).
        Spacer(Modifier.height(MeTheme.spacing.sm))
        val phoneNumber = AppHelpModalStrings.Phone

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
        ) {
          if (showGuide && onGuideClick != null) {
            AppButton(
              label = AppHelpModalStrings.GuideButton,
              type = ButtonType.InlineTextPrimary,
              onClick = onGuideClick,
            )
          }
          AppButton(
            label = phoneNumber,
            type = ButtonType.InlineTextPrimary,
            onClick = {
              val intent =
                Intent(Intent.ACTION_DIAL).apply {
                  data = "tel:$phoneNumber".toUri()
                }
              context.startActivity(intent)
            },
          )

          AppButton(
            label = AppHelpModalStrings.Email,
            type = ButtonType.InlineTextPrimary,
            onClick = {
              val intent =
                Intent(Intent.ACTION_SENDTO).apply {
                  data = "mailto:${AppHelpModalStrings.Email}".toUri()
                }
              context.startActivity(intent)
            },
          )
        }
      }
    }
  }
}

@PreviewTheme
@Composable
fun AppHelpModalPreview() {
  MeAppTheme {
    AppHelpModal(onClose = {}, showGuide = true, onGuideClick = {})
  }
}
