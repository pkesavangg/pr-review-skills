package com.dmdbrands.gurus.weight.features.MyAccounts.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.features.MyAccounts.reducer.MyAccountsIntent
import com.dmdbrands.gurus.weight.features.MyAccounts.reducer.MyAccountsReducer
import com.dmdbrands.gurus.weight.features.MyAccounts.reducer.MyAccountsState
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for MyAccountsScreen. Follows the same intent-driven pattern as SettingsViewModel.
 * UI should call viewModel.handleIntent(MyAccountsIntent.XYZ(...)) for all actions.
 */
@HiltViewModel
class MyAccountsViewModel @Inject constructor(
    private val accountService: IAccountService,
    private val dialogUtility: IDialogUtility,
) : BaseIntentViewModel<MyAccountsState, MyAccountsIntent>(MyAccountsReducer()) {

    override fun provideInitialState(): MyAccountsState = MyAccountsState()

    override fun handleIntent(intent: MyAccountsIntent) {
        super.handleIntent(intent)

        when (intent) {
            is MyAccountsIntent.RequestRemoveAccount -> {
                showRemoveAccountDialog(intent.account)
            }

            is MyAccountsIntent.LoginToAccount -> {
                goToLogin(intent.account)
            }

            is MyAccountsIntent.CreateAccount -> {
                goToSignUp()
            }

            is MyAccountsIntent.SelectAccount -> {
                onAccountSelect(intent.account)
            }

            is MyAccountsIntent.ShowMaxAccountsAlert -> {
                showMaxLimitReachedAlert()
            }

            else -> {} // SetAccounts and dialog dismiss handled by reducer
        }
    }

    init {
        // Stop scanning when MyAccounts screen loads
        viewModelScope.launch {
            accountService.emitNavigateToMyAccounts()
        }

        viewModelScope.launch {
            accountService.loggedInAccountsFlow.collectLatest { accounts ->
                val hasReachedMaxAccounts = accountService.hasReachedMaxAccounts.first()
                handleIntent(MyAccountsIntent.SetAccounts(accounts, hasReachedMaxAccounts))
            }
        }
    }

    private fun goToLogin(account: Account?) {
        if (state.value.hasReachedMaxAccounts) {
            handleIntent(MyAccountsIntent.ShowMaxAccountsAlert)
        } else {
            navigateTo(AppRoute.Auth.Login(account?.email))
        }
    }

    private fun goToSignUp() {
        if (state.value.hasReachedMaxAccounts) {
            handleIntent(MyAccountsIntent.ShowMaxAccountsAlert)
        } else {
            navigateTo(AppRoute.Auth.Signup)
        }
    }

    private fun onAccountSelect(account: Account) {
        if (!account.isActiveAccount) {
            viewModelScope.launch {
                try {
                    val canSwitch = accountService.switchAccount(account, true)
                  AppLog.e("onAccountSelect", "Failed to switch account: ${canSwitch}")
                  if(canSwitch){
                    navigationService.reInitialize()
                  }
                } catch (e: Exception) {
                    AppLog.e("onAccountSelect", "Failed to switch account: ${e.message}", e)
                }
            }
        }
    }

    private fun navigateTo(path: AppRoute) {
        viewModelScope.launch {
            navigationService.navigateTo(path)
        }
    }

    private fun showRemoveAccountDialog(account: Account) {
        dialogQueueService.enqueue(
          DialogModel.Confirm(
                title = String.format(AppPopupStrings.RemoveAccountDialog.Title, account.firstName),
                message = String.format(AppPopupStrings.RemoveAccountDialog.Message, account.firstName),
                confirmText = AppPopupStrings.RemoveAccountDialog.ConfirmButton,
                cancelText = AppPopupStrings.RemoveAccountDialog.CancelButton,
                primaryActionType = com.dmdbrands.gurus.weight.features.common.components.ButtonType.ErrorText,
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
     * Shows the max limit reached alert dialog.
     */
    private fun showMaxLimitReachedAlert() {
        dialogUtility.showMaxAccountAlert(
            isFromLanding = false,
            onDismiss = {},
        )
    }

    private fun onRemoveAccount() {
        dialogQueueService.showLoader(AppPopupStrings.RemoveAccountDialog.Loader)
        state.value.accountToRemove?.let { account ->
            val wasActiveAccount = account.isActiveAccount
            viewModelScope.launch {
                try {
                    // "Remove" = gone (MA-2672/MOB-424): log out + fully delete from device — not
                    // logout(), which keeps the account (logged-out) and left it on the Multi-Account
                    // Landing.
                    accountService.removeAccountFromDevice(account.id, account.fcmToken)
                    // Deleting the CURRENT (active) account ends the session. If other accounts
                    // remain on the device, show the Multi-Account Landing (pick/login); if it was
                    // the only account, show the plain Landing screen. The removed account is fully
                    // gone (removeAccountFromDevice), so it no longer appears on either. A non-active
                    // removal keeps the user on Switch Account (loggedInAccountsFlow refreshes the
                    // list). (MOB-1474)
                    if (wasActiveAccount) {
                        val remaining = accountService.getLoggedInAccounts()
                        navigationService.replaceStack(
                            if (remaining.isEmpty()) {
                                AppRoute.Auth.Landing
                            } else {
                                AppRoute.Auth.MultiAccountLanding
                            },
                        )
                    }
                } catch (e: Exception) {
                    AppLog.e("onRemoveAccount", "Failed to remove account", e)
                } finally {
                    dialogQueueService.dismissLoader()
                }
            }
        }
    }

    /**
     * Called when user navigates back from MyAccounts screen
     * This will start scanning again
     */
    fun onNavigateBack() {
        viewModelScope.launch {
            accountService.emitNavigateBackFromMyAccounts()
        }
    }
}
