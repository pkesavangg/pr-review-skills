package com.dmdbrands.gurus.weight.app.viewmodel

import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.dmdbrands.gurus.weight.app.components.ReconnectScale
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.network.ITokenManager
import com.dmdbrands.gurus.weight.core.service.AppNotificationEventService
import com.dmdbrands.gurus.weight.core.service.BluetoothPreferencesService
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.service.NotificationEventType
import com.dmdbrands.gurus.weight.core.service.WeightOnlyModeEventService
import com.dmdbrands.gurus.weight.core.service.WeightOnlyModeEventType
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.LogManager
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
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
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.Helper.ScaleMetricsHelper
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.LcbtScaleSetupStep
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.getSKU
import com.dmdbrands.gurus.weight.features.common.model.SCALES
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toScaleEntry
import com.dmdbrands.library.ggbluetooth.enums.GGAppType
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.enums.GGScanResponseType
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
  private val healthConnectService: IHealthConnectService,
  private val deviceInfoService: IDeviceInfoService,
  private val workManager: WorkManager,
  private val bluetoothPreferencesService: BluetoothPreferencesService,
  private val feedService: IFeedService,
  private val ggInAppMessagingService: GGInAppMessagingService,
  private val accountFlagService: IAccountFlagService
) : BaseIntentViewModel<AppState, AppIntent>(
  reducer = AppReducer(),
) {
  companion object {
    private const val TAG = "AppViewModel"
    private var currentAccountId: String? = null

    // Delay constants for Health Connect permission check
    private const val INITIAL_DELAY = 1L // 1 second
    private const val DELAYED_ALERT = 3000L // 3 seconds
  }

  override fun provideInitialState(): AppState = AppState()
  private var canShowPopUp = true
  private var sku: String? = null
  private var discoveredBroadcastId: String? = null
  private var permissionSubscribeJob: Job? = null
  private var syncScaleJob: Job? = null
  private var deviceSubscribeJob: Job? = null
  private var initialized = false
  private var isPermissionAlertShown = false
  private var unreadFeedCount = 0
  private var showUnreadFeedIndication = false

  init {
    viewModelScope.launch {
      try {
        logManager.cleanupOldLogs(5)
        AppLog.i("MainActivity", "Cleaning up old logs")
      } catch (e: Exception) {
        AppLog.e("MainActivity", "Failed to cleanup old logs", e)
      }

      // Load all tokens into TokenManager's in-memory map
      try {
        tokenManager.loadAllTokens()
        tokenManager.getCurrentAccountID()
        AppLog.v(TAG, "Loaded all tokens into TokenManager")
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to load tokens into TokenManager", e)
      }
      // Initialize IAM dialog events listener
      try {
        initIAMDialogListener()
        AppLog.d(TAG, "IAM dialog events listener initialized")
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to initialize IAM dialog events listener", e.toString())
      }

      // Initialize feed notification listener
      try {
        updateUnreadFeedCount()
        AppLog.d(TAG, "Feed notification listener initialized")
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to initialize feed notification listener", e.toString())
      }
      initialize()
    }
  }

  private fun initialize() {
    viewModelScope.launch {
      workManager.getWorkInfosByTagLiveData("ionic_migration").asFlow().collect { workInfos ->
        if (workInfos.isEmpty()) {
          val account = accountService.getCurrentAccount()
          initLoadingData(account)
          initEvents()
        } else {
          if (workInfos.all { it.state.isFinished }) {
            val account = accountService.getCurrentAccount()
            initLoadingData(account)
            initEvents()
          }
        }
      }
    }
  }

  override fun handleIntent(intent: AppIntent) {
    when (intent) {
      is AppIntent.OnPopUpConnect -> onPopUpConnect()

      is AppIntent.OnPopUpDismiss -> onPopUpDismiss()


      else -> {}
    }
    super.handleIntent(intent)
  }

  private fun onPopUpConnect() {
    viewModelScope.launch {
      if (sku == "0412") {
        navigationService.navigateTo(
          AppRoute.ScaleSetup.BtWifiScaleSetup(
            "0412",
            BtWifiSetupStep.CONNECTING_BLUETOOTH,
            discoveredBroadcastId,
          ),
        )
      } else if (sku != null) {
        val scaleInfo = SCALES.find { it.sku == sku }
        navigationService.navigateTo(
          AppRoute.ScaleSetup.LcbtScaleSetup(
            sku!!,
            discoveredBroadcastId,
            LcbtScaleSetupStep.CONNECTING_BLUETOOTH,
            scaleInfo = scaleInfo,
          ),
        )
      }
      onPopUpDismiss()
    }
  }

  private fun onPopUpDismiss() {
    viewModelScope.launch {
      handleIntent(AppIntent.SetScaleDiscovered(false))
      if (discoveredBroadcastId != null)
        ggDeviceService.skipDevice(discoveredBroadcastId!!)
      delay(30 * 1000)
      canShowPopUp = true
    }
  }

  private fun syncScales() {
    syncScaleJob = viewModelScope.launch {
      deviceService.getGGBTDevices().collect {
        ggDeviceService.syncDevices(it)
      }
    }
  }

  private fun initEvents() {
    viewModelScope.launch {
      appNavigationService.authEvent.collect { authState ->
        when (authState) {
          is AuthState.LoggedIn -> {
            // handle login event
            stopScan()
            initLoadingData(authState.account, true)
          }

          is AuthState.LoggedOut -> {
            if (authState.isActiveAccount) {
              routeToLandingOrApp()
            }
            stopScan()
          }

          is AuthState.AccountDeleted -> {
            if (authState.isActiveAccount) {
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
            stopScan()
            initLoadingData(authState.account)
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
            stopScan()
            initLoadingData(authState.account, true)
          }

          is AuthState.ProfileUpdated -> {
            // Profile updated - no navigation needed, just log
            AppLog.d(TAG, "Profile updated for account: ${authState.account.id}")
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
   * Checks the login status for all accounts using the split methods.
   * @return true if login status check was successful
   */
  private suspend fun checkLoginStatus(): Boolean =
    try {
      val isActiveAccountChecked = accountService.checkLoginStatusForActiveAccount()
      // Then check other logged-in accounts
      val isLoggedInAccountsChecked = accountService.checkLoginStatusForLoggedInAccounts()

      AppLog.d(TAG, "Checked login status for all accounts")
      isActiveAccountChecked && isLoggedInAccountsChecked
    } catch (e: Exception) {
      AppLog.e(TAG, "Error checking login status", e)
      false
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

  private suspend fun initLoadingData(account: Account?, isLoggedIn: Boolean = false) {
    try {
      initialized = false
      val isLoginStatusChecked = checkLoginStatus()
      if (account != null && isLoginStatusChecked) {
        permissionSubscribeJob?.cancel()
        deviceSubscribeJob?.cancel()
        syncScaleJob?.cancel()
        entryService.updateAccountId(account.id)
        dashboardService.setAccountId(account.id)
        deviceService.setAccountId(account.id)
        feedService.fetchFeedItems()
        if (isLoggedIn) {
          deviceInfoService.updateDeviceInfo()
        }
        navigationService.autoLogin()
        // Check for IAM feed modal trigger after fetching feed items
        entryService.initializeGoalCardMonitoring()
        subscribePermissions()
        subscribeDeviceCallback()
        syncScales()
      } else {
        routeToLandingOrApp()
      }
    } catch (e: Exception) {
      routeToLandingOrApp()
      AppLog.e(TAG, "Load data failed", e)
    }
  }

  private fun subscribePermissions() {
    startScan()
    permissionSubscribeJob = viewModelScope.launch {
      ggPermissionService.permissionCallBackFlow.collect { permissions ->
        if (permissions.isNotEmpty()) {
          if (AppPermissionsHelper.checkScanPermissions(permissions)) {
            initialized = true
          } else {
            if (!initialized) {
              val pairedScales = deviceService.pairedScales.first()
              val hasBtWifiScales = pairedScales.any { savedScale ->
                val scaleInfo = SCALES.find { it.sku == savedScale.sku }
                scaleInfo?.setupType == ScaleSetupType.BtWifiR4
              } && pairedScales.isNotEmpty()
              val canRequestNotifPermission = AppPermissionsHelper
                .canRequestNotificationPermission(ggPermissionService.permissionCallBackFlow.value)
              if (canRequestNotifPermission && hasBtWifiScales) {
                requestPermissions(GGPermissionType.NOTIFICATION)
              }
              if (hasBtWifiScales) {
                requestPermissions(GGPermissionType.ALL)
              }
              initialized = true
            }
          }
        }
      }
    }
    checkHealthConnectPermissionWithDelay()
  }

  private fun subscribeDeviceCallback() {
    deviceSubscribeJob = viewModelScope.launch {
      ggDeviceService.deviceCallbackFlow.collect { response ->
        when (response) {
          is GGScanResponse.DeviceDetail -> {
            handleDeviceResponse(response)
          }

          is GGScanResponse.Entry -> {
            handleEntryResponse(response)
          }

          else -> null
        }
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

      when (deviceResponse.type) {
        GGScanResponseType.NEW_DEVICE -> {
          if (canShowPopUp && (data.protocolType == GGDeviceProtocolType.GG_DEVICE_PROTOCOL_R4.value || data.protocolType == GGDeviceProtocolType.GG_DEVICE_PROTOCOL_A6.value)) {
            val currentRoute = navigationService.getCurrentRoute()
            if (currentRoute !is AppRoute.ScaleSetup) {
              // Apply MAC address filtering for 0412 scales (similar to Angular's onfoundnewsmartwifiscale)
              val deviceSku = data.getSKU()
              val shouldShow = if (deviceSku == "0412") {
                val isAllow = bluetoothPreferencesService.shouldShowDevice(data.macAddress)
                isAllow
              } else {
                true // Don't filter non-0412 scales
              }

              if (shouldShow) {
                handleIntent(AppIntent.SetScaleDiscovered(true))
                handleIntent(AppIntent.SetSku(deviceSku))
                sku = deviceSku
                discoveredBroadcastId = data.broadcastId
                val customizedDevice = if (sku == "0412") customizeDevice(data) else Device(
                  device = data,
                  deviceType = ScaleSetupType.Lcbt.value,
                  sku = sku,
                )
                ggDeviceService.addCacheDevice(discoveredBroadcastId, customizedDevice)
                canShowPopUp = false
              } else {
                AppLog.d(TAG, "Filtered out 0412 scale with MAC: ${data.macAddress}")
              }
            }
          }
        }

        GGScanResponseType.DEVICE_CONNECTED -> {
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
          dialogQueueService.showDialog(
            ReconnectScale.getMaxUserAlert(
              onConfirm = {
                viewModelScope.launch {
                  dialogQueueService.showLoader("Loading...")
                  val device = deviceService.getScaleByBroadcastId(data.broadcastId!!)
                  if (device == null) {
                    return@launch
                  }
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
                if (data.broadcastId != null) {
                  ggDeviceService.skipDevice(data.broadcastId!!)
                }
              },
            ),
          )
        }

        GGScanResponseType.DEVICE_DUPLICATE_USER -> {
          try {
            dialogQueueService.showDialog(
              ReconnectScale.getDuplicateUserAlert(
                onConfirm = {
                  viewModelScope.launch {
                    val device = deviceService.getScaleByBroadcastId(data.broadcastId!!)
                    if (device == null) {
                      return@launch
                    }
                    ggDeviceService.deleteAccount(device.toGGBTDevice()) {}
                    ggDeviceService.addCacheDevice(data.broadcastId, device)
                    navigationService.navigateTo(
                      AppRoute.ScaleSetup.BtWifiScaleSetup(
                        data.getSKU(),
                        BtWifiSetupStep.CONNECTING_BLUETOOTH,
                        data.broadcastId,
                      ),
                    )
                  }
                },
                onCancel = {
                  if (data.broadcastId != null) {
                    ggDeviceService.skipDevice(data.broadcastId!!)
                  }
                },
              ),
            )
          }
          catch (e: Exception) {
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
      val accountId = accountService.activeAccountFlow.first()?.id
      val device = deviceService.getScaleByBroadcastId(ggEntry.first().broadcastId)
      if (device == null) {
        return@launch
      }
      val entry = ggEntry.map { it.toScaleEntry(accountId ?: "", device.id) }
      try {
        entryService.addEntry(entry)
        dialogQueueService.showToast(
          Toast(
            message = "entry saved successfully",
          ),
        )
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

  /**
   * Checks Health Connect permission with appropriate delay based on whether permission alert was shown.
   */
  private fun checkHealthConnectPermissionWithDelay() {
    viewModelScope.launch {
      try {
        val delayTime = if (isPermissionAlertShown) DELAYED_ALERT else INITIAL_DELAY
        delay(delayTime)
        healthConnectService.checkHealthConnectPermissionDisabled()
        AppLog.d(TAG, "Health Connect permission check completed after ${delayTime}ms delay")
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to check Health Connect permission", e)
      }
    }
  }

  private fun startScan() {
    viewModelScope.launch {
       accountService.activeAccountFlow.map {
         it?.toGGBTUserProfile()
       }.distinctUntilChanged().collect { ggBTUserProfile ->
        if (ggBTUserProfile != null) {
          val latestWeightEntry = entryService.latestEntry.value
          val currentWeight = when (latestWeightEntry) {
            is ScaleEntry -> latestWeightEntry.scale.scaleEntry.weight
            else -> ggBTUserProfile.weight // Fallback to initial weight
          }
          val updatedProfile = ggBTUserProfile.copy(weight = currentWeight, goalWeight = ggBTUserProfile.goalWeight?.div(
            10
          ))
          ggPermissionService.startScan(GGAppType.WEIGHT_GURUS, updatedProfile)
          handleIntent(AppIntent.SetScanStatus(true))
          AppLog.i(TAG, "Scan started with current weight: $currentWeight")
        }
      }
    }
  }

  private fun stopScan() {
    viewModelScope.launch {
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
   */
  private fun checkCanShowWeightOnlyModeAlert() {
    viewModelScope.launch {
      try {
        val pairedScales = deviceService.pairedScales.first()
        pairedScales.forEach { device ->
          AppLog.d(
            TAG,
            "Scale: ${device.device?.deviceName}, MAC: ${device.device?.macAddress}, " +
              "Connected: ${device.connectionStatus == BLEStatus.CONNECTED}, " +
              "WeightOnlyMode: ${device.isWeighOnlyModeEnabledByOthers}",
          )
        }

        val connectedScales = pairedScales.filter { it.connectionStatus == BLEStatus.CONNECTED }
        val weightOnlyScales = connectedScales.filter { it.isWeighOnlyModeEnabledByOthers }

        val hasWeightOnlyModeScale = weightOnlyScales.isNotEmpty()
        AppLog.d(TAG, "Connected scales: ${connectedScales.size}, Weight-only scales: ${deviceService.isWeightOnlyModeAlertShown.value}")
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
   */
  private fun initIAMDialogListener() {
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

      else -> {}
    }
  }
}
