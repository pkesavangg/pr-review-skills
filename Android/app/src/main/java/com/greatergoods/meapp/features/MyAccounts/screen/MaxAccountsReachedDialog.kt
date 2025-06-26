package com.greatergoods.meapp.features.MyAccounts.screen

import androidx.compose.runtime.Composable
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.strings.AppPopupStrings

/**
 * Dialog shown when the maximum number of accounts is reached.
 */
@Composable
fun MaxAccountsReachedDialog(onDismiss: () -> Unit) {
    DialogModel.Alert(
        title = AppPopupStrings.MaxAccountReachedAlert.Title,
        message = AppPopupStrings.MaxAccountReachedAlert.Message,
        dismissText = AppPopupStrings.MaxAccountReachedAlert.ConfirmButton,
        onDismiss = onDismiss,
    )
}
