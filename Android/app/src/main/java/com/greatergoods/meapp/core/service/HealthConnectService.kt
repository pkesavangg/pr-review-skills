package com.greatergoods.meapp.core.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.greatergoods.libs.healthconnect.HealthConnect
import com.greatergoods.libs.healthconnect.enums.DataType
import com.greatergoods.libs.healthconnect.enums.HealthConnectPermissionStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectRequestStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectStatus
import com.greatergoods.libs.healthconnect.model.HealthConnectData
import com.greatergoods.libs.healthconnect.model.HealthConnectOptions
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.DeviceInfoUtil
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.interfaces.IDialogQueueService
import com.greatergoods.meapp.domain.model.integrations.IntegratedDeviceInfo
import com.greatergoods.meapp.domain.model.integrations.IntegrationData
import com.greatergoods.meapp.domain.model.integrations.IntegrationOperationType
import com.greatergoods.meapp.domain.model.integrations.IntegrationPreferences
import com.greatergoods.meapp.domain.model.integrations.IntegrationType
import com.greatergoods.meapp.domain.model.storage.entry.Entry
import com.greatergoods.meapp.domain.model.storage.entry.PeriodBodyScaleSummary
import com.greatergoods.meapp.domain.model.storage.entry.toPeriodBodyScaleSummary
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.repository.IEntryRepository
import com.greatergoods.meapp.domain.repository.IHealthConnectRepository
import com.greatergoods.meapp.domain.repository.IIntegrationRepository
import com.greatergoods.meapp.domain.services.IHealthConnectService
import com.greatergoods.meapp.features.common.components.DialogType
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.integration.strings.HealthConnectStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of Health Connect service for managing Health Connect integration.
 * This service uses the HealthConnect library's built-in load() functionality for permission handling.
 */
