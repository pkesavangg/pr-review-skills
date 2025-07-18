package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.data.storage.datastore.HealthConnectData
import com.greatergoods.meapp.domain.model.integrations.IntegratedDeviceInfo
import com.greatergoods.meapp.domain.model.integrations.IntegrationData
import com.greatergoods.meapp.features.integration.model.IntegrationState
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Health Connect data operations, abstracting HealthConnectDataStore.
 */
interface IHealthConnectRepository {
  /** Emits a Flow of the current map of account data. */
  val accountDataFlow: Flow<Map<String, HealthConnectData>>
  val integrationState: Flow<IntegrationState>
  /** Returns a Flow of the current active account ID. */
  val activeAccountIdFlow: Flow<String?>

  /** Gets the current map of account data. */
  suspend fun getAccountDataMap(): Map<String, HealthConnectData>

  /** Sets or updates an account data entry for the given accountId. */
  suspend fun addAccount(accountId: String, data: HealthConnectData)

  /** Removes an account data entry for the given accountId. */
  suspend fun removeAccount(accountId: String)

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

  /** Updates the modal state for an account. */
  suspend fun updateModalState(accountId: String, state: Boolean)

  /** Updates the update timestamp for an account. */
  suspend fun updateTimestamp(accountId: String, timestamp: String)

  /** Updates the integration timestamp for an account. */
  suspend fun updateIntegrationTimestamp(accountId: String, timestamp: String)

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
}
