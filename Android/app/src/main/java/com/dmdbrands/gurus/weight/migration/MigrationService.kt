package com.dmdbrands.gurus.weight.migration

import com.dmdbrands.gurus.weight.core.shared.utilities.IonicDatabaseHelper
import com.dmdbrands.gurus.weight.data.storage.datastore.DashboardKeysDatastore
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.migration.IonicDataConverter.toMetricKey
import com.dmdbrands.gurus.weight.migration.IonicDataConverter.toThemeMode
import com.dmdbrands.gurus.weight.proto.UserAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log

/**
 * Service responsible for handling migration business logic.
 * This service orchestrates the migration process and handles all non-data operations.
 */
@Singleton
class MigrationService @Inject constructor(
  private val migrationRepository: MigrationRepository
) {

  companion object {
    private const val TAG = "MigrationService"
    private const val BATCH_SIZE = 500
  }

  /**
   * Performs the complete Ionic database migration.
   * This is the main entry point for Ionic migration.
   */
  suspend fun performIonicMigration(context: Context): MigrationResult =
    withContext(Dispatchers.IO) {
      return@withContext try {
        Log.i(TAG, "Starting Ionic database migration")

        val migrationResult = migrateIonicDatabase(context)

        if (migrationResult.isSuccess) {
          IonicDatabaseHelper.cleanupIonicDatabase(context)
          Log.i(TAG, "Ionic database migration completed successfully")
        } else {
          Log.e(TAG, "Ionic database migration failed: ${migrationResult.errorMessage}")
        }

        migrationResult
      } catch (t: Throwable) {
        Log.e(TAG, "Ionic migration failed with exception: ${t.message}")
        MigrationResult.Companion.failure("Migration failed: ${t.message}")
      }
    }

  /**
   * Core Ionic migration logic with account migration first, then entries.
   */
  private suspend fun migrateIonicDatabase(context: Context): MigrationResult {
    var sqliteDb: SQLiteDatabase? = null
    var totalMigratedEntries = 0
    var accountMigrated = false

    return try {
      Log.i(TAG, "Starting Ionic migration with account data first")

      // Step 1: Migrate account data from Capacitor Preferences
      accountMigrated = migrateAccountData(context)
      if (accountMigrated) {
        Log.i(TAG, "Account migration completed successfully")
      } else {
        Log.w(TAG, "No account data found or account migration failed")
      }

      // Step 2: Locate and open Ionic database for entries
      val dbPath = IonicDatabaseHelper.locateIonicDb(context)
      if (dbPath == null) {
        return MigrationResult.Success(0, accountMigrated)
      }
      sqliteDb = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)

      // Step 3: Perform comprehensive database analysis
      val analysisResult = IonicDatabaseHelper.analyzeIonicDatabaseRaw(sqliteDb)

      if (analysisResult.totalEntries == 0) {
        Log.i(TAG, "No entries found in Ionic database")
        return if (accountMigrated) {
          MigrationResult.Companion.success(0, accountMigrated)
        } else {
          MigrationResult.Companion.failure("No data found to migrate")
        }
      }

      // Step 4: Migrate entries using raw SQL
      totalMigratedEntries = migrateEntriesWithRawSQL(context, sqliteDb, analysisResult.totalEntries)

      // Step 5: Save migration timestamp
      IonicDatabaseHelper.saveMigrationTimestamp(context)

      Log.i(TAG, "Migration completed: Account=$accountMigrated, Entries=$totalMigratedEntries")
      MigrationResult.Companion.success(totalMigratedEntries, accountMigrated)
    } catch (e: Exception) {
      Log.e(TAG, "Migration failed: ${e.message}")
      MigrationResult.Companion.failure(e.message ?: "Unknown error")
    } finally {
      sqliteDb?.close()
    }
  }

  /**
   * Migrates account data from Capacitor Preferences storage using Gson.
   * Looks for the activeAccountKey and converts it to AccountEntity.
   */
  private suspend fun migrateAccountData(context: Context): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
      delay(5000)
      Log.d(TAG, "🏠 Starting account data migration from Capacitor Preferences")

      // Try to locate and read Capacitor Preferences
      val accountJsonString =
        CapacitorStorageHelper.locateAndReadAccountFromCapacitorStorage(context)

      if (accountJsonString.isNullOrEmpty()) {
        Log.w(TAG, "No account data found in Capacitor storage")
        return@withContext false
      }

      // Parse JSON account data using Gson
      val ionicAccount = IonicDataConverter.parseAccountWithGson(accountJsonString)
      if (ionicAccount == null) {
        Log.w(TAG, "Failed to parse account JSON with Gson")
        return@withContext false
      }

      Log.d(TAG, "📋 Successfully parsed IonicAccount: ${ionicAccount.profile?.email}")

      // Convert to AccountEntity and UserAccount
      val accountEntity = IonicDataConverter.convertIonicAccountToAccountEntity(ionicAccount)
      val userAccount = IonicDataConverter.convertIonicAccountToUserAccount(ionicAccount)
      val themeModeMap =
        CapacitorStorageHelper.locateAndReadThemeModeFromCapacitorStorage(context)

      if (accountEntity == null) {
        Log.w(TAG, "Failed to convert IonicAccount to AccountEntity")
        return@withContext false
      }

      // Save account and related data
      saveAccountAndSettings(context, ionicAccount, accountEntity, userAccount, themeModeMap)

      Log.i(TAG, "✅ Account migration successful: ${accountEntity.email}")
      true
    } catch (e: Exception) {
      Log.e(TAG, "❌ Account migration failed: ${e.message}")
      false
    }
  }

  /**
   * Saves account and all related settings to the database.
   */
  private suspend fun saveAccountAndSettings(
    context: Context,
    ionicAccount: IonicAccount,
    accountEntity: AccountEntity,
    userAccount: UserAccount?,
    themeModeMap: Map<String, String>
  ) {
    // Update UserDataStore with UserAccount
    val userDataStore = UserDataStore(context)
    userDataStore.updateAccountTokens(
      accountEntity.id,
      userAccount?.refreshToken ?: "",
      userAccount?.accessToken ?: "",
      userAccount?.expiresAt ?: "",
      true,
    )
    userDataStore.setActiveAccount(accountEntity.id)

    // Save theme modes
    themeModeMap.forEach { (key, value) ->
      val themeMode = value.toThemeMode()
      userDataStore.addAccount(key, themeMode = themeMode)
    }

    // Insert account and settings using extension functions
    migrationRepository.insertAccountWithSettings(
      accountEntity,
      ionicAccount.toGoalSettings(),
      ionicAccount.toWeightlessSettings(),
      ionicAccount.toIntegrationsSettings(),
      ionicAccount.toWeightCompSettings(),
      ionicAccount.toNotificationSettings(),
    )

    // Save dashboard metrics
    val dashboardKeysDatastore = DashboardKeysDatastore(context)
    val dashboardMetrics = ionicAccount.dashboardMetrics?.dashboardMetrics
    if (dashboardMetrics?.isNotEmpty() == true) {
      Log.d(TAG, "📋 Dashboard metrics: $dashboardMetrics")
      val dashboardMetricKeys = dashboardMetrics.mapNotNull { it.name.toMetricKey() }
      dashboardKeysDatastore.updateVisibleMetricKeys(accountEntity.id, dashboardMetricKeys)
    }
    dashboardKeysDatastore.resetVisibleMilestoneKeys(accountEntity.id)
  }

  /**
   * Migrates entries using raw SQL queries.
   */
  private suspend fun migrateEntriesWithRawSQL(context: Context, sqliteDb: SQLiteDatabase, totalEntries: Int): Int {
    var migratedCount = 0
    var cursor: Cursor? = null

    try {
      // Query to join opStack_v1 with opStack_metric for complete data
      val query = """
        SELECT
          e.id, e.userId, e.entryTimestamp, e.operationType, e.weight, e.bodyFat,
          e.muscleMass, e.water, e.bmi, e.source, e.attempts,
          m.bmr, m.metabolicAge, m.proteinPercent, m.pulse, m.skeletalMusclePercent,
          m.subcutaneousFatPercent, m.visceralFatLevel, m.boneMass
        FROM opStack e
        LEFT JOIN opStack_metric m ON e.userId = m.userId AND e.entryTimestamp = m.entryTimestamp
        ORDER BY e.entryTimestamp ASC
      """

      cursor = sqliteDb.rawQuery(query, null)

      val scaleEntries = mutableListOf<ScaleEntry>()

      while (cursor.moveToNext()) {
        try {
          val scaleEntry = IonicDataConverter.convertCursorToScaleEntry(cursor)
          if (scaleEntry != null) {
            scaleEntries.add(scaleEntry)

            // Process in batches
            if (scaleEntries.size >= BATCH_SIZE) {
              val batchResult = migrationRepository.insertScaleEntries(scaleEntries)
              migratedCount += batchResult
              scaleEntries.clear()

              val progress = (migratedCount * 100) / totalEntries
              Log.d(TAG, "Migration progress: $progress% ($migratedCount/$totalEntries entries)")
            }
          }
        } catch (e: Exception) {
          Log.w(TAG, "Failed to convert entry at position ${cursor.position}: ${e.message}")
        }
      }

      // Process remaining entries
      if (scaleEntries.isNotEmpty()) {
        val batchResult = migrationRepository.insertScaleEntries(scaleEntries)
        migratedCount += batchResult
      }
    } finally {
      cursor?.close()
    }

    return migratedCount
  }

  /**
   * Handles emergency cleanup if migration fails completely.
   */
  suspend fun performEmergencyCleanup(context: Context) = withContext(Dispatchers.IO) {
    try {
      Log.w(TAG, "Performing emergency cleanup")
      IonicDatabaseHelper.deleteRoomDbCompletely(context, "MeApp")
      Log.i(TAG, "Emergency cleanup completed")
    } catch (e: Exception) {
      Log.e(TAG, "Emergency cleanup failed: ${e.message}")
    }
  }
}
