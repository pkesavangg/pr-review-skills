package com.dmdbrands.gurus.weight.migration.service

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectDataStore
import com.dmdbrands.gurus.weight.data.storage.db.AppDatabase
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.DashboardSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.GoalSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.IntegrationsSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.NotificationSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightCompSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightlessSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceDetails
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.migration.helper.toHealthConnectData
import com.dmdbrands.gurus.weight.migration.model.IonicHealthConnectData
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

/**
 * Repository responsible for handling data access operations for migration.
 * Contains only database access methods and data persistence operations.
 */
@Singleton
class MigrationRepository @Inject constructor(
  private val context: Context
) {

  companion object {
    private const val TAG = "MigrationRepository"
  }

  suspend fun insertDevice(devices: List<DeviceDetails>) {
    val appDatabase = AppDatabase.getInstance(context)
    devices.forEach { device ->
      appDatabase.deviceDao().insertDevice(device)
    }
  }

  suspend fun saveIntegrationSettings(settings: Map<String, IonicHealthConnectData>) {
    val healthConnectData = HealthConnectDataStore(context)
    settings.map { (accountID, ionicHealthData) ->
      val healthData = ionicHealthData.toHealthConnectData(accountID)
      healthConnectData.setHealthConnectData(accountID, healthData)
    }
  }

  /**
   * Inserts account with all related settings in a single transaction.
   */
  suspend fun insertAccountWithSettings(
    accountEntity: AccountEntity,
    goalSettings: GoalSettingsEntity,
    weightlessSettings: WeightlessSettingsEntity,
    integrationsSettings: IntegrationsSettingsEntity,
    weightCompSettings: WeightCompSettingsEntity,
    notificationSettings: NotificationSettingsEntity,
    dashboardSettings: DashboardSettingsEntity
  ) {
    try {
      val appDatabase = AppDatabase.getInstance(context)
      appDatabase.accountDao().insertAccount(accountEntity)
      appDatabase.accountDao().insertGoalSettings(goalSettings)
      appDatabase.accountDao().insertWeightlessSettings(weightlessSettings)
      appDatabase.accountDao().insertIntegrationsSettings(integrationsSettings)
      appDatabase.accountDao().insertWeightCompSettings(weightCompSettings)
      appDatabase.accountDao().insertNotificationSettings(notificationSettings)
      appDatabase.accountDao().insertDashboardSettings(dashboardSettings)

      AppLog.d(TAG, "Account and settings inserted successfully for ${accountEntity.email}")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to insert account and settings: ${e.message}")
      throw e
    }
  }

  /**
   * Inserts a batch of ScaleEntry objects. Any per-row failure (FK violation, conversion
   * mismatch, etc.) propagates up so the caller fails the whole migration. The rationale:
   * blank lastSyncTimestamp on failure forces a full server sync via getAllOperations()
   * on the next launch, which is the simpler recovery path than tracking partial state.
   */
  suspend fun insertScaleEntries(scaleEntries: List<ScaleEntry>): Int {
    AppLog.d(TAG, "Starting insert of ${scaleEntries.size} scale entries")
    val appDatabase = AppDatabase.getInstance(context)
    scaleEntries.forEach { scaleEntry ->
      appDatabase.entryDao().insert(scaleEntry)
    }
    AppLog.d(TAG, "Inserted ${scaleEntries.size} scale entries")
    return scaleEntries.size
  }
}
