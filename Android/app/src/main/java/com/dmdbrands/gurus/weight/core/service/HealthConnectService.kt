package com.dmdbrands.gurus.weight.core.service

import androidx.activity.ComponentActivity
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.DeviceInfoUtil
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegratedDeviceInfo
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationData
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationOperationType
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationPreferences
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationType
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.toPeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.repository.IIntegrationRepository
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.integration.strings.HealthConnectStrings
import com.greatergoods.libs.healthconnect.HealthConnect
import com.greatergoods.libs.healthconnect.enums.DataType
import com.greatergoods.libs.healthconnect.enums.HealthConnectPermissionStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectRequestStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectStatus
import com.greatergoods.libs.healthconnect.model.HealthConnectData
import com.greatergoods.libs.healthconnect.model.HealthConnectOptions
import com.greatergoods.libs.healthconnect.model.HealthConnectResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * Implementation of Health Connect service for managing Health Connect integration.
 * This service uses the HealthConnect library's built-in load() functionality for permission handling.
 */
@Singleton
class HealthConnectService @Inject constructor(
  private val context: Context,
  private val healthConnectRepository: IHealthConnectRepository,
  private val accountRepository: IAccountRepository,
  connectivityObserver: IConnectivityObserver,
  dialogQueueService: IDialogQueueService,
  appNavigationService: IAppNavigationService,
  private val entryRepository: IEntryRepository, // Add entry repository for fetching entries
  private val integrationRepository: IIntegrationRepository, // Inject IntegrationRepository for integrations flow
  // Inject IntegrationService for API calls
) : BaseService(connectivityObserver, dialogQueueService, appNavigationService), IHealthConnectService {

  // Core Health Connect components
  private lateinit var healthConnect: HealthConnect
  private lateinit var currentActivity: Activity

  // Service state
  private val tag = "HealthConnectService"
  private var isLoaded = false

  // Global account ID - automatically updated when account changes
  private var currentAccountId: String? = null
  /**
   * Checks if there's an active account.
   * @return true if there's an active account, false otherwise
   */
  private fun hasActiveAccount(): Boolean = currentAccountId != null

  /**
   * Gets the current account ID or throws an exception if none is available.
   * @return The current account ID
   * @throws IllegalStateException if no account is active
   */
  private fun requireCurrentAccountId(): String {
    return currentAccountId ?: throw IllegalStateException("No active account found")
  }

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
  override fun load(activity: ComponentActivity) {
    try {
      AppLog.i(tag, "Initializing Health Connect service...")
      currentActivity = activity
      healthConnect = HealthConnect(activity)
      isLoaded = true
      AppLog.i(tag, "Health Connect service initialized successfully.")
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to initialize Health Connect service", e)
      isLoaded = false
    }
  }

  /**
   * Legacy method for backward compatibility.
   * This now delegates to the new initialize method.
   */
  override fun initializeHealthConnect(activity: ComponentActivity) {
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
      AppLog.e(tag, "Failed to check Health Connect availability", e)
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
      AppLog.e(tag, "Failed to get Health Connect status", e)
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
      AppLog.e(tag, "Failed to check permission status", e)
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
      AppLog.e(tag, "Failed to request authorization", e)
      callback(HealthConnectRequestStatus.CANCELLED)
    }
  }

  /**
   * Opens the Health Connect app or settings.
   */
  override suspend fun openHealthConnect(isFromSetup: Boolean): Boolean {
    return try {
      val activity = currentActivity
      val accountId = requireCurrentAccountId()
      run {
        val result = healthConnect.launchHealthConnect(activity, false)
        if (isFromSetup) {
          healthConnectRepository.setOpen(accountId, true)
        }
        AppLog.i(tag, "Health Connect launch result: $result")
        result
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to open Health Connect", e)
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
        is HealthConnectResult.Success -> {
          AppLog.i(tag, "Successfully revoked Health Connect permissions")
          true
        }

        is HealthConnectResult.Error -> {
          AppLog.e(tag, "Failed to revoke permissions", result.toString())
          false
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Exception while revoking permissions", e)
      false
    }
  }

  override suspend fun checkIfAlreadyUsed(): Boolean {
    return try {
      val currentAccount = accountRepository.getActiveAccount().first()
      if (currentAccount == null) {
        AppLog.w(tag, "No current account found for integration check")
        return false
      }

      val allAccountData = healthConnectRepository.getAccountDataMap()
      val assignedToAccountId = allAccountData.values
        .firstOrNull { it.hasAssignedTo() }
        ?.assignedTo
      val isIntegrated: Boolean = assignedToAccountId == null || assignedToAccountId == currentAccount.id
      AppLog.d(tag, "Health Connect integration check for account ${currentAccount.id}: $isIntegrated")
      isIntegrated
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check Health Connect integration", e)
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
      AppLog.e(tag, "Failed to handle Health Connect intent", e)
    }
  }

  override suspend fun syncAllData(fromOutOfSync: Boolean): Boolean {
    dialogQueueService.showLoader("Syncing...")
    AppLog.i(tag, "Data syncing")

    if (!hasActiveAccount()) {
      AppLog.w(tag, "No active account found")
      dialogQueueService.dismissLoader()
      return false
    }

    val accountId = requireCurrentAccountId()
    try {
      // Check if Health Connect is integrated with current account
      val isIntegrated = checkIfAlreadyUsed()
      if (!isIntegrated) {
        AppLog.w(tag, "Health Connect not integrated with current account")
        dialogQueueService.dismissLoader()
        return false
      }

      val entries: List<Entry> = entryRepository.getEntriesByAccount(accountId)
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
      AppLog.e(tag, "User denied health connect permission or sync failed", e)
      dialogQueueService.dismissLoader()
      dialogQueueService.showDialog(
        DialogModel.Alert(
          title = HealthConnectStrings.dataNotSynced.title,
          message = HealthConnectStrings.dataNotSynced.message,
          dismissText = HealthConnectStrings.ActionButtons.close,
          onDismiss = { dialogQueueService.dismissCurrent() },
        ),
      )
      return false
    } finally {
      dialogQueueService.dismissLoader()
    }
  }

  override suspend fun syncData(entries: List<PeriodBodyScaleSummary>) {
    val finalData = mutableListOf<HealthConnectData>()
    for (entry in entries) {
      entry.weight.let {
        finalData.add(HealthConnectData(DataType.Weight, it, timeStamp = Instant.parse(entry.entryTimestamp)))
      }

      entry.bodyFat.let {
        finalData.add(HealthConnectData(DataType.BodyFat, it, timeStamp = Instant.parse(entry.entryTimestamp)))
      }

      if (entry.bodyFat != null) {
        val leanBodyMass = calculateLeanBodyMass(entry.weight, entry.bodyFat)
        finalData.add(
          HealthConnectData(
            DataType.LeanBodyMass,
            leanBodyMass,
            timeStamp = Instant.parse(entry.entryTimestamp),
          ),
        )
      }
      entry.boneMass.let {
        finalData.add(
          HealthConnectData(
            DataType.BoneMass,
            (it?.times(entry.weight))?.div(100),
            timeStamp = Instant.parse(entry.entryTimestamp),
          ),
        )
      }
      entry.bmr.let {
        finalData.add(
          HealthConnectData(
            DataType.BasalMetabolicRate,
            it,
            timeStamp = Instant.parse(entry.entryTimestamp),
          ),
        )
      }
      entry.pulse.let {
        finalData.add(HealthConnectData(DataType.RestingHeartRate, it, timeStamp = Instant.parse(entry.entryTimestamp)))
      }
    }
    try {
      healthConnect.saveData(finalData)
      // Optionally, set a flag or observable for successful sync
    } catch (e: Exception) {
      AppLog.e(tag, "User denied health connect permission or save failed", e)
      throw e
    }
  }

  // Helper to calculate lean body mass
  private fun calculateLeanBodyMass(weight: Double, bodyFat: Double): Double {
    return weight * (1 - bodyFat / 100)
  }

  override suspend fun deleteEntry(entry: Entry): Boolean {
    return try {
      if (!isLoaded) {
        AppLog.w(tag, "Health Connect service not loaded")
        return false
      }

      // Convert entry to PeriodBodyScaleSummary
      val summary = entry.toPeriodBodyScaleSummary()
      if (summary == null) {
        AppLog.w(tag, "Could not convert entry to PeriodBodyScaleSummary for deletion")
        return false
      }

      // Check if integrated
      val isIntegrated = checkIfAlreadyUsed()
      if (!isIntegrated) {
        AppLog.w(tag, "Health Connect not integrated, skipping deletion")
        return false
      }

      // Build data to delete matching the Angular implementation
      val dataToDelete = mutableListOf<HealthConnectData>()
      val timestamp = Instant.parse(summary.entryTimestamp)

      summary.weight?.let {
        dataToDelete.add(HealthConnectData(DataType.Weight, it, timeStamp = timestamp))
      }
      summary.bodyFat?.let {
        dataToDelete.add(HealthConnectData(DataType.BodyFat, it, timeStamp = timestamp))
      }
      if (summary.muscleMass != null && summary.weight != null) {
        val leanBodyMass = (summary.muscleMass * summary.weight) / 100
        dataToDelete.add(HealthConnectData(DataType.LeanBodyMass, leanBodyMass, timeStamp = timestamp))
      }
      if (summary.boneMass != null && summary.weight != null) {
        val boneMassValue = (summary.boneMass * summary.weight) / 100
        dataToDelete.add(HealthConnectData(DataType.BoneMass, boneMassValue, timeStamp = timestamp))
      }
      summary.bmr?.let {
        dataToDelete.add(HealthConnectData(DataType.BasalMetabolicRate, it, timeStamp = timestamp))
      }
      summary.pulse?.let {
        dataToDelete.add(HealthConnectData(DataType.RestingHeartRate, it, timeStamp = timestamp))
      }

      // Delete data from Health Connect
      val result = healthConnect.deleteEntry(dataToDelete)

      when (result) {
        is HealthConnectResult.Success -> {
          AppLog.i(tag, "Successfully deleted entry from Health Connect")
          true
        }
        is HealthConnectResult.Error -> {
          AppLog.e(tag, "Failed to delete entry from Health Connect", result.toString())
          false
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Exception while deleting entry from Health Connect", e)
      false
    }
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
        is HealthConnectResult.Success -> {
          AppLog.i(tag, "Successfully deleted all Health Connect data")
          true
        }

        is HealthConnectResult.Error -> {
          AppLog.e(tag, "Failed to delete Health Connect data", result.toString())
          false
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Exception while deleting Health Connect data", e)
      false
    }
  }

  override suspend fun turnOnIntegration(fromMultiDevice: Boolean, isRequestNeed: Boolean) {
    try {
      if (!hasActiveAccount()) {
        AppLog.e(tag, "No active account found")
        return
      }

      val accountId = requireCurrentAccountId()
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
      healthConnectRepository.setHcPermissions(accountId, permissionList)
      // Need to check before.
      integrationRepository.updateLocalAccount()
      true
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to turn on integration", e)
      false
    }

    if (fromMultiDevice) {
      return
    } else if (!isRequestNeed) {
      syncWeightHistory()
    } else {
      syncAllData(true)
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
      val accountId = currentAccountId
      if (accountId != null) {
        healthConnectRepository.removeServerHcIntegration(deviceId)
        healthConnectRepository.setHealthConnectIntegrationStatus(accountId, false)
        healthConnectRepository.updateOutOfSync(accountId, false)
        healthConnectRepository.updateAlertSeen(accountId, true)
        delay(500)
        integrationRepository.updateLocalAccount()
      }
      _outOfSyncState.value = false
      dialogQueueService.showToast(Toast(HealthConnectStrings.ToastStrings.removeHC))
      true
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to remove Health Connect integration", e)
      false
    }
  }

  override suspend fun clearHealthConnect(): Boolean {
    return try {
      val accountId = currentAccountId

      if (accountId == null) {
        AppLog.w(tag, "No active account found")
        return false
      }
      val deviceId = getDeviceId()
      val removingIntegrationData = IntegratedDeviceInfo(
        operationType = IntegrationOperationType.REMOVE.value,
        scopes = IntegrationData(
          deviceId = deviceId,
          type = IntegrationType.HEALTH_CONNECT.value,
        ),
        isCurrentDeviceDeleted = true,
      )
      healthConnectRepository.setStoredIntegrationData(accountId, removingIntegrationData)
      try {
        revokePermission()
        AppLog.i(tag, "Successfully revoked Health Connect permissions")
      } catch (e: Exception) {
        AppLog.w(tag, "Failed to revoke permissions: ${e.message}")
      }
      setHealthConnectIntegrationStatus(accountId, false)
      healthConnectRepository.updateAlertSeen(accountId, false)
      healthConnectRepository.updateOutOfSync(accountId, false)
      healthConnectRepository.updateModalState(accountId, false)
      healthConnectRepository.setOpen(accountId, false)
      healthConnectRepository.removeAccount(accountId)
      deleteAllData()
      AppLog.i(tag, "Health Connect cleared successfully")
      true
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to clear Health Connect", e)
      false
    }
  }

  override suspend fun checkPermissionChange() {
    try {
      val accountId = currentAccountId

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
      } else {
        AppLog.i(tag, "Permissions found, checking for changes")
        if (storedGrantedPermissions.toSet() != currentGrantedPermissions.toSet()) {
          AppLog.i(tag, "Permission changes detected, updating integration")
          healthConnectRepository.setHcPermissions(accountId, currentGrantedPermissions)
        }
        turnOnIntegration(fromMultiDevice = true, isRequestNeed = true)
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check permission changes", e)
    }
  }

  override suspend fun checkMultiDeviceConnection(isPermissionEnabled: Boolean, isIntegrated: Boolean): Boolean {
    return try {
      val accountId = currentAccountId

      if (accountId == null) {
        AppLog.w(tag, "No active account found")
        return false
      }
      val allHealthConnectData = healthConnectRepository.getAccountDataMap()
      val integratedAccounts = allHealthConnectData.values.filter { it.integrated }
      val isMultipleDeviceConnected = (integratedAccounts.size > 1 ||
        (integratedAccounts.isEmpty() && integratedAccounts.none {
          allHealthConnectData.entries.firstOrNull { entry -> entry.value == it }?.key == accountId
        })) && isIntegrated

      if (isMultipleDeviceConnected) {
        AppLog.i(tag, "Multiple device connection detected")
        healthConnectRepository.setHealthConnectIntegrationStatus(accountId, true)
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
      AppLog.e(tag, "Failed to check multiple device connection", e)
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
      AppLog.e(tag, "Failed to get approved permission list", e)
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
          dialogQueueService.showLoader(HealthConnectStrings.Loader.loading)
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

  override suspend fun healthConnectOutOfSync(): Boolean {
    return try {
      val accountId = currentAccountId
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
                _outOfSyncState.value = false
                healthConnectRepository.updateOutOfSync(accountId, false)
                val isModalDismissed = healthConnectData.modalState
                if (!isModalDismissed) {
                  dialogQueueService.showDialog(
                    DialogModel.Custom(
                      contentKey = DialogType.FinishConnect,
                      onConfirm = {
                        CoroutineScope(Dispatchers.IO).launch {
                          appNavigationService.navigateTo(AppRoute.Integration.IntegrationList)
                          appNavigationService.navigateTo(AppRoute.Integration.HealthConnect)
                          dialogQueueService.dismissCurrent()
                        }
                      },
                      onDismiss = {
                        dialogQueueService.dismissCurrent()
                      },
                    ),
                  )
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
      AppLog.e(tag, "Failed to check Health Connect out of sync status", e)
      false
    }
  }

  /**
   * Checks Health Connect permission and shows the appropriate modal if needed.
   * This matches the Angular checkHealthConnectPermissionDisabled logic.
   */
  override suspend fun checkHealthConnectPermissionDisabled() {
    val healthConnectStatus = healthConnectStatus()
    val accountId = currentAccountId ?: return
    val healthConnectData = healthConnectRepository.getAccountByID(accountId)
    val isHealthConnectOpened = healthConnectData?.open ?: false
    val outOfSyncSession = healthConnectData?.outOfSync ?: false
    val isIntegrationCancelled = healthConnectData?.alertSeen ?: false
    val isLocallyIntegrated = healthConnectData?.integrated ?: false
    val isIntegrated = integrationRepository.integrations.first()
    val isAlreadyConnected = try {
      checkIfAlreadyUsed()
    } catch (e: Exception) {
      false
    }

    if (!isAlreadyConnected) {
      _outOfSyncState.value = outOfSyncSession
      return
    }
    val permissionStatus = checkPermissionStatus()
    // val integrationsFromServer = isIntegrated // Placeholder for server check
    val shouldShowPopup = true // You can add your own logic for this
    if (isLocallyIntegrated) {
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
    // Multi-device connection check
    if ((permissionStatus == HealthConnectPermissionStatus.NONE ||
        permissionStatus == HealthConnectPermissionStatus.PARTIAL ||
        permissionStatus == HealthConnectPermissionStatus.ALL) && isIntegrated?.isHealthConnectOn == true && !isLocallyIntegrated
    ) {
      val isConnect = permissionStatus == HealthConnectPermissionStatus.PARTIAL ||
        permissionStatus == HealthConnectPermissionStatus.ALL
      val isMultiDeviceConnected = checkMultiDeviceConnection(isConnect, true)
      if (isMultiDeviceConnected) {
        return
      }
    }
    // Out of sync modal
    if (permissionStatus == HealthConnectPermissionStatus.NONE && isIntegrated?.isHealthConnectOn == true && !outOfSyncSession) {
      healthConnectRepository.updateOutOfSync(accountId, true)
      healthConnectRepository.updateModalState(accountId, true)
      _outOfSyncState.value = true // Set observable for out of sync
      if (shouldShowPopup) {
        dialogQueueService.showDialog(
          DialogModel.Custom(
            contentKey = DialogType.OutOfSyncModal,
            params =
              mapOf(
                "secondaryAction" to {
                  CoroutineScope(Dispatchers.IO).launch {
                    dialogQueueService.showLoader(HealthConnectStrings.Loader.loading)
                    removeHealthConnectIntegration()
                    healthConnectRepository.updateModalState(accountId, true)
                    dialogQueueService.dismissCurrent()
                    dialogQueueService.dismissLoader()
                  }
                },
              ),
            onConfirm = {
              dialogQueueService.dismissCurrent()
              CoroutineScope(Dispatchers.IO).launch {
                openHealthConnect()
                healthConnectRepository.setHealthConnectIntegrationStatus(accountId, true)
                // On confirm, you may want to reset out-of-sync state if permissions are restored
                healthConnectRepository.updateOutOfSync(accountId, true)
                healthConnectRepository.updateModalState(accountId, true)
                _outOfSyncState.value = true
              }
            },
            onDismiss = {
              CoroutineScope(Dispatchers.IO).launch {
                healthConnectRepository.setHealthConnectIntegrationStatus(accountId, true)
                healthConnectRepository.updateOutOfSync(accountId, true)
                healthConnectRepository.updateModalState(accountId, false)
                _outOfSyncState.value = true
              }
            },
          ),
        )
        healthConnectRepository.updateModalState(accountId, true)
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
      if (isIntegrated?.isHealthConnectOn == false && !isIntegrationCancelled && !isHealthConnectOpened) {
        if (shouldShowPopup) {
          dialogQueueService.showDialog(
            DialogModel.Custom(
              contentKey = DialogType.FinishConnect,
              onConfirm = {
                CoroutineScope(Dispatchers.IO).launch {
                  appNavigationService.navigateTo(AppRoute.Integration.IntegrationList)
                  appNavigationService.navigateTo(AppRoute.Integration.HealthConnect)
                  dialogQueueService.dismissCurrent()
                }
              },
              onDismiss = {
                CoroutineScope(Dispatchers.IO).launch {
                  healthConnectRepository.updateAlertSeen(accountId, true)
                  dialogQueueService.dismissCurrent()
                }
              },
            ),
          )
        }
        return
      } else if (isHealthConnectOpened) {
        // from integration failed
        if (shouldShowPopup) {
          dialogQueueService.showDialog(
            DialogModel.Custom(
              contentKey = DialogType.FinishConnect,
              onConfirm = {
                CoroutineScope(Dispatchers.IO).launch {
                  healthConnectRepository.setOpen(accountId, false)
                  appNavigationService.navigateTo(AppRoute.Integration.IntegrationList)
                  appNavigationService.navigateTo(AppRoute.Integration.HealthConnect)
                  dialogQueueService.dismissCurrent()
                }
              },
              onDismiss = {
                CoroutineScope(Dispatchers.IO).launch {
                  healthConnectRepository.setOpen(accountId, false)
                }
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
    if (healthConnectStatus == HealthConnectStatus.INSTALL_REQUIRED && isIntegrated?.isHealthConnectOn == true) {
      dialogQueueService.enqueue(
        DialogModel.Alert(
          title = HealthConnectStrings.NotAvailable.header,
          message = HealthConnectStrings.NotAvailable.message,
          dismissText = HealthConnectStrings.ActionButtons.close,
          onDismiss = {
            dialogQueueService.dismissCurrent()
          },
        ),
      )
    }
  }

  suspend fun setHealthConnectIntegrationStatus(accountId: String, integrated: Boolean) {
    try {
              AppLog.v(tag, "Setting Health Connect integration status: $integrated for account: $accountId")

      if (integrated) {
        // Set integration status to true
        healthConnectRepository.setHealthConnectIntegrationStatus(accountId, true)
        // Set assignedTo to current account ID when integrating
        healthConnectRepository.setAssignedTo(accountId, accountId)
        AppLog.v(tag, "Health Connect assigned to account: $accountId")
      } else {
        // Check if this account is currently integrated
        val currentData = healthConnectRepository.getAccountByID(accountId)
        val assignedAccountID = currentData?.assignedTo
        if (assignedAccountID == accountId) {
          // Set integration status to false
          healthConnectRepository.setHcIntegrationStatus(accountId, false)
          // Clear assignedTo when disintegrating
          healthConnectRepository.clearAssignedTo(accountId)
          AppLog.v(tag, "Health Connect disintegrated from account: $accountId")
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to set Health Connect integration status", e)
      throw e
    }
  }
}
