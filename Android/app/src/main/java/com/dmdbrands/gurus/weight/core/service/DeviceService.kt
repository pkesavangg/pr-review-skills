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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

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

  override val isWeightOnlyModeAlertShown = MutableStateFlow(false)

  override suspend fun onDeviceUpdate(deviceDetail: GGDeviceDetail, connectionStatus: BLEStatus?) {
    val device = pairedScales.first().find { it.device?.macAddress == deviceDetail.macAddress }
    val macAddress = device?.device?.macAddress ?: deviceDetail.macAddress
    val connectionStatus = connectionStatus ?: device?.connectionStatus ?: BLEStatus.DISCONNECTED
    macAddress.let { macAddress ->
      _connectionStatusMap.value = _connectionStatusMap.value.toMutableMap().apply {
        this[macAddress] = connectionStatus
      }

      // Immediately update the device with new connection status and recalculate weight-only mode
      val currentDevices = _pairedScales.value.toMutableList()
      val deviceIndex = currentDevices.indexOfFirst { it.device?.macAddress == macAddress }

      if (deviceIndex >= 0) {
        val device = currentDevices[deviceIndex]
        connectionStatus == BLEStatus.CONNECTED

        val updatedDevice = device.copy(
          connectionStatus = connectionStatus,
          device = device.device?.copy(
            isWifiConfigured = deviceDetail.isWifiConfigured
          ),
          isWeighOnlyModeEnabledByOthers = device.preferences?.shouldMeasureImpedance == true && (deviceDetail.impedanceSwitchState == false || deviceDetail.impedanceSwitchState == null)
        )

        currentDevices[deviceIndex] = updatedDevice
        _pairedScales.value = currentDevices
        _connectedScales.value = currentDevices
      }
    }
    // Optionally log or handle the null case
    // else log.warn("Received update with null MAC address")
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
      combine(
        deviceRepository.getDevices(resolvedAccountId),
        _connectionStatusMap,
      ) { devices, connectionStatusMap ->
        devices.map { device ->
          val connectionStatus = connectionStatusMap[device.device?.macAddress] ?: BLEStatus.DISCONNECTED

          device.copy(
            connectionStatus = connectionStatus,
            device = device.device?.copy(
              isWifiConfigured = device.device.isWifiConfigured
            ),
          )
        }
      }.collect { updatedDevices ->
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
    // Cancel any existing collector job
    deviceCollectionJob?.cancel()
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
      tempDevice?.let {
        it.copy(isSynced = false)
        storedDevices.add(it)
      }

      // 3. Classify devices
      val devicesToSync = storedDevices.filter { device ->
        (!device.isSynced || (device.preferences != null && !device.preferences.isSynced)) && !device.isDeleted
      }
      val deletedDevices = storedDevices.filter { it.isDeleted }
      storedDevices.filter {
        !it.isDeleted && it.isSynced && (it.preferences?.isSynced != false)
      }

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
          syncedDevicesToStore.add(savedDevice)
        } catch (e: Exception) {
          AppLog.e(tag, "Error syncing device ${device.id}", e)
          unsyncedDevices.add(
            device.copy(
              isSynced = false,
            ),
          )
        }
      }

      // 5. Delete devices marked for deletion
      for (device in deletedDevices) {
        try {
          deviceRepository.deleteDeviceFromApi(device.id)
          deviceRepository.deleteDeviceFromDb(device.id)
        } catch (e: Exception) {
          AppLog.e(tag, "Error deleting device ${device.id}", e)
          unsyncedDevices.add(device)
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
      apiDevices.map { it ->
        val device = deviceRepository.getDevice(it.id).first()
        if (device != null) {
          it.copy(
            isSynced = true,
            device = device.device?.copy(
              macAddress = device.device.macAddress,
              isWifiConfigured = device.device.isWifiConfigured,
            ),
          )
        } else {
          it.copy(isSynced = true)
        }
      } + unsyncedDevices.map { it.copy(isSynced = false) }
    } catch (e: Exception) {
      AppLog.e(tag, "Error fetching devices from API", e)
      syncedDevicesToStore.map { it.copy(isSynced = true) } + unsyncedDevices.map { it.copy(isSynced = false) }
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
    val current = device
    val scaleID = System.currentTimeMillis().toString()
    // If your Preferences has `scaleId` (like your TS), update that:
    val updatedPrefs = current.preferences?.copy(
      id = scaleID,                      // use `id = scaleID` if your model uses `id`
    )
    var updatedDevice = current.copy(
      id = scaleID,
      preferences = updatedPrefs
    )

   return try{
      val savedDevice = deviceRepository.saveDeviceToApi(updatedDevice, currentAccountId ?: "")
     if (savedDevice.preferences != null) {
       val updatedPreferences = savedDevice.preferences.copy(id = savedDevice.id)
       updatedDevice = savedDevice.copy(preferences = updatedPreferences)
     }
      syncDevices(savedDevice)
      AppLog.d(tag, "saveScale (via syncDevices): ${updatedDevice.id}")
      savedDevice
    }
    catch (e: Exception) {
      AppLog.d(tag, "saveScale (via syncDevices): $e")
      syncDevices(updatedDevice)
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
      // TODO("Update preferences to the scale via ggBluetoothPlugin")
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
   * @return The device if found, null otherwise
   */
  override suspend fun getScaleByBroadcastId(broadcastId: String): Device? =
    try {
      deviceRepository.getDeviceByBroadcastId(broadcastId).first()
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
