package com.greatergoods.meapp.features.settings.components.MyAccounts.screen

import androidx.compose.runtime.Composable
import com.greatergoods.meapp.features.common.components.AppPopup
import com.greatergoods.meapp.features.settings.components.MyAccounts.strings.MyAccountsScreenStrings

/**
 * Dialog shown when the maximum number of accounts is reached.
 */
@Composable
fun MaxAccountsReachedDialog(onDismiss: () -> Unit) {
    AppPopup(
        visible = true,
        heading = MyAccountsScreenStrings.MaxAccountsReachedTitle,
        supportingText = MyAccountsScreenStrings.MaxAccountsReachedBody,
        onClose = onDismiss,
        primaryLabel = MyAccountsScreenStrings.MaxAccountsReachedButton,
        onPrimaryAction = onDismiss,
    )
} 