package com.greatergoods.meapp.features.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.service.IAppEventService
import com.greatergoods.meapp.domain.interfaces.IDialogQueueHandler
import com.greatergoods.meapp.domain.interfaces.INavigationHandler
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.meapp.domain.repository.IUserRepository
import com.greatergoods.meapp.features.common.service.DialogQueueService
import com.greatergoods.meapp.features.common.viewmodel.DialogQueueViewModel
import com.greatergoods.meapp.features.common.viewmodel.NavigationViewmodel
import com.greatergoods.meapp.proto.UserAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Home screen, providing current user data, all accounts,
 * and actions for logout and account switching using UserRepository.
 *
 * @property userRepository The repository for user account operations.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appRepository: IAppRepository,
    private val userRepository: IUserRepository,
    private val navigationService: IAppEventService,
    private val dialogQueueService: DialogQueueService
) : ViewModel(),
    INavigationHandler by NavigationViewmodel(navigationService),
    IDialogQueueHandler by DialogQueueViewModel(dialogQueueService) {

    private val _currentAccount = MutableStateFlow<UserAccount?>(null)

    /**
     * The currently active account.
     */
    val currentAccount: StateFlow<UserAccount?> = _currentAccount.asStateFlow()

    private val _allAccounts = MutableStateFlow<Map<String, UserAccount>>(emptyMap())

    /**
     * All user accounts keyed by account ID.
     */
    val allAccounts: StateFlow<Map<String, UserAccount>> = _allAccounts.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.currentAccountFlow.collectLatest { account ->
                _currentAccount.value = account
            }
        }
        viewModelScope.launch {
            userRepository.accountsFlow.collectLatest { accounts ->
                _allAccounts.value = accounts
            }
        }
    }

    /**
     * Logs out all accounts (clears all user data).
     */
    fun logout() {
        viewModelScope.launch {
            userRepository.logoutCurrentAccount()
        }
    }

    /**
     * Switches to the selected account by accountId.
     * @param accountId The account ID to activate.
     */
    fun switchAccount(accountId: String) {
        viewModelScope.launch {
            userRepository.setActiveAccount(accountId)
        }
    }
    
    fun createRandomAccount() {
        viewModelScope.launch {
            userRepository.createRandomAccount()
        }
    }
}
