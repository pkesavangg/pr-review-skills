package com.greatergoods.meapp.features.settings.components.MyAccounts.reducer

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.Account

/**
 * State for MyAccountsScreen.
 */
data class MyAccountsState(
    val accounts: List<Account> = emptyList(),
    val showMaxAccountsDialog: Boolean = false,
    val accountToRemove: Account? = null
) : IReducer.State

/**
 * Intents for MyAccountsScreen actions.
 */
sealed interface MyAccountsIntent : IReducer.Intent {
    data class SetAccounts(val accounts: List<Account>) : MyAccountsIntent
    object ShowMaxAccountsDialog : MyAccountsIntent
    object DismissMaxAccountsDialog : MyAccountsIntent
    data class RequestRemoveAccount(val account: Account) : MyAccountsIntent
    object ConfirmRemoveAccount : MyAccountsIntent
    object CancelRemoveAccount : MyAccountsIntent
    data class Login(val account: Account) : MyAccountsIntent
    object CreateAccount : MyAccountsIntent
    data class SelectAccount(val account: Account) : MyAccountsIntent
}

/**
 * Reducer for MyAccountsScreen.
 */
class MyAccountsReducer : IReducer<MyAccountsState, MyAccountsIntent> {
    override fun reduce(state: MyAccountsState, intent: MyAccountsIntent): MyAccountsState? = when (intent) {
        is MyAccountsIntent.SetAccounts -> state.copy(accounts = intent.accounts)
        MyAccountsIntent.ShowMaxAccountsDialog -> state.copy(showMaxAccountsDialog = true)
        MyAccountsIntent.DismissMaxAccountsDialog -> state.copy(showMaxAccountsDialog = false)
        is MyAccountsIntent.RequestRemoveAccount -> state.copy(accountToRemove = intent.account)
        MyAccountsIntent.ConfirmRemoveAccount, MyAccountsIntent.CancelRemoveAccount -> state.copy(accountToRemove = null)
        else -> null
    }
} 