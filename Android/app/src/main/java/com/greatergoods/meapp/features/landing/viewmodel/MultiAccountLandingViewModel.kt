package com.greatergoods.meapp.features.landing.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.interfaces.IDialogUtility
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.landing.reducer.MultiAccountLandingIntent
import com.greatergoods.meapp.features.landing.reducer.MultiAccountLandingReducer
import com.greatergoods.meapp.features.landing.reducer.MultiAccountLandingState
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

            else -> {}
        }
    }

    init {
        loadAccounts()
    }

    private fun onSelectAccount(account: Account) {
        viewModelScope.launch {
            try {
                accountService.switchAccount(account)
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
            accountService.loggedInAccountsFlow.collectLatest {
                val hasReachedMaxAccounts = accountService.hasReachedMaxAccounts.first()
                handleIntent(MultiAccountLandingIntent.SetAccounts(it, hasReachedMaxAccounts))
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
