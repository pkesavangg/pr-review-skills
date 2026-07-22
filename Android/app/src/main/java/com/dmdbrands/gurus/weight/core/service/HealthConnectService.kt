package com.dmdbrands.gurus.weight.core.service

import androidx.activity.ComponentActivity
import com.dmdbrands.gurus.weight.core.di.ApplicationScope
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
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.repository.IIntegrationRepository
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.integration.model.Integrations
import com.dmdbrands.gurus.weight.features.integration.strings.HealthConnectStrings
import com.greatergoods.libs.healthconnect.HealthConnect
import com.greatergoods.libs.healthconnect.enums.DataType
import com.greatergoods.libs.healthconnect.enums.HealthConnectPermissionStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectRequestStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectStatus
import com.greatergoods.libs.healthconnect.model.HealthConnectOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * Implementation of Health Connect service for managing Health Connect integration.
 * This service uses the HealthConnect library's built-in load() functionality for permission handling.
 *
 * Thin coordinator (MOB-1500): the availability/permission wrappers, the sync read-write engine,
 * and the out-of-sync / permission-disabled orchestration live in [HealthConnectPermissionManager],
 * [HealthConnectSyncManager] and [HealthConnectOutOfSyncManager] respectively. This class keeps the
 * shared state, the integration lifecycle, and the [IHealthConnectService] contract (delegating the
 * moved slices to the collaborators).
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
  @ApplicationScope private val appScope: CoroutineScope,
) : BaseService(connectivityObserver, dialogQueueService, appNavigationService), IHealthConnectService {

  // Core Health Connect components
  private lateinit var healthConnect: HealthConnect
  private lateinit var currentActivity: Activity
  private var currentIntegrations: Integrations? = null

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
      DataType.BloodPressure,
    ),
  )

  // Extracted collaborators (MOB-1500). Instantiated internally so the Hilt module is untouched;
  // shared mutable state is passed via getters/setters, cross-cutting operations via callbacks.
  private val permissionManager: HealthConnectPermissionManager = HealthConnectPermissionManager(
    getHealthConnect = { healthConnect },
    getCurrentActivity = { currentActivity },
    getIsLoaded = { isLoaded },
    requireCurrentAccountId = { requireCurrentAccountId() },
    requestingPermissions = requestingPermissions,
    healthConnectRepository = healthConnectRepository,
  )

  private val syncManager: HealthConnectSyncManager = HealthConnectSyncManager(
    getHealthConnect = { healthConnect },
    getIsLoaded = { isLoaded },
    requestingPermissions = requestingPermissions,
    hasActiveAccount = { hasActiveAccount() },
    requireCurrentAccountId = { requireCurrentAccountId() },
    checkIfAlreadyUsed = { checkIfAlreadyUsed() },
    dialogQueueService = dialogQueueService,
    entryRepository = entryRepository,
    appScope = appScope,
  )

  private val outOfSyncManager: HealthConnectOutOfSyncManager = HealthConnectOutOfSyncManager(
    getCurrentAccountId = { currentAccountId },
    getCurrentIntegrations = { currentIntegrations },
    healthConnectStatus = { healthConnectStatus() },
    checkPermissionStatus = { checkPermissionStatus() },
    checkIfAlreadyUsed = { checkIfAlreadyUsed() },
    checkPermissionChange = { checkPermissionChange() },
    turnOnIntegration = { fromMultiDevice, isRequestNeed -> turnOnIntegration(fromMultiDevice, isRequestNeed) },
    checkMultiDeviceConnection = { isPermissionEnabled -> checkMultiDeviceConnection(isPermissionEnabled) },
    removeHealthConnectIntegration = { removeHealthConnectIntegration() },
    openHealthConnect = { openHealthConnect() },
    setOutOfSyncState = { _outOfSyncState.value = it },
    healthConnectRepository = healthConnectRepository,
    integrationRepository = integrationRepository,
    dialogQueueService = dialogQueueService,
    appNavigationService = appNavigationService,
    appScope = appScope,
  )

  init {

    appScope.launch {
      AppLog.d(tag, "Health connect service gets initialised")
      accountRepository.getActiveAccount().collect { it ->
        currentAccountId = it?.id
      }
    }
  }

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

  // ---------------------------------------------------------------------------
  // Availability / permission wrappers — delegated to HealthConnectPermissionManager
  // ---------------------------------------------------------------------------

  override fun handleOnNewIntent(intent: Intent?) = permissionManager.handleOnNewIntent(intent)

  override suspend fun checkAvailability(): Boolean = permissionManager.checkAvailability()

  override suspend fun healthConnectStatus(): HealthConnectStatus = permissionManager.healthConnectStatus()

  override suspend fun checkPermissionStatus(): HealthConnectPermissionStatus =
    permissionManager.checkPermissionStatus()

  override suspend fun requestAuthorization(callback: (HealthConnectRequestStatus) -> Unit) =
    permissionManager.requestAuthorization(callback)

  override suspend fun openHealthConnect(isFromSetup: Boolean): Boolean =
    permissionManager.openHealthConnect(isFromSetup)

  override suspend fun revokePermission(): Boolean = permissionManager.revokePermission()

  override suspend fun getApprovedPermissionList(): List<String> = permissionManager.getApprovedPermissionList()

  // ---------------------------------------------------------------------------
  // Sync / read-write engine — delegated to HealthConnectSyncManager
  // ---------------------------------------------------------------------------

  override suspend fun syncAllData(fromOutOfSync: Boolean): Boolean = syncManager.syncAllData(fromOutOfSync)

  override suspend fun syncData(entries: List<PeriodBodyScaleSummary>) = syncManager.syncData(entries)

  override suspend fun syncEntries(entries: List<Entry>) = syncManager.syncEntries(entries)

  override suspend fun deleteEntry(entry: Entry): Boolean = syncManager.deleteEntry(entry)

  override suspend fun deleteAllData(): Boolean = syncManager.deleteAllData()

  override fun syncWeightHistory() = syncManager.syncWeightHistory()

  // ---------------------------------------------------------------------------
  // Out-of-sync / permission-disabled orchestration — delegated to HealthConnectOutOfSyncManager
  // ---------------------------------------------------------------------------

  override suspend fun healthConnectOutOfSync(): Boolean = outOfSyncManager.healthConnectOutOfSync()

  override suspend fun checkHealthConnectPermissionDisabled() =
    outOfSyncManager.checkHealthConnectPermissionDisabled()

  // ---------------------------------------------------------------------------
  // Integration lifecycle / multi-device (retained on the coordinator)
  // ---------------------------------------------------------------------------

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
      val isIntegrated: Boolean = assignedToAccountId == null || assignedToAccountId.isEmpty() || assignedToAccountId == currentAccount.id
      AppLog.d(tag, "Health Connect integration check for account ${currentAccount.id}: $isIntegrated")
      isIntegrated
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check Health Connect integration", e)
      false
    }
  }

  /**
   * Checks if Health Connect is integrated with the current account.
   * Compares the assignedTo value with the current account ID.
   *
   * @return true if Health Connect is assigned to the current account, false otherwise
   */
  override suspend fun checkIntegrated(): Boolean {
    return try {
      val accountId = currentAccountId
      if (accountId == null) {
        AppLog.w(tag, "No active account found for integration check")
        return false
      }

      val allAccountData = healthConnectRepository.getAccountDataMap()
      val assignedToAccountId = allAccountData.values
        .firstOrNull { it.hasAssignedTo() }
        ?.assignedTo

      val isIntegrated = assignedToAccountId == accountId
      AppLog.d(tag, "Health Connect integrated check for account $accountId: $isIntegrated")
      return isIntegrated
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check Health Connect integrated status", e)
      false
    }
  }

  override suspend fun turnOnIntegration(fromMultiDevice: Boolean, isRequestNeed: Boolean) {
    try {
      val accountId = requireCurrentAccountId()
      val permissionList = getApprovedPermissionList()
      val deviceId = getDeviceId()
      val integration = IntegrationData(
        deviceId = deviceId,
        type = IntegrationType.HEALTH_CONNECT.value,
        preferences = IntegrationPreferences(scopes = permissionList),
      )
      healthConnectRepository.saveIntegration(integration)
      healthConnectRepository.setHealthConnectIntegrationStatus(accountId, true)
      integrationRepository.updateHealthConnectIntegrationOffline(true)
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
      syncAllData(isRequestNeed)
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
        deleteAllData()
        integrationRepository.updateHealthConnectIntegrationOffline(false)
        healthConnectRepository.removeServerHcIntegration(deviceId)
        healthConnectRepository.setHealthConnectIntegrationStatus(accountId, false)
        healthConnectRepository.updateOutOfSync(accountId, false)
        healthConnectRepository.updateAlertSeen(accountId, true)
        delay(500)
        integrationRepository.updateLocalAccount()
      }
      _outOfSyncState.value = false
      dialogQueueService.showToast(Toast.Simple(HealthConnectStrings.ToastStrings.removeHC))
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
      healthConnectRepository.setHealthConnectIntegrationStatus(accountId, false)
      healthConnectRepository.updateAlertSeen(accountId, false)
      healthConnectRepository.updateOutOfSync(accountId, false)
      healthConnectRepository.updateModalState(accountId, false)
      healthConnectRepository.setOpen(accountId, false)
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

  /**
   * Checks for multiple device connections.
   * Similar to Angular's checkMultiDeviceConnection implementation.
   * First checks if multiple device IDs exist using checkMultipleDeviceIds.
   * If true, sets up the integration and handles out-of-sync state.
   *
   * @param isPermissionEnabled Whether permissions are currently enabled
   * @return true if multiple device connection detected and handled, false otherwise
   */
  override suspend fun checkMultiDeviceConnection(isPermissionEnabled: Boolean): Boolean {
    return try {
      val accountId = currentAccountId

      if (accountId == null) {
        AppLog.w(tag, "No active account found")
        return false
      }

      // Check if multiple device IDs exist (similar to Angular)
      val isMultipleDeviceIds = checkMultipleDeviceIds(IntegrationType.HEALTH_CONNECT)
      if (isMultipleDeviceIds) {
        AppLog.i(tag, "Multiple device IDs detected - setting up integration")

        // Set Health Connect integration status to true
        integrationRepository.updateHealthConnectIntegrationOffline(true)
        healthConnectRepository.setHealthConnectIntegrationStatus(accountId, true)
        dialogQueueService.showDialog(
          DialogModel.Custom(
            contentKey = DialogType.MultipleDeviceConnection,
            onConfirm = {
              dialogQueueService.dismissCurrent()
              onConfirmMultiDevice(accountId)
            },
            onDismiss = {
              onCancelMultiDevice(accountId)
            },
          ),
        )
        // Set out of sync state if permissions are not enabled
        if (!isPermissionEnabled) {
          _outOfSyncState.value = true
          healthConnectRepository.updateOutOfSync(accountId, true)
        }

        // Turn on integration for multi-device scenario
        turnOnIntegration(fromMultiDevice = true, isRequestNeed = true)
        return true
      }

      // Return the result (false if no multiple device IDs detected)
      return isMultipleDeviceIds
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check multiple device connection", e)
      false
    }
  }

  private fun onConfirmMultiDevice(accountId: String){
    appScope.launch(Dispatchers.Main) {
      appNavigationService.navigateTo(AppRoute.Integration.IntegrationList)
      appNavigationService.navigateTo(AppRoute.Integration.HealthConnect)
      healthConnectRepository.updateOutOfSync(accountId, false)
      healthConnectRepository.updateModalState(accountId, true)
    }
  }

  private fun onCancelMultiDevice(accountId: String){
    appScope.launch(Dispatchers.Main) {
      healthConnectRepository.setHealthConnectIntegrationStatus(accountId, true)
      healthConnectRepository.updateOutOfSync(accountId, true)
      healthConnectRepository.updateModalState(accountId, true)
      _outOfSyncState.value = true
    }
  }

  /**
   * Checks if multiple device IDs exist for the given integration type.
   * Similar to Angular's checkMultipleDeviceIds implementation.
   * Returns true if stored integration data is null/undefined and the integration is on.
   *
   * @param integrations The integration type to check (HEALTH_CONNECT or HEALTH_KIT)
   * @return true if stored integration is null and integration is on, false otherwise
   */
  override suspend fun checkMultipleDeviceIds(integrations: IntegrationType): Boolean {
    return try {
      val accountId = currentAccountId
      if (accountId == null) {
        AppLog.w(tag, "No active account found for checkMultipleDeviceIds")
        return false
      }

      // Get stored integration data (local: no deviceId = this device hasn't registered yet)
      val localIntegration = healthConnectRepository.getStoredIntegrationData(accountId)
      // Get integration status from server
      val integrationsFromServer = integrationRepository.integrationsFromServer.first()
      val isIntegrationOn =  integrationsFromServer?.isHealthConnectOn ?: false
      AppLog.d(tag, "checkMultipleDeviceIds for $integrations: storedIntegration=${localIntegration}")
      // Return true if stored integration is null AND integration is on
      val result = ( localIntegration == null || localIntegration.scopes.deviceId.isEmpty()) && isIntegrationOn
      AppLog.d(tag, "checkMultipleDeviceIds for $integrations: storedIntegration=${localIntegration}, isIntegrationOn=$isIntegrationOn, result=$result")
      result
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to check multiple device IDs for $integrations", e)
      false
    }
  }
}
