package com.dmdbrands.gurus.weight.features.landing.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.features.landing.reducer.MultiAccountLandingIntent
import com.dmdbrands.gurus.weight.features.landing.reducer.MultiAccountLandingReducer
import com.dmdbrands.gurus.weight.features.landing.reducer.MultiAccountLandingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for MultiAccountLandingScreen. Handles account list and user actions.
 */
@HiltViewModel
class MultiAccountLandingViewModel @Inject constructor(
    private val accountService: IAccountService,
    private val dialogUtility: IDialogUtility,
) : BaseIntentViewModel<MultiAccountLandingState, MultiAccountLandingIntent>(
    MultiAccountLandingReducer(),
) {
    override fun provideInitialState(): MultiAccountLandingState = MultiAccountLandingState()

    /**
     * Handles intents from the UI.
     */
    override fun handleIntent(intent: MultiAccountLandingIntent) {
        super.handleIntent(intent)
        when (intent) {

            is MultiAccountLandingIntent.SelectAccount -> {
                onSelectAccount(intent.account)
            }

            is MultiAccountLandingIntent.Login -> {
                goToLogin(intent.account)
            }

            is MultiAccountLandingIntent.CreateAccount -> {
                goToSignup()
            }

            is MultiAccountLandingIntent.ShowMaxLimitReachedAlert -> {
                showMaxLimitReachedDialog()
            }

            is MultiAccountLandingIntent.RequestRemoveAccount -> {
                showRemoveAccountDialog(intent.account)
            }

            else -> {}
        }
    }

    override fun onDependenciesReady() {
        loadAccounts()
    }

    private fun onSelectAccount(account: Account) {
        viewModelScope.launch {
            try {
                accountService.switchAccount(account, true)
                 navigationService.reInitialize()
            } catch (e: Exception) {
                AppLog.e("MultiAccountLandingViewModel", "Failed to switch account: ${e.message}")
            }
        }
    }

    /**
     * Loads accounts.
     */
    private fun loadAccounts() {
        viewModelScope.launch {
            accountService.loggedInAccountsFlow.collectLatest { accounts ->
                if (accounts.isEmpty()) {
                    // Last account was removed from this device — no accounts remain, so send
                    // the user to the fresh Login / Welcome screen (MOB-424).
                    navigationService.replaceStack(AppRoute.Auth.Landing)
                } else {
                    val hasReachedMaxAccounts = accountService.hasReachedMaxAccounts.first()
                    handleIntent(MultiAccountLandingIntent.SetAccounts(accounts, hasReachedMaxAccounts))
                }
            }
        }
    }

    /**
     * Shows the max limit reached alert dialog.
     */
    private fun showMaxLimitReachedDialog() {
        dialogUtility.showMaxAccountAlert(
            isFromLanding = true,
            onDismiss = {},
        )
    }

    /**
     * Shows the remove-account confirmation dialog.
     */
    private fun showRemoveAccountDialog(account: Account) {
        dialogQueueService.enqueue(
            DialogModel.Confirm(
              title = String.format(AppPopupStrings.RemoveAccountDialog.Title, account.firstName),
              message = String.format(AppPopupStrings.RemoveAccountDialog.Message, account.firstName),
              confirmText = AppPopupStrings.RemoveAccountDialog.ConfirmButton,
              cancelText = AppPopupStrings.RemoveAccountDialog.CancelButton,
              primaryActionType = ButtonType.ErrorText,
              onConfirm = {
                    onRemoveAccount()
                    dialogQueueService.dismissCurrent()
                },
              onCancel = {
                    dialogQueueService.dismissCurrent()
                },
              onDismiss = {
                    dialogQueueService.dismissCurrent()
                },
            ),
        )
    }

    /**
     * Removes the account selected for removal from this device ("Removed = gone").
     * Unlike logout, this fully deletes the local account so it no longer appears in the list.
     */
    private fun onRemoveAccount() {
        dialogQueueService.showLoader(AppPopupStrings.RemoveAccountDialog.Loader)
        state.value.accountToRemove?.let { account ->
            viewModelScope.launch {
                try {
                  AppLog.d("MultiAccountLandingViewModel", "Removing account: ${account.id}")
                    accountService.removeAccountFromDevice(account.id, account.fcmToken)
                } catch (e: Exception) {
                    AppLog.e("MultiAccountLandingViewModel", "Failed to remove account", e)
                } finally {
                    dialogQueueService.dismissLoader()
                }
            }
        }
    }

    private fun goToLogin(account: Account?) {
        viewModelScope.launch {
            if (state.value.hasReachedMaxAccounts && account == null) {
                handleIntent(MultiAccountLandingIntent.ShowMaxLimitReachedAlert)
            } else {
                navigationService.navigateTo(AppRoute.Auth.Login(account?.email))
            }
        }
    }

    private fun goToSignup() {
        viewModelScope.launch {
            if (state.value.hasReachedMaxAccounts) {
                handleIntent(MultiAccountLandingIntent.ShowMaxLimitReachedAlert)
            } else {
                navigationService.navigateTo(AppRoute.Auth.Signup)
            }
        }
    }
}
