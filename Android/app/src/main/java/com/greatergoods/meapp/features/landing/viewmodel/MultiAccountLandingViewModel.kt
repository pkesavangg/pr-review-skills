package com.greatergoods.meapp.features.landing.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.common.strings.AppPopupStrings
import com.greatergoods.meapp.features.landing.reducer.MultiAccountLandingIntent
import com.greatergoods.meapp.features.landing.reducer.MultiAccountLandingReducer
import com.greatergoods.meapp.features.landing.reducer.MultiAccountLandingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for MultiAccountLandingScreen. Handles account list and user actions.
 */
@HiltViewModel
class MultiAccountLandingViewModel @Inject constructor(
    private val accountService: IAccountService,
) : BaseIntentViewModel<MultiAccountLandingState, MultiAccountLandingIntent>(
    MultiAccountLandingReducer()
) {
    override fun provideInitialState(): MultiAccountLandingState = MultiAccountLandingState()

    /**
     * Handles intents from the UI.
     */
    override fun handleIntent(intent: MultiAccountLandingIntent) {
        super.handleIntent(intent)
        when (intent) {

            is MultiAccountLandingIntent.SelectAccount -> {
            }

            is MultiAccountLandingIntent.RemoveAccount -> {
                showRemoveAccountDialog(intent.account)
            }

            is MultiAccountLandingIntent.Login -> {
                goToLogin(intent.account)
            }

            is MultiAccountLandingIntent.CreateAccount -> {
                navigate(AppRoute.Auth.Signup)
            }

            is MultiAccountLandingIntent.ShowMaxLimitReachedAlert -> {
                showMaxLimitReachedDialog()
            }

            else -> {}
        }
    }

    init {
        loadAccounts()
    }

    private fun navigate(route: AppRoute) {
        viewModelScope.launch {
            navigationService.navigateTo(route)
        }
    }

    private fun goToLogin(account: Account?) {
        viewModelScope.launch {
            navigate(AppRoute.Auth.Login)
        }
    }

    /**
     * Shows the remove account confirmation dialog.
     */
    private fun showRemoveAccountDialog(account: Account) {
        dialogQueueService.enqueue(
            DialogModel.Confirm(
                title = String.format(AppPopupStrings.RemoveAccountDialog.Title, account.firstName),
                message = String.format(
                    AppPopupStrings.RemoveAccountDialog.Message,
                    account.firstName
                ),
                confirmText = AppPopupStrings.RemoveAccountDialog.ConfirmButton,
                cancelText = AppPopupStrings.RemoveAccountDialog.CancelButton,
                onConfirm = {
                    // TODO: Implement account removal logic
                    // Example: accountService.removeAccount(account.id)
                    // Then refresh the accounts list
                    dialogQueueService.dismissCurrent()
                },
                onCancel = {
                    dialogQueueService.dismissCurrent()
                },
                onDismiss = {
                    dialogQueueService.dismissCurrent()
                })
        )
    }

    /**
     * Shows the max limit reached alert dialog.
     */
    private fun showMaxLimitReachedDialog() {
        dialogQueueService.enqueue(
            DialogModel.Alert(
                title = AppPopupStrings.MaxAccountReachedAlert.Title,
                message = AppPopupStrings.MaxAccountReachedAlert.Message,
                dismissText = AppPopupStrings.MaxAccountReachedAlert.ConfirmButton,
                onDismiss = {
                    dialogQueueService.dismissCurrent()
                })
        )
    }

    /**
     * Loads accounts (stub for now).
     */
    private fun loadAccounts() {
        viewModelScope.launch {
            accountService.loggedInAccountsFlow.collectLatest {
                handleIntent(MultiAccountLandingIntent.SetAccounts(it))
            }
        }
    }
}
