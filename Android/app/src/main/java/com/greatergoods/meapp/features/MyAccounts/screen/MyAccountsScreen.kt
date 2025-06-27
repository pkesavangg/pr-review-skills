package com.greatergoods.meapp.features.MyAccounts.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * MyAccountsScreen displays the list of logged-in accounts and allows switching, login, and removal.
 * Shows a max accounts dialog if the limit is reached.
 */
@Composable
fun MyAccountsScreen() {
    val viewmodel: MyAccountsViewModel = hiltViewModel()
    val state by viewmodel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    MyAccountsScreenContent(state, viewmodel::handleIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyAccountsScreenContent(
    state: MyAccountsState,
    handleIntent: (MyAccountsIntent) -> Unit,
) {
    val backStack = LocalNavBackStack.current
    val coroutineScope = rememberCoroutineScope()
    AppScaffold(
        title = MyAccountsScreenStrings.Title,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) {
                coroutineScope.launch {
                    backStack.removeLast()
                }
            }
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppUserList(
                accounts =
                    List(10) { index ->
                        state.accounts.map { it.copy(id = index.toString()) }
                    }.flatten(),
                modifier = Modifier.background(Color.Green),
                showAccountActivity = true,
                onDeleteRequest = { handleIntent(MyAccountsIntent.RequestRemoveAccount(it)) },
                onAccountSelect = { handleIntent(MyAccountsIntent.SelectAccount(it)) },
                onLoginRequest = { handleIntent(MyAccountsIntent.LoginToAccount(it)) },
                contentPadding = PaddingValues(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.md),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
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
            }
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
