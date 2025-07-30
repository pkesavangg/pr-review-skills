package com.dmdbrands.gurus.weight.features.MyAccounts.reducer

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account

/**
 * State for MyAccountsScreen.
 */
data class MyAccountsState(
    val accounts: List<Account> = emptyList(),
    val showMaxAccountsDialog: Boolean = false,
    val accountToRemove: Account? = null,
    val hasReachedMaxAccounts: Boolean = false,
) : IReducer.State

/**
 * Intents for MyAccountsScreen actions.
 */
sealed interface MyAccountsIntent : IReducer.Intent {
    data class SetAccounts(val accounts: List<Account>, val hasReachedMaxAccounts: Boolean) : MyAccountsIntent
    object ShowMaxAccountsAlert : MyAccountsIntent
    data class RequestRemoveAccount(val account: Account) : MyAccountsIntent

    data class LoginToAccount(
        val account: Account? = null,
    ) : MyAccountsIntent

    object CreateAccount : MyAccountsIntent
    data class SelectAccount(val account: Account) : MyAccountsIntent
}

/**
 * Reducer for MyAccountsScreen.
 */
class MyAccountsReducer : IReducer<MyAccountsState, MyAccountsIntent> {
    override fun reduce(state: MyAccountsState, intent: MyAccountsIntent): MyAccountsState? = when (intent) {
        is MyAccountsIntent.SetAccounts -> state.copy(
            accounts = intent.accounts,
            hasReachedMaxAccounts = intent.hasReachedMaxAccounts,
        )

        MyAccountsIntent.ShowMaxAccountsAlert -> state.copy(showMaxAccountsDialog = true)

        is MyAccountsIntent.RequestRemoveAccount -> state.copy(accountToRemove = intent.account)

        // Intents handled only in ViewModel (side-effects), no state change needed
        is MyAccountsIntent.LoginToAccount,
        is MyAccountsIntent.CreateAccount,
        is MyAccountsIntent.SelectAccount -> null
    }
}
