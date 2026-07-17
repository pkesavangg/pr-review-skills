package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.config.HttpErrorConfig
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.MeasurementUnits
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.auth.SignupRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.ProfileUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.dmdbrands.gurus.weight.domain.services.IOfflineHandlerService
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings.Error.LoginError
import com.dmdbrands.gurus.weight.proto.ThemeMode
import com.dmdbrands.gurus.weight.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Singleton

/**
 * Service for managing account authentication and session state.
 * Handles login, logout, account switching, and token management.
 *
 * This is a thin coordinator: credential flows delegate to [AccountAuthenticator], session lifecycle
 * to [AccountSessionManager], and login-status checks to [AccountValidationService]. Those
 * collaborators are built from this service's own injected dependencies (so DI wiring and the
 * mock-based unit tests are unaffected) and each extends [BaseService] for the shared network/toast
 * helpers. Split out under MOB-1499 to clear detekt `LargeClass`; behaviour is unchanged.
 */
@Singleton
class AccountService(
  private val accountRepository: IAccountRepository,
  private val offlineHandlerService: IOfflineHandlerService,
  connectivityObserver: IConnectivityObserver,
  dialogQueueService: IDialogQueueService,
  appNavigationService: IAppNavigationService,
  private val storageClearService: StorageClearService,
  private val analyticsService: IAnalyticsService,
  private val userDataStore: UserDataStore,
  @ApplicationScope private val appScope: CoroutineScope,
) : BaseService(connectivityObserver, dialogQueueService, appNavigationService),
  IAccountService {
  companion object {
    private const val MAX_ACCOUNTS = 10
    private const val TAG = "AccountService"
    private const val GRAPH_SCROLL_HINT_PRIORITY = 1
    private const val GRAPH_SCROLL_HINT_DELAY_MS = 1_500L
  }

  private val authenticator =
    AccountAuthenticator(accountRepository, analyticsService, connectivityObserver, dialogQueueService, appNavigationService)
  private val sessionManager =
    AccountSessionManager(accountRepository, analyticsService, connectivityObserver, dialogQueueService, appNavigationService)
  private val validationService =
    AccountValidationService(accountRepository, offlineHandlerService, connectivityObserver, dialogQueueService, appNavigationService)

  private var repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  // region Public Properties

  /**
   * Flow emitting authentication state changes (login, logout, errors, etc).
   */
  override val authEvent = appNavigationService.authEvent

  private val _activeAccount = MutableStateFlow<Account?>(null)
  override val activeAccount: StateFlow<Account?> = _activeAccount

  /**
   * Flow emitting the currently active account, or null if none is active.
   */
  override val activeAccountFlow: Flow<Account?> = accountRepository.getActiveAccount()

  /**
   * Flow emitting the list of all logged-in accounts, with the active account first.
   */
  override val loggedInAccountsFlow: Flow<List<Account>> =
    accountRepository.getLoggedInAccounts().map { it.sortedActiveFirst() }

  /**
   * Flow indicating whether the maximum number of accounts has been reached.
   */
  override val hasReachedMaxAccounts: Flow<Boolean> = loggedInAccountsFlow.map { it.size >= MAX_ACCOUNTS }

  /**
   * Flow for triggering integration checks, similar to Angular BehaviorSubject.
   * Emits true when login or checkLoginStatusForLoggedInAccounts is called.
   */
  override val checkIntegrations: StateFlow<Boolean> get() = validationService.checkIntegrations

  override fun subscribeAccount() {
    repositoryScope.cancel()
    repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    repositoryScope.launch {
      accountRepository.getActiveAccount().collect {
        _activeAccount.value = it
      }
    }
  }

  /**
   * Gets the current active account.
   * @return The active account or null if none
   */
  override suspend fun getCurrentAccount(): Account? = activeAccountFlow.first()

  /**
   * Gets the list of all logged-in accounts, with the active account first.
   * @return List of accounts
   */
  override suspend fun getLoggedInAccounts(): List<Account> = loggedInAccountsFlow.first().sortedActiveFirst()

  /**
   * Gets the current theme mode for the active account as a flow.
   * @return Flow of ThemeMode that emits changes
   */
  override val currentThemeModeFlow = accountRepository.currentThemeModeFlow

  /**
   * Logs in a user with email and password.
   * @param email User's email
   * @param password User's password
   * @return The authenticated account or null if login fails
   */
  override suspend fun login(
    email: String,
    password: String,
  ): Account? = authenticator.login(email, password)

  /**
   * Adds a new account using the provided request data.
   * @param request Account creation request data
   * @return The created account or null if creation fails
   * @throws MaxAccountsReachedException if the maximum number of accounts is reached
   */
  override suspend fun signup(request: SignupRequest): Account? = authenticator.signup(request)

  /**
   * Resets the password for the given email address.
   * @param email The email address to reset the password for
   */
  override suspend fun resetPassword(email: String) = authenticator.resetPassword(email)

  /**
   * Changes the password for the current account.
   * @param currentPassword The current password
   * @param newPassword The new password to set
   * @return true if the password was changed successfully, false otherwise
   */
  override suspend fun changePassword(
    currentPassword: String,
    newPassword: String,
  ): Boolean = authenticator.changePassword(currentPassword, newPassword)

  /**
   * Updates the user's profile information with offline support.
   * If online, calls API and marks as synced. If offline, stores locally with isSynced = false.
   * @param profileUpdateRequest The profile data to update
   * @return The updated account or null if update fails
   */
  override suspend fun updateProfile(
    profileUpdateRequest: ProfileUpdateRequest,
    isFromProfile: Boolean,
    showToast: Boolean
  ) {
    try {
      if (isFromProfile) {
        requireNetworkAvailable(onError = { showNetworkErrorAndThrow() })
      }
      accountRepository.updateProfile(profileUpdateRequest)
      if (showToast) {
        showSuccessToast(
          ToastStrings.Success.UpdateProfileSuccess.Header,
          ToastStrings.Success.UpdateProfileSuccess.Message,
        )
      }
    } catch (e: HttpException) {
      when (e.code()) {
        HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION -> {
          // For no internet, we still want to show success since offline updates are allowed
          showSuccessToast(
            ToastStrings.Success.UpdateProfileSuccess.Header,
            ToastStrings.Success.UpdateProfileSuccess.Message,
          )
        }

        else -> {
          // For other errors, show error toast
          val msg = when (e.code()) {
            HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR -> ToastStrings.Error.UpdateProfileError.MessageServError
            HttpErrorConfig.ResponseCode.UNAUTHORIZED -> ToastStrings.Error.UpdateProfileError.errorUpdatingProfileMessage
            HttpErrorConfig.ResponseCode.BAD_REQUEST -> ToastStrings.Error.UpdateProfileError.ErrorUpdatingEmail
            else -> ToastStrings.Error.UpdateProfileError.MessageGeneric
          }
          val header = when (e.code()) {
            HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR -> ToastStrings.Error.UpdateProfileError.Header
            HttpErrorConfig.ResponseCode.UNAUTHORIZED -> ToastStrings.Error.UpdateProfileError.errorUpdatingProfileHeader

            else -> ToastStrings.Error.UpdateProfileError.HeaderGeneric
          }
          showErrorToast(header, msg)
          AppLog.e(TAG, "Profile update failed", e)
          throw e
        }
      }
    }
  }

  override suspend fun updateDashboardType(type: DashboardType) {
    AppLog.d(TAG, "Update Dashboard Type")
    try {
      val accountId = activeAccountFlow.first()?.id ?: return
      accountRepository.updateDashboardType(type.value)
      accountRepository.updateLocalDashboardType(accountId, dashboardType = type)
    } catch (e: Exception) {
      AppLog.d(TAG, "Error updating Dashboard Type", e.toString())
    }
  }

  override suspend fun emailCheck(email: String): Boolean {
    AppLog.d(TAG, "emailCheck")
    return accountRepository.emailCheck(email)
  }

  override suspend fun updateMeasurementUnits(measurementUnits: MeasurementUnits) {
    AppLog.d(TAG, "Update Measurement Units: ${measurementUnits.value}")
    requireNetworkAvailable(onError = { showNetworkErrorAndThrow() })
    accountRepository.updateMeasurementUnits(measurementUnits)
  }

  override suspend fun addProduct(productType: ProductType) {
    val apiValue = productType.apiValue
    val current = getCurrentAccount()?.productTypes ?: listOf(ProductType.MY_WEIGHT.apiValue)
    if (apiValue in current) {
      AppLog.d(TAG, "addProduct: $apiValue already present, skipping")
      return
    }
    AppLog.d(TAG, "addProduct: $apiValue")
    requireNetworkAvailable(onError = { showNetworkErrorAndThrow() })
    accountRepository.updateProducts(current + apiValue)
  }

  override suspend fun checkLoginStatusForActiveAccount(): Boolean =
    validationService.checkLoginStatusForActiveAccount()

  override suspend fun checkLoginStatusForLoggedInAccounts(): Boolean =
    validationService.checkLoginStatusForLoggedInAccounts()

  /**
   * Handles unauthorized logout when token refresh fails.
   * Marks account as expired, removes from storage, and triggers unauthorized logout event.
   * @param accountId The ID of the account to logout
   * @return The affected account or null if not found
   */
  override suspend fun handleUnauthorizedLogout(accountId: String?): Account? =
    sessionManager.handleUnauthorizedLogout(accountId)

  /**
   * Logs out the current user.
   * @param accountId ID of the account to log out
   * @param fcmToken FCM token for push notifications (optional)
   * @return true if logout was successful
   */
  override suspend fun logout(
    accountId: String,
    fcmToken: String?,
  ): Boolean = sessionManager.logout(accountId, fcmToken)

  /**
   * Logs out all users.
   * @return true if all accounts were logged out successfully
   */
  override suspend fun logoutAll(): Boolean = sessionManager.logoutAll()

  /**
   * Removes the specified account from this device only ("Removed = gone", MA-2672 / MOB-424).
   * @param accountId ID of the account to remove
   * @param fcmToken FCM token for push notifications (optional)
   * @return true if the account was removed successfully, false otherwise
   */
  override suspend fun removeAccountFromDevice(
    accountId: String,
    fcmToken: String?,
  ): Boolean = sessionManager.removeAccountFromDevice(accountId, fcmToken)

  /**
   * Deletes the current user account from the server and local storage.
   */
  override suspend fun deleteAccount(
    accountID: String,
    isActiveAccount: Boolean,
  ) = sessionManager.deleteAccount(accountID, isActiveAccount)

  /**
   * Switches to a different account.
   * @param account Account to switch to
   * @param showToast Whether to show a toast notification after switching (default: false)
   * @return true if switch was successful
   */
  override suspend fun switchAccount(
    account: Account,
    showToast: Boolean,
  ): Boolean = sessionManager.switchAccount(account, showToast)

  /**
   * Sets the theme mode for the active account.
   * @param themeMode The ThemeMode to set
   */
  override suspend fun setCurrentThemeMode(themeMode: ThemeMode) {
    AppLog.d(TAG, "setCurrentThemeMode() called with themeMode: $themeMode")
    try {
      accountRepository.setCurrentThemeMode(themeMode)
      AppLog.d(TAG, "Successfully set theme mode to: $themeMode")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to set theme mode", e)
      appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Failed to set theme mode"))
    }
  }

  override suspend fun reset() {
    AppLog.d(TAG, "reset() called. Clearing all storage.")
    try {
      storageClearService.clearAllStorage()
      AppLog.d(TAG, "reset() completed. All storage cleared.")
    } catch (e: Exception) {
      AppLog.e(TAG, "reset() failed during storage clear", e)
      dialogQueueService.showToast(
        Toast.Simple(
          title = null,
          message = LoginError.MessageGeneric,
        ),
      )
    }
  }

  /**
   * Refreshes the current account data from the server.
   * Updates the local account data with the latest information from the API.
   * If network is unavailable or API call fails, continues with cached data.
   */
  override suspend fun refreshAccount() {
    try {
      val currentAccount = getCurrentAccount()
      if (currentAccount == null) {
        AppLog.w(TAG, "No active account found for refreshAccount()")
        return
      }
      if (connectivityObserver.getCurrentNetworkState().available) {
        AppLog.d(TAG, "Connection available, updating account from API")
        try {
          val accountInfo = accountRepository.getAccountFromAPI(currentAccount.id)
          accountRepository.syncAccountSettingsWithServer(accountInfo, isOnline = true)
        } catch (e: Exception) {
          AppLog.w(TAG, "Error getting account from API during refresh, using cached data", e.toString())
        }
      } else {
        AppLog.d(TAG, "No network connection available, using cached account data")
      }
    } catch (e: Exception) {
      throw e
    }
  }

  override suspend fun clearSyncTimestampForResync() {
    accountRepository.updateSyncTimeStamp("")
  }

  /**
   * Gets whether the notification alert has been shown for the specified account.
   * @param accountId The account ID to check.
   * @return True if the notification alert has been shown for this account, false otherwise.
   */
  override suspend fun hasShownNotificationAlertForAccount(accountId: String): Boolean {
    AppLog.d(TAG, "hasShownNotificationAlertForAccount() called for accountId: $accountId")
    return try {
      val result = accountRepository.hasShownNotificationAlertForAccount(accountId)
      AppLog.d(TAG, "Notification alert shown status for account $accountId: $result")
      result
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to get notification alert status for account: $accountId", e)
      false
    }
  }

  /**
   * Sets whether the notification alert has been shown for the specified account.
   * @param accountId The account ID to update.
   * @param hasShown Whether the notification alert has been shown.
   */
  override suspend fun setNotificationAlertShownForAccount(accountId: String, hasShown: Boolean) {
    AppLog.d(TAG, "setNotificationAlertShownForAccount() called for accountId: $accountId, hasShown: $hasShown")
    try {
      accountRepository.setNotificationAlertShownForAccount(accountId, hasShown)
      AppLog.d(TAG, "Successfully set notification alert status for account: $accountId")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to set notification alert status for account: $accountId", e)
    }
  }

  override suspend fun checkAndTriggerGraphScrollHint() {
    try {
      val accountId = getCurrentAccount()?.id ?: return
      if (userDataStore.hasShownGraphScrollHintForAccount(accountId)) return
      userDataStore.setGraphScrollHintShownForAccount(accountId, true)
      val dialog = DialogModel.Custom(
        contentKey = DialogType.GraphScrollHintModal,
        params = emptyMap(),
        customPriority = GRAPH_SCROLL_HINT_PRIORITY,
        // Small gap after any preceding login-time modal settles before this one appears.
        customDelayMillis = GRAPH_SCROLL_HINT_DELAY_MS,
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        onDismiss = {
          dialogQueueService.dismissCurrent()
        },
      )
      dialogQueueService.showDialog(dialog)
      AppLog.d(TAG, "Queued graph scroll hint modal for account $accountId")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to check graph scroll hint trigger", e.toString())
    }
  }

  /**
   * Emits navigation to MyAccounts event to stop scanning.
   */
  override suspend fun emitNavigateToMyAccounts() {
    appNavigationService.emitAuthEvent(AuthState.NavigateToMyAccounts)
    AppLog.d(TAG, "Emitted NavigateToMyAccounts event")
  }

  /**
   * Emits navigation back from MyAccounts event to start scanning.
   */
  override suspend fun emitNavigateBackFromMyAccounts() {
    appNavigationService.emitAuthEvent(AuthState.NavigateBackFromMyAccounts)
    AppLog.d(TAG, "Emitted NavigateBackFromMyAccounts event")
  }
}
