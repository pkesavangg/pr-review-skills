package com.greatergoods.meapp.features.MyAccounts.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.MyAccounts.reducer.MyAccountsIntent
import com.greatergoods.meapp.features.MyAccounts.reducer.MyAccountsState
import com.greatergoods.meapp.features.MyAccounts.strings.MyAccountsScreenStrings
import com.greatergoods.meapp.features.MyAccounts.viewmodel.MyAccountsViewModel
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppUserList
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeTheme

/**
 * MyAccountsScreen displays the list of logged-in accounts and allows switching, login, and removal.
 * Shows a max accounts dialog if the limit is reached.
 */
@Composable
fun MyAccountsScreen() {
    val viewmodel: MyAccountsViewModel = hiltViewModel()
    val state by viewmodel.state.collectAsState()
    MyAccountsScreenContent(state, viewmodel::handleIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyAccountsScreenContent(
    state: MyAccountsState,
    handleIntent: (MyAccountsIntent) -> Unit
) {
    val backStack = LocalNavBackStack.current

    AppScaffold(
        title = MyAccountsScreenStrings.Title,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) {
                backStack.removeLast()
            }
        },
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(MeTheme.spacing.sm),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppUserList(
                accounts = state.accounts,
                showAccountActivity = true,
                onDeleteRequest = { handleIntent(MyAccountsIntent.RequestRemoveAccount(it)) },
                onAccountSelect = { handleIntent(MyAccountsIntent.SelectAccount(it)) },
                onLoginRequest = { handleIntent(MyAccountsIntent.LoginToAccount(it)) },
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.x3l))
            AppButton(
                label = MyAccountsScreenStrings.LogIntoExistingAccount,
                type = ButtonType.PrimaryOutlined,
                onClick = { handleIntent(MyAccountsIntent.LoginToAccount()) },
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
            AppButton(
                label = MyAccountsScreenStrings.CreateNewAccount,
                type = ButtonType.TextPrimary,
                onClick = { handleIntent(MyAccountsIntent.CreateAccount) },
            )
        }
        if (state.showMaxAccountsDialog) {
            MaxAccountsReachedDialog(
                onDismiss = {
                    handleIntent(MyAccountsIntent.DismissMaxAccountsDialog)
                },
            )
        }
        if (state.accountToRemove != null) {
            RemoveAccountDialog(
                account = state.accountToRemove,
                onConfirm = { handleIntent(MyAccountsIntent.ConfirmRemoveAccount) },
                onCancel = { handleIntent(MyAccountsIntent.CancelRemoveAccount) },
            )
        }
    }
}
