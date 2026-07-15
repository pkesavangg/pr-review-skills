package com.dmdbrands.gurus.weight.app.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.app.components.ReconnectScale
import com.dmdbrands.gurus.weight.app.string.AppString.SCALEDISCOVEREDTIMEOUT
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.network.ITokenManager
import com.dmdbrands.gurus.weight.core.network.TokenMigrationHelper
import com.dmdbrands.gurus.weight.core.power.interfaces.IPowerSaveModeObserver
import com.dmdbrands.gurus.weight.core.service.AppNotificationEventService
import com.dmdbrands.gurus.weight.core.service.BluetoothPreferencesService
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.service.NotificationEventType
import com.dmdbrands.gurus.weight.core.service.NotificationTapPayload
import com.dmdbrands.gurus.weight.core.service.WeightOnlyModeEventService
import com.dmdbrands.gurus.weight.core.service.WeightOnlyModeEventType
import com.dmdbrands.gurus.weight.core.service.pushNotification.NotificationDestination
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.LogManager
import com.dmdbrands.gurus.weight.data.services.OperationType
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import com.dmdbrands.gurus.weight.domain.enums.BabyEntryType
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.permission.PermissionState
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.repository.IAppRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.IAccountFlagService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IDeviceInfoService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.Helper.DeviceMetricsHelper
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BabyScaleSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.LcbtScaleSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.MonitorSetupStepHelper
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceDataHelper
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0412
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.getSKU
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.ReadingToast
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.strings.ReadingToastStrings
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.formatWeightValue
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toBpmEntry
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toScaleEntry
import com.dmdbrands.library.ggbluetooth.enums.GGAppType
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.enums.GGScanResponseType
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBPMEntry
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.dmdbrands.library.ggbluetooth.model.GGEntry
import com.dmdbrands.library.ggbluetooth.model.GGScaleEntry
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.dmdbrands.library.ggbluetooth.model.GGWeightEntry
import com.greatergoods.blewrapper.GGCacheDevice
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.ggInAppMessaging.core.service.GGInAppMessagingService
import com.greatergoods.ggInAppMessaging.core.service.IAMDialogEvent
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Centralized ViewModel for app-wide state, including theme mode and FCM token.
 *
 * @property appRepository The repository providing theme and FCM token flows and actions.
 * @constructor Injects the AppRepository dependency.
 */
