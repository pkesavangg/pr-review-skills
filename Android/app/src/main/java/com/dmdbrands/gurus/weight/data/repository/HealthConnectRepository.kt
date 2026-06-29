package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.HealthConnectIntegrationRequest
import com.dmdbrands.gurus.weight.data.api.HealthConnectSyncEntry
import com.dmdbrands.gurus.weight.data.api.IHealthConnectAPI
import com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectData
import com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectDataStore
import com.dmdbrands.gurus.weight.data.storage.datastore.ProtoIntegratedDeviceInfo
import com.dmdbrands.gurus.weight.data.storage.datastore.ProtoIntegrationOperationType
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegratedDeviceInfo
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationData
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationOperationType
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationPreferences
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationType
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import kotlinx.coroutines.flow.Flow
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

  /**
   * Updates the out of sync status in local storage and syncs the state.
   */
  override suspend fun updateOutOfSyncStatus(accountId: String, outOfSync: Boolean) {
    try {
      updateOutOfSync(accountId, outOfSync)
      AppLog.d(tag, "Out of sync status updated: $outOfSync for account: $accountId")
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to update out of sync status", e)
    }
  }

  override suspend fun syncEntry(entry: HealthConnectSyncEntry) {
    healthConnectAPI.sync(entry)
  }

  override suspend fun getAccountDataMap(): Map<String, HealthConnectData> =
    healthConnectDataStore.healthConnectData()

  override suspend fun addAccount(accountId: String, data: HealthConnectData) {
    healthConnectDataStore.setHealthConnectData(accountId, data)
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
    // Verify the data was saved correctly
    val savedData = getAccountByID(accountId)
    AppLog.d(tag, "updateAlertSeen: accountId=$accountId, seen=$seen, dataExists=${savedData != null}, alertSeen=${savedData?.alertSeen}")
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
   * Sets the assignedTo field for an account.
   */
  override suspend fun setAssignedTo(accountId: String, assignedTo: String) {
    healthConnectDataStore.setAssignedTo(accountId, assignedTo)
  }

  /**
   * Gets the assignedTo field for an account.
   */
  override suspend fun getAssignedTo(accountId: String): String? {
    return healthConnectDataStore.getAssignedTo(accountId)
  }

  /**
   * Clears the assignedTo field for an account.
   */
  override suspend fun clearAssignedTo(accountId: String) {
    healthConnectDataStore.clearAssignedTo(accountId)
  }

  /**
   * Updates the modal state for an account.
   */
  override suspend fun updateModalState(accountId: String, state: Boolean) {
    healthConnectDataStore.updateModalState(accountId, state)
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
      healthConnectDataStore.setIntegrationInfo(accountId, IntegratedDeviceInfo(
        operationType = IntegrationOperationType.SAVE.value,
        scopes = integrationData,
        isCurrentDeviceDeleted = false
      ))
      AppLog.e(tag, "Failed to save integration: ${e.message}")
    }
  }

  /**
   * Syncs integration data between local storage and server
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
    var storedIntegrationInfo = integrationInfo ?: getStoredIntegrationData(accountId)
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
            AppLog.d(tag, "Integration synced successfully for account: $accountId")
          }

          IntegrationOperationType.REMOVE.value -> {
            healthConnectAPI.removeIntegration(storedIntegrationInfo.scopes.deviceId)
            if (!storedIntegrationInfo.isCurrentDeviceDeleted) {
             storedIntegrationInfo = IntegratedDeviceInfo(
                operationType = IntegrationOperationType.REMOVE.value,
                scopes = storedIntegrationInfo.scopes,
                isCurrentDeviceDeleted = true
                )
            }
          }
        }

      }

    } catch (e: Exception) {
      AppLog.e(tag, "Failed to sync integration for account $accountId: ${e.message}")
    }
    try {
      healthConnectDataStore.setIntegrationInfo(accountId, storedIntegrationInfo)
      AppLog.e(tag, "success on setting integration for account $accountId:")

    }
    catch (e: Exception) {
      AppLog.e(tag, "Failed to set integration for account $accountId: ${e.message}")

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
    val account = accountRepository.getActiveAccount().first() ?: return
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
      if(integrationInfo != null){
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
      AppLog.e(tag, "Failed to store integration data", e)
    }
  }

  /**
   * Retrieves stored integration data from local storage.
   * Similar to Angular's getStoredIntegrationData.
   */
  override suspend fun getStoredIntegrationData(accountId: String): IntegratedDeviceInfo? {
    return try {
      val healthConnectData = getAccountByID(accountId)
      IntegratedDeviceInfo(
        operationType = healthConnectData?.integrationInfo?.toDomain()?.operationType ?: "",
        scopes = IntegrationData(
          deviceId = healthConnectData?.integrationInfo?.scopes?.deviceId ?: "",
          type = IntegrationType.HEALTH_CONNECT.value,
          preferences = IntegrationPreferences(
            scopes = healthConnectData?.grantedPermissionList ?: emptyList(),
          ),
        ),
        isCurrentDeviceDeleted = healthConnectData?.integrationInfo?.toDomain()?.isCurrentDeviceDeleted ?: false,
      )
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to get stored integration data", e)
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
        // Set assignedTo to current account ID when integrating
        setAssignedTo(accountId, accountId)
      } else {
        val healthConnectData = getAccountByID(accountId)
        if (healthConnectData?.assignedTo == accountId) {
          setHcIntegrationStatus(accountId, false)
          // Clear assignedTo when disintegrating
          clearAssignedTo(accountId)
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Failed to set Health Connect integration status", e)
      throw e
    }
  }
}
