package com.dmdbrands.gurus.weight.features.landing.reducer

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account

/**
 * State for MultiAccountLandingScreen.
 */
data class MultiAccountLandingState(
    val accounts: List<Account> = emptyList(),
    val hasReachedMaxAccounts: Boolean = false,
    val accountToRemove: Account? = null,
) : IReducer.State

/**
 * Intents for MultiAccountLandingScreen actions.
 */
sealed interface MultiAccountLandingIntent : IReducer.Intent {
    data class SetAccounts(val accounts: List<Account>, val hasReachedMaxAccounts: Boolean = false) :
        MultiAccountLandingIntent

    data class SelectAccount(val account: Account) : MultiAccountLandingIntent
    data class Login(val account: Account? = null) : MultiAccountLandingIntent
    object CreateAccount : MultiAccountLandingIntent
    object ShowMaxLimitReachedAlert : MultiAccountLandingIntent
    data class RequestRemoveAccount(val account: Account) : MultiAccountLandingIntent
}

/**
 * Reducer for MultiAccountLandingScreen.
 */
class MultiAccountLandingReducer : IReducer<MultiAccountLandingState, MultiAccountLandingIntent> {
    override fun reduce(
        state: MultiAccountLandingState,
        intent: MultiAccountLandingIntent
    ): MultiAccountLandingState? =
        when (intent) {
            is MultiAccountLandingIntent.SetAccounts ->
                state.copy(accounts = intent.accounts, hasReachedMaxAccounts = intent.hasReachedMaxAccounts)

            is MultiAccountLandingIntent.SelectAccount -> state // No state change, handled in ViewModel
            is MultiAccountLandingIntent.Login -> state
            MultiAccountLandingIntent.CreateAccount -> state
            MultiAccountLandingIntent.ShowMaxLimitReachedAlert -> state
            is MultiAccountLandingIntent.RequestRemoveAccount -> state.copy(accountToRemove = intent.account)
        }
}
