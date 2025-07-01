package com.greatergoods.meapp.features.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppIconButtonType
import com.greatergoods.meapp.features.common.components.AppProfileAvatar
import com.greatergoods.meapp.features.common.components.BaseModal
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.TextTransform
import com.greatergoods.meapp.features.common.strings.AppPopupStrings
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeTheme
import com.greatergoods.meapp.theme.MeTheme.spacing

@Composable
fun AccountSwitchInfoModal(
    userInitial: String,
    onAddAccount: () -> Unit,
    onClose: () -> Unit
) {
    BaseModal(
        onDismiss = onClose,
        content = {
            Box(Modifier.fillMaxWidth()) {
                // Close button (top right)
                AppIconButton(
                    id = AppIcons.Default.Close,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp),
                    contentDescription = AppPopupStrings.AccountSwitchInfo.CloseContentDescription,
                    type = AppIconButtonType.Primary,
                    onClick = onClose,
                )
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppProfileAvatar(
                        text = userInitial,
                        size = 72.dp,
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
