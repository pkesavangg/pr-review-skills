package com.dmdbrands.gurus.weight.app.viewmodel

import com.dmdbrands.gurus.weight.app.components.ReconnectScale
import com.dmdbrands.gurus.weight.app.string.AppString.SCALEDISCOVEREDTIMEOUT
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.service.BluetoothPreferencesService
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.Helper.DeviceMetricsHelper
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BabyScaleSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.LcbtScaleSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.MonitorSetupStepHelper
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceDataHelper
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0412
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.getSKU
import com.dmdbrands.library.ggbluetooth.enums.GGScanResponseType
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGCacheDevice
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Owns the device-response / discovery-popup / reconnect-alert slice of [AppViewModel] (MOB-1500).
 * Behaviour-preserving verbatim move of the device-callback routing, the freshly-discovered scale
 * popup (and its connect/dismiss), and the memory-full / duplicate-user reconnect alerts. Holds the
 * discovery state (discoveredBroadcastId / canShowScaleDiscoveredModal / scaleToIgnore); the
 * reflected `sku` field stays on [AppViewModel] and is reached via [getSku]/[setSku]. Paired-scale
 * state and the weight-only-mode check live in [ScaleConnectionManager] (reached via
 * [getLatestPairedScales] / [onCheckWeightOnlyAlert]). Base-class lateinit services are reached
 * through lazy provider lambdas so their value is never captured before injection.
 */
