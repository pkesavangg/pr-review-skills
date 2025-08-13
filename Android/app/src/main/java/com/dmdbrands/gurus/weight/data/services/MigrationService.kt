package com.dmdbrands.gurus.weight.data.services

import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dmdbrands.gurus.weight.data.repository.MigrationRepository
import com.dmdbrands.gurus.weight.data.repository.MigrationResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import android.content.Context
import android.util.Log

/**
 * Worker that handles migration from Ionic database to Room database.
 * Uses clean architecture with MigrationRepository.
 */
@HiltWorker
class IonicMigrationWorker @AssistedInject constructor(
  @Assisted val appContext: Context,
  @Assisted val params: WorkerParameters
) : CoroutineWorker(appContext, params) {

  companion object {
    private const val TAG = "IonicMigrationWorker"
  }

  override suspend fun doWork(): Result {
    return try {
      val migrationRepository = MigrationRepository(applicationContext)

      val migrationResult = migrationRepository.performIonicMigration(applicationContext)
      when {
        migrationResult.isSuccess -> {
          val successResult = migrationResult as MigrationResult.Success
          Log.i(TAG, "✅ Migration completed successfully: ${successResult.migratedCount} entries migrated")
          Result.success()
        }

        else -> {
          Log.e(TAG, "❌ Migration failed: ${migrationResult.errorMessage}")
          migrationRepository.performEmergencyCleanup(applicationContext)
          Result.retry()
        }
      }
    } catch (t: Throwable) {
      Log.e(TAG, "💥 Migration worker failed with exception: ${t.message}", t)
      Result.retry()
    }
  }
}
