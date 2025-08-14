package com.dmdbrands.gurus.weight.migration

import android.content.Context
import android.util.Log
import com.dmdbrands.gurus.weight.data.storage.db.AppDatabase
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.GoalSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.IntegrationsSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.NotificationSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightCompSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightlessSettingsEntity
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import javax.inject.Inject
import javax.inject.Singleton

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

  /**
   * Inserts account with all related settings in a single transaction.
   */
  suspend fun insertAccountWithSettings(
      accountEntity: AccountEntity,
      goalSettings: GoalSettingsEntity,
      weightlessSettings: WeightlessSettingsEntity,
      integrationsSettings: IntegrationsSettingsEntity,
      weightCompSettings: WeightCompSettingsEntity,
      notificationSettings: NotificationSettingsEntity
  ) {
    try {
      val appDatabase = AppDatabase.Companion.getInstance(context)
      appDatabase.accountDao().insertAccount(accountEntity)
      appDatabase.accountDao().insertGoalSettings(goalSettings)
      appDatabase.accountDao().insertWeightlessSettings(weightlessSettings)
      appDatabase.accountDao().insertIntegrationsSettings(integrationsSettings)
      appDatabase.accountDao().insertWeightCompSettings(weightCompSettings)
      appDatabase.accountDao().insertNotificationSettings(notificationSettings)

      Log.d(TAG, "Account and settings inserted successfully for ${accountEntity.email}")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to insert account and settings: ${e.message}")
      throw e
    }
  }

  /**
   * Inserts a batch of ScaleEntry objects.
   */
  suspend fun insertScaleEntries(scaleEntries: List<ScaleEntry>): Int {
    var successCount = 0
    val appDatabase = AppDatabase.Companion.getInstance(context)

    scaleEntries.forEach { scaleEntry ->
      try {
        appDatabase.entryDao().insert(scaleEntry)
        successCount++
      } catch (e: Exception) {
        Log.w(TAG, "Failed to insert scale entry: ${e.message}")
      }
    }

    Log.d(TAG, "Successfully inserted $successCount out of ${scaleEntries.size} scale entries")
    return successCount
  }
}
