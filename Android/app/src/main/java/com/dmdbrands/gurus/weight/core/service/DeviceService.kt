package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.device.R4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.api.device.toR4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.repository.IDeviceRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.library.ggbluetooth.model.GGBTDevice
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import android.util.Log

/**
 * Service for managing device/scale data operations.
 * Provides a centralized way to access and manage scale data with automatic synchronization.
 */
@Singleton
class DeviceService
@Inject
constructor(
  private val deviceRepository: IDeviceRepository,
  connectivityObserver: IConnectivityObserver,
  dialogQueueService: IDialogQueueService,
  appNavigationService: IAppNavigationService,
  @ApplicationContext private val context: Context,
) : BaseService(connectivityObserver, dialogQueueService, appNavigationService), IDeviceService {
  private val tag = "DeviceService"

  // Internal scope for launching long-lived jobs
  private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var fetchJob: Job? = null

  private val _connectionStatusMap = MutableStateFlow<Map<String, BLEStatus>>(emptyMap())
  private val _connectedDeviceMap = MutableStateFlow<Map<String, GGDeviceDetail>>(emptyMap())
  private val _connectedScales = MutableStateFlow<List<Device>>(emptyList())
  override val connectedScales: Flow<List<Device>>
    get() = _connectedScales.asStateFlow()
  private val _pairedScales = MutableStateFlow<List<Device>>(emptyList())
  override val pairedScales: Flow<List<Device>>
    get() = _pairedScales.asStateFlow()

  override val hasBluetoothWifiScale: Flow<Boolean>
    get() = _pairedScales.map { devices ->
      devices.any { device -> device.deviceType == ScaleSetupType.BtWifiR4.value }
    }

  override val isWeightOnlyModeAlertShown = MutableStateFlow(false)

  private suspend fun updateConnectedDeviceDetailMap(macAddress: String, deviceDetail: GGDeviceDetail) {
    _connectedDeviceMap.value = _connectedDeviceMap.value.toMutableMap().apply {
      this[macAddress] = deviceDetail
    }
  }

  override suspend fun updateConnectionStatus(macAddress: String, connectionStatus: BLEStatus) {
    _connectionStatusMap.value = _connectionStatusMap.value.toMutableMap().apply {
      this[macAddress] = connectionStatus
    }
  }

  override suspend fun onDeviceUpdate(deviceDetail: GGDeviceDetail, connectionStatus: BLEStatus?) {
    val macAddress = deviceDetail.macAddress
    val resolvedConnectionStatus = connectionStatus ?: BLEStatus.DISCONNECTED

    AppLog.d(
      tag,
      "onDeviceUpdate called for device ${macAddress} with WiFi configured: ${deviceDetail.isWifiConfigured}",
    )

    // Update connection status map
    updateConnectionStatus(macAddress, resolvedConnectionStatus)
    updateConnectedDeviceDetailMap(macAddress, deviceDetail)

    // Try to find the device in current paired scales
    val currentDevices = _pairedScales.value.toMutableList()
    val deviceIndex = currentDevices.indexOfFirst { it.device?.macAddress == macAddress }

    if (deviceIndex >= 0) {
      // Device found in current list - update it directly
      val device = currentDevices[deviceIndex]
      val updatedDevice = device.copy(
        connectionStatus = resolvedConnectionStatus,
        device = device.device?.copy(
          isWifiConfigured = deviceDetail.isWifiConfigured,
          wifiMacAddress = if (deviceDetail.isWifiConfigured == true) deviceDetail.wifiMacAddress else null,
        ),
        isWeighOnlyModeEnabledByOthers = device.preferences?.shouldMeasureImpedance == true && (deviceDetail.impedanceSwitchState == false),
      )
      currentDevices[deviceIndex] = updatedDevice
      _pairedScales.value = currentDevices
      _connectedScales.value = currentDevices
    }
  }

  /**
   * Current account ID for filtering devices.
   */
  private var currentAccountId: String? = null
  private var deviceCollectionJob: Job? = null

  init {
    AppLog.d(tag, "DeviceService initialized")
    // Note: We don't call syncScales here as we need an account ID first
    // This will be called when setAccountId is called
  }

  override suspend fun fetchScales(accountId: String?) {
    val resolvedAccountId = accountId ?: this.currentAccountId
    ?: throw IllegalArgumentException("No account ID provided")

    // Cancel any previous fetch operation
    fetchJob?.cancel()

    fetchJob = repositoryScope.launch {
      deviceRepository.getDevices(resolvedAccountId).collect { devices ->
        val updatedDevices = devices.map { device ->
          val connectionStatus = _connectionStatusMap.value[device.device?.macAddress] ?: BLEStatus.DISCONNECTED
          val deviceDetail = _connectedDeviceMap.value[device.device?.macAddress] ?: device.device
          device.copy(
            connectionStatus = connectionStatus,
            device = deviceDetail,
            isWeighOnlyModeEnabledByOthers = device.preferences?.shouldMeasureImpedance == true && (deviceDetail?.impedanceSwitchState == false),
          )
        }
        _pairedScales.value = updatedDevices
        AppLog.d(tag, "Updated ${updatedDevices.size} devices with connection status and weight-only mode calculation")
      }
    }
  }

  override fun getGGBTDevices(): Flow<List<GGBTDevice>> {
    return deviceRepository
      .getDevices(currentAccountId!!)
      .map { deviceList ->
        deviceList.map { it.toGGBTDevice() }
      }
      .distinctUntilChanged { oldList, newList ->
        if (oldList.size != newList.size) return@distinctUntilChanged false
        oldList.zip(newList).all { (a, b) -> a == b }
      }
  }

  /**
   * Set the current account ID and initialize device data for that account.
   * This should be called when the user logs in or switches accounts.
   *
   * @param accountId The account ID to set
   */
  override suspend fun setAccountId(accountId: String) {
    AppLog.d(tag, "Setting account ID: $accountId")
    currentAccountId = accountId
    syncDevices()
    fetchScales(accountId)
    // Sync devices from API and local DB
  }

  /**
   * Clear the current account data.
   * This should be called when the user logs out.
   */
  override suspend fun clearAccountData() {
    AppLog.d(tag, "Clearing account data")
    currentAccountId = null
  }

  /**
   * Unified sync method for device operations and initial sync.
   * - If called with no arguments, performs initial sync: fetches from API, merges with local, updates StateFlow.
   * - If called with device lists, processes add/update/delete operations and updates StateFlow.
   *
   * @param newOrUpdatedDevices Devices to add or update (optional)
   * @param deletedDevices Devices to delete (optional)
   */
  override suspend fun syncDevices(
    tempDevice: Device?
  ) {
    val tag = "DeviceService-syncDevices"
    if (currentAccountId == null) return

    val unsyncedDevices = mutableListOf<Device>()
    val syncedDevicesToStore = mutableListOf<Device>()

    try {
      // 1. Get locally stored devices
      val storedDevices = deviceRepository.getDevices(currentAccountId!!, false).first().toMutableList()

      // 2. Inject a temporary new device if passed
      tempDevice?.let { td ->
        // ✅ FIX: Only mark as unsynced if it's actually not synced yet
        val temp = if (td.isSynced) td else td.copy(isSynced = false)
        storedDevices.add(temp)
      }

      // 3. Classify devices
      val devicesToSync = storedDevices.filter { device ->
        !device.isDeleted &&
          (!device.isSynced || (device.preferences != null && !device.preferences.isSynced))
      }
      val devicesMarkedDeleted = storedDevices.filter { it.isDeleted }

      // 4. Sync new/updated devices
      for (device in devicesToSync) {
        try {
          var savedDevice = deviceRepository.saveDeviceToApi(device, currentAccountId!!)
          savedDevice = savedDevice.copy(isSynced = true)

          // Sync preference if needed
          if (savedDevice.deviceType == ScaleSetupType.BtWifiR4.value && savedDevice.preferences != null) {
            try {
              val updatedPref = savedDevice.preferences.copy(
                isSynced = true,
                id = savedDevice.id,
              )
              deviceRepository.saveScalePreferencesToApi(updatedPref.toR4ScalePreferenceApiModel())
              savedDevice = savedDevice.copy(preferences = updatedPref)
            } catch (e: Exception) {
              AppLog.e(tag, "Error syncing preference for ${savedDevice.id}", e)
              savedDevice = savedDevice.copy(
                preferences = savedDevice.preferences?.copy(isSynced = false),
              )
            }
          }

          // ✅ FIX: If ID changed (server-generated ID), remove old temp record
          if (savedDevice.id != device.id) {
            try {
              deviceRepository.deleteDeviceFromDb(device.id)
            } catch (e: Exception) {
              AppLog.e(tag, "Could not delete temp local device ${device.id}", e)
            }
          }

          syncedDevicesToStore.add(savedDevice)
        } catch (e: Exception) {
          AppLog.e(tag, "Error syncing device ${device.id}", e)
          unsyncedDevices.add(device.copy(isSynced = false))
        }
      }

      // 5. Delete devices marked for deletion
      for (device in devicesMarkedDeleted) {
        try {
          deviceRepository.deleteDeviceFromApi(device.id)
          deviceRepository.deleteDeviceFromDb(device.id)
        } catch (e: Exception) {
          AppLog.e(tag, "Error deleting device ${device.id}", e)
          val errorMessage = e.message ?: ""
          if (errorMessage.contains("Not Found")) {
            AppLog.d(tag, "Device ${device.id} not found on server, marking as synced and deleted")
            deviceRepository.markDeviceSynced(device.id, true)
            deviceRepository.markDeviceDeleted(device.id, true)
            deviceRepository.deleteDeviceFromDb(device.id)
          } else {
            unsyncedDevices.add(device)
          }
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "General sync error for account $currentAccountId", e)
      tempDevice?.let {
        unsyncedDevices.add(
          it.copy(
            id = UUID.randomUUID().toString(),
            isSynced = false,
          ),
        )
      }
    }

    // 6. Get fresh data from API and merge with unsynced
    val finalDevices = try {
      val apiDevices = deviceRepository.getDevicesFromApi(currentAccountId!!)
      apiDevices.map { apiDev ->
        val localMatch = deviceRepository.getDevice(apiDev.id).first()
        if (localMatch != null) {
          apiDev.copy(
            isSynced = true,
            isDeleted = localMatch.isDeleted,
            device = apiDev.device?.copy(
              macAddress = localMatch.device?.macAddress ?: apiDev.device?.macAddress ?: "",
              isWifiConfigured = localMatch.device?.isWifiConfigured ?: apiDev.device?.isWifiConfigured ?: false,
            ),
          )
        } else {
          apiDev.copy(isSynced = true)
        }
      } + unsyncedDevices.map { it.copy(isSynced = false) }
    } catch (e: Exception) {
      AppLog.e(tag, "Error fetching devices from API", e)
      syncedDevicesToStore.map { it.copy(isSynced = true) } +
        unsyncedDevices.map { it.copy(isSynced = false) }
    }

    // 7. Store updated device list locally
    try {
      finalDevices.forEach { device ->
        deviceRepository.saveDeviceToDb(device, currentAccountId!!)
      }

      // 8. Refresh the pairedScales StateFlow to reflect changes in UI
      fetchScales(currentAccountId)
    } catch (e: Exception) {
      AppLog.e(tag, "Error storing final device list", e)
    }
  }

  /**
   * Save a new scale or update an existing one using syncDevices.
   * If the scale is not synced, it will be marked as temporary and synced later.
   *
   * @param device The device to save
   */
  override suspend fun saveScale(device: Device): Device? {
    val tag = "DeviceService-saveScale"
    val scaleID = System.currentTimeMillis().toString()

    // If your Preferences has `scaleId`, align it; otherwise keep using `id`.
    val updatedPrefs = device.preferences?.copy(
      id = scaleID,
    )
    val updatedDevice = device.copy(
      id = scaleID,
      preferences = updatedPrefs,
    )

    Log.d("saveddevice333", "$device")

    return try {
      // Attempt API save (online path). If offline, this throws and we fall back.
      val savedDevice = deviceRepository.saveDeviceToApi(updatedDevice, currentAccountId ?: "")
      Log.d("saveddevice444","$savedDevice")
      if (updatedPrefs?.toR4ScalePreferenceApiModel() != null) {
        deviceRepository.saveScalePreferencesToApi(
          updatedPrefs.toR4ScalePreferenceApiModel().copy(
            scaleId = savedDevice.id,
          ),
        )
        savedDevice.copy(preferences = updatedPrefs)
      }
      val adjusted = if (savedDevice.preferences != null) {
        val updatedPreferences = savedDevice.preferences.copy(id = savedDevice.id)
        savedDevice.copy(preferences = updatedPreferences)
      } else savedDevice

      // Let sync handle the rest; it will also remove any temp rows if IDs differ.
      syncDevices(adjusted)
      AppLog.d(tag, "saveScale (via syncDevices): ${adjusted.id}")
      adjusted
    } catch (e: Exception) {
      // Offline (or API error) -> push as temp; syncDevices() will store locally and retry later.
      AppLog.d(tag, "saveScale (via syncDevices) offline/fallback: $e")
      syncDevices(updatedDevice.copy(isSynced = false))
      null
    }
  }

  /**
   * Delete a scale from both local database and API using syncDevices.
   *
   * @param deviceId The ID of the device to delete
   */
  override suspend fun deleteScale(deviceId: String) {
    AppLog.d(tag, "deleteScale (via syncDevices): $deviceId")
    val device = deviceRepository.getDevice(deviceId).first()
    if (device != null) {
      if (!device.isSynced) {
        deviceRepository.deleteDeviceFromDb(deviceId)
        syncDevices()
      } else {
        syncDevices(device.copy(isDeleted = true))
      }
    } else {
      AppLog.w(tag, "Device not found for delete: $deviceId")
    }
  }

  /**
   * Update a scale's nickname.
   *
   * @param device The ID of the device
   * @param nickname The new nickname
   */
  override suspend fun updateScaleNickname(
    device: Device,
    nickname: String,
  ) {
    AppLog.d(tag, "Updating scale nickname: $device -> $nickname")
    try {
      deviceRepository.updateDeviceNickname(device, nickname)
      AppLog.d(tag, "Scale nickname updated successfully")
    } catch (e: Exception) {
      AppLog.e(tag, "Error updating scale nickname", e)
    }
  }

  /**
   * Update scale preferences for a specific device.
   *
   * @param deviceId The ID of the device
   * @param preferences The preferences to update
   * @return True if successful, false otherwise
   */
  override suspend fun updateScalePreferences(
    deviceId: String,
    preferences: R4ScalePreferenceApiModel,
  ): Boolean {
    AppLog.d(tag, "Updating scale preferences for device: $deviceId")
    return try {
      val updatedPreference =
        preferences.copy(
          wifiFotaScheduleTime = 0,
          tzOffset = getTimeZoneInMinutes(),
        )
      // Save preferences to API
      deviceRepository.saveScalePreferencesToApi(updatedPreference)
      syncDevices()
      AppLog.d(tag, "Scale preferences updated successfully")
      true
    } catch (e: Exception) {
      AppLog.e(tag, "Error updating scale preferences", e)
      false
    }
  }

  /**
   * Get a specific scale by its ID.
   *
   * @param deviceId The ID of the device
   * @return The device if found, null otherwise
   */
  override suspend fun getScale(deviceId: String): Device? =
    try {
      deviceRepository.getDevice(deviceId).first()
    } catch (e: Exception) {
      AppLog.e(tag, "Error getting scale", e)
      null
    }

  /**
   * Get scales by type.
   *
   * @param deviceType The type of devices to get
   * @return List of devices of the specified type
   */
  override suspend fun getScalesByType(deviceType: String): List<Device> =
    _pairedScales.value.filter { it.deviceType == deviceType }

  /**
   * Get connected scales.
   *
   * @return List of currently connected devices
   */
  override suspend fun getConnectedScales(): List<Device> =
    _pairedScales.value.filter { it.connectionStatus == BLEStatus.CONNECTED }

  /**
   * Get unsynced scales (temporary scales).
   *
   * @return List of devices that are not yet synced with the server
   */
  override suspend fun getUnsyncedScales(): List<Device> = _pairedScales.value.filter { !it.hasServerID }

  /**
   * Check if a scale exists by MAC address.
   *
   * @param mac The MAC address to check
   * @return True if the scale exists, false otherwise
   */
  override suspend fun scaleExistsByMac(mac: String): Boolean =
    try {
      _pairedScales.value.any { it.device?.macAddress == mac }
    } catch (e: Exception) {
      AppLog.e(tag, "Error checking scale existence by MAC", e)
      false
    }

  /**
   * Get a scale by broadcast ID.
   *
   * @param broadcastId The broadcast ID to search for
   * @param accountId The account ID to filter by
   * @return The device if found, null otherwise
   */
  override suspend fun getScaleByBroadcastId(broadcastId: String, accountId: String): Device? =
    try {
      deviceRepository.getDeviceByBroadcastId(broadcastId, accountId).first()
    } catch (e: Exception) {
      AppLog.e(tag, "Error getting scale by broadcast ID", e)
      null
    }

  /**
   * Get a scale by MAC address.
   *
   * @param mac The MAC address to search for
   * @return The device if found, null otherwise
   */
  override suspend fun getScaleByMac(mac: String): Device? =
    try {
      deviceRepository.getDeviceByMac(mac, this.currentAccountId ?: "").first()
    } catch (e: Exception) {
      AppLog.e(tag, "Error getting scale by MAC", e)
      null
    }

  /**
   * Get the current account ID.
   *
   * @return The current account ID, or null if not set
   */
  override suspend fun getCurrentAccountId(): String? = currentAccountId

  /**
   * Check if the service is initialized with an account.
   *
   * @return True if an account ID is set, false otherwise
   */
  override suspend fun isInitialized(): Boolean = currentAccountId != null

  /**
   * Get scale token from the API.
   * @return The scale token as a string.
   */
  override suspend fun getScaleToken(isR4: Boolean): String? {
    AppLog.d(tag, "Getting scale token from API")
    return try {
      val token = deviceRepository.getScaleTokenFromApi(isR4)
      AppLog.d(tag, "Scale token retrieved successfully")
      token
    } catch (e: Exception) {
      AppLog.e(tag, "Error getting scale token", e)
      null
    }
  }

  override fun weightOnlyModeDismissAlert(
    onConfirm: () -> Unit
  ) {
    dialogQueueService.showDialog(
      DialogModel.Confirm(
        title = ScaleSetupStrings.WeightOnlyModeAlertDismiss.Title,
        message = ScaleSetupStrings.WeightOnlyModeAlertDismiss.Message,
        confirmText = ScaleSetupStrings.WeightOnlyModeAlertDismiss.Dismiss,
        cancelText = ScaleSetupStrings.WeightOnlyModeAlertDismiss.Cancel,
        onConfirm = {
          updateWeightOnlyModeAlertShown(true)
          onConfirm.invoke()
          dialogQueueService.dismissCurrent()
        },
        onCancel = {
          dialogQueueService.dismissCurrent()
        },
      ),
    )
  }

  override fun updateWeightOnlyModeAlertShown(isAlertShown: Boolean) {
    isWeightOnlyModeAlertShown.value = isAlertShown
  }

  // Setup progress state management
  private var _isSetupInProgress = false

  override fun isSetupInProgress(): Boolean = _isSetupInProgress

  override fun setSetupInProgress(inProgress: Boolean) {
    _isSetupInProgress = inProgress
    AppLog.d(tag, "Setup progress state updated: $inProgress")
  }

  override suspend fun updateScalePreferencesByMac(
    macAddress: String,
    preferences: R4ScalePreferenceApiModel
  ): Boolean {
    val device = getScaleByMac(macAddress) ?: return false
    return updateScalePreferences(device.id, preferences)
  }

  private fun getTimeZoneInMinutes(): Int {
    val timeZone = TimeZone.getDefault()
    val offsetInMillis = timeZone.getOffset(System.currentTimeMillis())
    return offsetInMillis / (60 * 1000) // convert milliseconds to minutes
  }
}
