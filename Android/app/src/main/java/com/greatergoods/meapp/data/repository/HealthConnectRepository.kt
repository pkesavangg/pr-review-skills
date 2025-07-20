package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.api.HealthConnectIntegrationRequest
import com.greatergoods.meapp.data.api.IHealthConnectAPI
import com.greatergoods.meapp.data.storage.datastore.HealthConnectData
import com.greatergoods.meapp.data.storage.datastore.HealthConnectDataStore
import com.greatergoods.meapp.data.storage.datastore.ProtoIntegratedDeviceInfo
import com.greatergoods.meapp.data.storage.datastore.ProtoIntegrationOperationType
import com.greatergoods.meapp.domain.model.integrations.IntegratedDeviceInfo
import com.greatergoods.meapp.domain.model.integrations.IntegrationData
import com.greatergoods.meapp.domain.model.integrations.IntegrationOperationType
import com.greatergoods.meapp.domain.model.integrations.IntegrationPreferences
import com.greatergoods.meapp.domain.model.integrations.IntegrationType
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.repository.IHealthConnectRepository
import com.greatergoods.meapp.features.integration.model.IntegrationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectRepository @Inject constructor(
  private val accountRepository: IAccountRepository,
  private val healthConnectAPI: IHealthConnectAPI,
  private val healthConnectDataStore: HealthConnectDataStore,
) : IHealthConnectRepository {

  private val tag = "HealthConnectRepository"

  // Initial state (replace with your actual model)
  private val _integrationState = MutableStateFlow(IntegrationState())
  override val integrationState: StateFlow<IntegrationState> = _integrationState

  /**
   * Updates the integration state and optionally persists to local storage.
   * This method should be called whenever the integration state changes.
   */
  override fun updateIntegrationState(newState: IntegrationState) {
    _integrationState.value = newState
    AppLog.d(tag, "Integration state updated: ${newState.integrations.size} integrations")
  }

  /**
   * Updates the integration state from local storage data for the current account.
   * This method should be called to sync the UI state with local storage.
   */
  override suspend fun updateIntegrationStateFromLocalStorage() {
    try {
      val currentAccount = accountRepository.getActiveAccount().first()
      if (currentAccount != null) {
        val healthConnectData = getAccountByID(currentAccount.id)
        val isHealthConnectOn = healthConnectData?.integrated == true
        val isOutOfSync = healthConnectData?.outOfSync == true
        // Update the integration state with local storage data
        val currentState = _integrationState.value
        val updatedIntegrations = currentState.integrations.map { integration ->
          if (integration.provider == com.greatergoods.meapp.domain.model.api.integration.IntegrationProvider.HealthConnect) {
            integration.copy(
              isConnected = isHealthConnectOn,
            )
          } else {
            integration
          }
        }
        updateIntegrationState(currentState.copy(integrations = updatedIntegrations))
        AppLog.d(tag, "Integration state updated from local storage - HealthConnect: connected=$isHealthConnectOn, outOfSync=$isOutOfSync")
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to update integration state from local storage", e.toString())
    }
  }

  /**
   * Updates the Health Connect integration status in local storage and syncs the state.
   */
  override suspend fun updateHealthConnectIntegrationStatus(accountId: String, integrated: Boolean) {
    try {
      setHcIntegrationStatus(accountId, integrated)
      updateIntegrationStateFromLocalStorage()
      AppLog.d(tag, "Health Connect integration status updated: $integrated for account: $accountId")
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to update Health Connect integration status", e.toString())
    }
  }

  /**
   * Updates the out of sync status in local storage and syncs the state.
   */
  override suspend fun updateOutOfSyncStatus(accountId: String, outOfSync: Boolean) {
    try {
      updateOutOfSync(accountId, outOfSync)
      updateIntegrationStateFromLocalStorage()
      AppLog.d(tag, "Out of sync status updated: $outOfSync for account: $accountId")
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to update out of sync status", e.toString())
    }
  }

  /**
   * Observes account changes and automatically updates integration state from local storage.
   * This method should be called to start automatic state synchronization.
   */
  override suspend fun observeAccountChanges() {
    try {
      accountRepository.getActiveAccount().collect { account ->
        if (account != null) {
          updateIntegrationStateFromLocalStorage()
          AppLog.d(tag, "Account changed, updated integration state for account: ${account.id}")
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to observe account changes", e.toString())
    }
  }

  override suspend fun getAccountDataMap(): Map<String, HealthConnectData> =
    healthConnectDataStore.healthConnectData()

  override suspend fun addAccount(accountId: String, data: HealthConnectData) {
    healthConnectDataStore.setHealthConnectData(accountId, data)
  }

  override suspend fun removeAccount(accountId: String) {
    healthConnectDataStore.removeHealthConnectData(accountId)
  }

  override suspend fun clearData() {
    healthConnectDataStore.clearData()
  }

  override suspend fun getAccountByID(accountId: String): HealthConnectData? =
    healthConnectDataStore.getHealthConnectData(accountId)

  override suspend fun hasAccountData(accountId: String): Boolean =
    healthConnectDataStore.hasHealthConnectData(accountId)

  override fun getHealthConnectDataFlow(accountId: String): Flow<HealthConnectData?> =
    healthConnectDataStore.getHealthConnectDataFlow(accountId)

  override suspend fun setHcIntegrationStatus(accountId: String, integrated: Boolean) {
    healthConnectDataStore.setHcIntegrationStatus(accountId, integrated)
  }

  override suspend fun updateAlertSeen(accountId: String, seen: Boolean) {
    healthConnectDataStore.updateAlertSeen(accountId, seen)
  }

  /**
   * Updates the out of sync status for an account.
   */
  override suspend fun updateOutOfSync(accountId: String, outOfSync: Boolean) {
    healthConnectDataStore.updateOutOfSync(accountId, outOfSync)
  }

  /**
   * Updates the open status for an account.
   */
  override suspend fun setOpen(accountId: String, open: Boolean) {
    healthConnectDataStore.setOpen(accountId, open)
  }

  /**
   * Gets the open status for an account.
   */
  override suspend fun getOpen(accountId: String): Boolean {
    return healthConnectDataStore.getOpen(accountId)
  }

  /**
   * Updates the modal state for an account.
   */
  override suspend fun updateModalState(accountId: String, state: Boolean) {
    healthConnectDataStore.updateModalState(accountId, state)
  }

  override suspend fun updateTimestamp(accountId: String, timestamp: String) {
    healthConnectDataStore.updateTimestamp(accountId, timestamp)
  }

  override suspend fun updateIntegrationTimestamp(accountId: String, timestamp: String) {
    healthConnectDataStore.updateIntegrationTimestamp(accountId, timestamp)
  }

  override suspend fun setHcPermissions(accountId: String, permissions: List<String>) {
    healthConnectDataStore.setPermissions(accountId, permissions)
  }

  /**
   * Saves integration data both locally and to server.
   * Handles offline storage and server sync.
   */
  override suspend fun saveIntegration(integrationData: IntegrationData) {
    // Get active account ID from AccountRepository
    val accountId = try {
      accountRepository.getActiveAccount().first()?.id ?: run {
        throw Exception("No active account found for integration")
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to get active account: ${e.message}")
      return
    }
    try {
      val request = HealthConnectIntegrationRequest(
        deviceId = integrationData.deviceId,
        preferences = integrationData.preferences,
        type = integrationData.type,
      )
      healthConnectAPI.saveIntegration(request)
      healthConnectDataStore.setIntegrationInfo(
        accountId,
        IntegratedDeviceInfo(
          operationType = IntegrationOperationType.SAVE.value,
          scopes = integrationData,
          isCurrentDeviceDeleted = false
        ),
      )
    } catch (e: Exception) {
      syncIntegration(IntegratedDeviceInfo(
        operationType = IntegrationOperationType.SAVE.value,
        scopes = integrationData,
        isCurrentDeviceDeleted = false
      ))
      AppLog.e(tag, "Failed to save integration: ${e.message}")
    }
  }

  /**
   * Syncs integration data between local storage and server.
   */
  override suspend fun syncIntegration(integrationInfo: IntegratedDeviceInfo?) {
    val accountId = try {
      accountRepository.getActiveAccount().first()?.id ?: run {
        AppLog.e(tag, "No active account found for sync")
        return
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to get active account for sync: ${e.message}")
      return
    }
    val storedIntegrationInfo = integrationInfo ?: getStoredIntegrationData(accountId)
    try {
      if(storedIntegrationInfo != null){
        when (storedIntegrationInfo.operationType) {
          IntegrationOperationType.SAVE.value -> {
            val request = HealthConnectIntegrationRequest(
              deviceId = storedIntegrationInfo.scopes.deviceId,
              preferences = storedIntegrationInfo.scopes.preferences,
              type = storedIntegrationInfo.scopes.type,
            )
            healthConnectAPI.saveIntegration(request)
            healthConnectDataStore.setHcIntegrationStatus(accountId, true)
            AppLog.d(tag, "Integration synced successfully for account: $accountId")
          }

          IntegrationOperationType.REMOVE.value -> {
            healthConnectAPI.removeIntegration(accountId)
            healthConnectDataStore.updateOutOfSync(accountId, false)
            healthConnectDataStore.setHcIntegrationStatus(accountId, false)
            healthConnectDataStore.setIntegrationInfo(accountId, null)
            AppLog.d(tag, "Integration removal synced successfully for account: $accountId")
          }
        }
      }

    } catch (e: Exception) {
      AppLog.e(tag, "Failed to sync integration for account $accountId: ${e.message}")
    }
  }

  /**
   * Helper function to convert proto to domain model.
   */
  private fun ProtoIntegratedDeviceInfo.toDomain(): IntegratedDeviceInfo {
    val operationType = when(this.operationType){
      ProtoIntegrationOperationType.PROTO_SAVE -> IntegrationOperationType.SAVE
      ProtoIntegrationOperationType.PROTO_REMOVE -> IntegrationOperationType.REMOVE
      else -> IntegrationOperationType.SAVE
    }
    return IntegratedDeviceInfo(
      operationType = operationType.value,
      scopes = IntegrationData(
        deviceId = scopes.deviceId,
        type = IntegrationType.HEALTH_CONNECT.value,
        preferences = IntegrationPreferences(scopes = scopes.scopesList),
      ),
    )
  }

  override suspend fun removeServerHcIntegration(deviceId: String) {
    val account = accountRepository.getActiveAccount().first()
    if (account == null) {
      return
    }
    val currentData = getAccountByID(account.id)
    if(currentData?.integrationInfo == null){
        return
    }
    val domainInfo = currentData.integrationInfo.toDomain()
    try {
      val isCurrentDeviceIntegrated = getStoredIntegrationData(account.id)
      if (isCurrentDeviceIntegrated !== null) {
        healthConnectAPI.removeIntegration(deviceId)
        if (currentData.integrationInfo != null) {
          healthConnectDataStore.setIntegrationInfo(
            account.id,
            IntegratedDeviceInfo(
              operationType = IntegrationOperationType.REMOVE.value,
              scopes = domainInfo.scopes,
              isCurrentDeviceDeleted = true,
            ),
          )
        }
      }
    } catch (e: Exception) {
      healthConnectDataStore.setIntegrationInfo(
        account.id,
        IntegratedDeviceInfo(
          operationType = IntegrationOperationType.REMOVE.value,
          scopes = domainInfo.scopes,
          isCurrentDeviceDeleted = false,
        ),
      )
    }
  }

  /**
   * Stores integration data locally for offline handling.
   * Similar to Angular's setStoredIntegrationData.
   */
  override suspend fun setStoredIntegrationData(accountId: String, integrationInfo: IntegratedDeviceInfo?) {
    try {
      if (integrationInfo == null) {
        AppLog.i(tag, "Clearing stored integration data for account: $accountId")
        removeAccount(accountId)
      } else {
        AppLog.i(tag, "Storing integration data for account: $accountId")
        val healthConnectData = getAccountByID(accountId)
        val updatedData = healthConnectData?.toBuilder()
          ?.setIntegrated(integrationInfo.operationType == IntegrationOperationType.SAVE.value)
          ?.setUpdatedAt(System.currentTimeMillis().toString())
          ?.build()
          ?: HealthConnectData.newBuilder()
            .setIntegrated(integrationInfo.operationType == IntegrationOperationType.SAVE.value)
            .setUpdatedAt(System.currentTimeMillis().toString())
            .build()

        addAccount(accountId, updatedData)
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to store integration data", e.toString())
    }
  }

  /**
   * Retrieves stored integration data from local storage.
   * Similar to Angular's getStoredIntegrationData.
   */
  override suspend fun getStoredIntegrationData(accountId: String): IntegratedDeviceInfo? {
    return try {
      val healthConnectData = getAccountByID(accountId)
      if (healthConnectData?.integrated == true) {
        // Reconstruct IntegratedDeviceInfo from stored data
        val currentAccount = accountRepository.getActiveAccount().first()
        val deviceId = currentAccount?.id ?: ""

        IntegratedDeviceInfo(
          operationType = IntegrationOperationType.SAVE.value,
          scopes = IntegrationData(
            deviceId = deviceId,
            type = IntegrationType.HEALTH_CONNECT.value,
            preferences = IntegrationPreferences(
              scopes = healthConnectData.grantedPermissionList,
            ),
          ),
          isCurrentDeviceDeleted = false,
        )
      } else {
        null
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to get stored integration data", e.toString())
      null
    }
  }

  /**
   * Sets the Health Connect integration status for the current account.
   * Similar to Angular's setHealthConnectIntegrationStatus.
   */
  override suspend fun setHealthConnectIntegrationStatus(accountId: String, integrated: Boolean) {
    try {
      AppLog.i(tag, "Setting Health Connect integration status: $integrated for account: $accountId")
      if (integrated) {
        setHcIntegrationStatus(accountId, true)
      } else {
        val healthConnectData = getAccountByID(accountId)
        if (healthConnectData?.integrated == true) {
          setHcIntegrationStatus(accountId, false)
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to set Health Connect integration status", e.toString())
    }
  }
}
