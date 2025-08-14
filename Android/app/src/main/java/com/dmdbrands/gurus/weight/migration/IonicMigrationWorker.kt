package com.dmdbrands.gurus.weight.migration

import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import android.content.Context
import android.util.Log

/**
 * Worker that handles migration from Ionic database to Room database.
 * Uses clean architecture with MigrationService for business logic.
 *
 * This worker is responsible for:
 * - Executing the migration process in the background
 * - Handling success and failure scenarios
 * - Coordinating with the MigrationService for actual migration logic
 */
@HiltWorker
class IonicMigrationWorker @AssistedInject constructor(
  @Assisted val appContext: Context,
  @Assisted val params: WorkerParameters,
  private val migrationService: MigrationService
) : CoroutineWorker(appContext, params) {

  companion object {
    private const val TAG = "IonicMigrationWorker"
  }

  /**
   * Performs the migration work in the background.
   * Delegates actual migration logic to MigrationService.
   */
  override suspend fun doWork(): Result {
    return try {
      Log.i(TAG, "🚀 Starting Ionic migration worker")

      val migrationResult = migrationService.performIonicMigration(applicationContext)

      when {
        migrationResult.isSuccess -> {
          val successResult = migrationResult as MigrationResult.Success
          Log.i(
            TAG,
            "✅ Migration completed successfully: ${successResult.migratedCount} entries migrated, account migrated: ${successResult.accountMigrated}",
          )
          Result.success()
        }

        else -> {
          Log.e(TAG, "❌ Migration failed: ${migrationResult.errorMessage}")
          migrationService.performEmergencyCleanup(applicationContext)
          Result.retry()
        }
      }
    } catch (t: Throwable) {
      Log.e(TAG, "💥 Migration worker failed with exception: ${t.message}", t)
      Result.retry()
    }
  }
}
