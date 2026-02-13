package com.dmdbrands.gurus.weight.migration.service

import com.dmdbrands.gurus.weight.core.shared.utilities.IonicDatabaseHelper
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.migration.helper.CapacitorStorageHelper
import com.dmdbrands.gurus.weight.migration.helper.IonicDataConverter
import com.dmdbrands.gurus.weight.migration.helper.IonicDataConverter.toThemeMode
import com.dmdbrands.gurus.weight.migration.helper.toDashboardSettings
import com.dmdbrands.gurus.weight.migration.helper.toDeviceDetails
import com.dmdbrands.gurus.weight.migration.helper.toGoalSettings
import com.dmdbrands.gurus.weight.migration.helper.toIntegrationsSettings
import com.dmdbrands.gurus.weight.migration.helper.toNotificationSettings
import com.dmdbrands.gurus.weight.migration.helper.toWeightCompSettings
import com.dmdbrands.gurus.weight.migration.helper.toWeightlessSettings
import com.dmdbrands.gurus.weight.migration.model.IntegratedDeviceInfo
import com.dmdbrands.gurus.weight.migration.model.IonicAccount
import com.dmdbrands.gurus.weight.migration.model.IonicHealthConnectData
import com.dmdbrands.gurus.weight.migration.model.MigrationResult
import com.dmdbrands.gurus.weight.migration.model.Preferences
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
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

      migrateDeviceData(context)
      migrateIntegration(context)
      migrateTimestampKey(context)
      // Step 2: Locate and open Ionic database for entries
      val dbPath = IonicDatabaseHelper.locateIonicDb(context)
      if (dbPath == null) {
        return MigrationResult.Success(0, accountMigrated)
      }
      sqliteDb = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)

      // Step 4: Migrate entries. Check table existence before querying to support 4.0.1 vs 4.2.0.
      val regularEntries = if (tableExists(sqliteDb, "entry")) {
        migrateEntriesFromEntryTables(context, sqliteDb)
      } else {
        Log.i(TAG, "entry table not present, skipping entry migration")
        0
      }
      val opStackEntries = if (tableExists(sqliteDb, "opStack")) {
        migrateEntriesWithRawSQL(context, sqliteDb)
      } else {
        Log.i(TAG, "opStack table not present (e.g. 4.0.1), skipping opStack migration")
        0
      }
      totalMigratedEntries = regularEntries + opStackEntries

      // Step 5: Save migration timestamp
      IonicDatabaseHelper.saveMigrationTimestamp(context)

      Log.i(
        TAG,
        "Migration completed: Account=$accountMigrated, OpStack Entries=$opStackEntries, Regular Entries=$regularEntries, Total=$totalMigratedEntries",
      )
      MigrationResult.Companion.success(totalMigratedEntries, accountMigrated)
    } catch (e: Exception) {
      Log.e(TAG, "Migration failed: ${e.message}")
      MigrationResult.Companion.failure(e.message ?: "Unknown error")
    } finally {
      sqliteDb?.close()
      clearAllIonicData(context)
    }
  }

  private fun clearAllIonicData(context: Context) {
    val dbPath = IonicDatabaseHelper.locateIonicDb(context)
    if (dbPath != null) {
      context.deleteDatabase(dbPath)
    }
    context.deleteSharedPreferences("CapacitorStorage")
  }

  /**
   * Returns true if the given table exists in the SQLite database.
   * Used to support both 4.0.1 (no opStack) and 4.2.0 (has opStack) migration.
   * Uses case-insensitive match because SQLite stores unquoted identifiers (e.g. opStack) as lowercase (opstack) in sqlite_master.
   */
  private fun tableExists(db: SQLiteDatabase, tableName: String): Boolean {
    var cursor: Cursor? = null
    return try {
      cursor = db.rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND LOWER(name)=LOWER(?)",
        arrayOf(tableName),
      )
      cursor.moveToFirst()
    } catch (e: Exception) {
      Log.w(TAG, "tableExists failed for $tableName: ${e.message}")
      false
    } finally {
      cursor?.close()
    }
  }

  /**
   * Migrates Health Connect integration settings from Capacitor Storage to the Android database.
   *
   * This function reads various Health Connect related settings from the Ionic app's Capacitor
   * storage and consolidates them into IonicHealthConnectData objects for each user account.
   *
   * The migration process:
   * 1. Reads multiple Health Connect setting categories from Capacitor storage
   * 2. Combines all settings for each user account key
   * 3. Creates IonicHealthConnectData objects with consolidated settings
   * 4. Saves the migrated integration settings to the database
   *
   * Health Connect settings migrated:
   * - healthConnectAssignedTo: Device assignment information
   * - healthConnectIntegrated: Integration status flags
   * - fromHealthConnectAlertSeen: Alert visibility status
   * - openHealthConnect: Open connection status
   * - outOfSyncSession: Synchronization session state
   * - outOfSyncModalState: Modal state for sync issues
   * - healthServerIntegration: Server integration status (parsed as JSON)
   * - healthConnectPermissionList: Granted permissions list
   *
   * @param context The Android context for accessing Capacitor storage
   */
  private suspend fun migrateIntegration(context: Context) {
    try {
      Log.i(TAG, "Starting Health Connect integration settings migration...")

      // Read all Health Connect integration settings from Capacitor storage
      val assignedToMap = CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "healthConnectAssignedTo")
      val integratedMap = CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "healthConnectIntegrated")
      val alertSeenMap = CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "fromHealthConnectAlertSeen")
      val openMap = CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "openHealthConnect")
      val outOfSyncMap = CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "outOfSyncSession")
      val modelStateMap = CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "outOfSyncModalState")

      // Parse JSON integration status data
      val integrationStatusMap =
        CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "healthServerIntegration")
          .mapValues { (_, integrationStatusString) ->
            try {
              Log.i(TAG, integrationStatusString.toString())
              val gson = GsonBuilder()
                .registerTypeAdapter(IntegratedDeviceInfo::class.java, IntegratedDeviceInfoAdapter())
                .registerTypeAdapter(Preferences::class.java, PreferencesAdapter())
                .create()
              gson.fromJson(integrationStatusString, IntegratedDeviceInfo::class.java)
            } catch (e: Exception) {
              Log.e(TAG, "Failed to parse integration status: ${e.message}")
              null
            }
          }
      val grantedPermissionMap =
        CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "healthConnectPermissionList")

      // Collect all setting maps for key consolidation
      val maps = listOf(
        assignedToMap,
        integratedMap,
        alertSeenMap,
        openMap,
        outOfSyncMap,
        modelStateMap,
        integrationStatusMap,
        grantedPermissionMap,
      )

      // Get all unique account keys across all setting maps
      val allKeys = maps.flatMap { it.keys }.toSet()

      if (allKeys.isEmpty()) {
        Log.i(TAG, "No Health Connect integration settings found to migrate")
        return
      }

      // Create consolidated IonicHealthConnectData for each account key
      val result = allKeys.associateWith { key ->
        IonicHealthConnectData(
          assignedTo = assignedToMap[key] ?: "",
          integrated = integratedMap[key] ?: "",
          alertSeen = alertSeenMap[key] ?: "",
          open = openMap[key] ?: "",
          outOfSync = outOfSyncMap[key] ?: "",
          modalState = modelStateMap[key] ?: "",
          integrationStatus = integrationStatusMap[key],
          grantedPermission = grantedPermissionMap[key] ?: "",
        )
      }

      // Save all consolidated integration settings to the database
      migrationRepository.saveIntegrationSettings(result)
      Log.i(TAG, "Health Connect integration settings migration completed: ${result.size} accounts")
    } catch (e: Exception) {
      Log.e(TAG, "Integration settings migration failed: ${e.message}")
    }
  }

  /**
   * Migrates per-account operations sync timestamp (timestampkey-{userId}) from Capacitor Storage
   * into UserDataStore so the app uses it for operations sync. Only updates accounts that already exist in UserDataStore.
   */
  private suspend fun migrateTimestampKey(context: Context) {
    try {
      val timestampMap = CapacitorStorageHelper.locateAndReadTimestampKeyFromCapacitorStorage(context)
      if (timestampMap.isEmpty()) {
        Log.d(TAG, "No timestampkey entries found in Capacitor storage")
        return
      }
      val userDataStore = UserDataStore(context)
      timestampMap.forEach { (accountId, timestamp) ->
        userDataStore.updateSyncTimestamp(accountId, timestamp)
      }
      Log.i(TAG, "Migrated ${timestampMap.size} operations sync timestamp(s) from Capacitor")
    } catch (e: Exception) {
      Log.e(TAG, "Timestampkey migration failed: ${e.message}")
    }
  }

  private suspend fun migrateDeviceData(context: Context): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
      val devicesJsonMap =
        CapacitorStorageHelper.locateAndReadPairedScalesFromCapacitorStorage(context)
      Log.d(
        TAG,
        "📱 Starting device data migration from Capacitor storage with ${devicesJsonMap} devices",
      )
      if (devicesJsonMap.isNullOrEmpty()) {
        Log.w(TAG, "No device data found in Capacitor storage")
        return@withContext false
      }
      val ionicDeviceMap = IonicDataConverter.parseDevicesWithGson(devicesJsonMap)
      Log.d("migrationdata","$ionicDeviceMap")
      val deviceDetails = ionicDeviceMap.flatMap { (accountID, ionicScales) ->
        ionicScales.mapNotNull { scale ->
          val deviceDetail = scale.toDeviceDetails(accountID)
          if (deviceDetail == null) {
            Log.w(TAG, "Skipping scale migration for account $accountID: SKU is null or empty")
          }
          deviceDetail
        }
      }
      if (deviceDetails.isEmpty()) {
        Log.w(TAG, "No valid devices to migrate (all devices had null/empty SKU)")
      } else {
        Log.d(TAG, "Migrating ${deviceDetails.size} devices with valid SKU")
      }
      migrationRepository.insertDevice(deviceDetails)
      true
    } catch (e: Exception) {
      Log.e(TAG, "Migration failed: ${e.message}")
      false
    }
  }

  /**
   * Migrates account data from Capacitor Preferences storage using Gson.
   * Looks for the activeAccountKey and converts it to AccountEntity.
   */
  private suspend fun migrateAccountData(context: Context): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
      Log.d(TAG, "Starting account data migration from Capacitor Preferences")

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

      Log.d(TAG, "Successfully parsed IonicAccount: ${ionicAccount}")

      // Convert to AccountEntity and UserAccount
      val accountEntity = IonicDataConverter.convertIonicAccountToAccountEntity(ionicAccount)
      val themeModeMap =
        CapacitorStorageHelper.locateAndReadThemeModeFromCapacitorStorage(context)

      if (accountEntity == null) {
        Log.w(TAG, "Failed to convert IonicAccount to AccountEntity")
        return@withContext false
      }

      // Read last sync timestamp from Capacitor (Ionic key: timestampkey-{accountId})
      val lastSyncTimestamp =
        CapacitorStorageHelper.getLastSyncTimestampForAccount(context, accountEntity.id)

      // Save account and related data
      saveAccountAndSettings(context, ionicAccount, accountEntity, themeModeMap, lastSyncTimestamp)

      Log.i(TAG, "Account migration successful: ${accountEntity.email}")
      true
    } catch (e: Exception) {
      Log.e(TAG, "Account migration failed: ${e.message}")
      false
    }
  }

  /**
   * Saves account and all related settings to the database.
   * @param lastSyncTimestamp Optional last operations sync timestamp from Ionic Capacitor storage (timestampkey-{accountId}); stored in UserDataStore when present.
   */
  private suspend fun saveAccountAndSettings(
    context: Context,
    ionicAccount: IonicAccount,
    accountEntity: AccountEntity,
    themeModeMap: Map<String, String>,
    lastSyncTimestamp: String? = null
  ) {
    // Update UserDataStore with UserAccount
    val userDataStore = UserDataStore(context)
    // Save theme modes

    themeModeMap.forEach { (key, value) ->
      val themeMode = value.toThemeMode()
      val syncTs = if (key == accountEntity.id && !lastSyncTimestamp.isNullOrBlank()) lastSyncTimestamp else ""
      val refreshToken = if (key == accountEntity.id) ionicAccount.refreshToken else ""
      val accessToken = if (key == accountEntity.id) ionicAccount.accessToken else ""

      Log.d(TAG, "Theme mode for $key: $value")
      userDataStore.addAccount(
        key,
        refreshToken = refreshToken ?: "",
        accessToken = accessToken ?: "",
        themeMode = themeMode,
        syncTimestamp = syncTs,
        forceUpdate = userDataStore.containsAccount(key),
      )
    }

    userDataStore.updateAccountTokens(
      accountEntity.id,
      ionicAccount.refreshToken ?: "",
      ionicAccount.accessToken ?: "",
      ionicAccount.expiresAt ?: "",
      true,
    )
    userDataStore.setActiveAccount(accountEntity.id)

    // If account was not in themeModeMap, set sync timestamp here (addAccount already set it when key == accountEntity.id)
    if (!lastSyncTimestamp.isNullOrBlank() && accountEntity.id !in themeModeMap) {
      userDataStore.updateSyncTimestamp(accountEntity.id, lastSyncTimestamp)
    }

    // Insert account and settings using extension functions
    migrationRepository.insertAccountWithSettings(
      accountEntity,
      ionicAccount.toGoalSettings(),
      ionicAccount.toWeightlessSettings(),
      ionicAccount.toIntegrationsSettings(),
      ionicAccount.toWeightCompSettings(),
      ionicAccount.toNotificationSettings(),
      ionicAccount.toDashboardSettings(),
    )

    // Dashboard metrics are now properly handled via DashboardSettingsEntity
    AppLog.i(TAG, "Dashboard settings migrated successfully for ${accountEntity.email}")
  }

  /**
   * Migrates entries using raw SQL queries.
   * Only called when opStack table exists. Uses opStack_metric if present, else selects from opStack only.
   */
  private suspend fun migrateEntriesWithRawSQL(context: Context, sqliteDb: SQLiteDatabase): Int {
    var migratedCount = 0
    var cursor: Cursor? = null

    try {
      // Build query depending on whether opStack_metric exists (LEFT JOIN would fail if table missing)
      val hasMetric = tableExists(sqliteDb, "opStack_metric")
      val query = if (hasMetric) {
        """
        SELECT
          e.id, e.userId, e.entryTimestamp, e.operationType, e.weight, e.bodyFat,
          e.muscleMass, e.water, e.bmi, e.source, e.attempts,
          m.bmr, m.metabolicAge, m.proteinPercent, m.pulse, m.skeletalMusclePercent,
          m.subcutaneousFatPercent, m.visceralFatLevel, m.boneMass, NULL AS impedance, NULL AS unit
        FROM opStack e
        LEFT JOIN opStack_metric m ON e.userId = m.userId AND e.entryTimestamp = m.entryTimestamp
        ORDER BY e.entryTimestamp ASC
        """
      } else {
        """
        SELECT
          e.id, e.userId, e.entryTimestamp, e.operationType, e.weight, e.bodyFat,
          e.muscleMass, e.water, e.bmi, e.source, e.attempts,
          NULL AS bmr, NULL AS metabolicAge, NULL AS proteinPercent, NULL AS pulse, NULL AS skeletalMusclePercent,
          NULL AS subcutaneousFatPercent, NULL AS visceralFatLevel, NULL AS boneMass, NULL AS impedance, NULL AS unit
        FROM opStack e
        ORDER BY e.entryTimestamp ASC
        """
      }

      cursor = sqliteDb.rawQuery(query, null)

      val scaleEntries = mutableListOf<ScaleEntry>()

      while (cursor.moveToNext()) {
        try {
          val scaleEntry = IonicDataConverter.convertCursorToScaleEntry(cursor, isOpStack = true)
          if (scaleEntry != null) {
            scaleEntries.add(scaleEntry)
            // Process in batches
            if (scaleEntries.size >= BATCH_SIZE) {
              val batchResult = migrationRepository.insertScaleEntries(scaleEntries)
              migratedCount += batchResult
              scaleEntries.clear()
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
   * Migrates entries from entry (and optionally entry_metric) tables using raw SQL queries.
   * Only called when entry table exists. Uses entry_metric if present, else selects from entry only.
   */
  private suspend fun migrateEntriesFromEntryTables(context: Context, sqliteDb: SQLiteDatabase): Int {
    var migratedCount = 0
    var cursor: Cursor? = null

    try {
      // Build query depending on whether entry_metric exists (LEFT JOIN would fail if table missing)
      val hasMetric = tableExists(sqliteDb, "entry_metric")
      val query = if (hasMetric) {
        """
        SELECT
          e.id, e.userId, e.entryTimestamp, e.operationType, e.weight, e.bodyFat,
          e.muscleMass, e.water, e.bmi, e.source, 0 AS attempts,
          m.bmr, m.metabolicAge, m.proteinPercent, m.pulse, m.skeletalMusclePercent,
          m.subcutaneousFatPercent, m.visceralFatLevel, m.boneMass, m.impedance, m.unit
        FROM entry e
        LEFT JOIN entry_metric m ON e.userId = m.userId AND e.entryTimestamp = m.entryTimestamp
        ORDER BY e.entryTimestamp ASC
        """
      } else {
        """
        SELECT
          e.id, e.userId, e.entryTimestamp, e.operationType, e.weight, e.bodyFat,
          e.muscleMass, e.water, e.bmi, e.source, 0 AS attempts,
          NULL AS bmr, NULL AS metabolicAge, NULL AS proteinPercent, NULL AS pulse, NULL AS skeletalMusclePercent,
          NULL AS subcutaneousFatPercent, NULL AS visceralFatLevel, NULL AS boneMass, NULL AS impedance, NULL AS unit
        FROM entry e
        ORDER BY e.entryTimestamp ASC
        """
      }

      cursor = sqliteDb.rawQuery(query, null)

      val scaleEntries = mutableListOf<ScaleEntry>()

      while (cursor.moveToNext()) {
        try {
          val scaleEntry = IonicDataConverter.convertCursorToScaleEntry(cursor, isOpStack = false)
          if (scaleEntry != null) {
            scaleEntries.add(scaleEntry)
            // Process in batches
            if (scaleEntries.size >= BATCH_SIZE) {
              val batchResult = migrationRepository.insertScaleEntries(scaleEntries)
              migratedCount += batchResult
              scaleEntries.clear()
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
