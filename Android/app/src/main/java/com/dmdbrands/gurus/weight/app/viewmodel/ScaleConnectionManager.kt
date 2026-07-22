package com.dmdbrands.gurus.weight.app.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.service.WeightOnlyModeEventService
import com.dmdbrands.gurus.weight.core.service.WeightOnlyModeEventType
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.permission.PermissionState
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceDataHelper
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.getSKU
import com.dmdbrands.library.ggbluetooth.enums.GGAppType
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Owns the BLE scan-lifecycle / permission / weight-only-mode slice of [AppViewModel] (MOB-1500).
 * Behaviour-preserving verbatim move of the scan start/stop, permission gating, paired-scale
 * observation and weight-only-mode alert. Device-response routing lives in [DeviceResponseManager]
 * (reached through [onHandleDeviceResponse]); entry routing in [EntrySaveManager] (via
 * [onEntryResponse]); the per-session observer tail in [AuthNotificationManager] (via
 * [onSessionObserversReady]). Base-class lateinit services are reached through lazy provider lambdas
 * so their value is never captured before injection.
 */
class ScaleConnectionManager(
  private val scope: CoroutineScope,
  private val getState: () -> AppState,
  private val onIntent: (AppIntent) -> Unit,
  private val getCurrentAccountId: () -> String?,
  private val provideNavigation: () -> IAppNavigationService,
  private val provideDialogQueue: () -> IDialogQueueService,
  private val ggPermissionService: GGPermissionService,
  private val ggDeviceService: GGDeviceService,
  private val deviceService: IDeviceService,
  private val accountService: IAccountService,
  private val entryReadService: IEntryReadService,
  private val dialogUtility: IDialogUtility,
  private val onHandleDeviceResponse: (GGScanResponse.DeviceDetail) -> Unit,
  private val onEntryResponse: (GGScanResponse.Entry) -> Unit,
  private val onSessionObserversReady: suspend (Account) -> Unit,
) {

  private val TAG = "AppViewModel"

  private val navigationService: IAppNavigationService get() = provideNavigation()
  private val dialogQueueService: IDialogQueueService get() = provideDialogQueue()

  private var permissionSubscribeJob: Job? = null
  private var syncScaleJob: Job? = null
  private var deviceSubscribeJob: Job? = null
  private var pairedScalesSubscribeJob: Job? = null
  private var initialized = false
  private var isPermissionAlertShown = false
  private var latestPairedScales: List<Device> = emptyList()

  /** Latest paired scales collected from the flow; read by [DeviceResponseManager] for isKnown checks. */
  fun latestPairedScales(): List<Device> = latestPairedScales

  /**
   * Starts long-lived observers only (no account setup or navigation).
   * Called when [AuthState.LoggedInFromLoading] is received; LoadingScreenViewModel already did loadData + autoLogin.
   */
  fun startObserversOnly(
    account: Account,
    fromLoadingScreen: Boolean = false,
  ) {
    scope.launch {
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
        onSessionObserversReady(account)
      } catch (e: Exception) {
        AppLog.e(TAG, "startObserversOnly failed", e)
      }
    }
  }

  private fun subscribePermissions() {
    startScan()
    permissionSubscribeJob =
      scope.launch {
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
      scope.launch {
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
              onHandleDeviceResponse(response)
            }

            is GGScanResponse.Entry -> {
              AppLog.i(TAG, "Scan Response Entry: $response")
              onEntryResponse(response)
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
      scope.launch {
        deviceService.pairedScales.collect { pairedScales ->
          latestPairedScales = pairedScales
          AppLog.d(TAG, "Updated latest paired scales: ${pairedScales.size} devices")
          // Check if weight-only mode alert should be shown when paired scales are updated
          checkCanShowWeightOnlyModeAlert()
        }
      }
  }

  fun syncScales() {
    syncScaleJob?.cancel()
    syncScaleJob =
      scope.launch {
        deviceService.getGGBTDevices().collect { devices ->
          AppLog.d(TAG, "syncScales called")
          ggDeviceService.syncDevices(devices)
        }
      }
  }

  /**
   * Checks if notification alert has been shown for current account and requests permission if needed.
   * Similar to Angular notification permission logic.
   */
  fun checkAndRequestNotificationPermission() {
    scope.launch {
      try {
        val accountId = getCurrentAccountId() ?: return@launch
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

  fun requestPermissions(permissionType: String) {
    scope.launch {
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

  fun startScan() {
    scope.launch {
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
            if (!getState().hasScanStarted) {
              ggPermissionService.startScan(GGAppType.ME_HEALTH, updatedProfile)
              onIntent(AppIntent.SetScanStatus(true))
            }
            ggDeviceService.updateProfile(updatedProfile) {}
            AppLog.i(TAG, "Scan started with current weight: $currentWeight")
          }
        }
    }
  }

  fun stopScan() {
    scope.launch {
      ggPermissionService.resetCallbacks()
      ggPermissionService.stopScan()
      onIntent(AppIntent.SetScanStatus(false))
      AppLog.i(TAG, "Scan stopped")
    }
  }

  fun navigateToAppPermissions() {
    scope.launch {
      navigationService.navigateTo(AppRoute.AccountSettings.AppPermissions)
    }
  }

  /**
   * Checks if weight-only mode alert should be shown and emits appropriate events.
   * Similar to checkCanShowWeightOnlyModeAlert() in Angular BluetoothService.
   * Uses the latest paired scales collected from the flow.
   */
  fun checkCanShowWeightOnlyModeAlert() {
    scope.launch {
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
}
