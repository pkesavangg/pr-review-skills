package com.dmdbrands.gurus.weight.features.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import com.dmdbrands.gurus.weight.features.common.components.AppProfileAvatar
import com.dmdbrands.gurus.weight.features.common.components.BaseModal
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.ModalConfigs
import com.dmdbrands.gurus.weight.features.common.components.ModalDialog
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextTransform
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

@Composable
fun AccountSwitchInfoModal(
    userInitial: String,
    onAddAccount: () -> Unit,
    onClose: () -> Unit
) {
    ModalDialog(
        onDismiss = onClose,
        config = ModalConfigs.Informational, // Perfect for account/info modals
    ) {
        BaseModal(
            onDismiss = onClose,
            content = {
                Box(Modifier.fillMaxWidth()) {
                  Row(
                    modifier = Modifier.align(Alignment.TopEnd)
                  ) {
                    // Close button (top right)
                    AppIcon(
                      id = AppIcons.Default.closeFilled,
                      modifier = Modifier
                        .padding(start = spacing.md)
                        .size(24.dp),
                      contentDescription = AppPopupStrings.AccountSwitchInfo.CloseContentDescription,
                      type = AppIconType.Primary,
                      tintColor = Color.Unspecified,
                      onClick = onClose,
                    )
                  }
                    Column(
                        modifier = Modifier
                          .padding(top = spacing.lg)
                          .align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppProfileAvatar(
                            text = userInitial,
                            size = 55.dp,
                            isInfoIcon = true,
                            isActive = true,
                            enabled = true,
                        )
                        Spacer(modifier = Modifier.height(spacing.md))
                        Text(
                            text = AppPopupStrings.AccountSwitchInfo.Header,
                            style = MeTheme.typography.heading4,
                            color = MeTheme.colorScheme.textHeading,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(spacing.sm))
                        Text(
                            text = AppPopupStrings.AccountSwitchInfo.Message,
                            style = MeTheme.typography.body2,
                            color = MeTheme.colorScheme.textBody,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        Spacer(modifier = Modifier.height(spacing.lg))
                        AppButton(
                            label = AppPopupStrings.AccountSwitchInfo.AddAccountButton,
                            type = ButtonType.PrimaryFilled,
                            size = ButtonSize.Large,
                            textTransform = TextTransform.UPPERCASE,
                            onClick = onAddAccount,
                        )
                    }
                }
            },
        )
    }
}

@PreviewTheme
@Composable
fun AccountSwitchModalPreview(){
  MeAppTheme {
  AccountSwitchInfoModal(
    userInitial = "U",
    onAddAccount = {}
  ) {}
  }
}
