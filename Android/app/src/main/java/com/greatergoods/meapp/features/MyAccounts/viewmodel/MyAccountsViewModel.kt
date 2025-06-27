package com.greatergoods.meapp.features.MyAccounts.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.features.MyAccounts.reducer.MyAccountsIntent
import com.greatergoods.meapp.features.MyAccounts.reducer.MyAccountsReducer
import com.greatergoods.meapp.features.MyAccounts.reducer.MyAccountsState
import com.greatergoods.meapp.features.common.components.DialogType
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.common.strings.AppPopupStrings
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
                goToSignup()
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
        viewModelScope.launch {

            accountService.loggedInAccountsFlow.collectLatest {
                val hasReachedMaxAccounts = accountService.hasReachedMaxAccounts.first()
                handleIntent(MyAccountsIntent.SetAccounts(it, hasReachedMaxAccounts))
            }
        }
    }

    private fun goToLogin(account: Account?) {
        if (state.value.hasReachedMaxAccounts) {
            handleIntent(MyAccountsIntent.ShowMaxAccountsAlert)
        } else {
            navigateTo(AppRoute.Auth.Login)
        }
    }

    private fun goToSignup() {
        if (state.value.hasReachedMaxAccounts) {
            handleIntent(MyAccountsIntent.ShowMaxAccountsAlert)
        } else {
            navigateTo(AppRoute.Auth.Signup)
        }
    }

    private fun onAccountSelect(account: Account) {
        if (!account.isActiveAccount) {
            viewModelScope.launch {
                accountService.switchAccount(account, true)
                navigationService.replaceStack(AppRoute.Init.Loading)
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
        dialogQueueService.enqueue(
            DialogModel.Custom(
                contentKey = DialogType.MaxAccountAlert,
                params = mapOf(
                    "isFromLanding" to false,
                ),
                onDismiss = {},
            ),
        )
    }

    private fun onRemoveAccount() {
        dialogQueueService.showLoader(AppPopupStrings.RemoveAccountDialog.Loader)
        state.value.accountToRemove?.let { account ->
            viewModelScope.launch {
                try {
                    accountService.logout(account.id, account.fcmToken)
                } catch (e: Exception) {
                    AppLog.e("onRemoveAccount", e.toString())
                } finally {
                    dialogQueueService.dismissLoader()
                }
            }
        }
    }
}
