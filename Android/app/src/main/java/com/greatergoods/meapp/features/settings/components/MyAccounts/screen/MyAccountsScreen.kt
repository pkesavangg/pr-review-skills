package com.greatergoods.meapp.features.settings.components.MyAccounts.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppUserList
import com.greatergoods.meapp.features.settings.components.MyAccounts.strings.MyAccountsScreenStrings
import com.greatergoods.meapp.features.settings.components.MyAccounts.viewmodel.MyAccountsViewModel
import com.greatergoods.meapp.theme.MeTheme

/**
 * MyAccountsScreen displays the list of logged-in accounts and allows switching, login, and removal.
 * Shows a max accounts dialog if the limit is reached.
 */
@Composable
fun MyAccountsScreen(
    viewModel: MyAccountsViewModel = hiltViewModel()
) {
    val state = viewModel.state.collectAsState().value

    AppScaffold(title = MyAccountsScreenStrings.Title) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(MeTheme.spacing.md),
            verticalArrangement = Arrangement.Top
        ) {
            AppUserList(
                accounts = state.accounts,
                onDeleteRequest = viewModel::onRemoveAccount,
                onAccountSelect = viewModel::onAccountSelect,
                onLoginRequest = viewModel::onLogin,
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.xl))
            AppButton(
                label = MyAccountsScreenStrings.LogIntoExistingAccount,
                type = com.greatergoods.meapp.features.common.components.ButtonType.PrimaryOutlined,
                onClick = viewModel::onLoginButtonClick,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            AppButton(
                label = MyAccountsScreenStrings.CreateNewAccount,
                type = com.greatergoods.meapp.features.common.components.ButtonType.TextPrimary,
                onClick = viewModel::onCreateAccountButtonClick,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        if (state.showMaxAccountsDialog) {
            MaxAccountsReachedDialog(onDismiss = viewModel::dismissMaxAccountsDialog)
        }
        if (state.accountToRemove != null) {
            RemoveAccountDialog(
                account = state.accountToRemove,
                onConfirm = viewModel::confirmRemoveAccount,
                onCancel = viewModel::cancelRemoveAccount
            )
        }
    }
}
