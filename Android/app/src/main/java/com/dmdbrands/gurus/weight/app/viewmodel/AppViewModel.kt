package com.dmdbrands.gurus.weight.app.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.app.components.ReconnectScale
import com.dmdbrands.gurus.weight.app.string.AppString.SCALEDISCOVEREDTIMEOUT
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.network.ITokenManager
import com.dmdbrands.gurus.weight.core.network.TokenMigrationHelper
import com.dmdbrands.gurus.weight.core.service.AppNotificationEventService
import com.dmdbrands.gurus.weight.core.service.BluetoothPreferencesService
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.service.NotificationEventType
import com.dmdbrands.gurus.weight.core.service.WeightOnlyModeEventService
import com.dmdbrands.gurus.weight.core.service.WeightOnlyModeEventType
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.LogManager
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.permission.PermissionState
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.repository.IAppRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.IAccountFlagService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IDeviceInfoService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.Helper.ScaleMetricsHelper
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.LcbtScaleSetupStep
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0412
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.getSKU
import com.dmdbrands.gurus.weight.features.common.helper.ScaleDataHelper
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toScaleEntry
import com.dmdbrands.library.ggbluetooth.enums.GGAppType
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.enums.GGScanResponseType
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.dmdbrands.library.ggbluetooth.model.GGScaleEntry
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGCacheDevice
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.ggInAppMessaging.core.service.GGInAppMessagingService
import com.greatergoods.ggInAppMessaging.core.service.IAMDialogEvent
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
) : BaseIntentViewModel<AppState, AppIntent>(
  reducer = AppReducer(),
) {
  companion object {
    private const val TAG = "AppViewModel"
  }

  override fun provideInitialState(): AppState = AppState()
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

    // Initialize and maintain currentAccountId globally
    viewModelScope.launch {
      accountService.activeAccountFlow.collect {
        currentAccountId = it?.id
      }
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
      if (sku == SKU_0412) {
        navigationService.navigateTo(
          AppRoute.ScaleSetup.BtWifiScaleSetup(
            SKU_0412,
            BtWifiSetupStep.CONNECTING_BLUETOOTH,
            discoveredBroadcastId,
          ),
        )
      } else if (sku != null) {
        val localSku = sku ?: return@launch
        val scaleInfo = ScaleDataHelper.findScaleInfoBySku(localSku)
        // Pass original SKU to routes (not mapped), setup will save original SKU
        navigationService.navigateTo(
          AppRoute.ScaleSetup.LcbtScaleSetup(
            localSku,
            discoveredBroadcastId,
            LcbtScaleSetupStep.CONNECTING_BLUETOOTH,
            scaleInfo = scaleInfo,
          ),
        )
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

          is AuthState.AccountAdded -> {
          }

          is AuthState.AccountSwitched -> {
            if (authState.showToast) {
              val accountName = authState.account.firstName
              dialogQueueService.showToast(
                Toast(
                  title = null,
                  message = ToastStrings.Success.AccountSwitchSuccess.Message(
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
            dialogQueueService.showToast(Toast(message = "Success! Entry added"))
          }

          else -> {}
        }
      }
    }
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
  private fun startObserversOnly(account: Account, fromLoadingScreen: Boolean = false) {
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
    permissionSubscribeJob = viewModelScope.launch {
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
              val hasBtWifiScales = pairedScales.isNotEmpty() && pairedScales.any { savedScale ->
                val scaleInfo = ScaleDataHelper.findScaleInfoBySku(savedScale.getSKU())
                scaleInfo?.setupType in listOf(
                  ScaleSetupType.BtWifiR4,
                  ScaleSetupType.Lcbt,
                  ScaleSetupType.EspTouchWifi,
                  ScaleSetupType.Wifi,
                )
              }
              val canRequestNotifPermission = AppPermissionsHelper
                .canRequestNotificationPermission(ggPermissionService.permissionCallBackFlow.value)
              if (canRequestNotifPermission && hasBtWifiScales) {
                checkAndRequestNotificationPermission()
              }
              // Get only the required permissions for the paired scales
              val requiredPermissionSets = AppPermissionsHelper.getRequiredPermissionSets(pairedScales)
              if (requiredPermissionSets.isNotEmpty()) {
                // Check if all required permissions are enabled
                val areAllRequiredPermissionsEnabled = requiredPermissionSets.all { permissionType ->
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
    deviceSubscribeJob = viewModelScope.launch {
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
    pairedScalesSubscribeJob = viewModelScope.launch {
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
    syncScaleJob = viewModelScope.launch {
      deviceService.getGGBTDevices().collect { devices ->
        AppLog.d(TAG, "syncScales called")
        ggDeviceService.syncDevices(devices)
      }
    }
  }


  private fun handleEntryResponse(entryResponse: GGScanResponse.Entry) {
    when (entryResponse.type) {
      GGScanResponseType.SINGLE_ENTRY, GGScanResponseType.MULTI_ENTRIES -> {
        saveEntry(entryResponse.data.map { it as GGScaleEntry })
      }

      else -> null
    }
  }

  private fun handleDeviceResponse(deviceResponse: GGScanResponse.DeviceDetail) {
    val data = deviceResponse.data
    viewModelScope.launch {
      // Check if scale is already known (paired) - similar to Angular's isKnown logic
      val accountId = currentAccountId ?: return@launch
      val isKnownScale = data.broadcastId?.let { broadcastId ->
        // Check against latestPairedScales list first (similar to Angular's this.scales.find)
        latestPairedScales.any { scale ->
          scale.device?.broadcastId == broadcastId
        } || deviceService.getScaleByBroadcastId(broadcastId, accountId) != null
      } == true
      AppLog.d(TAG, "device response ${deviceResponse.type}")

      when (deviceResponse.type) {
        GGScanResponseType.NEW_DEVICE -> {
          AppLog.d(TAG, "new device discovered ${data.macAddress} $canShowScaleDiscoveredModal")
          if (canShowScaleDiscoveredModal && (data.protocolType == GGDeviceProtocolType.GG_DEVICE_PROTOCOL_R4.value || data.protocolType == GGDeviceProtocolType.GG_DEVICE_PROTOCOL_A6.value)) {
            val currentRoute = navigationService.getCurrentRoute()
            val isSetupInProgress = deviceService.isSetupInProgress()
            val isOnMainScreen = currentRoute is AppRoute.Home || currentRoute is AppRoute.Main.Dashboard

            if (isOnMainScreen && currentRoute !is AppRoute.ScaleSetup && !isSetupInProgress) {
              // Check if device is in skipDevices list
              val isSkipped =
                data.broadcastId?.let { bluetoothPreferencesService.containsSkipDevice(it) } == true ||
                  data.macAddress.let { bluetoothPreferencesService.containsSkipDevice(it) }

              // Check if same scale was shown recently (15 seconds)
              val isIgnored = data.macAddress == scaleToIgnore
              AppLog.d(TAG, "isSkipped: $isSkipped, isIgnored: $isIgnored")

              // Apply MAC address filtering for 0412 scales (similar to Angular's onfoundnewsmartwifiscale)
              val deviceSku = data.getSKU()
              val shouldShow = if (deviceSku == "0412") {
                val isAllow = bluetoothPreferencesService.shouldShowDevice(data.macAddress)
                isAllow
              } else {
                true // Don't filter non-0412 scales
              }

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

                val customizedDevice = if (sku == "0412") customizeDevice(data) else Device(
                  device = data,
                  deviceType = ScaleSetupType.Lcbt.value,
                  sku = sku,
                )
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
          if (currentRoute !is AppRoute.ScaleSetup && !isKnownScale && !isOnAuthScreen) {
            dialogQueueService.showDialog(
              ReconnectScale.getMaxUserAlert(
                onConfirm = {
                  viewModelScope.launch {
                    val accountId = currentAccountId ?: return@launch
                    val broadcastId = data.broadcastId ?: return@launch
                    dialogQueueService.showLoader("Loading...")
                    val device = deviceService.getScaleByBroadcastId(broadcastId, accountId) ?: return@launch
                    ggDeviceService.addCacheDevice(data.broadcastId, device)
                    ggDeviceService.getUsers(device.toGGBTDevice()) { response ->
                      viewModelScope.launch {
                        dialogQueueService.dismissLoader()
                        navigationService.navigateTo(
                          AppRoute.ScaleSetup.BtWifiScaleSetup(
                            sku = data.getSKU(),
                            initialStep = BtWifiSetupStep.USER_LIMIT_REACHED,
                            broadcastId = data.broadcastId,
                            userList = response.user,
                          ),
                        )
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
        }

        GGScanResponseType.DEVICE_DUPLICATE_USER -> {
          try {
            val currentRoute = navigationService.getCurrentRoute()
            val isOnAuthScreen = currentRoute is AppRoute.Auth
            if (currentRoute !is AppRoute.ScaleSetup && !isOnAuthScreen) {
              dialogQueueService.showDialog(
                ReconnectScale.getDuplicateUserAlert(
                  onConfirm = {
                    viewModelScope.launch {
                      val accountId = currentAccountId ?: return@launch
                      val broadcastId = data.broadcastId ?: return@launch
                      val device = deviceService.getScaleByBroadcastId(broadcastId, accountId) ?: return@launch
                      val userList = suspendCoroutine { continuation ->
                        ggDeviceService.getUsers(device.toGGBTDevice()) { response ->
                          continuation.resume(response.user)
                        }
                      }
                      val scaleToken = userList.find { user -> user.name == device.preferences?.displayName }?.token
                      ggDeviceService.deleteAccount(device.toGGBTDevice().copy(token = scaleToken)) {
                        if (it.name == GGUserActionResponseType.DELETE_COMPLETED.name) {
                          viewModelScope.launch {
                            ggDeviceService.addCacheDevice(data.broadcastId, device)
                            navigationService.navigateTo(
                              AppRoute.ScaleSetup.BtWifiScaleSetup(
                                data.getSKU(),
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
    val device = Device(
      device = ggDeviceDetail,
      token = token,
    )
    return device.copy(
      deviceType = ScaleSetupType.BtWifiR4.value,
      sku = "0412",
      preferences = ScaleMetricsHelper.getDefaultPreference(username, device.id),
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
      val device = deviceService.getScaleByBroadcastId(ggEntry.first().broadcastId, accountId)

      if (device == null && !isSetupInProgress) return@launch

      // Get user height for BMI calculation
      val activeAccount = accountService.activeAccountFlow.first()
      val userHeight = activeAccount?.height

      val entry = ggEntry.map { ggScaleEntry ->
        val scaleEntry = ggScaleEntry.toScaleEntry(accountId, device?.id ?: "")

        // Check if BMI is 0.0 or null and calculate it if user height is available
        if ((scaleEntry.scale.scaleEntry.bmi == null || scaleEntry.scale.scaleEntry.bmi == 0.0) && userHeight != null) {
          val calculatedBmi = EntryHelper.getCalculatedBMI(
            weight = scaleEntry.scale.scaleEntry.weight.toFloat(),
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

      try {
        entryService.addEntry(entry)
        if (!isSetupInProgress) {
          dialogQueueService.showToast(
            Toast(
              message = "entry saved successfully",
            ),
          )
        }
        // Check for account flags after entry is saved
        checkAccountFlags("entry")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during saving entry", e)
      }
    }
  }

  private fun onDeviceUpdate(
    deviceDetail: GGDeviceDetail,
    connectionStatus: BLEStatus? = null,
  ) {
    viewModelScope.launch {
      deviceService.onDeviceUpdate(
        deviceDetail, connectionStatus,
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
      accountService.activeAccountFlow.map {
        it?.toGGBTUserProfile()
      }.distinctUntilChanged().collect { ggBTUserProfile ->
        if (ggBTUserProfile != null) {
          val currentWeight = when (val latestWeightEntry = entryService.latestEntry.value) {
            is ScaleEntry -> latestWeightEntry.scale.scaleEntry.weight
            else -> ggBTUserProfile.weight // Fallback to initial weight
          }
          val updatedProfile = ggBTUserProfile.copy(
            weight = currentWeight,
            goalWeight = ggBTUserProfile.goalWeight?.div(
              10,
            ),
          )
          if (!_state.value.hasScanStarted) {
            ggPermissionService.startScan(GGAppType.WEIGHT_GURUS, updatedProfile)
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
    iamDialogListenerJob = viewModelScope.launch {
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
          Toast(
            message = ToastStrings.Success.PromoCodeCopied.Message,
          ),
        )
      }

      else -> {}
    }
  }
}