class DeviceResponseManager(
  private val scope: CoroutineScope,
  private val getState: () -> AppState,
  private val onIntent: (AppIntent) -> Unit,
  private val getCurrentAccountId: () -> String?,
  private val getSku: () -> String?,
  private val setSku: (String?) -> Unit,
  private val provideNavigation: () -> IAppNavigationService,
  private val provideDialogQueue: () -> IDialogQueueService,
  private val ggDeviceService: GGDeviceService,
  private val deviceService: IDeviceService,
  private val bluetoothPreferencesService: BluetoothPreferencesService,
  private val accountService: IAccountService,
  private val analyticsService: IAnalyticsService,
  private val getLatestPairedScales: () -> List<Device>,
  private val onCheckWeightOnlyAlert: () -> Unit,
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

  private val navigationService: IAppNavigationService get() = provideNavigation()
  private val dialogQueueService: IDialogQueueService get() = provideDialogQueue()

  private var canShowScaleDiscoveredModal = true
  private var scaleToIgnore: String? = null
  private var discoveredBroadcastId: String? = null

  /**
   * Resets scale discovered related properties when switching accounts or on logout.
   * This ensures that the new account can see discovered scales without
   * being affected by the previous account's skip/ignore state, and prevents
   * showing a stale scale-discovered popup when deviceCallbackFlow does not run
   * (e.g. after logout with Bluetooth off and then switching account).
   */
  fun resetScaleDiscoveredState() {
    bluetoothPreferencesService.clearSkipDevices()
    scaleToIgnore = null
    canShowScaleDiscoveredModal = true
    discoveredBroadcastId = null
    setSku(null)
    onIntent(AppIntent.SetScaleDiscovered(false))
    AppLog.d(TAG, "Reset scale discovered state for account switch")
  }

  fun onPopUpConnect() {
    scope.launch {
      onIntent(AppIntent.SetScaleDiscovered(false))
      // Clear all dialogs including IAM modal to ensure it's dismissed when connecting to scale
      dialogQueueService.clear()
      val localSku = getSku() ?: return@launch
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

  fun onPopUpDismiss(shouldSkipDevice: Boolean = false) {
    scope.launch {
      onIntent(AppIntent.SetScaleDiscovered(false))
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
    AppLog.d(TAG, "Closed scale discovered popup ${getState().isScaleDiscovered}")
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

  fun handleDeviceResponse(deviceResponse: GGScanResponse.DeviceDetail) {
    val data = deviceResponse.data
    scope.launch {
      // Check if scale is already known (paired) - similar to Angular's isKnown logic
      val accountId = getCurrentAccountId() ?: return@launch
      val isKnownScale =
        data.broadcastId?.let { broadcastId ->
          // Check against latestPairedScales list first (similar to Angular's this.scales.find)
          getLatestPairedScales().any { scale ->
            scale.device?.broadcastId == broadcastId
          } ||
            deviceService.getScaleByBroadcastId(broadcastId, accountId) != null
        } == true
      AppLog.d(TAG, "device response ${deviceResponse.type}")

      when (deviceResponse.type) {
        GGScanResponseType.NEW_DEVICE -> handleNewDeviceDiscovered(data, isKnownScale)

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
          onCheckWeightOnlyAlert()
        }

        GGScanResponseType.DEVICE_DISCONNECTED -> {
          onDeviceUpdate(
            deviceDetail = data,
            connectionStatus = BLEStatus.DISCONNECTED,
          )
          onIntent(AppIntent.SetScaleDiscovered(false))
          onCheckWeightOnlyAlert()
        }

        GGScanResponseType.DEVICE_INFO_UPDATE -> {
          onDeviceUpdate(
            deviceDetail = data,
            connectionStatus = BLEStatus.CONNECTED,
          )
          onCheckWeightOnlyAlert()
        }

        GGScanResponseType.WIFI_STATUS_UPDATE -> {
          onDeviceUpdate(
            deviceDetail = data,
            connectionStatus = BLEStatus.CONNECTED,
          )
        }

        GGScanResponseType.DEVICE_MEMORY_FULL -> handleDeviceMemoryFull(data, isKnownScale)

        GGScanResponseType.DEVICE_DUPLICATE_USER -> handleDeviceDuplicateUser(data)

        else -> null
      }
    }
  }

  /** Handles a freshly-discovered device: filters, caches it, and shows the discovered modal. */
  private suspend fun handleNewDeviceDiscovered(data: GGDeviceDetail, isKnownScale: Boolean) {
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
          showDiscoveredScale(data, deviceSku)
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

  /** Marks the scale discovered, sets the 15s ignore window, caches it, and gates the modal. */
  private suspend fun showDiscoveredScale(data: GGDeviceDetail, deviceSku: String) {
    onIntent(AppIntent.SetScaleDiscovered(true))
    onIntent(AppIntent.SetSku(deviceSku))
    setSku(deviceSku)
    discoveredBroadcastId = data.broadcastId

    // Set scaleToIgnore for 15 seconds to prevent showing same scale again
    data.macAddress.let { macAddress ->
      scaleToIgnore = macAddress
      scope.launch {
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
  }

  /** Shows the "scale memory full / max users" reconnect alert for a known scale. */
  private suspend fun handleDeviceMemoryFull(data: GGDeviceDetail, isKnownScale: Boolean) {
    val currentRoute = navigationService.getCurrentRoute()
    val isOnAuthScreen = currentRoute is AppRoute.Auth
    if (currentRoute !is AppRoute.DeviceSetup && isKnownScale && !isOnAuthScreen) {
      dialogQueueService.showDialog(
        ReconnectScale.getMaxUserAlert(
          onConfirm = {
            scope.launch {
              try {
                val accountId = getCurrentAccountId() ?: return@launch
                val broadcastId = data.broadcastId ?: return@launch
                val device = deviceService.getScaleByBroadcastId(broadcastId, accountId)
                if (device == null) {
                  AppLog.w(TAG, "DEVICE_MEMORY_FULL: scale not found for broadcastId=$broadcastId")
                  return@launch
                }
                dialogQueueService.showLoader("Loading...")
                ggDeviceService.addCacheDevice(data.broadcastId, device)
                ggDeviceService.getUsers(device.toGGBTDevice()) { response ->
                  scope.launch {
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

  /** Shows the duplicate-user reconnect alert, reclaiming the scale slot on confirm. */
  private suspend fun handleDeviceDuplicateUser(data: GGDeviceDetail) {
    try {
      val currentRoute = navigationService.getCurrentRoute()
      val isOnAuthScreen = currentRoute is AppRoute.Auth
      if (currentRoute !is AppRoute.DeviceSetup && !isOnAuthScreen) {
        dialogQueueService.showDialog(
          ReconnectScale.getDuplicateUserAlert(
            onConfirm = {
              scope.launch {
                val accountId = getCurrentAccountId() ?: return@launch
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
                    scope.launch {
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

  private fun onDeviceUpdate(
    deviceDetail: GGDeviceDetail,
    connectionStatus: BLEStatus? = null,
  ) {
    scope.launch {
      deviceService.onDeviceUpdate(
        deviceDetail,
        connectionStatus,
      )
    }
  }
}
