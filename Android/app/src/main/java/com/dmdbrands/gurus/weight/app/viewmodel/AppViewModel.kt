package com.dmdbrands.gurus.weight.app.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.network.ITokenManager
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.LogManager
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.repository.IAppRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IDeviceInfoService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
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
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
  private val deviceInfoService: IDeviceInfoService
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
  private var deviceSubscribeJob: Job? = null
  private var initialized = false
  private var isPermissionAlertShown = false

  init {
    viewModelScope.launch {
      try {
        logManager.cleanupOldLogs(5)
        AppLog.i("MainActivity", "Cleaning up old logs")
      } catch (e: Exception) {
        AppLog.e("MainActivity", "Failed to cleanup old logs", e.toString())
      }

      // Load all tokens into TokenManager's in-memory map
      try {
        tokenManager.loadAllTokens()
        AppLog.d(TAG, "Loaded all tokens into TokenManager")
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to load tokens into TokenManager", e.toString())
      }

      val account = accountService.getCurrentAccount()
      initLoadingData(account)
      initEvents()
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
        navigationService.navigateTo(
          AppRoute.ScaleSetup.LcbtScaleSetup(
            sku!!,
            discoveredBroadcastId,
            LcbtScaleSetupStep.CONNECTING_BLUETOOTH,
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
    viewModelScope.launch {
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
            deviceInfoService.updateDeviceInfo()
            initLoadingData(authState.account)
          }

          is AuthState.LoggedOut -> {
            if (authState.isActiveAccount) {
              routeToLandingOrApp()
            }
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
                navigationService.replaceStack(route = AppRoute.Auth.MultiAccountLanding)
                dialogUtility.showAccountLoggedOutAlert(activeAccount.firstName)
              }
            }
          }

          is AuthState.AccountAdded -> {
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
            initLoadingData(authState.account)
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
  }

  /**
   * Checks the login status for all accounts using the split methods.
   * @return true if login status check was successful
   */
  private suspend fun checkLoginStatus(): Boolean =
    try {
      // Check active account first
      val isActiveAccountChecked = accountService.checkLoginStatusForActiveAccount()
      // Then check other logged-in accounts
      val isLoggedInAccountsChecked = accountService.checkLoginStatusForLoggedInAccounts()

      AppLog.d(TAG, "Checked login status for all accounts")
      isActiveAccountChecked && isLoggedInAccountsChecked
    } catch (e: Exception) {
      AppLog.e(TAG, "Error checking login status", e.toString())
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

  private suspend fun initLoadingData(account: Account?) {
    try {
      val isLoginStatusChecked = checkLoginStatus()
      if (account != null && isLoginStatusChecked) {
        permissionSubscribeJob?.cancel()
        deviceSubscribeJob?.cancel()
        entryService.updateAccountId(account.id)
        dashboardService.setAccountId(account.id)
        deviceService.setAccountId(account.id)
        subscribePermissions()
        subscribeDeviceCallback()
        syncScales()
        navigationService.autoLogin()
      } else {
        routeToLandingOrApp()
      }
    } catch (e: Exception) {
      routeToLandingOrApp()
      AppLog.e(TAG, "Load data failed", e.toString())
    }
  }

  private fun subscribePermissions() {
    startScan()
    permissionSubscribeJob = viewModelScope.launch {
      ggPermissionService.permissionCallBackFlow.collect { permissions ->
        if (permissions.isNotEmpty()) {
          if (AppPermissionsHelper.checkScanPermissions(permissions)) {
            startScan()
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
            ggPermissionService.stopScan()
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
              handleIntent(AppIntent.SetScaleDiscovered(true))
              handleIntent(AppIntent.SetSku(data.getSKU()))
              sku = data.getSKU()
              discoveredBroadcastId = data.broadcastId
              val customizedDevice = if (sku == "0412") customizeDevice(data) else Device(
                device = data,
                deviceType = ScaleSetupType.Lcbt.value,
                sku = sku,
              )
              ggDeviceService.addCacheDevice(discoveredBroadcastId, customizedDevice)
              canShowPopUp = false
            }
          }
        }

        GGScanResponseType.DEVICE_CONNECTED -> {
          onDeviceUpdate(
            deviceDetail = data,
            connectionStatus = BLEStatus.CONNECTED,
          )
        }

        GGScanResponseType.DEVICE_DISCONNECTED -> {
          onDeviceUpdate(
            deviceDetail = data,
            connectionStatus = BLEStatus.DISCONNECTED,
          )
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
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during saving entry", e.toString())
      }
    }
  }

  private fun onDeviceUpdate(
    deviceDetail: GGDeviceDetail,
    connectionStatus: BLEStatus? = null,
  ) {
    viewModelScope.launch {
      val device = deviceService.pairedScales.first().find { it.device?.macAddress == deviceDetail.macAddress }
      if (device != null)
        deviceService.onDeviceUpdate(
          macAddress = device.device?.macAddress, connectionStatus = connectionStatus ?: device.connectionStatus,
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
        AppLog.e(TAG, "Failed to check Health Connect permission", e.toString())
      }
    }
  }

  private fun startScan() {
    viewModelScope.launch {
      val account = accountService.getCurrentAccount()
      if (account != null) {
        ggPermissionService.startScan(GGAppType.WEIGHT_GURUS, account.toGGBTUserProfile())
      }
    }
  }

  private fun navigateToAppPermissions() {
    viewModelScope.launch {
      navigationService.navigateTo(AppRoute.AccountSettings.AppPermissions)
    }
  }
}
