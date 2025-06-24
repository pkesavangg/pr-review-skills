package com.greatergoods.meapp.features.MyAccounts.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.domain.model.Account
import com.greatergoods.meapp.domain.services.IAccountAuthService
import com.greatergoods.meapp.features.MyAccounts.reducer.MyAccountsIntent
import com.greatergoods.meapp.features.MyAccounts.reducer.MyAccountsReducer
import com.greatergoods.meapp.features.MyAccounts.reducer.MyAccountsState
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for MyAccountsScreen. Follows the same intent-driven pattern as SettingsViewModel.
 * UI should call viewModel.handleIntent(MyAccountsIntent.XYZ(...)) for all actions.
 */
@HiltViewModel
class MyAccountsViewModel @Inject constructor(
    private val accountAuthService: IAccountAuthService,
) : BaseIntentViewModel<MyAccountsState, MyAccountsIntent>(MyAccountsReducer()) {

    override fun provideInitialState(): MyAccountsState = MyAccountsState()

    override fun handleIntent(intent: MyAccountsIntent) {
        super.handleIntent(intent)

        when (intent) {
            is MyAccountsIntent.ConfirmRemoveAccount -> {
                onRemoveAccount()
            }

            is MyAccountsIntent.RequestRemoveAccount -> {
                // Already handled by reducer to set `accountToRemove`
            }

            is MyAccountsIntent.CancelRemoveAccount -> {
                // Clears the dialog
            }

            is MyAccountsIntent.LoginToAccount -> {
                onLogin(intent.account)
            }

            is MyAccountsIntent.CreateAccount -> {
                if (state.value.accounts.size >= 10) {
                    handleIntent(MyAccountsIntent.ShowMaxAccountsDialog)
                } else {
                    navigateTo(AppRoute.Auth.Signup)
                }
            }

            is MyAccountsIntent.SelectAccount -> {
                onAccountSelect(intent.account)
            }

            else -> {} // SetAccounts and dialog dismiss handled by reducer
        }
    }

    init {
        viewModelScope.launch {
            accountAuthService.loggedInAccountsFlow.collectLatest {
                handleIntent(MyAccountsIntent.SetAccounts(it))
            }
        }
    }

    private fun onLogin(account: Account?) {
        if (account == null) {
            if (state.value.accounts.size >= 10) {
                handleIntent(MyAccountsIntent.ShowMaxAccountsDialog)
            } else {
                navigateTo(AppRoute.Auth.Login)
            }
        } else {
            // Handle login for existing/inactive account
            viewModelScope.launch {
                // TODO: implement actual login behavior for the inactive account
                // accountAuthService.login(account)
            }
        }
    }

    private fun onAccountSelect(account: Account) {
        // Implement switch/select account logic if needed
    }

    private fun onRemoveAccount() {
        state.value.accountToRemove?.let { account ->
            viewModelScope.launch {
                accountAuthService.removeAccount(account.id)
            }
        }
    }

    private fun navigateTo(path: AppRoute) {
        viewModelScope.launch {
            navigationService.navigateTo(path)
        }
    }
}
