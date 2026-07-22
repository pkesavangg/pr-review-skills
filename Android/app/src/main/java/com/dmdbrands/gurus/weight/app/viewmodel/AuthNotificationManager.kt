package com.dmdbrands.gurus.weight.app.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.service.AppNotificationEventService
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.service.NotificationEventType
import com.dmdbrands.gurus.weight.core.service.NotificationReceivedPayload
import com.dmdbrands.gurus.weight.core.service.NotificationTapPayload
import com.dmdbrands.gurus.weight.core.service.pushNotification.NotificationDestination
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.dmdbrands.gurus.weight.features.common.model.ReadingToast
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings
import com.greatergoods.ggInAppMessaging.core.service.GGInAppMessagingService
import com.greatergoods.ggInAppMessaging.core.service.IAMDialogEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Owns the auth-event / notification / IAM slice of [AppViewModel] (MOB-1500).
 * Behaviour-preserving verbatim move of the auth-state routing, notification sync/tap handling,
 * remote-sync toast, IAM dialog listener, and the session-observer startup tail. Scan-side
 * effects (stop/reset/start scan and start observers) are delegated back to [ScaleConnectionManager]
 * through callbacks; base-class lateinit services are reached through lazy provider lambdas so
 * their value is never captured before injection.
 */
class AuthNotificationManager(
  private val scope: CoroutineScope,
  private val onIntent: (AppIntent) -> Unit,
  private val provideNavigation: () -> IAppNavigationService,
  private val provideDialogQueue: () -> IDialogQueueService,
  private val appNavigationService: IAppNavigationService,
  private val accountService: IAccountService,
  private val dashboardService: IDashboardService,
  private val entryService: IEntryService,
  private val feedService: IFeedService,
  private val dialogUtility: IDialogUtility,
  private val ggInAppMessagingService: GGInAppMessagingService,
  private val onStopScan: () -> Unit,
  private val onResetScaleDiscoveredState: () -> Unit,
  private val onStartObserversOnly: (Account, Boolean) -> Unit,
  private val onStartScan: () -> Unit,
  private val onSyncScales: () -> Unit,
) {

  private val TAG = "AppViewModel"

  private val navigationService: IAppNavigationService get() = provideNavigation()
  private val dialogQueueService: IDialogQueueService get() = provideDialogQueue()

  private var iamDialogListenerJob: Job? = null

  fun initEvents() {
    observeAuthEvents()
    observeNotificationSync()
    observeReceivedNotifications()
    observeNotificationTaps()
  }

  private fun observeAuthEvents() {
    scope.launch {
      appNavigationService.authEvent.collect { authState ->
        handleAuthState(authState)
      }
    }
  }

  private suspend fun handleAuthState(authState: AuthState) {
    when (authState) {
      is AuthState.LoggedInFromLoading -> {
        onStopScan()
        onResetScaleDiscoveredState()
        onStartObserversOnly(authState.account, true)
        dashboardService.setSelectedKey(null)
      }

      is AuthState.LoggedOut -> {
        onStopScan()
        if (authState.isActiveAccount || authState.isLastAccount) {
          onResetScaleDiscoveredState()
          routeToLandingOrApp()
          dialogQueueService.clear()
        }
      }

      is AuthState.AccountDeleted -> {
        if (authState.isActiveAccount) {
          onStopScan()
          dashboardService.setSelectedKey(null)
          routeToLandingOrApp()
        }
      }

      is AuthState.UnauthorizedLogout -> handleUnauthorizedLogout(authState)

      is AuthState.EncryptionFailure -> handleEncryptionFailure()

      is AuthState.AccountAdded -> {
      }

      is AuthState.AccountSwitched -> handleAccountSwitched(authState)

      is AuthState.ProfileUpdated -> {
        // Profile updated - no navigation needed, just log
        AppLog.d(TAG, "Profile updated for account: ${authState.account.id}")
      }

      is AuthState.NavigateToMyAccounts -> {
        // Stop scan when navigating to MyAccounts screen
        onStopScan()
        AppLog.d(TAG, "Stopped scan due to navigation to MyAccounts screen")
      }

      is AuthState.NavigateBackFromMyAccounts -> {
        // Start scan when navigating back from MyAccounts screen
        onStartScan()
        onSyncScales()
        AppLog.d(TAG, "Started scan due to navigation back from MyAccounts screen")
      }

      is AuthState.Error -> {
        // Handle auth errors without triggering navigation
        AppLog.e(TAG, "Auth error: ${authState.message}")
      }

      // handle other AuthState events as needed
      else -> {}
    }
  }

  private fun handleUnauthorizedLogout(authState: AuthState.UnauthorizedLogout) {
    // Show account logged out alert
    scope.launch {
      val activeAccount =
        accountService.handleUnauthorizedLogout(authState.accountId)
      if (activeAccount != null) {
        onStopScan()
        navigationService.replaceStack(route = AppRoute.Auth.MultiAccountLanding)
        dialogUtility.showAccountLoggedOutAlert(activeAccount.firstName)
      }
    }
  }

  private fun handleEncryptionFailure() {
    // Encryption failure affects all accounts (shared encrypted file).
    // Reuse existing logout alert pattern — force re-login.
    scope.launch {
      val activeAccount = accountService.getCurrentAccount()
      val username = activeAccount?.firstName ?: ""
      // Log out all accounts since encrypted storage is shared
      accountService.logoutAll()
      onStopScan()
      navigationService.replaceStack(route = AppRoute.Auth.Landing)
      if (username.isNotEmpty()) {
        dialogUtility.showAccountLoggedOutAlert(username)
      }
    }
  }

  private fun handleAccountSwitched(authState: AuthState.AccountSwitched) {
    // Switching accounts must start the new account with a clean scan state. Otherwise the
    // previous account's skip/ignore flags leak across and can suppress the duplicate-user
    // reconnect alert after switching back to a previously connected account (MOB-175).
    // Mirrors the reset already done on LoggedInFromLoading / LoggedOut.
    onResetScaleDiscoveredState()
    if (authState.showToast) {
      val accountName = authState.account.firstName
      dialogQueueService.showToast(
        Toast.Simple(
          title = null,
          message =
            ToastStrings.Success.AccountSwitchSuccess.Message(
              accountName,
            ),
          action = null,
        ),
      )
    }
  }

  private fun observeNotificationSync() {
    scope.launch {
      AppNotificationEventService.events.collect {
        when (it) {
          NotificationEventType.NOTIFICATION_TAPPED -> {
            entryService.syncOperations()
          }

          NotificationEventType.NOTIFICATION_RECEIVED -> {
            // The "saved to your log" card is shown from the receivedEvents collector below,
            // which carries the reading value/product. Here we just pull the new entry in.
            entryService.syncOperations()
          }

          else -> {}
        }
      }
    }
  }

  private fun observeReceivedNotifications() {
    scope.launch {
      AppNotificationEventService.receivedEvents.collect { payload ->
        showRemoteSyncToast(payload)
      }
    }
  }

  private fun observeNotificationTaps() {
    scope.launch {
      AppNotificationEventService.tapEvents.collect { tap ->
        handleNotificationTap(tap)
        AppNotificationEventService.consumeTap()
      }
    }
  }

  /**
   * Handles a notification deep-link: switches to the target account when it differs from
   * the active one, then navigates to the relevant History destination (MOB-434).
   */
  private suspend fun handleNotificationTap(tap: NotificationTapPayload) {
    val accountId = tap.accountId
    if (accountId != null && accountId != accountService.activeAccount.value?.id) {
      accountService.getLoggedInAccounts().firstOrNull { it.id == accountId }?.let { target ->
        accountService.switchAccount(target)
      }
    }
    navigationService.navigateTo(NotificationDestination.toRoute(tap.destination, tap.monthKey))
  }

  /**
   * Routes to either the landing page or the app based on login status.
   * @param isLoggedIn true if user is logged in, false otherwise
   */
  private suspend fun routeToLandingOrApp() {
    val loggedInAccounts =
      accountService.getLoggedInAccounts().filter {
        !it.isActiveAccount
      }
    val hasAccounts = loggedInAccounts.isNotEmpty()
    val route =
      if (hasAccounts) {
        AppRoute.Auth.MultiAccountLanding
      } else {
        AppRoute.Auth.Landing
      }
    navigationService.replaceStack(route = route)
  }

  /**
   * Foreground handler for a Wi-Fi / remotely-synced reading (arrives as an FCM push). Unlike a
   * Bluetooth reading — which is unsaved and shows SAVE/DISCARD — this one is already saved
   * server-side, so it shows the single-VIEW "New Reading saved to your log" card (Figma
   * 30456-24170). Falls back to a simple confirmation if the push carried no measurement/product
   * (the "without measurement" push variant). (MOB-1537)
   */
  private fun showRemoteSyncToast(payload: NotificationReceivedPayload) {
    val productType = payload.destination?.let { ProductType.fromId(it) }
    val measurement = payload.measurement
    if (productType == null || measurement.isNullOrBlank()) {
      dialogQueueService.showToast(Toast.Simple(message = "Success! Entry added"))
      return
    }
    dialogQueueService.showToast(
      Toast.Custom(
        ReadingToast(
          reading = measurement,
          type = productType,
          timestamp = "Just now",
          savedToLog = true,
          onView = {
            scope.launch { navigationService.navigateTo(AppRoute.Main.History) }
          },
        ),
      ),
    )
  }

  /**
   * Runs the per-session observer startup tail after [ScaleConnectionManager] has (re)subscribed the
   * scan/device/paired-scale collectors on login. Extracted verbatim from `startObserversOnly`.
   */
  suspend fun startSessionObservers(account: Account) {
    accountService.checkAndTriggerGraphScrollHint()
    entryService.initializeGoalCardMonitoring(account.id)
    feedService.fetchFeedItems()
    initialiseIAMDialogListener()
    feedService.checkAndTriggerFeedModal()
    updateUnRead()
  }

  private fun initialiseIAMDialogListener() {
    // Initialize IAM dialog events listener
    try {
      initIAMDialogListener()
      AppLog.d(TAG, "IAM dialog events listener initialized")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to initialize IAM dialog events listener", e.toString())
    }
  }

  private suspend fun updateUnRead() {
    // Initialize feed notification listener
    try {
      updateUnreadFeedCount()
      AppLog.d(TAG, "Feed notification listener initialized")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to initialize feed notification listener", e.toString())
    }
  }

  /**
   * Updates the unread feed count and indicator visibility
   */
  private suspend fun updateUnreadFeedCount() {
    try {
      val count = feedService.getUnreadFeedCount()
      val feedSettings = feedService.getFeedSettings()
      val shouldShow = count > 0 && (feedSettings?.showNotificationBadge ?: true)
      onIntent(AppIntent.SetUnreadFeedCount(count))
      onIntent(AppIntent.SetShowUnreadFeedIndication(shouldShow))
      AppLog.d(TAG, "Updated unread feed count: $count, show indicator: $shouldShow")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to update unread feed count", e.toString())
    }
  }

  /**
   * Initializes the IAM dialog events listener
   * Listens to dialog events from GGInAppMessagingService and shows appropriate dialogs
   * Cancels any existing listener job before creating a new one to prevent duplicate triggers
   */
  private fun initIAMDialogListener() {
    // Cancel any existing listener job to prevent duplicate collectors
    iamDialogListenerJob?.cancel()
    iamDialogListenerJob =
      scope.launch {
        try {
          ggInAppMessagingService.dialogEvents.collect { event ->
            handleIAMDialogEvent(event)
          }
        } catch (e: Exception) {
          AppLog.e(TAG, "Error in IAM dialog events listener", e.toString())
        }
      }
  }

  /**
   * Handles IAM dialog events and shows appropriate dialogs
   */
  private fun handleIAMDialogEvent(event: IAMDialogEvent) {
    when (event) {
      is IAMDialogEvent.ShowFeedModal -> {
        feedService.showIAMFeedModal(event.feedItem)
      }

      is IAMDialogEvent.PromoCodeCopied -> {
        dialogQueueService.showToast(
          Toast.Simple(
            message = ToastStrings.Success.PromoCodeCopied.Message,
          ),
        )
      }

      else -> {}
    }
  }
}