@HiltViewModel
class AppViewModel
  @Inject
  constructor(
    private val appRepository: IAppRepository,
    private val entryService: IEntryService,
    private val entryReadService: IEntryReadService,
    private val logManager: LogManager,
    private val appNavigationService: IAppNavigationService,
    private val tokenManager: ITokenManager,
    private val dashboardService: IDashboardService,
    private val accountService: IAccountService,
    private val dialogUtility: IDialogUtility,
    private val deviceService: IDeviceService,
    private val ggPermissionService: GGPermissionService,
    private val ggDeviceService: GGDeviceService,
    private val deviceInfoService: IDeviceInfoService,
    private val bluetoothPreferencesService: BluetoothPreferencesService,
    private val feedService: IFeedService,
    private val ggInAppMessagingService: GGInAppMessagingService,
    private val accountFlagService: IAccountFlagService,
    private val tokenMigrationHelper: TokenMigrationHelper,
    private val analyticsService: IAnalyticsService,
    private val powerSaveModeObserver: IPowerSaveModeObserver,
  ) : BaseIntentViewModel<AppState, AppIntent>(
      reducer = AppReducer(),
    ) {
    companion object {
      private const val TAG = "AppViewModel"

      /** BLE protocol types eligible for the discovery popup. */
      private val DISCOVERY_ELIGIBLE_PROTOCOLS =
        setOf(
          GGDeviceProtocolType.GG_DEVICE_PROTOCOL_R4.value, // WiFi+BT scales (0412)
          GGDeviceProtocolType.GG_DEVICE_PROTOCOL_A6.value, // LCBT/Baby scales
          GGDeviceProtocolType.GG_DEVICE_PROTOCOL_TK_BGM.value, // Blood pressure monitors
        )
    }

    override fun provideInitialState(): AppState = AppState()

    /**
     * Device Power Saving Mode state, surfaced to the Compose tree via LocalPowerSaveMode so
     * components can drop continuous animations while Battery Saver is on (MOB-226).
     */
    val powerSaveMode: StateFlow<Boolean> =
      powerSaveModeObserver
        .observe()
        .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
          initialValue = powerSaveModeObserver.isPowerSaveMode(),
        )

    private var currentAccountId: String? = null
    private var canShowScaleDiscoveredModal = true
    private var scaleToIgnore: String? = null
    private var sku: String? = null
    private var discoveredBroadcastId: String? = null
    private var permissionSubscribeJob: Job? = null
    private var syncScaleJob: Job? = null
    private var deviceSubscribeJob: Job? = null
    private var iamDialogListenerJob: Job? = null
    private var pairedScalesSubscribeJob: Job? = null
    private var initialized = false
    private var isPermissionAlertShown = false
    private var latestPairedScales: List<Device> = emptyList()

    init {

      // Drive Compose theme directly from the persisted preference so toggling
      // appearance repaints surfaces immediately, without relying on
      // setDefaultNightMode/UiModeManager to round-trip through Configuration
      // (which is unreliable on API < 31).
      viewModelScope.launch {
        appRepository.themeModeFlow
          .distinctUntilChanged()
          .collect { mode ->
            handleIntent(AppIntent.SetThemeMode(mode))
          }
      }

      // Initialize and maintain currentAccountId globally
      viewModelScope.launch {
        accountService.activeAccountFlow.collect {
          currentAccountId = it?.id
        }
      }

      // Once per launch, after the account is available: commit any entries left in the swipe-delete
      // Undo window by a previous process kill (the pendingDelete flag persists). (MOB-1173)
      viewModelScope.launch {
        accountService.activeAccountFlow.firstOrNull { it != null }
        entryService.commitPendingDeletes()
      }

      viewModelScope.launch {
        try {
          logManager.cleanupOldLogs(5)
          AppLog.i("MainActivity", "Cleaning up old logs")
        } catch (e: Exception) {
          AppLog.e("MainActivity", "Failed to cleanup old logs", e)
        }

        // Migrate tokens from DataStore to EncryptedSharedPreferences (one-time)
        // then load all tokens into TokenManager's in-memory map
        try {
          initEvents()
          tokenMigrationHelper.migrateIfNeeded()
          tokenManager.loadAllTokens()
          tokenManager.getCurrentAccountID()
          AppLog.v(TAG, "Loaded all tokens into TokenManager")
        } catch (e: Exception) {
          AppLog.e(TAG, "Failed to load tokens into TokenManager", e)
        }
      }
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

    override fun handleIntent(intent: AppIntent) {
      when (intent) {
        is AppIntent.OnPopUpConnect -> onPopUpConnect()

        is AppIntent.OnPopUpDismiss -> onPopUpDismiss(shouldSkipDevice = true)

        else -> {}
      }
      super.handleIntent(intent)
    }

    /**
     * Resets scale discovered related properties when switching accounts or on logout.
     * This ensures that the new account can see discovered scales without
     * being affected by the previous account's skip/ignore state, and prevents
     * showing a stale scale-discovered popup when deviceCallbackFlow does not run
     * (e.g. after logout with Bluetooth off and then switching account).
     */
    private fun resetScaleDiscoveredState() {
      bluetoothPreferencesService.clearSkipDevices()
      scaleToIgnore = null
      canShowScaleDiscoveredModal = true
      discoveredBroadcastId = null
      sku = null
      handleIntent(AppIntent.SetScaleDiscovered(false))
      AppLog.d(TAG, "Reset scale discovered state for account switch")
    }

    private fun onPopUpConnect() {
      viewModelScope.launch {
        handleIntent(AppIntent.SetScaleDiscovered(false))
        // Clear all dialogs including IAM modal to ensure it's dismissed when connecting to scale
        dialogQueueService.clear()
        val localSku = sku ?: return@launch
        val scaleInfo = DeviceDataHelper.findScaleInfoBySku(localSku)
        when {
          localSku == SKU_0412 -> {
            navigationService.navigateTo(
              AppRoute.DeviceSetup.BtWifiScaleSetup(
                SKU_0412,
                BtWifiSetupStep.CONNECTING_BLUETOOTH,
                discoveredBroadcastId,
              ),
            )
          }
          DeviceHelper.isBabyScale(localSku) -> {
            navigationService.navigateTo(
              AppRoute.DeviceSetup.BabyScaleSetup(
                sku = localSku,
                initialStep = BabyScaleSetupStep.WAKEUP,
                broadcastId = discoveredBroadcastId,
                scaleInfo = scaleInfo,
              ),
            )
          }
          DeviceHelper.isBpmDevice(localSku) -> {
            navigationService.navigateTo(
              AppRoute.DeviceSetup.BpmSetup(sku = localSku),
            )
          }
          else -> {
            navigationService.navigateTo(
              AppRoute.DeviceSetup.LcbtScaleSetup(
                localSku,
                discoveredBroadcastId,
                LcbtScaleSetupStep.CONNECTING_BLUETOOTH,
                scaleInfo = scaleInfo,
              ),
            )
          }
        }
        onPopUpDismiss()
      }
    }

    private fun onPopUpDismiss(shouldSkipDevice: Boolean = false) {
      viewModelScope.launch {
        handleIntent(AppIntent.SetScaleDiscovered(false))
        // Set canShowScaleDiscoveredModal to false for 30 seconds
        canShowScaleDiscoveredModal = false
        delay(30 * 1000)
        canShowScaleDiscoveredModal = true
      }
      if (shouldSkipDevice) {
        discoveredBroadcastId?.let { broadcastId ->
          ggDeviceService.skipDevice(broadCastId = broadcastId, considerForSession = true)
        }
      }
      AppLog.d(TAG, "Closed scale discovered popup ${state.value.isScaleDiscovered}")
    }

    private fun initEvents() {
      viewModelScope.launch {
        appNavigationService.authEvent.collect { authState ->
          when (authState) {
            is AuthState.LoggedInFromLoading -> {
              stopScan()
              resetScaleDiscoveredState()
              startObserversOnly(authState.account, fromLoadingScreen = true)
              dashboardService.setSelectedKey(null)
            }

            is AuthState.LoggedOut -> {
              stopScan()
              if (authState.isActiveAccount || authState.isLastAccount) {
                resetScaleDiscoveredState()
                routeToLandingOrApp()
                dialogQueueService.clear()
              }
            }

            is AuthState.AccountDeleted -> {
              if (authState.isActiveAccount) {
                stopScan()
                dashboardService.setSelectedKey(null)
                routeToLandingOrApp()
              }
            }

            is AuthState.UnauthorizedLogout -> {
              // Show account logged out alert
              viewModelScope.launch {
                val activeAccount =
                  accountService.handleUnauthorizedLogout(authState.accountId)
                if (activeAccount != null) {
                  stopScan()
                  navigationService.replaceStack(route = AppRoute.Auth.MultiAccountLanding)
                  dialogUtility.showAccountLoggedOutAlert(activeAccount.firstName)
                }
              }
            }

            is AuthState.EncryptionFailure -> {
              // Encryption failure affects all accounts (shared encrypted file).
              // Reuse existing logout alert pattern — force re-login.
              viewModelScope.launch {
                val activeAccount = accountService.getCurrentAccount()
                val username = activeAccount?.firstName ?: ""
                // Log out all accounts since encrypted storage is shared
                accountService.logoutAll()
                stopScan()
                navigationService.replaceStack(route = AppRoute.Auth.Landing)
                if (username.isNotEmpty()) {
                  dialogUtility.showAccountLoggedOutAlert(username)
                }
              }
            }

            is AuthState.AccountAdded -> {
            }

            is AuthState.AccountSwitched -> {
              // Switching accounts must start the new account with a clean scan state. Otherwise the
              // previous account's skip/ignore flags leak across and can suppress the duplicate-user
              // reconnect alert after switching back to a previously connected account (MOB-175).
              // Mirrors the reset already done on LoggedInFromLoading / LoggedOut.
              resetScaleDiscoveredState()
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

            is AuthState.ProfileUpdated -> {
              // Profile updated - no navigation needed, just log
              AppLog.d(TAG, "Profile updated for account: ${authState.account.id}")
            }

            is AuthState.NavigateToMyAccounts -> {
              // Stop scan when navigating to MyAccounts screen
              stopScan()
              AppLog.d(TAG, "Stopped scan due to navigation to MyAccounts screen")
            }

            is AuthState.NavigateBackFromMyAccounts -> {
              // Start scan when navigating back from MyAccounts screen
              startScan()
              syncScales()
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
      }
      viewModelScope.launch {
        AppNotificationEventService.events.collect {
          when (it) {
            NotificationEventType.NOTIFICATION_TAPPED -> {
              entryService.syncOperations()
            }

            NotificationEventType.NOTIFICATION_RECEIVED -> {
              entryService.syncOperations()
              dialogQueueService.showToast(Toast.Simple(message = "Success! Entry added"))
            }

            else -> {}
          }
        }
      }
      viewModelScope.launch {
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
     * Starts long-lived observers only (no account setup or navigation).
     * Called when [AuthState.LoggedInFromLoading] is received; LoadingScreenViewModel already did loadData + autoLogin.
     */
    private fun startObserversOnly(
      account: Account,
      fromLoadingScreen: Boolean = false,
    ) {
      viewModelScope.launch {
        try {
          permissionSubscribeJob?.cancel()
          deviceSubscribeJob?.cancel()
          syncScaleJob?.cancel()
          pairedScalesSubscribeJob?.cancel()
          // Reset initialized flag to ensure permission checks happen after login
          initialized = false
          if (fromLoadingScreen) {
            delay(1000)
          }
          subscribePermissions()
          subscribeDeviceCallback()
          subscribePairedScales()
          syncScales()
          accountService.checkAndTriggerGraphScrollHint()
          entryService.initializeGoalCardMonitoring(account.id)
          feedService.fetchFeedItems()
          initialiseIAMDialogListener()
          feedService.checkAndTriggerFeedModal()
          updateUnRead()
        } catch (e: Exception) {
          AppLog.e(TAG, "startObserversOnly failed", e)
        }
      }
    }

    private fun subscribePermissions() {
      startScan()
      permissionSubscribeJob =
        viewModelScope.launch {
          AppLog.d("AppViewModel", "subscribePermissions launched")
          ggPermissionService.permissionCallBackFlow.collect { permissions ->
            if (permissions.isNotEmpty()) {
              if (AppPermissionsHelper.checkScanPermissions(permissions)) {
                AppLog.d("AppViewModel", "Scan initialised")
                initialized = true
              } else {
                if (!initialized) {
                  val pairedScales = deviceService.pairedScales.first()
                  AppLog.d(TAG, "Paired scales: $pairedScales")
                  val hasBtWifiScales =
                    pairedScales.isNotEmpty() &&
                      pairedScales.any { savedScale ->
                        val scaleInfo = DeviceDataHelper.findScaleInfoBySku(savedScale.getSKU())
                        scaleInfo?.setupType in
                          listOf(
                            DeviceSetupType.BtWifiR4,
                            DeviceSetupType.Lcbt,
                            DeviceSetupType.EspTouchWifi,
                            DeviceSetupType.Wifi,
                          )
                      }
                  val canRequestNotifPermission =
                    AppPermissionsHelper
                      .canRequestNotificationPermission(ggPermissionService.permissionCallBackFlow.value)
                  if (canRequestNotifPermission && hasBtWifiScales) {
                    checkAndRequestNotificationPermission()
                  }
                  // Get only the required permissions for the paired scales
                  val requiredPermissionSets = AppPermissionsHelper.getRequiredPermissionSets(pairedScales)
                  if (requiredPermissionSets.isNotEmpty()) {
                    // Check if all required permissions are enabled
                    val areAllRequiredPermissionsEnabled =
                      requiredPermissionSets.all { permissionType ->
                        val permissionState = permissions[permissionType] ?: PermissionState.NOT_DETERMINED
                        permissionState == PermissionState.ENABLED
                      }

                    // Only request permissions if they are not all enabled
                    if (!areAllRequiredPermissionsEnabled) {
                      requestPermissions(GGPermissionType.ALL)
                    }
                  }
                  initialized = true
                }
              }
            }
          }
        }
    }

    private fun subscribeDeviceCallback() {
      deviceSubscribeJob =
        viewModelScope.launch {
          ggDeviceService.deviceCallbackFlow.collect { response ->
            AppLog.d(
              TAG,
              "deviceCallback triggered: response type=${response.javaClass.simpleName}, response=$response",
            )
            // Note: Bluetooth state check is done in GGBluetoothSDKHelper before emitting
            // This log helps track when callbacks are received in the ViewModel
            when (response) {
              is GGScanResponse.DeviceDetail -> {
                AppLog.i(TAG, "Scan Response Device Detail: $response")
                handleDeviceResponse(response)
              }

              is GGScanResponse.Entry -> {
                AppLog.i(TAG, "Scan Response Entry: $response")
                handleEntryResponse(response)
              }

              else -> null
            }
          }
        }
    }

    /**
     * Subscribes to paired scales flow and stores the latest value.
     * This ensures we always have the most up-to-date paired scales for weight-only mode checks.
     */
    private fun subscribePairedScales() {
      pairedScalesSubscribeJob?.cancel()
      pairedScalesSubscribeJob =
        viewModelScope.launch {
          deviceService.pairedScales.collect { pairedScales ->
            latestPairedScales = pairedScales
            AppLog.d(TAG, "Updated latest paired scales: ${pairedScales.size} devices")
            // Check if weight-only mode alert should be shown when paired scales are updated
            checkCanShowWeightOnlyModeAlert()
          }
        }
    }

    private fun syncScales() {
      syncScaleJob?.cancel()
      syncScaleJob =
        viewModelScope.launch {
          deviceService.getGGBTDevices().collect { devices ->
            AppLog.d(TAG, "syncScales called")
            ggDeviceService.syncDevices(devices)
          }
        }
    }

    private fun handleEntryResponse(entryResponse: GGScanResponse.Entry) {
      val data = entryResponse.data
      val scaleEntries = data.filterIsInstance<GGScaleEntry>()
      val bpmEntries = data.filterIsInstance<GGBPMEntry>()
      // Weight-only devices (baby scale + weight-only scales) emit GGWeightEntry, which carries
      // no body composition — distinct from the body-scale GGScaleEntry (MOB-598).
      val weightEntries = data.filterIsInstance<GGWeightEntry>()

      // Confirms the scale actually emitted a reading and which GGEntry subtype reached the app —
      // the missing log when a baby reading "doesn't sync" (it never arrived / wasn't a handled type).
      AppLog.i(
        TAG,
        "handleEntryResponse type=${entryResponse.type} total=${data.size} " +
          "scale=${scaleEntries.size} bpm=${bpmEntries.size} weight=${weightEntries.size} " +
          "subtypes=${data.map { it.javaClass.simpleName }}",
      )

      when (entryResponse.type) {
        GGScanResponseType.SINGLE_ENTRY, GGScanResponseType.MULTI_ENTRIES -> {
          if (scaleEntries.isNotEmpty()) {
            saveEntry(scaleEntries)
          }
          if (bpmEntries.isNotEmpty()) {
            saveBpmEntry(bpmEntries)
          }
          // Route weight-only readings through the same save/assign path by representing each
          // as a weight-only scale entry. saveEntry's SKU check then sends a baby-scale reading
          // into the assign-to-baby flow; only the weight is used downstream (MOB-598).
          if (weightEntries.isNotEmpty()) {
            saveEntry(weightEntries.map { it.toWeightOnlyScaleEntry() })
          }
        }

        else ->
          AppLog.w(TAG, "handleEntryResponse: unhandled entry type=${entryResponse.type}")
      }
    }

    /**
     * Represents a weight-only [GGWeightEntry] (baby scale / weight-only scale) as a body-scale
     * [GGScaleEntry] with zeroed body-composition so it can flow through the shared [saveEntry]
     * path. Only the weight is meaningful; the baby-assignment flow (and `toBabyEntry`) reads the
     * weight alone, so the zeroed metrics are never persisted (MOB-598).
     */
    private fun GGWeightEntry.toWeightOnlyScaleEntry(): GGScaleEntry =
      GGScaleEntry(
        bmi = 0f,
        bmr = 0,
        bodyFat = 0f,
        water = 0f,
        boneMass = 0f,
        metabolicAge = 0,
        muscleMass = 0f,
        proteinPercent = 0f,
        skeletalMusclePercent = 0f,
        subcutaneousFatPercent = 0f,
        unit = unit,
        visceralFatLevel = 0,
        weight = weight,
        weightInKg = weightInKg ?: weight,
        date = date,
        impedance = 0f,
        pulse = 0,
        broadcastId = broadcastId,
        broadcastIdString = broadcastIdString,
        protocolType = protocolType,
        operationType = operationType,
      )

    private fun saveBpmEntry(ggEntries: List<GGBPMEntry>) {
      // A monitor holds multiple user slots; attribute the reading to the row matching this slot.
      val userNumber = ggEntries.firstOrNull()?.userNumber?.toInt()
      val protocolType = ggEntries.firstOrNull()?.protocolType
      saveBluetoothEntries(ggEntries, userNumber, protocolType) { accountId, deviceId ->
        ggEntries.mapIndexed { index, entry -> entry.toBpmEntry(accountId, deviceId, index.toLong()) }
      }
    }

    private fun <T : GGEntry> saveBluetoothEntries(
      ggEntries: List<T>,
      userNumber: Int? = null,
      protocolType: String? = null,
      mapEntries: suspend (accountId: String, deviceId: String) -> List<Entry>,
    ) {
      viewModelScope.launch {
        if (ggEntries.isEmpty()) return@launch
        val accountId = currentAccountId ?: return@launch
        val isSetupInProgress = deviceService.isSetupInProgress()
        val broadcastId = ggEntries.first().broadcastId
        // Match the reading to the exact paired row by broadcastId + userNumber (a monitor can be
        // paired under multiple user slots). Fall back to broadcastId-only, then to the single-device
        // heal: a device synced from GET /v3/paired-device carries no broadcastId (the server omits
        // it), so we attribute to the single un-identified BPM device and backfill it. (MOB-596)
        val device =
          (userNumber?.let { deviceService.getScaleByBroadcastIdAndUser(broadcastId, it, accountId) })
            ?: deviceService.getScaleByBroadcastId(broadcastId, accountId)
            ?: deviceService.healBpmDeviceBroadcastId(broadcastId, accountId, protocolType)

        if (device == null && !isSetupInProgress) return@launch

        try {
          val entries = mapEntries(accountId, device?.id ?: "")
          if (isSetupInProgress) {
            // During setup, save immediately without a reading card (parity with the weight path).
            entryService.addEntry(entries)
            checkAccountFlags("entry")
          } else {
            // Show the "New BPM Reading Received" card with SAVE/DISCARD; the reading is persisted
            // only when the user taps SAVE — no auto-save, matching the weight-scale flow.
            showBpmReadingToast(entries)
          }
        } catch (e: Exception) {
          AppLog.e(TAG, "Error saving entry", e)
        }
      }
    }

    /**
     * Shows the "New BPM Reading Received" arrival card (SAVE/DISCARD) for a synced monitor reading,
     * mirroring the weight-scale flow: the reading is only persisted on SAVE; DISCARD just dismisses
     * (nothing was written). Extra buffered readings surface a "+N more… VIEW" pill and all save
     * together on SAVE.
     */
    private fun showBpmReadingToast(entries: List<Entry>) {
      val latest = entries.filterIsInstance<BpmEntry>().maxByOrNull { it.entry.entryTimestamp } ?: return
      val additionalCount = (entries.size - 1).coerceAtLeast(0)
      dialogQueueService.showToast(
        Toast.Custom(
          ReadingToast(
            reading = "${latest.systolic}/${latest.diastolic} mmHg pulse ${latest.pulse}",
            type = ProductType.BLOOD_PRESSURE,
            timestamp = "Just now",
            additionalCount = additionalCount,
            primaryAction = { saveBpmEntriesFromToast(entries) },
            secondaryAction = {
              // The reading is only persisted on SAVE, so discarding just dismisses the card (MOB-598).
              dialogQueueService.dismissToast()
              AppLog.i(TAG, "BPM entry discarded via reading toast")
            },
            onView = {
              viewModelScope.launch { navigationService.navigateTo(AppRoute.Main.History) }
            },
          ),
        ),
      )
    }

    /** Persists the buffered BPM readings from the reading toast's SAVE action. */
    private fun saveBpmEntriesFromToast(entries: List<Entry>) {
      viewModelScope.launch {
        try {
          entryService.addEntry(entries)
          checkAccountFlags("entry")
          AppLog.i(TAG, "BPM entry saved via reading toast")
        } catch (e: Exception) {
          AppLog.e(TAG, "Error saving BPM entry from toast", e)
        }
      }
    }

    /**
     * Picks which scale-user token to delete when reconnecting a duplicate-name user (MOB-175).
     *
     * When two accounts share a scale display name (e.g. both "renu"), a name-only match could pick an
     * arbitrary user and delete the wrong account's slot. Prefer the user whose token matches THIS
     * account's stored token ([localToken]); fall back to the first name match only when no token
     * matches. Returns null when no user shares the display name.
     */
    internal fun selectDuplicateUserToken(
      userList: List<GGBTUser>,
      displayName: String?,
      localToken: String?,
    ): String? {
      val nameMatches = userList.filter { user -> user.name == displayName }
      return nameMatches.firstOrNull { it.token == localToken }?.token
        ?: nameMatches.firstOrNull()?.token
    }

    private fun handleDeviceResponse(deviceResponse: GGScanResponse.DeviceDetail) {
      val data = deviceResponse.data
      viewModelScope.launch {
        // Check if scale is already known (paired) - similar to Angular's isKnown logic
        val accountId = currentAccountId ?: return@launch
        val isKnownScale =
          data.broadcastId?.let { broadcastId ->
            // Check against latestPairedScales list first (similar to Angular's this.scales.find)
            latestPairedScales.any { scale ->
              scale.device?.broadcastId == broadcastId
            } ||
              deviceService.getScaleByBroadcastId(broadcastId, accountId) != null
          } == true
        AppLog.d(TAG, "device response ${deviceResponse.type}")

        when (deviceResponse.type) {
          GGScanResponseType.NEW_DEVICE -> {
            AppLog.d(TAG, "new device discovered ${data.macAddress} $canShowScaleDiscoveredModal")
            if (canShowScaleDiscoveredModal && data.protocolType in DISCOVERY_ELIGIBLE_PROTOCOLS) {
              val currentRoute = navigationService.getCurrentRoute()
              val isSetupInProgress = deviceService.isSetupInProgress()
              val isOnMainScreen = currentRoute is AppRoute.Home || currentRoute is AppRoute.Main.Dashboard

              if (isOnMainScreen && currentRoute !is AppRoute.DeviceSetup && !isSetupInProgress) {
                // Check if device is in skipDevices list
                val isSkipped =
                  data.broadcastId?.let { bluetoothPreferencesService.containsSkipDevice(it) } == true ||
                    data.macAddress.let { bluetoothPreferencesService.containsSkipDevice(it) }

                // Check if same scale was shown recently (15 seconds)
                val isIgnored = data.macAddress == scaleToIgnore
                AppLog.d(TAG, "isSkipped: $isSkipped, isIgnored: $isIgnored")

                // Apply MAC address filtering for 0412 scales (similar to Angular's onfoundnewsmartwifiscale)
                val deviceSku = data.getSKU().orEmpty()
                val shouldShow =
                  if (deviceSku == SKU_0412) {
                    val isAllow = bluetoothPreferencesService.shouldShowDevice(data.macAddress)
                    isAllow
                  } else {
                    true // Don't filter non-0412 scales
                  }
                AppLog.d(TAG, "devicesku: $deviceSku")
                // Only show if not skipped, not ignored, not known, and shouldShow is true
                if (!isSkipped && !isIgnored && !isKnownScale && shouldShow) {
                  handleIntent(AppIntent.SetScaleDiscovered(true))
                  handleIntent(AppIntent.SetSku(deviceSku))
                  sku = deviceSku
                  discoveredBroadcastId = data.broadcastId

                  // Set scaleToIgnore for 15 seconds to prevent showing same scale again
                  data.macAddress.let { macAddress ->
                    scaleToIgnore = macAddress
                    viewModelScope.launch {
                      delay(SCALEDISCOVEREDTIMEOUT)
                      scaleToIgnore = null
                    }
                  }

                  val customizedDevice =
                    when {
                      deviceSku == SKU_0412 -> customizeDevice(data)
                      DeviceHelper.isBabyScale(deviceSku) ->
                        Device(
                          device = data,
                          deviceType = DeviceSetupType.BabyScale.value,
                          sku = deviceSku,
                        )
                      DeviceHelper.isBpmDevice(deviceSku) ->
                        Device(
                          device = data,
                          deviceType = MonitorSetupStepHelper.setupTypeForSku(deviceSku).value,
                          sku = deviceSku,
                        )
                      else ->
                        Device(
                          device = data,
                          deviceType = DeviceSetupType.Lcbt.value,
                          sku = deviceSku,
                        )
                    }
                  ggDeviceService.addCacheDevice(discoveredBroadcastId, customizedDevice)
                  canShowScaleDiscoveredModal = false
                } else {
                  if (isSkipped) {
                    AppLog.d(TAG, "Skipped device with broadcastId: ${data.broadcastId} or MAC: ${data.macAddress}")
                  }
                  if (isIgnored) {
                    AppLog.d(TAG, "Ignoring recently shown scale with MAC: ${data.macAddress}")
                  }
                  if (isKnownScale) {
                    AppLog.d(TAG, "Known device (already paired) with broadcastId: ${data.broadcastId}")
                  }
                  if (!shouldShow) {
                    AppLog.d(TAG, "Filtered out 0412 scale with MAC: ${data.macAddress}")
                  }
                }
              }
            }
          }

          GGScanResponseType.DEVICE_CONNECTED -> {
            AppLog.d(TAG, "Device connected ${data.broadcastId}")
            analyticsService.logEvent(
              IAnalyticsService.Events.SCALE_CONNECTED,
              android.os.Bundle().apply {
                putString(IAnalyticsService.Params.SCALE_TYPE, data.broadcastId ?: "unknown")
              },
            )
            onDeviceUpdate(
              deviceDetail = data,
              connectionStatus = BLEStatus.CONNECTED,
            )
            checkCanShowWeightOnlyModeAlert()
          }

          GGScanResponseType.DEVICE_DISCONNECTED -> {
            onDeviceUpdate(
              deviceDetail = data,
              connectionStatus = BLEStatus.DISCONNECTED,
            )
            handleIntent(AppIntent.SetScaleDiscovered(false))
            checkCanShowWeightOnlyModeAlert()
          }

          GGScanResponseType.DEVICE_INFO_UPDATE -> {
            onDeviceUpdate(
              deviceDetail = data,
              connectionStatus = BLEStatus.CONNECTED,
            )
            checkCanShowWeightOnlyModeAlert()
          }

          GGScanResponseType.WIFI_STATUS_UPDATE -> {
            onDeviceUpdate(
              deviceDetail = data,
              connectionStatus = BLEStatus.CONNECTED,
            )
          }

          GGScanResponseType.DEVICE_MEMORY_FULL -> {
            val currentRoute = navigationService.getCurrentRoute()
            val isOnAuthScreen = currentRoute is AppRoute.Auth
            if (currentRoute !is AppRoute.DeviceSetup && isKnownScale && !isOnAuthScreen) {
              dialogQueueService.showDialog(
                ReconnectScale.getMaxUserAlert(
                  onConfirm = {
                    viewModelScope.launch {
                      try {
                        val accountId = currentAccountId ?: return@launch
                        val broadcastId = data.broadcastId ?: return@launch
                        val device = deviceService.getScaleByBroadcastId(broadcastId, accountId)
                        if (device == null) {
                          AppLog.w(TAG, "DEVICE_MEMORY_FULL: scale not found for broadcastId=$broadcastId")
                          return@launch
                        }
                        dialogQueueService.showLoader("Loading...")
                        ggDeviceService.addCacheDevice(data.broadcastId, device)
                        ggDeviceService.getUsers(device.toGGBTDevice()) { response ->
                          viewModelScope.launch {
                            dialogQueueService.dismissLoader()
                            navigationService.navigateTo(
                              AppRoute.DeviceSetup.BtWifiScaleSetup(
                                sku = data.getSKU().orEmpty(),
                                initialStep = BtWifiSetupStep.USER_LIMIT_REACHED,
                                broadcastId = data.broadcastId,
                                userList = response.user,
                              ),
                            )
                          }
                        }
                      } catch (e: Exception) {
                        AppLog.e(TAG, "DEVICE_MEMORY_FULL: error handling max user alert", e)
                        dialogQueueService.dismissLoader()
                      }
                    }
                  },
                  onCancel = {
                    data.broadcastId?.let { broadcastId ->
                      ggDeviceService.skipDevice(broadcastId, considerForSession = true)
                    }
                  },
                ),
              )
            }
          }

          GGScanResponseType.DEVICE_DUPLICATE_USER -> {
            try {
              val currentRoute = navigationService.getCurrentRoute()
              val isOnAuthScreen = currentRoute is AppRoute.Auth
              if (currentRoute !is AppRoute.DeviceSetup && !isOnAuthScreen) {
                dialogQueueService.showDialog(
                  ReconnectScale.getDuplicateUserAlert(
                    onConfirm = {
                      viewModelScope.launch {
                        val accountId = currentAccountId ?: return@launch
                        val broadcastId = data.broadcastId ?: return@launch
                        val device = deviceService.getScaleByBroadcastId(broadcastId, accountId) ?: return@launch
                        val userList =
                          suspendCoroutine { continuation ->
                            ggDeviceService.getUsers(device.toGGBTDevice()) { response ->
                              continuation.resume(response.user)
                            }
                          }
                        val scaleToken =
                          selectDuplicateUserToken(
                            userList = userList,
                            displayName = device.preferences?.displayName,
                            localToken = device.toGGBTDevice().token,
                          )
                        ggDeviceService.deleteAccount(device.toGGBTDevice().copy(token = scaleToken)) {
                          if (it.name == GGUserActionResponseType.DELETE_COMPLETED.name) {
                            viewModelScope.launch {
                              ggDeviceService.addCacheDevice(data.broadcastId, device)
                              navigationService.navigateTo(
                                AppRoute.DeviceSetup.BtWifiScaleSetup(
                                  data.getSKU().orEmpty(),
                                  BtWifiSetupStep.CONNECTING_BLUETOOTH,
                                  data.broadcastId,
                                ),
                              )
                            }
                          }
                        }
                      }
                    },
                    onCancel = {
                      data.broadcastId?.let { broadcastId ->
                        ggDeviceService.skipDevice(broadcastId, considerForSession = true)
                      }
                    },
                  ),
                )
              }
            } catch (e: Exception) {
              AppLog.d(TAG, "Error during duplicate user alert $e")
            }
          }

          else -> null
        }
      }
    }

    private suspend fun customizeDevice(ggDeviceDetail: GGDeviceDetail): GGCacheDevice {
      val username = accountService.activeAccountFlow.first()?.firstName ?: "Default"
      val token = deviceService.getScaleToken()
      val device =
        Device(
          device = ggDeviceDetail,
          token = token,
        )
      return device.copy(
        deviceType = DeviceSetupType.BtWifiR4.value,
        sku = "0412",
        preferences = DeviceMetricsHelper.getDefaultPreference(username, device.id),
      )
    }

    private fun saveEntry(ggEntry: List<GGScaleEntry>) {
      viewModelScope.launch {
        if (ggEntry.isEmpty()) {
          return@launch
        }
        val accountId = currentAccountId ?: return@launch
        // During setup scale list will be empty so ignoring this check during setup and allow all entries.
        val isSetupInProgress = deviceService.isSetupInProgress()
        val readingBroadcastId = ggEntry.first().broadcastId
        // A6 baby scales are often saved without a broadcastId (server omits it / older rows), so the
        // id lookup misses and the reading gets misclassified as a WEIGHT reading — surfacing a
        // "weight reading received" toast and saving it as weight even when it's a baby scale with no
        // baby profile. Fall back to attributing an A6 reading to the lone paired baby scale (by SKU)
        // so it classifies as BABY. (baby-scale reconnect fix)
        val readingIsA6 = ggEntry.first().protocolType == GGDeviceProtocolType.GG_DEVICE_PROTOCOL_A6.value
        val device =
          deviceService.getScaleByBroadcastId(readingBroadcastId, accountId)
            ?: if (readingIsA6) deviceService.healBabyScaleBroadcastId(readingBroadcastId, accountId) else null

        // A reading whose broadcastId matches no paired device (and isn't an A6 baby scale healed
        // above) must not be saved/toasted under this account — mirrors the BPM path's guard.
        if (device == null && !isSetupInProgress) {
          return@launch
        }

        // Get user height for BMI calculation
        val activeAccount = accountService.activeAccountFlow.first()
        val userHeight = activeAccount?.height
        // Store the reading in the My Weight (adult) unit preference — account.isMetric is
        // weightUnit == KG. NOT measurementUnits, which is the baby-scale unit. So an A6/0382
        // reading (always broadcast in kg) is saved as the lb value the scale displays for
        // imperial (lb) accounts, and as kg for metric accounts. (MOB-872)
        val isMetric = activeAccount?.isMetric == true

        val entry =
          ggEntry.map { ggScaleEntry ->
            val scaleEntry = ggScaleEntry.toScaleEntry(accountId, device?.id ?: "", isMetric)

            // Check if BMI is 0.0 or null and calculate it if user height is available
            if ((scaleEntry.scale.scaleEntry.bmi == null || scaleEntry.scale.scaleEntry.bmi == 0.0) &&
              userHeight != null
            ) {
              val calculatedBmi =
                EntryHelper.getCalculatedBMI(
                  weight =
                    scaleEntry.scale.scaleEntry.weight
                      .toFloat(),
                  unit = scaleEntry.entry.unit,
                  height = userHeight,
                )

              // Update the BMI in the scale entry
              val updatedScaleEntry = scaleEntry.scale.scaleEntry.copy(bmi = calculatedBmi)
              val updatedScaleEntryWithMetrics = scaleEntry.scale.copy(scaleEntry = updatedScaleEntry)

              AppLog.d(
                TAG,
                "Calculated BMI: $calculatedBmi for weight: ${scaleEntry.scale.scaleEntry.weight}, height: $userHeight",
              )

              scaleEntry.copy(scale = updatedScaleEntryWithMetrics)
            } else {
              scaleEntry
            }
          }

        if (isSetupInProgress) {
          // During setup, save immediately without toast
          try {
            entryService.addEntry(entry)
            checkAccountFlags("entry")
          } catch (e: Exception) {
            AppLog.e(TAG, "Error during saving entry", e)
          }
        } else {
          // Show reading toast — user decides to save or discard
          val readingType =
            device?.sku?.let { sku ->
              when {
                DeviceHelper.isBabyScale(sku) -> ProductType.BABY
                DeviceHelper.isBpmDevice(sku) -> ProductType.BLOOD_PRESSURE
                else -> ProductType.MY_WEIGHT
              }
            } ?: ProductType.MY_WEIGHT
          showReadingToast(entry, readingType, sourceSku = device?.sku)
        }
      }
    }

    /**
     * Shows the post-reading toast (Save/Discard, or the baby assign flow) for [entry]. Extracted
     * from [saveEntry] so it can be reused. [sourceSku]
     * is the originating device SKU (null for synthesized/debug readings).
     */
    private fun showReadingToast(
      entry: List<ScaleEntry>,
      readingType: ProductType,
      sourceSku: String?,
    ) {
      // Show the latest reading in the card; any extra buffered readings (taken while
      // disconnected) surface as a "+N more… VIEW" count pill (MOB-598).
      val latestEntry = entry.maxByOrNull { it.entry.entryTimestamp } ?: return
      val reading = formatReadingForDisplay(latestEntry, readingType)
      val additionalCount = (entry.size - 1).coerceAtLeast(0)

      // Snapshot of baby profiles at arrival — drives the single-baby card and timeout auto-assign.
      val babiesAtArrival = if (readingType == ProductType.BABY) availableBabyProfiles() else emptyList()
      val singleBabyName = babiesAtArrival.singleOrNull()?.name

      // A baby scale reading with no baby profile has nowhere to be saved —
      // surface an "ADD A BABY" CTA instead of the assign flow (MOB-426).
      val hasNoBabyProfile = readingType == ProductType.BABY && babiesAtArrival.isEmpty()

      // Multi-baby readings auto-assign to the last-assigned baby on timeout, if it still
      // exists; single-baby/no-baby readings have no auto-assign target (MOB-598).
      val autoAssignBabyId =
        lastAssignedBabyId
          ?.takeIf { readingType == ProductType.BABY && babiesAtArrival.size > 1 }
          ?.takeIf { id -> babiesAtArrival.any { it.id == id } }

      dialogQueueService.showToast(
        Toast.Custom(
          ReadingToast(
            reading = reading,
            type = readingType,
            timestamp = "Just now",
            noBabyProfile = hasNoBabyProfile,
            assignTargetName = singleBabyName,
            additionalCount = additionalCount,
            primaryAction = {
              if (hasNoBabyProfile) {
                // Hold the reading and auto-assign it to the baby the user is about to create; the
                // deactivate handler assigns on success or drops it on cancel (Option A).
                pendingBabyReading =
                  PendingBabyReading(
                    reading = reading,
                    entry = entry,
                    sourceSku = sourceSku,
                    baselineBabyIds = babiesAtArrival.map { it.id }.toSet(),
                  )
                viewModelScope.launch {
                  registerAddBabyDeactivateHandler()
                  navigationService.navigateTo(AppRoute.AccountSettings.AddBaby())
                }
              } else if (readingType == ProductType.BABY) {
                if (babiesAtArrival.size == 1) {
                  // Single baby — SAVE persists straight to that baby (no picker) (MOB-598).
                  viewModelScope.launch {
                    assignReadingToBaby(
                      reading,
                      entry,
                      babiesAtArrival.first().id,
                      babiesAtArrival,
                      emptyList(),
                      sourceSku,
                    )
                  }
                } else {
                  showAssignMeasurementDialog(reading, entry, sourceSku = sourceSku)
                }
              } else {
                saveEntryFromToast(entry)
              }
            },
            secondaryAction = {
              // The reading is only persisted on Save/Assign, so discarding an unsynced
              // reading just dismisses the card — nothing was written (MOB-428). Also drop any
              // held reading so a later baby-add never picks it up.
              pendingBabyReading = null
              dialogQueueService.dismissToast()
              AppLog.i(TAG, "Entry discarded via reading toast")
            },
            onView = {
              // "VIEW" opens History so all buffered readings can be seen (MOB-598).
              viewModelScope.launch { navigationService.navigateTo(AppRoute.Main.History) }
            },
            // Timeout = no user response → KEEP the reading (per Figma "auto-assign on timeout"),
            // NOT discard: weight/BPM auto-save, baby auto-assigns to its target. Only the no-baby
            // and multi-baby-without-a-target cases have nowhere to save, so they just dismiss.
            onTimeout =
              when {
                hasNoBabyProfile -> null
                autoAssignBabyId != null -> {
                  val babyId = autoAssignBabyId
                  {
                    viewModelScope.launch {
                      assignReadingToBaby(
                        reading,
                        entry,
                        babyId,
                        babiesAtArrival,
                        emptyList(),
                        sourceSku,
                      )
                    }
                  }
                }
                readingType == ProductType.BABY && babiesAtArrival.size == 1 -> {
                  val babyId = babiesAtArrival.first().id
                  {
                    viewModelScope.launch {
                      assignReadingToBaby(
                        reading,
                        entry,
                        babyId,
                        babiesAtArrival,
                        emptyList(),
                        sourceSku,
                      )
                    }
                  }
                }
                readingType == ProductType.BABY -> null
                else -> {
                  { saveEntryFromToast(entry) }
                }
              },
          ),
        ),
      )
    }

    /** Saves a non-baby reading straight from the reading toast's SAVE action. */
    private fun saveEntryFromToast(entry: List<ScaleEntry>) {
      viewModelScope.launch {
        try {
          entryService.addEntry(entry)
          checkAccountFlags("entry")
          AppLog.i(TAG, "Entry saved via reading toast")
        } catch (e: Exception) {
          AppLog.e(TAG, "Error saving entry from toast", e)
        }
      }
    }

    private fun availableBabyProfiles(): List<BabyProfile> =
      productSelectionManager.availableProducts.value
        .filterIsInstance<ProductSelection.Baby>()
        .map { it.profile }

    /** Most-recently-assigned baby; the timeout auto-assign target for multi-baby readings (MOB-598). */
    private var lastAssignedBabyId: String? = null

    /**
     * A baby-scale reading held while the user creates a baby from the no-baby toast's "ADD A BABY".
     * [baselineBabyIds] is the set of baby ids that existed when the reading arrived, so we can detect
     * the one newly-created baby to auto-assign it to.
     */
    private data class PendingBabyReading(
      val reading: String,
      val entry: List<ScaleEntry>,
      val sourceSku: String?,
      val baselineBabyIds: Set<String>,
    )

    private var pendingBabyReading: PendingBabyReading? = null

    /**
     * Registers a one-shot handler that fires when the user leaves the Add-a-Baby screen after tapping
     * "ADD A BABY" on a no-baby reading toast. If a new baby was created, the held reading is
     * auto-assigned to it (Option A); if the user cancelled (no new baby), the pending reading is
     * dropped so it can never latch onto an unrelated future baby.
     */
    private fun registerAddBabyDeactivateHandler() {
      viewModelScope.launch {
        navigationService.registerOnDeactivate(AppRoute.AccountSettings.AddBaby()) {
          val pending = pendingBabyReading
          pendingBabyReading = null
          if (pending != null) {
            val newBaby = availableBabyProfiles().firstOrNull { it.id !in pending.baselineBabyIds }
            if (newBaby != null) {
              // A baby now exists — re-present the reading as the ASSIGN / DON'T ASSIGN card so the
              // user chooses (or it auto-assigns on timeout). Don't silently auto-assign and show the
              // "Reading assigned / Assign to new baby" post card. (Figma 30295-24866)
              AppLog.i(TAG, "Re-presenting held reading now that baby ${newBaby.id} exists")
              showReadingToast(pending.entry, ProductType.BABY, pending.sourceSku)
            } else {
              AppLog.i(TAG, "Add-a-baby cancelled — dropping held reading")
            }
          }
          navigationService.unregisterOnDeactivate(AppRoute.AccountSettings.AddBaby())
          // Never block leaving the screen.
          true
        }
      }
    }

    /**
     * Shows the baby picker. On confirm, assigns the reading to the chosen baby.
     * [preSelectedBabyId] pre-selects a baby (used by Reassign); [previousEntryIds] are the
     * locally-saved entries to remove first when reassigning, so a reassign never duplicates.
     */
    private fun showAssignMeasurementDialog(
      reading: String,
      entry: List<ScaleEntry>,
      preSelectedBabyId: String? = null,
      previousEntryIds: List<Long> = emptyList(),
      sourceSku: String? = null,
    ) {
      val babies = availableBabyProfiles()
      dialogQueueService.showDialog(
        DialogModel.Custom(
          contentKey = DialogType.AssignMeasurement,
          params =
            buildMap {
              put("babies", babies)
              put("reading", reading)
              put("timestamp", "Just now")
              preSelectedBabyId?.let { put("preSelectedBabyId", it) }
              // "Assign to new baby" row → leave the picker for the Add-a-Baby flow (MOB-598).
              put(
                "onAssignNewBaby",
                { viewModelScope.launch { navigationService.navigateTo(AppRoute.AccountSettings.AddBaby()) } },
              )
            },
          onConfirm = { result ->
            val babyId = result as? String ?: return@Custom
            viewModelScope.launch {
              assignReadingToBaby(reading, entry, babyId, babies, previousEntryIds, sourceSku)
            }
          },
          dismissOnBackPress = true,
          dismissOnClickOutside = true,
        ),
      )
    }

    /**
     * Persists the reading to the selected baby (synced to /v3/entries, category=baby — §2.16) and
     * surfaces the post-assignment card with a Reassign affordance. When reassigning, the entries
     * from the previously chosen baby are deleted first so the reading lands on exactly one baby.
     */
    private suspend fun assignReadingToBaby(
      reading: String,
      entry: List<ScaleEntry>,
      babyId: String,
      babies: List<BabyProfile>,
      previousEntryIds: List<Long>,
      sourceSku: String? = null,
    ) {
      try {
        val accountId = currentAccountId ?: return
        // Persist to the new baby first; addBabyEntry returns -1 on a null account or a swallowed
        // DB-insert exception. Bail before touching the previous baby's entries or claiming success,
        // so a failed write never surfaces as "Reading assigned to X" (and a later Reassign never
        // deletes a bogus -1 id, which would leave a duplicate behind).
        // One batched insert + a SINGLE server sync for all buffered readings (not one full sync per
        // reading) — assigning K readings is one round-trip. (MOB-598 PR #2130)
        val savedIds = entryService.addBabyEntries(entry.map { it.toBabyEntry(babyId, accountId, sourceSku) })
        if (savedIds.size != entry.size || savedIds.any { it <= 0L }) {
          AppLog.e(TAG, "Baby entry save failed for $babyId (savedIds=$savedIds)")
          dialogQueueService.showToast(Toast.Simple(message = ReadingToastStrings.SaveFailed))
          return
        }
        // Save succeeded — now it's safe to remove the entries from the previously chosen baby.
        // deleteBabyEntry syncs the deletion to the server (operationType=delete), so a reassign
        // never leaves the reading on both babies locally or server-side.
        previousEntryIds.forEach { entryService.deleteBabyEntry(it) }
        // Remember the target so a later multi-baby reading can auto-assign here on timeout (MOB-598).
        lastAssignedBabyId = babyId
        checkAccountFlags("entry")
        AppLog.i(TAG, "Baby entry assigned to $babyId")
        babies.firstOrNull { it.id == babyId }?.let { baby ->
          // No other baby in this snapshot → the post-assign card offers "Assign to new baby".
          val noOtherBaby = babies.none { it.id != babyId }
          showBabyAssignedToast(reading, entry, baby, savedIds, sourceSku, noOtherBaby)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error saving baby entry", e)
      }
    }

    /**
     * Post-assignment card ("Reading assigned to X"). Its action re-opens the picker for Reassign,
     * or — when this is the only baby, so there's nothing to reassign to — becomes "Assign to new
     * baby" and routes into the Add-a-Baby flow (MOB-598).
     */
    private fun showBabyAssignedToast(
      reading: String,
      entry: List<ScaleEntry>,
      baby: BabyProfile,
      savedIds: List<Long>,
      sourceSku: String? = null,
      noOtherBaby: Boolean = false,
    ) {
      dialogQueueService.showToast(
        Toast.Custom(
          ReadingToast(
            reading = reading,
            type = ProductType.BABY,
            timestamp = "Just now",
            assignedTo = baby.name,
            assignToNewBaby = noOtherBaby,
            primaryAction = {
              dialogQueueService.dismissToast()
              if (noOtherBaby) {
                viewModelScope.launch { navigationService.navigateTo(AppRoute.AccountSettings.AddBaby()) }
              } else {
                showAssignMeasurementDialog(
                  reading = reading,
                  entry = entry,
                  preSelectedBabyId = baby.id,
                  previousEntryIds = savedIds,
                  sourceSku = sourceSku,
                )
              }
            },
          ),
        ),
      )
    }

    /**
     * Builds a [BabyEntry] from an incoming scale reading. The reading weight is stored
     * in tenths-of-lb; the baby graph reads decigrams, so convert lb → decigrams to keep
     * storage and display consistent. [entryType] is `weight` and [source] is the originating
     * scale SKU (e.g. "0220"), so [BabyEntry.toUnifiedRequest] POSTs it to /v3/entries/ as a
     * baby-weight reading. Marked CREATE/unsynced; [IEntryService.addBabyEntry] persists and
     * syncs it (§2.16).
     */
    private fun ScaleEntry.toBabyEntry(
      babyId: String,
      accountId: String,
      sourceSku: String?,
    ): BabyEntry {
      val lbs = ConversionTools.convertStoredToLbs(scale.scaleEntry.weight)
      return BabyEntry(
        entry =
          entry.copy(
            id = 0L,
            accountId = accountId,
            // The baby scale's RTC is unreliable (reports ~1974/1980), which otherwise plotted the
            // reading decades in the past on the graph. A live weigh happens now, so stamp it with
            // the device time — same approach as the BPM live reading (MOB-598).
            entryTimestamp = DateTimeConverter.timestampToIso(System.currentTimeMillis()),
            operationType = OperationType.CREATE.name,
            isSynced = false,
          ),
        babyEntry =
          BabyEntryEntity(
            id = 0L,
            babyId = babyId,
            babyWeightDecigrams = ConversionTools.convertLbToDecigrams(lbs),
            entryNote = scale.scaleEntry.note,
            entryType = BabyEntryType.WEIGHT.value,
            source = sourceSku,
          ),
      )
    }

    private fun formatReadingForDisplay(
      entry: ScaleEntry,
      type: ProductType,
    ): String {
      val weight = entry.scale.scaleEntry.weight
      val unit = entry.entry.unit
      return when (type) {
        ProductType.BABY -> {
          // Normalise the native deci-pound reading to the canonical decigrams, then let the
          // shared SKU-aware converter do the lb/oz split — no bespoke oz math, no unit.label
          // (which is "lbs"/"lbs & oz" and wrong here). Always "<lb> lb <oz> oz".
          val decigrams = ConversionTools.convertLbToDecigrams(weight / 10.0)
          val (lbs, oz) = ConversionTools.convertBabyWeightToLbOz(decigrams, entry.scale.scaleEntry.source)
          "$lbs lb ${formatWeightValue(oz)} oz"
        }
        ProductType.BLOOD_PRESSURE -> {
          // Weight field stores systolic for BPM protocol entries
          "${formatWeightValue(weight)} ${unit.label}"
        }
        ProductType.MY_WEIGHT -> "${formatWeightValue(weight)} ${unit.label}"
      }
    }

    private fun onDeviceUpdate(
      deviceDetail: GGDeviceDetail,
      connectionStatus: BLEStatus? = null,
    ) {
      viewModelScope.launch {
        deviceService.onDeviceUpdate(
          deviceDetail,
          connectionStatus,
        )
      }
    }

    /**
     * Checks if notification alert has been shown for current account and requests permission if needed.
     * Similar to Angular notification permission logic.
     */
    private fun checkAndRequestNotificationPermission() {
      viewModelScope.launch {
        try {
          val accountId = currentAccountId ?: return@launch
          val notificationAlertShown = accountService.hasShownNotificationAlertForAccount(accountId)
          if (!notificationAlertShown) {
            accountService.setNotificationAlertShownForAccount(accountId, true)
            AppLog.d(TAG, "Stored notification alert setting for account: $accountId")
            requestPermissions(GGPermissionType.NOTIFICATION)
          } else {
            AppLog.d(TAG, "Notification alert already shown for account: $accountId, skipping permission request")
          }
        } catch (e: Exception) {
          AppLog.e(TAG, "Failed to check/request notification permission", e.toString())
        }
      }
    }

    private fun requestPermissions(permissionType: String) {
      viewModelScope.launch {
        isPermissionAlertShown = true
        dialogUtility.permissionAlert(
          permissionType = permissionType,
          onRequest = {
            if (permissionType == GGPermissionType.ALL) {
              navigateToAppPermissions()
              dialogQueueService.dismissCurrent()
            } else {
              ggPermissionService.requestPermission(permissionType)
            }
          },
          onDismiss = {
          },
        )
      }
    }

    private fun startScan() {
      viewModelScope.launch {
        accountService.activeAccountFlow
          .map {
            it?.toGGBTUserProfile()
          }.distinctUntilChanged()
          .collect { ggBTUserProfile ->
            if (ggBTUserProfile != null) {
              // Cold Flow: creates a new Room observer each call. Acceptable here because
              // .first() materialises a single value and disposes immediately; this path
              // only runs when activeAccountFlow emits (account switch / login).
              val currentWeight =
                when (val latestWeightEntry = entryReadService.latestEntry().first()) {
                  is ScaleEntry -> latestWeightEntry.scale.scaleEntry.weight
                  else -> ggBTUserProfile.weight // Fallback to initial weight
                }
              val updatedProfile =
                ggBTUserProfile.copy(
                  weight = currentWeight,
                  goalWeight =
                    ggBTUserProfile.goalWeight?.div(
                      10,
                    ),
                )
              if (!_state.value.hasScanStarted) {
                ggPermissionService.startScan(GGAppType.ME_HEALTH, updatedProfile)
                handleIntent(AppIntent.SetScanStatus(true))
              }
              ggDeviceService.updateProfile(updatedProfile) {}
              AppLog.i(TAG, "Scan started with current weight: $currentWeight")
            }
          }
      }
    }

    private fun stopScan() {
      viewModelScope.launch {
        ggPermissionService.resetCallbacks()
        ggPermissionService.stopScan()
        handleIntent(AppIntent.SetScanStatus(false))
        AppLog.i(TAG, "Scan stopped")
      }
    }

    private fun navigateToAppPermissions() {
      viewModelScope.launch {
        navigationService.navigateTo(AppRoute.AccountSettings.AppPermissions)
      }
    }

    /**
     * Checks if weight-only mode alert should be shown and emits appropriate events.
     * Similar to checkCanShowWeightOnlyModeAlert() in Angular BluetoothService.
     * Uses the latest paired scales collected from the flow.
     */
    private fun checkCanShowWeightOnlyModeAlert() {
      viewModelScope.launch {
        try {
          // Use the latest paired scales collected from the flow
          // This ensures we always have the most up-to-date data
          val pairedScales = latestPairedScales
          pairedScales.forEach { device ->
            AppLog.d(
              TAG,
              "Scale: ${device.device?.deviceName}, MAC: ${device.device?.macAddress}, " +
                "Connected: ${device.connectionStatus == BLEStatus.CONNECTED}, " +
                "WeightOnlyMode: ${device.isWeighOnlyModeEnabledByOthers}",
            )
          }

          val connectedScales = pairedScales.filter { it.connectionStatus == BLEStatus.CONNECTED && it.sku == "0412" }
          val weightOnlyScales = connectedScales.filter { it.isWeighOnlyModeEnabledByOthers }

          val hasWeightOnlyModeScale = weightOnlyScales.isNotEmpty()
          AppLog.d(
            TAG,
            "Connected scales: ${connectedScales.size}, Weight-only scales: ${deviceService.isWeightOnlyModeAlertShown.value}",
          )
          if (hasWeightOnlyModeScale && !deviceService.isWeightOnlyModeAlertShown.value) {
            WeightOnlyModeEventService.emit(WeightOnlyModeEventType.SHOW_ALERT)
            deviceService.updateWeightOnlyModeAlertShown(false)
          } else if (!hasWeightOnlyModeScale) {
            WeightOnlyModeEventService.emit(WeightOnlyModeEventType.HIDE_ALERT)
          }
        } catch (e: Exception) {
          AppLog.e(TAG, "Failed to check weight-only mode alert", e.toString())
        }
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
        handleIntent(AppIntent.SetUnreadFeedCount(count))
        handleIntent(AppIntent.SetShowUnreadFeedIndication(shouldShow))
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
        viewModelScope.launch {
          try {
            ggInAppMessagingService.dialogEvents.collect { event ->
              handleIAMDialogEvent(event)
            }
          } catch (e: Exception) {
            AppLog.e(TAG, "Error in IAM dialog events listener", e.toString())
          }
        }
    }

    // * Checks for account flags and triggers appropriate actions.
    // * @param trigger The trigger type (e.g., "login", "entry")
    // */
    private fun checkAccountFlags(trigger: String) {
      viewModelScope.launch {
        try {
          // First get the account flag
          val accountFlag = accountFlagService.getAccountFlag()
          if (accountFlag != null) {
            AppLog.d(TAG, "Found account flag: ${accountFlag.type} for trigger: $trigger")
            // Check if the flag should be triggered
            accountFlagService.checkAccountFlag(trigger)
          } else {
            AppLog.d(TAG, "No account flags found for trigger: $trigger")
          }
        } catch (e: Exception) {
          AppLog.e(TAG, "Failed to check account flags for trigger: $trigger", e.toString())
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
