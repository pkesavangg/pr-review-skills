package com.greatergoods.meapp.features.settings.components.MyAccounts.screen

import androidx.compose.runtime.Composable
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.features.common.components.AppPopup
import com.greatergoods.meapp.features.settings.components.MyAccounts.strings.MyAccountsScreenStrings

/**
 * Dialog shown to confirm account removal.
 */
@Composable
fun RemoveAccountDialog(
    account: Account,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AppPopup(
        visible = true,
        heading = String.format(MyAccountsScreenStrings.RemoveAccountTitle, account.firstName),
        supportingText = String.format(MyAccountsScreenStrings.RemoveAccountBody, account.firstName),
        onClose = onCancel,
        primaryLabel = MyAccountsScreenStrings.RemoveAccountConfirm,
        secondaryLabel = MyAccountsScreenStrings.RemoveAccountCancel,
        onPrimaryAction = onConfirm,
        onSecondaryAction = onCancel,
    )
}
