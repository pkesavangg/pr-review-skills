package com.greatergoods.meapp.features.landing.reducer

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.storage.Account.Account

/**
 * State for MultiAccountLandingScreen.
 */
data class MultiAccountLandingState(
    val accounts: List<Account> = emptyList()
) : IReducer.State

/**
 * Intents for MultiAccountLandingScreen actions.
 */
sealed interface MultiAccountLandingIntent : IReducer.Intent {
    data class SetAccounts(val accounts: List<Account>) : MultiAccountLandingIntent
    data class SelectAccount(val account: Account) : MultiAccountLandingIntent
    data class RemoveAccount(val account: Account) : MultiAccountLandingIntent
    data class Login(val account: Account? = null) : MultiAccountLandingIntent
    object CreateAccount : MultiAccountLandingIntent
    object ShowMaxLimitReachedAlert : MultiAccountLandingIntent
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
            is MultiAccountLandingIntent.SetAccounts -> state.copy(accounts = intent.accounts)
            is MultiAccountLandingIntent.SelectAccount -> state // No state change, handled in ViewModel
            is MultiAccountLandingIntent.RemoveAccount -> state
            is MultiAccountLandingIntent.Login -> state
            MultiAccountLandingIntent.CreateAccount -> state
            MultiAccountLandingIntent.ShowMaxLimitReachedAlert -> state
        }
}
