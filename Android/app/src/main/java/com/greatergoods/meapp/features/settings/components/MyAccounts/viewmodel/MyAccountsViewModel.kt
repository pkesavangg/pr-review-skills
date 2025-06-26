package com.greatergoods.meapp.features.settings.components.MyAccounts.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.service.AccountAuthService
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.settings.components.MyAccounts.reducer.MyAccountsIntent
import com.greatergoods.meapp.features.settings.components.MyAccounts.reducer.MyAccountsReducer
import com.greatergoods.meapp.features.settings.components.MyAccounts.reducer.MyAccountsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for MyAccountsScreen. Handles account list, login, create, remove, and dialog state via reducer.
 */
@HiltViewModel
class MyAccountsViewModel @Inject constructor(
    private val accountAuthService: AccountAuthService,
) : BaseIntentViewModel<MyAccountsState, MyAccountsIntent>(MyAccountsReducer()) {

    override fun provideInitialState(): MyAccountsState = MyAccountsState()

    override fun handleIntent(intent: MyAccountsIntent) {
        super.handleIntent(intent)
        // Add side effects here if needed (e.g., async work)
        when (intent) {
            is MyAccountsIntent.ConfirmRemoveAccount -> {
                state.value.accountToRemove?.let { account ->
                    viewModelScope.launch {
                        accountAuthService.removeAccount(account.id)
                        // State update is already handled by reducer
                    }
                }
            }

            else -> {}
        }
    }

    init {
        viewModelScope.launch {
            accountAuthService.loggedInAccountsFlow.collectLatest {
                handleIntent(MyAccountsIntent.SetAccounts(it))
            }
        }
    }

    fun onLogin(account: Account) {
        // Optionally: implement account-specific login logic
    }

    fun onLoginButtonClick() {
        if (state.value.accounts.size >= 10) {
            handleIntent(MyAccountsIntent.ShowMaxAccountsDialog)
        } else {
            navigateTo(AppRoute.Auth.Login)
        }
    }

    fun onCreateAccountButtonClick() {
        if (state.value.accounts.size >= 10) {
            handleIntent(MyAccountsIntent.ShowMaxAccountsDialog)
        } else {
            navigateTo(AppRoute.Auth.Signup)
        }
    }

    fun onRemoveAccount(account: Account) {
        handleIntent(MyAccountsIntent.RequestRemoveAccount(account))
    }

    fun confirmRemoveAccount() {
        handleIntent(MyAccountsIntent.ConfirmRemoveAccount)
    }

    fun cancelRemoveAccount() {
        handleIntent(MyAccountsIntent.CancelRemoveAccount)
    }

    fun dismissMaxAccountsDialog() {
        handleIntent(MyAccountsIntent.DismissMaxAccountsDialog)
    }

    fun onAccountSelect(account: Account) {
        // Optionally: implement account selection/switch logic
    }

    private fun navigateTo(path: AppRoute) {
        viewModelScope.launch {
            navigationService.navigateTo(path)
        }
    }
}