@Singleton
class HealthConnectService @Inject constructor(
  private val context: Context,
  private val healthConnectRepository: IHealthConnectRepository,
  private val accountRepository: IAccountRepository,
  private val dialogQueueService: IDialogQueueService,
  private val appNavigationService: IAppNavigationService,
  private val entryRepository: IEntryRepository, // Add entry repository for fetching entries
  private val integrationRepository: IIntegrationRepository // Inject IntegrationRepository for integrations flow
) : IHealthConnectService {

  // Core Health Connect components
  private lateinit var healthConnect: HealthConnect
  private lateinit var currentActivity: Activity

  // Service state
  private val tag = "HealthConnectService"
  private var isLoaded = false

  // State flows
  private val _outOfSyncState = MutableStateFlow(false)
  override val outOfSyncState: Flow<Boolean> = _outOfSyncState.asStateFlow()

  /**
   * The requesting permissions for Health Connect integration.
   * Defines what data types the app needs read/write access to.
   */
  override val requestingPermissions = HealthConnectOptions(
    writeTypes = setOf(
      DataType.Weight,
      DataType.BodyFat,
      DataType.LeanBodyMass,
      DataType.BasalMetabolicRate,
      DataType.RestingHeartRate,
      DataType.BoneMass,
    ),
  )

  /**
   * Initializes the Health Connect service.
   * The HealthConnect library handles its own initialization,
   * so this method just creates the instance.
   *
   * @param activity The Activity context for permission handling
   */
  override fun load(activity: androidx.activity.ComponentActivity) {
    try {
      AppLog.i(tag, "Initializing Health Connect service...")
      currentActivity = activity
      healthConnect = HealthConnect(activity)
      isLoaded = true
      AppLog.i(tag, "Health Connect service initialized successfully.")
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to initialize Health Connect service", e.toString())
      isLoaded = false
    }
  }

  /**
   * Legacy method for backward compatibility.
   * This now delegates to the new initialize method.
   */
  override fun initializeHealthConnect(activity: androidx.activity.ComponentActivity) {
    if (!isLoaded) {
      load(activity)
    }
  }

  /**
   * Gets the device ID for health connect integration.
   * Uses the device's unique identifier (ANDROID_ID).
   */
  private fun getDeviceId(): String {
    return DeviceInfoUtil.getDeviceUUID(context)
  }

  /**
   * Checks if Health Connect is available on the device.
   */
  override suspend fun checkAvailability(): Boolean {
    return try {
      healthConnect.isAvailable()
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check Health Connect availability", e.toString())
      false
    }
  }

  /**
   * Gets the current Health Connect status.
   */
  override suspend fun healthConnectStatus(): HealthConnectStatus {
    return try {
      healthConnect.getStatus()
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to get Health Connect status", e.toString())
      HealthConnectStatus.UNAVAILABLE
    }
  }

  /**
   * Checks the current permission status.
   */
  override suspend fun checkPermissionStatus(): HealthConnectPermissionStatus {
    return try {
      healthConnect.getPermissionStatus(requestingPermissions)
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check permission status", e.toString())
      HealthConnectPermissionStatus.NONE
    }
  }

  /**
   * Requests Health Connect authorization using the callback-based approach.
   * This matches the library's async pattern and handles results properly.
   */
  override suspend fun requestAuthorization(callback: (HealthConnectRequestStatus) -> Unit) {
    try {
      // Use the library's callback-based approach directly
      healthConnect.requestAuthorization(requestingPermissions) { result ->
        AppLog.i(tag, "Authorization completed with result: $result")
        callback(result)
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to request authorization", e.toString())
      callback(HealthConnectRequestStatus.CANCELLED)
    }
  }

  /**
   * Opens the Health Connect app or settings.
   */
  override suspend fun openHealthConnect(): Boolean {
    return try {
      val activity = currentActivity
      run {
        val result = healthConnect.launchHealthConnect(activity, false)
        AppLog.i(tag, "Health Connect launch result: $result")
        result
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to open Health Connect", e.toString())
      false
    }
  }

  /**
   * Revokes all Health Connect permissions.
   */
  override suspend fun revokePermission(): Boolean {
    return try {
      val result = healthConnect.revokeAllPermissions()
      when (result) {
        is com.greatergoods.libs.healthconnect.model.HealthConnectResult.Success -> {
          AppLog.i(tag, "Successfully revoked Health Connect permissions")
          true
        }

        is com.greatergoods.libs.healthconnect.model.HealthConnectResult.Error -> {
          AppLog.e(tag, "Failed to revoke permissions", result.toString())
          false
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Exception while revoking permissions", e.toString())
      false
    }
  }

  /**
   * Checks if Health Connect is already being used by another account.
   */
  override suspend fun checkIfAlreadyUsed(): Boolean {
    return try {
      val currentAccount = accountRepository.getActiveAccount().first()
      val healthConnectData = healthConnectRepository.getAccountDataMap()
      val integratedAccount = healthConnectData.values.firstOrNull { it.integrated }
      when {
        integratedAccount == null -> true // No account is using it
        currentAccount?.id == null -> false // No current account
        else -> {
          // Check if current account is the one using Health Connect
          val currentAccountData = healthConnectData[currentAccount.id]
          currentAccountData?.integrated == true
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check if Health Connect is already used", e.toString())
      false
    }
  }

  /**
   * Handles new intents for privacy policy and permissions rationale.
   * This method forwards the intent to the HealthConnect library for processing.
   * The library will handle the callback appropriately based on the intent action.
   *
   * @param intent The new intent to handle
   */
  override fun handleOnNewIntent(intent: Intent?) {
    if (!isLoaded) {
      AppLog.w(tag, "Health Connect service not loaded, ignoring intent")
      return
    }

    try {
      when (intent?.action) {
        HealthConnect.ACTION_SHOW_PERMISSIONS_RATIONALE,
        HealthConnect.ACTION_VIEW_PERMISSION_USAGE -> {
          AppLog.i(tag, "Forwarding Health Connect intent: ${intent.action}")
          healthConnect.handleOnNewIntent(intent)
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to handle Health Connect intent", e.toString())
    }
  }

  override suspend fun syncAllData(fromOutOfSync: Boolean): Boolean {
    dialogQueueService.showLoader("Syncing...")
    AppLog.i(tag, "Data syncing")
    val activeAccount = accountRepository.getActiveAccount().first()
    if (activeAccount != null) {
      try {
        val entries: List<Entry> = entryRepository.getEntriesByAccount(activeAccount.id)
        val summaries: List<PeriodBodyScaleSummary> = entries.mapNotNull { it.toPeriodBodyScaleSummary() }
        syncData(summaries)
        dialogQueueService.dismissLoader()
        if (!fromOutOfSync) {
          dialogQueueService.showToast(Toast(HealthConnectStrings.ToastStrings.syncToast))
        } else {
          dialogQueueService.showToast(Toast(HealthConnectStrings.ToastStrings.syncHc))
        }
        return true
      } catch (e: Exception) {
        AppLog.e(tag, "User denied health connect permission or sync failed", e.toString())
        dialogQueueService.dismissLoader()
        dialogQueueService.showDialog(
          DialogModel.Alert(
            title = HealthConnectStrings.dataNotSynced.title,
            message = HealthConnectStrings.dataNotSynced.message,
            dismissText = HealthConnectStrings.ActionButtons.close,
            onDismiss = { dialogQueueService.dismissCurrent() },
          )
        )
        return false
      } finally {
        dialogQueueService.dismissLoader()
      }
    } else return false
  }

  private suspend fun syncData(entries: List<PeriodBodyScaleSummary>) {
    val finalData = mutableListOf<HealthConnectData>()
    for (entry in entries) {
      entry.weight.let {
          finalData.add(HealthConnectData(DataType.Weight, it, timeStamp = Instant.parse(entry.entryTimestamp)))
      }

      entry.bodyFat.let {
        finalData.add(HealthConnectData(DataType.BodyFat, it, timeStamp = Instant.parse(entry.entryTimestamp)))
      }

      if(entry.bodyFat != null){
        val leanBodyMass = calculateLeanBodyMass(entry.weight, entry.bodyFat)
        finalData.add(HealthConnectData(DataType.LeanBodyMass, leanBodyMass, timeStamp = Instant.parse(entry.entryTimestamp)))
      }
      entry.boneMass.let {
        finalData.add(HealthConnectData(DataType.BoneMass, (it?.times(entry.weight))?.div(100), timeStamp = Instant.parse(entry.entryTimestamp)))
      }
      entry.bmr.let {
        finalData.add(HealthConnectData(DataType.BasalMetabolicRate, it, timeStamp = Instant.parse(entry.entryTimestamp)))
      }
      entry.pulse.let {
        finalData.add(HealthConnectData(DataType.RestingHeartRate, it, timeStamp = Instant.parse(entry.entryTimestamp)))
      }
    }
    try {
      healthConnect.saveData(finalData)
      // Optionally, set a flag or observable for successful sync
    } catch (e: Exception) {
      AppLog.e(tag, "User denied health connect permission or save failed", e.toString())
      throw e
    }
  }

  // Conversion utility (replace with actual implementation if available)
  private fun convertStoredToLbs(value: Double): Double {
      // TODO: Replace with actual conversion logic if needed
      return value // Assume value is already in lbs for now
  }

  // Helper to calculate lean body mass
  private fun calculateLeanBodyMass(weight: Double, bodyFat: Double): Double {
    return weight * (1 - bodyFat / 100)
  }


  override suspend fun deleteAllData(): Boolean {
    return try {
      if (!isLoaded) {
        AppLog.w(tag, "Health Connect service not loaded")
        return false
      }

      AppLog.i(tag, "Deleting all Health Connect data")
      val result = healthConnect.deleteAllData(requestingPermissions)

      when (result) {
        is com.greatergoods.libs.healthconnect.model.HealthConnectResult.Success -> {
          AppLog.i(tag, "Successfully deleted all Health Connect data")
          true
        }

        is com.greatergoods.libs.healthconnect.model.HealthConnectResult.Error -> {
          AppLog.e(tag, "Failed to delete Health Connect data", result.toString())
          false
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Exception while deleting Health Connect data", e.toString())
      false
    }
  }

  override suspend fun turnOnIntegration(fromMultiDevice: Boolean, isRequestNeed: Boolean) {
     try {
      val currentAccount = accountRepository.getActiveAccount().first()
      val accountId = currentAccount?.id

      if (accountId == null) {
        AppLog.e(tag, "No active account found")
        return
      }
      // Get approved permissions from Health Connect
      val permissionList = getApprovedPermissionList()
      // Create integration data
      val deviceId = getDeviceId()
      val integration = IntegrationData(
        deviceId = deviceId,
        type = IntegrationType.HEALTH_CONNECT.value,
        preferences = IntegrationPreferences(scopes = permissionList),
      )
       healthConnectRepository.setHealthConnectIntegrationStatus(accountId, true)
      healthConnectRepository.saveIntegration(integration)
      integrationRepository.updateLocalAccount()
      true
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to turn on integration", e.toString())
      false
    }

    if (fromMultiDevice) {
      return;
    }
    else if (!isRequestNeed) {
      syncWeightHistory()
    }
    else {
       syncAllData(true);
    }
  }

  override suspend fun removeHealthConnectIntegration(): Boolean {
    return try {
      val healthConnectStatus = healthConnectStatus()
      if (healthConnectStatus == HealthConnectStatus.INSTALLED ||
        healthConnectStatus == HealthConnectStatus.UPDATE_REQUIRED
      ) {
        // Revoke permissions if Health Connect is available
        revokePermission()
      }
      // Get the device ID for the current integration
      val deviceId = getDeviceId()
      val account = accountRepository.getActiveAccount().first()
      if(account != null){
        healthConnectRepository.removeServerHcIntegration(deviceId)
        healthConnectRepository.setHcIntegrationStatus(account.id, false)
        // Update integrations flow
        integrationRepository.updateLocalAccount()
      }
      _outOfSyncState.emit(false)
      AppLog.i(tag, "Successfully removed Health Connect integration")
      true
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to remove Health Connect integration", e.toString())
      false
    }
  }

  override suspend fun clearHealthConnect(): Boolean {
    return try {
      val currentAccount = accountRepository.getActiveAccount().first()
      val accountId = currentAccount?.id

      if (accountId == null) {
        AppLog.w(tag, "No active account found")
        return false
      }
      // Check if currently integrated and handle server removal
      val healthConnectData = healthConnectRepository.getAccountByID(accountId)
      if (healthConnectData?.integrated == true) {
        val deviceId = getDeviceId()
        val removingIntegrationData = IntegratedDeviceInfo(
          operationType = IntegrationOperationType.REMOVE.value,
          scopes = IntegrationData(
            deviceId = deviceId,
            type = IntegrationType.HEALTH_CONNECT.value,
          ),
          isCurrentDeviceDeleted = true,
        )
        // Store removal data and try to sync
        healthConnectRepository.setStoredIntegrationData(accountId, removingIntegrationData)
      }

      // Revoke Health Connect permissions
      try {
        revokePermission()
        AppLog.i(tag, "Successfully revoked Health Connect permissions")
      } catch (e: Exception) {
        AppLog.w(tag, "Failed to revoke permissions: ${e.message}")
      }
      // Set integration status to false
      healthConnectRepository.setHealthConnectIntegrationStatus(accountId, false)
      // Clear all local Health Connect data
      healthConnectRepository.updateAlertSeen(accountId, false)
      healthConnectRepository.updateOutOfSync(accountId, false)
      healthConnectRepository.updateModalState(accountId, false)
      healthConnectRepository.removeAccount(accountId)

      // Delete all Health Connect data
      deleteAllData()

      AppLog.i(tag, "Health Connect cleared successfully")
      true
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to clear Health Connect", e.toString())
      false
    }
  }

  override suspend fun checkPermissionChange() {
     try {
      val currentAccount = accountRepository.getActiveAccount().first()
      val accountId = currentAccount?.id

      if (accountId == null) {
        AppLog.w(tag, "No active account found")
        return
      }
      val healthConnectData = healthConnectRepository.getAccountByID(accountId)
      val storedGrantedPermissions = healthConnectData?.grantedPermissionList ?: emptyList()
      // Get current granted permissions from Health Connect
      val currentGrantedPermissions = getApprovedPermissionList()
      if (storedGrantedPermissions.isEmpty()) {
        AppLog.i(tag, "No stored permissions found, treating as initial setup")
        return
        // Update permissions and turn on integration
      } else {
        AppLog.i(tag, "Permissions found, checking for changes")
        // Compare permissions and update if different
        if (storedGrantedPermissions.toSet() != currentGrantedPermissions.toSet()) {
          AppLog.i(tag, "Permission changes detected, updating integration")
          healthConnectRepository.setHcPermissions(accountId, currentGrantedPermissions)
        }
        turnOnIntegration(fromMultiDevice = true, isRequestNeed = true)
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check permission changes", e.toString())
    }
  }

  override suspend fun checkMultiDeviceConnection(isPermissionEnabled: Boolean): Boolean {
    return try {
      val currentAccount = accountRepository.getActiveAccount().first()
      val accountId = currentAccount?.id

      if (accountId == null) {
        AppLog.w(tag, "No active account found")
        return false
      }
      // Check if Health Connect is already integrated with another account/device
      val allHealthConnectData = healthConnectRepository.getAccountDataMap()
      val integratedAccounts = allHealthConnectData.values.filter { it.integrated }
      val isMultipleDeviceConnected = integratedAccounts.size > 1 ||
        (integratedAccounts.isNotEmpty() && integratedAccounts.none {
          allHealthConnectData.entries.firstOrNull { entry -> entry.value == it }?.key == accountId
        })

      if (isMultipleDeviceConnected) {
        AppLog.i(tag, "Multiple device connection detected")
        // Set integration status and handle multi-device connection
        healthConnectRepository.setHealthConnectIntegrationStatus(accountId, true)
        // TODO: Show multi-device connection UI
        // This would typically trigger a UI state change that the ViewModel observes
        // The Angular equivalent shows HealthConnectSetup.multipleDeviceConnection modal
        if (!isPermissionEnabled) {
          _outOfSyncState.value = true
          healthConnectRepository.updateOutOfSync(accountId, true)
        }
        // Turn on integration for multi-device scenario
        turnOnIntegration(fromMultiDevice = true, isRequestNeed = true)
        return true
      }
      return false
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check multiple device connection", e.toString())
      false
    }
  }

  override suspend fun healthConnectOutOfSync(): Boolean {
    return try {
      val currentAccount = accountRepository.getActiveAccount().first()
      val accountId = currentAccount?.id
      if (accountId == null) {
        AppLog.w(tag, "No active account found")
        return false
      }

      val healthConnectStatus = healthConnectStatus()
      val healthConnectData = healthConnectRepository.getAccountByID(accountId)
      val outOfSyncSession = healthConnectData?.outOfSync ?: false
      val isIntegrated = healthConnectData?.integrated ?: false

      when (healthConnectStatus) {
        HealthConnectStatus.INSTALLED,
        HealthConnectStatus.UPDATE_REQUIRED -> {
          val permissionStatus = checkPermissionStatus()

          if (outOfSyncSession && isIntegrated) {
            when (permissionStatus) {
              HealthConnectPermissionStatus.NONE -> {
                // User has disabled permissions - mark as out of sync
                _outOfSyncState.value = true
                healthConnectRepository.updateOutOfSync(accountId, true)
                healthConnectRepository.updateModalState(accountId, true)
                AppLog.i(tag, "Health Connect permissions disabled - marked as out of sync")
                return true
              }

              HealthConnectPermissionStatus.ALL,
              HealthConnectPermissionStatus.PARTIAL -> {
                // Permissions are available - clear out of sync state
                _outOfSyncState.value = false
                healthConnectRepository.updateOutOfSync(accountId, false)
                val isModalDismissed = healthConnectData.modalState
                if (!isModalDismissed) {
                  AppLog.i(tag, "Health Connect permissions re-enabled - showing finish connect flow")
                }
                // Check for permission changes and update integration
                checkPermissionChange()
                AppLog.i(tag, "Health Connect permissions restored - integration updated")
                return false // Not out of sync anymore
              }
            }
          }
        }

        HealthConnectStatus.UNAVAILABLE -> {
          AppLog.w(tag, "Health Connect unavailable")
          return false
        }

        else -> {
          AppLog.w(tag, "Health Connect status: $healthConnectStatus")
          return false
        }
      }
      false
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check Health Connect out of sync status", e.toString())
      false
    }
  }

  /**
   * Gets the list of approved permissions from Health Connect.
   * This method returns the permissions that the user has granted to the app.
   */
  override suspend fun getApprovedPermissionList(): List<String> {
    return try {
      if (!isLoaded) {
        AppLog.w(tag, "Health Connect service not loaded")
        return emptyList()
      }

      val approvedPermissions = healthConnect.getApprovedPermissionList()
      AppLog.i(tag, "Retrieved ${approvedPermissions.size} approved permissions")
      approvedPermissions.toList()
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to get approved permission list", e.toString())
      emptyList()
    }
  }

  override fun syncWeightHistory() {
    dialogQueueService.showDialog(
      DialogModel.Confirm(
        title = HealthConnectStrings.SyncAlert.title,
        message = HealthConnectStrings.SyncAlert.description,
        confirmText = HealthConnectStrings.ActionButtons.sync,
        cancelText = HealthConnectStrings.ActionButtons.cancel,
        onConfirm = {
          dialogQueueService.showLoader("Removing...")
          CoroutineScope(Dispatchers.IO).launch {
            syncAllData()
            dialogQueueService.dismissCurrent()
            dialogQueueService.dismissLoader()
            dialogQueueService.showToast(Toast(HealthConnectStrings.ToastStrings.syncToast))
          }
        },
        onCancel = {
          dialogQueueService.dismissCurrent()
        },
        onDismiss = {
          dialogQueueService.dismissCurrent()
        },
      ),
    )
  }

  /**
   * Checks Health Connect permission and shows the appropriate modal if needed.
   * This matches the Angular checkHealthConnectPermissionDisabled logic.
   */
  override suspend fun checkHealthConnectPermissionDisabled() {
    val healthConnectStatus = healthConnectStatus()
    val currentAccount = accountRepository.getActiveAccount().first()
    val accountId = currentAccount?.id
    if (accountId == null) return
    val healthConnectData = healthConnectRepository.getAccountByID(accountId)
    val isHealthConnectOpened = healthConnectData?.modalState ?: false
    val outOfSyncSession = healthConnectData?.outOfSync ?: false
    val isIntegrated = currentAccount.isHealthConnectOn
    val isAlreadyConnected = try {
      checkIfAlreadyUsed()
    } catch (e: Exception) {
      false
    }
    val permissionStatus = checkPermissionStatus()
    val integrationsFromServer = isIntegrated // Placeholder for server check
    val shouldShowPopup = true // You can add your own logic for this
    // Migration: If integrated, try to turn on integration
    if (isIntegrated) {
      turnOnIntegration(fromMultiDevice = true, isRequestNeed = true)
    }
    // If permission is NONE and modal was opened, reset modal state
    if (permissionStatus == HealthConnectPermissionStatus.NONE && isHealthConnectOpened) {
      healthConnectRepository.updateModalState(accountId, false)
    }
    // Out of sync flow
    if (outOfSyncSession) {
      healthConnectOutOfSync()
    }
    // If already connected, do not show popup
    if (!isAlreadyConnected) {
      _outOfSyncState.value = outOfSyncSession
      return
    }
    // Multi-device connection check
    if ((permissionStatus == HealthConnectPermissionStatus.NONE ||
        permissionStatus == HealthConnectPermissionStatus.PARTIAL ||
        permissionStatus == HealthConnectPermissionStatus.ALL) && integrationsFromServer
    ) {
      val isConnect = permissionStatus == HealthConnectPermissionStatus.PARTIAL ||
        permissionStatus == HealthConnectPermissionStatus.ALL
      val isMultiDeviceConnected = checkMultiDeviceConnection(isConnect)
      if (isMultiDeviceConnected) {
        // Show multiple device connection modal
        dialogQueueService.showDialog(
          DialogModel.Custom(
            contentKey = DialogType.MultipleDeviceConnection,
            onConfirm = {  CoroutineScope(Dispatchers.Main).launch {
              appNavigationService.navigateBack(AppRoute.Integration.IntegrationList)
              appNavigationService.navigateBack(AppRoute.Integration.HealthConnect)
              dialogQueueService.dismissCurrent()
            } },
            onDismiss = { dialogQueueService.dismissCurrent() },
          ),
        )
        return
      }
    }
    // Out of sync modal
    if (permissionStatus == HealthConnectPermissionStatus.NONE && isIntegrated && !outOfSyncSession) {
      healthConnectRepository.updateOutOfSync(accountId, true)
      healthConnectRepository.updateModalState(accountId, true)
      // If you have a session state for out of sync, update it here as well
      // healthConnectRepository.updateOutOfSyncSession(accountId, true)
      _outOfSyncState.value = true // Set observable for out of sync
      if (shouldShowPopup) {
        dialogQueueService.showDialog(
          DialogModel.Custom(
            contentKey = DialogType.OutOfSyncModal,
            onConfirm = {
              dialogQueueService.dismissCurrent()
              CoroutineScope(Dispatchers.Main).launch {
                openHealthConnect()          // ← now allowed
                // On confirm, you may want to reset out-of-sync state if permissions are restored
                healthConnectRepository.updateOutOfSync(accountId, false)
                healthConnectRepository.updateModalState(accountId, false)
                _outOfSyncState.value = false
              }
            },
            onDismiss = {
              CoroutineScope(Dispatchers.Main).launch {
                removeHealthConnectIntegration()
                dialogQueueService.dismissCurrent()
              } },
          ),
        )
      }
      return
    }
    // If permissions are restored, clear out-of-sync state
    if ((permissionStatus == HealthConnectPermissionStatus.ALL || permissionStatus == HealthConnectPermissionStatus.PARTIAL) && outOfSyncSession) {
      healthConnectRepository.updateOutOfSync(accountId, false)
      healthConnectRepository.updateModalState(accountId, false)
      // healthConnectRepository.updateOutOfSyncSession(accountId, false)
      _outOfSyncState.value = false
    }
    // Finish connect modal
    if (permissionStatus == HealthConnectPermissionStatus.PARTIAL ||
      permissionStatus == HealthConnectPermissionStatus.ALL
    ) {
      val isModalDismissed = healthConnectData?.modalState ?: false
      if (!isIntegrated && !isModalDismissed && !isHealthConnectOpened) {
        if (shouldShowPopup) {
          dialogQueueService.showDialog(
            DialogModel.Custom(
              contentKey = DialogType.FinishConnect,
              onConfirm = {
                CoroutineScope(Dispatchers.Main).launch {
                  appNavigationService.navigateTo(AppRoute.Integration.IntegrationList)
                  appNavigationService.navigateTo(AppRoute.Integration.HealthConnect)
                  dialogQueueService.dismissCurrent()
                }
              },
              onDismiss = { dialogQueueService.dismissCurrent() },
            ),
          )
        }
        return
      } else if (isHealthConnectOpened) {
        if (shouldShowPopup) {
          dialogQueueService.showDialog(
            DialogModel.Custom(
              contentKey = DialogType.FinishConnect,
              onConfirm = { CoroutineScope(Dispatchers.Main).launch {
                appNavigationService.navigateBack(AppRoute.Integration.IntegrationList)
                appNavigationService.navigateBack(AppRoute.Integration.HealthConnect)
                dialogQueueService.dismissCurrent()
              } },
              onDismiss = {
                dialogQueueService.dismissCurrent()
              },
            ),
          )
          healthConnectRepository.updateModalState(accountId, false)
        }
        return
      }
    }
    // Install required modal (not implemented, but you can add it here)
    if (healthConnectStatus == HealthConnectStatus.INSTALL_REQUIRED && isIntegrated) {
      // Show install required modal or alert
      // dialogQueueService.showDialog(...)
    }
  }
}
