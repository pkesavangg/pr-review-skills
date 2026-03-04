package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.data.api.HealthConnectSyncEntry
import com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectData
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegratedDeviceInfo
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationData
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Health Connect data operations, abstracting HealthConnectDataStore.
 */
interface IHealthConnectRepository {
  /** Gets the current map of account data. */
  suspend fun getAccountDataMap(): Map<String, HealthConnectData>

  /** Sets or updates an account data entry for the given accountId. */
  suspend fun addAccount(accountId: String, data: HealthConnectData)

  /** Clears all account data entries. */
  suspend fun clearData()

  /** Gets an account data entry by its accountId. */
  suspend fun getAccountByID(accountId: String): HealthConnectData?

  /** Checks if an account data entry exists for the given accountId. */
  suspend fun hasAccountData(accountId: String): Boolean

  /** Returns a Flow of HealthConnectData for a specific account. */
  fun getHealthConnectDataFlow(accountId: String): Flow<HealthConnectData?>

  /** Updates the integration status for an account. */
  suspend fun setHcIntegrationStatus(accountId: String, integrated: Boolean)

  /** Updates the alert seen status for an account. */
  suspend fun updateAlertSeen(accountId: String, seen: Boolean)

  /** Updates the out of sync status for an account. */
  suspend fun updateOutOfSync(accountId: String, outOfSync: Boolean)

  /** Updates the open status for an account. */
  suspend fun setOpen(accountId: String, open: Boolean)

  /** Gets the open status for an account. */
  suspend fun getOpen(accountId: String): Boolean

  /** Sets the assignedTo field for an account. */
  suspend fun setAssignedTo(accountId: String, assignedTo: String)

  /** Gets the assignedTo field for an account. */
  suspend fun getAssignedTo(accountId: String): String?

  /** Clears the assignedTo field for an account. */
  suspend fun clearAssignedTo(accountId: String)

  /** Updates the modal state for an account. */
  suspend fun updateModalState(accountId: String, state: Boolean)


  /** Updates the granted permissions for an account. */
  suspend fun setHcPermissions(accountId: String, permissions: List<String>)

  /**
   * Saves integration data both locally and to server.
   * Handles offline storage and server sync.
   */
  suspend fun saveIntegration(integrationData: IntegrationData)

  /**
   * Syncs integration data between local storage and server.
   * Handles both save and remove operations.
   */
  suspend fun syncIntegration(integrationInfo: IntegratedDeviceInfo? = null)


  /**
   * Removes integration from both server and local storage.
   */
  suspend fun removeServerHcIntegration(deviceId: String)

  /**
   * Stores integration data locally for offline handling.
   */
  suspend fun setStoredIntegrationData(accountId: String, integrationInfo: IntegratedDeviceInfo?)

  /**
   * Retrieves stored integration data from local storage.
   */
  suspend fun getStoredIntegrationData(accountId: String): IntegratedDeviceInfo?

  /**
   * Sets the Health Connect integration status for the current account.
   */
  suspend fun setHealthConnectIntegrationStatus(accountId: String, integrated: Boolean)

  /**
   * Updates the out of sync status in local storage and syncs the state.
   */
  suspend fun updateOutOfSyncStatus(accountId: String, outOfSync: Boolean)

  suspend fun syncEntry(entry: HealthConnectSyncEntry)
}
