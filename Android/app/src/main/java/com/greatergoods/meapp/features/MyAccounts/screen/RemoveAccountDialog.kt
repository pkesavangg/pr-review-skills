package com.greatergoods.meapp.features.MyAccounts.screen

import androidx.compose.runtime.Composable
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.strings.AppPopupStrings

/**
 * Dialog shown to confirm account removal.
 */
@Composable
fun RemoveAccountDialog(
    account: Account,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    DialogModel.Confirm(
        title = String.format(AppPopupStrings.RemoveAccountDialog.Title, account.firstName),
        message = String.format(AppPopupStrings.RemoveAccountDialog.Message, account.firstName),
        confirmText = AppPopupStrings.RemoveAccountDialog.ConfirmButton,
        cancelText = AppPopupStrings.RemoveAccountDialog.CancelButton,
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}
