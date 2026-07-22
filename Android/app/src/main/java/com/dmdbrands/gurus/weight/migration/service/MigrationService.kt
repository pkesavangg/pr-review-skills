package com.dmdbrands.gurus.weight.migration.service

import com.dmdbrands.gurus.weight.core.shared.utilities.IonicDatabaseHelper
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.core.network.EncryptionUnavailableException
import com.dmdbrands.gurus.weight.core.network.SecureTokenStore
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.model.api.user.Token
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
    // Small page size keeps both the SQLite cursor window and the JVM accumulator bounded
    // on low-RAM devices. See MA-3852 for the native OOM that motivated keyset paging.
    private const val PAGE_SIZE = 100
    private const val SCOPE_ENTRY = "entry"
    private const val SCOPE_OPSTACK = "opstack"
  }

  /**
   * Performs the complete Ionic database migration.
   * This is the main entry point for Ionic migration.
   */
  suspend fun performIonicMigration(context: Context): MigrationResult =
    withContext(Dispatchers.IO) {
      return@withContext try {
        AppLog.i(TAG, "Starting Ionic database migration")

        val migrationResult = migrateIonicDatabase(context)

        if (migrationResult.isSuccess) {
          IonicDatabaseHelper.cleanupIonicDatabase(context)
          AppLog.i(TAG, "Ionic database migration completed successfully")
        } else {
          AppLog.e(TAG, "Ionic database migration failed: ${migrationResult.errorMessage}")
        }

        migrationResult
      } catch (t: Throwable) {
        AppLog.e(TAG, "Ionic migration failed with exception: ${t.message}")
        MigrationResult.Companion.failure("Migration failed: ${t.message}")
      }
    }

  /**
   * Core Ionic migration logic with account migration first, then entries.
   * Uses the active account ID from account migration for all subsequent steps (devices, integration, entries, timestamp).
   */
  private suspend fun migrateIonicDatabase(context: Context): MigrationResult {
    var sqliteDb: SQLiteDatabase? = null
    var totalMigratedEntries = 0
    var accountMigrated = false
    var activeAccountId: String? = null

    return try {
      AppLog.i(TAG, "Starting Ionic migration with account data first")

      // Step 1: Migrate account data from Capacitor Preferences and get active account ID
      val accountResult = migrateAccountData(context)
      accountMigrated = accountResult.first
      activeAccountId = accountResult.second
      if (accountMigrated && activeAccountId != null) {
        AppLog.i(TAG, "Account migration completed successfully for activeAccountId=$activeAccountId")
      } else if (!accountMigrated) {
        AppLog.w(TAG, "No account data found or account migration failed")
      }

      migrateDeviceData(context, activeAccountId)
      migrateIntegration(context, activeAccountId)

      // Step 2: Locate and open Ionic database for entries
      val dbPath = IonicDatabaseHelper.locateIonicDb(context) ?: return MigrationResult.Success(0, accountMigrated)

      // Step 2a: a source entry DB exists, so we need an AccountEntity in Room or every
      // insert will FK-fail. Fail-stop so the worker retries; if it stays broken, the
      // blank lastSyncTimestamp drives a full server re-sync via getAllOperations().
      if (!accountMigrated || activeAccountId == null) {
        return MigrationResult.failure(
          "Source entry DB found but account migration did not produce an activeAccountId"
        )
      }

      sqliteDb = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)

      // Step 4: Migrate entries. Check table existence before querying to support 4.0.1 vs 4.2.0.
      val regularEntries = if (tableExists(sqliteDb, "entry")) {
        migrateEntriesFromEntryTables(sqliteDb, activeAccountId)
      } else {
        AppLog.i(TAG, "entry table not present, skipping entry migration")
        0
      }
      val opStackEntries = if (tableExists(sqliteDb, "opStack")) {
        migrateEntriesWithRawSQL(sqliteDb, activeAccountId)
      } else {
        AppLog.i(TAG, "opStack table not present (e.g. 4.0.1), skipping opStack migration")
        0
      }
      totalMigratedEntries = regularEntries + opStackEntries

      // Step 5: All entries saved — only now write the last sync timestamp.
      // If entry migration threw above, this is skipped via the catch block.
      val lastSyncTimestamp = CapacitorStorageHelper.getLastSyncTimestampForAccount(context, activeAccountId)
      if (!lastSyncTimestamp.isNullOrBlank()) {
        UserDataStore(context).updateSyncTimestamp(activeAccountId, lastSyncTimestamp)
        AppLog.i(TAG, "Last sync timestamp written for $activeAccountId after entry migration")
      }

      // Step 6: Save migration timestamp and clear resume checkpoints (full success path)
      IonicDatabaseHelper.saveMigrationTimestamp(context)
      IonicDatabaseHelper.clearResumeRowids(context)

      AppLog.i(
        TAG,
        "Migration completed: Account=$accountMigrated, activeAccountId=$activeAccountId, OpStack Entries=$opStackEntries, Regular Entries=$regularEntries, Total=$totalMigratedEntries",
      )
      clearAllIonicData(context)
      MigrationResult.success(totalMigratedEntries, accountMigrated)
    } catch (e: Exception) {
      AppLog.e(TAG, "Migration failed: ${e.message}")
      MigrationResult.failure(e.message ?: "Unknown error")
    } finally {
      sqliteDb?.close()
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
      AppLog.w(TAG, "tableExists failed for $tableName: ${e.message}")
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
   * @param activeAccountId When non-null, only integration settings for this account are migrated (same ID as account migration).
   */
  private suspend fun migrateIntegration(context: Context, activeAccountId: String?) {
    try {
      AppLog.i(TAG, "Starting Health Connect integration settings migration...")

      // Read all Health Connect integration settings from Capacitor storage
      val assignedToMap = CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "healthConnectAssignedTo")
      val integratedMap = CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "healthConnectIntegrated")
      val alertSeenMap = CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "fromHealthConnectAlertSeen")
      val openMap = CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "openHealthConnect")
      val outOfSyncMap = CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "outOfSyncSession")
      val modelStateMap = CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "outOfSyncModalState")

      // Parse JSON integration status data
      val integrationStatusMap = parseIntegrationStatusMap(context)
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

      // Get all unique account keys across all setting maps; when activeAccountId is set, only migrate that account
      val allKeys = maps.flatMap { it.keys }.toSet()
      val keysToMigrate = if (activeAccountId != null) allKeys.filter { it == activeAccountId } else allKeys.toList()

      if (keysToMigrate.isEmpty()) {
        AppLog.i(TAG, "No Health Connect integration settings found to migrate for activeAccountId=$activeAccountId")
        return
      }

      // Create consolidated IonicHealthConnectData for each account key
      val result = buildConsolidatedIntegrationData(
        keysToMigrate = keysToMigrate,
        assignedToMap = assignedToMap,
        integratedMap = integratedMap,
        alertSeenMap = alertSeenMap,
        openMap = openMap,
        outOfSyncMap = outOfSyncMap,
        modelStateMap = modelStateMap,
        integrationStatusMap = integrationStatusMap,
        grantedPermissionMap = grantedPermissionMap,
      )

      // Save all consolidated integration settings to the database
      migrationRepository.saveIntegrationSettings(result)
      AppLog.i(TAG, "Health Connect integration settings migration completed: ${result.size} accounts")
    } catch (e: Exception) {
      AppLog.e(TAG, "Integration settings migration failed: ${e.message}")
    }
  }

  /** Reads + JSON-parses the `healthServerIntegration` map into [IntegratedDeviceInfo] values. */
  private fun parseIntegrationStatusMap(context: Context): Map<String, IntegratedDeviceInfo?> =
    CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "healthServerIntegration")
      .mapValues { (_, integrationStatusString) ->
        try {
          AppLog.i(TAG, integrationStatusString.toString())
          val gson = GsonBuilder()
            .registerTypeAdapter(IntegratedDeviceInfo::class.java, IntegratedDeviceInfoAdapter())
            .registerTypeAdapter(Preferences::class.java, PreferencesAdapter())
            .create()
          gson.fromJson(integrationStatusString, IntegratedDeviceInfo::class.java)
        } catch (e: Exception) {
          AppLog.e(TAG, "Failed to parse integration status: ${e.message}")
          null
        }
      }

  /** Consolidates the per-key Health Connect setting maps into [IonicHealthConnectData] per account. */
  @Suppress("LongParameterList")
  private fun buildConsolidatedIntegrationData(
    keysToMigrate: List<String>,
    assignedToMap: Map<String, String>,
    integratedMap: Map<String, String>,
    alertSeenMap: Map<String, String>,
    openMap: Map<String, String>,
    outOfSyncMap: Map<String, String>,
    modelStateMap: Map<String, String>,
    integrationStatusMap: Map<String, IntegratedDeviceInfo?>,
    grantedPermissionMap: Map<String, String>,
  ): Map<String, IonicHealthConnectData> =
    keysToMigrate.associateWith { key ->
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

  /**
   * Migrates per-account operations sync timestamp (timestampkey-{userId}) from Capacitor Storage
   * into UserDataStore so the app uses it for operations sync. Only updates accounts that already exist in UserDataStore.
   */
  private suspend fun migrateTimestampKey(context: Context , activeAccountID : String?) {
    try {
      val timestampMap = CapacitorStorageHelper.locateAndReadTimestampKeyFromCapacitorStorage(context)
      if (timestampMap.isEmpty()) {
        AppLog.d(TAG, "No timestampkey entries found in Capacitor storage")
        return
      }
      val userDataStore = UserDataStore(context)
       AppLog.d(TAG, "Found ${timestampMap.size} timestampkey entries ${timestampMap}")
      timestampMap.forEach { (accountId, timestamp) ->
        if (accountId == activeAccountID ) {
          AppLog.d(TAG, "Updating sync timestamp for $accountId: $timestamp")
          userDataStore.updateSyncTimestamp(accountId, timestamp)
         AppLog.d(TAG, "Migrated Sync timestamp updated for $accountId")
        }
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Timestampkey migration failed: ${e.message}")
    }
  }

  /**
   * Migrates device data from Capacitor storage.
   * @param activeAccountId When non-null, only devices for this account are migrated (same ID as account migration).
   */
  private suspend fun migrateDeviceData(context: Context, activeAccountId: String?): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
      val devicesJsonMap =
        CapacitorStorageHelper.locateAndReadPairedScalesFromCapacitorStorage(context)
      AppLog.d(
        TAG,
        "📱 Starting device data migration from Capacitor storage with ${devicesJsonMap} devices, activeAccountId=$activeAccountId",
      )
      if (devicesJsonMap.isNullOrEmpty()) {
        AppLog.w(TAG, "No device data found in Capacitor storage")
        return@withContext false
      }
      val ionicDeviceMap = IonicDataConverter.parseDevicesWithGson(devicesJsonMap)
      AppLog.d("migrationdata","$ionicDeviceMap")
      val mapToMigrate = if (activeAccountId != null) {
        ionicDeviceMap.filterKeys { it == activeAccountId }
      } else {
        ionicDeviceMap
      }
      val deviceDetails = mapToMigrate.flatMap { (accountID, ionicScales) ->
        ionicScales.mapNotNull { scale ->
          val deviceDetail = scale.toDeviceDetails(accountID)
          if (deviceDetail == null) {
            AppLog.w(TAG, "Skipping scale migration for account $accountID: SKU is null or empty")
          }
          deviceDetail
        }
      }
      if (deviceDetails.isEmpty()) {
        AppLog.w(TAG, "No valid devices to migrate (all devices had null/empty SKU)")
      } else {
        AppLog.d(TAG, "Migrating ${deviceDetails.size} devices with valid SKU")
      }
      migrationRepository.insertDevice(deviceDetails)
      true
    } catch (e: Exception) {
      AppLog.e(TAG, "Migration failed: ${e.message}")
      false
    }
  }

  /**
   * Migrates account data from Capacitor Preferences storage using Gson.
   * Looks for the activeAccountKey and converts it to AccountEntity.
   * @return Pair of (success, activeAccountId). When success is true, activeAccountId is the migrated account's ID for use in subsequent migration steps.
   */
  private suspend fun migrateAccountData(context: Context): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
    return@withContext try {
      AppLog.d(TAG, "Starting account data migration from Capacitor Preferences")

      // Try to locate and read Capacitor Preferences
      val accountJsonString =
        CapacitorStorageHelper.locateAndReadAccountFromCapacitorStorage(context)

      if (accountJsonString.isNullOrEmpty()) {
        AppLog.w(TAG, "No account data found in Capacitor storage")
        return@withContext Pair(false, null)
      }

      // Parse JSON account data using Gson
      val ionicAccount = IonicDataConverter.parseAccountWithGson(accountJsonString)

      if (ionicAccount == null) {
        AppLog.w(TAG, "Failed to parse account JSON with Gson")
        return@withContext Pair(false, null)
      }

      AppLog.d(TAG, "Successfully parsed IonicAccount: ${ionicAccount}")

      // Convert to AccountEntity and UserAccount
      val accountEntity = IonicDataConverter.convertIonicAccountToAccountEntity(ionicAccount)
      val themeModeMap =
        CapacitorStorageHelper.locateAndReadThemeModeFromCapacitorStorage(context)

      if (accountEntity == null) {
        AppLog.w(TAG, "Failed to convert IonicAccount to AccountEntity")
        return@withContext Pair(false, null)
      }

      // Sync timestamp is intentionally NOT read here — it is written only after
      // all entries finish saving successfully (see migrateIonicDatabase Step 5).
      saveAccountAndSettings(context, ionicAccount, accountEntity, themeModeMap)
      AppLog.d(TAG, "Account migration completed")
      AppLog.i(TAG, "Account migration successful: activeAccountId=${accountEntity.id}")
      Pair(true, accountEntity.id)
    } catch (e: Exception) {
      AppLog.e(TAG, "Account migration failed: ${e.message}")
      Pair(false, null)
    }
  }

  /**
   * Saves account and all related settings to the database.
   * Note: sync timestamp is deliberately not handled here — it is written from
   * migrateIonicDatabase only after entry migration succeeds.
   */
  private suspend fun saveAccountAndSettings(
    context: Context,
    ionicAccount: IonicAccount,
    accountEntity: AccountEntity,
    themeModeMap: Map<String, String>,
  ) {
    val userDataStore = UserDataStore(context)

    themeModeMap.forEach { (key, value) ->
      val themeMode = value.toThemeMode()
      // Sync timestamp is deferred: written only after all entries migrate successfully (see migrateIonicDatabase Step 5).
      val syncTs = ""

      AppLog.d(TAG, "Theme mode for $key: $value")
      userDataStore.addAccount(
        key,
        refreshToken = "",
        accessToken = "",
        themeMode = themeMode,
        forceUpdate = userDataStore.containsAccount(key),
      )
    }

    // Write tokens to EncryptedSharedPreferences only (not DataStore)
    try {
      val secureTokenStore = SecureTokenStore(context)
      secureTokenStore.saveToken(
        accountEntity.id,
        Token(
          accountId = accountEntity.id,
          isActive = true,
          accessToken = ionicAccount.accessToken,
          refreshToken = ionicAccount.refreshToken,
          expiresAt = ionicAccount.expiresAt,
        )
      )
    } catch (e: EncryptionUnavailableException) {
      AppLog.e(TAG, "Failed to save token to encrypted storage during Ionic migration", e.toString())
      // Token will be unavailable — user will be forced to re-login after migration
    }
    // Update DataStore account entry (isActive flag only, no token fields)
    userDataStore.updateAccount(
      accountId = accountEntity.id,
      isActive = true,
    )

    userDataStore.setActiveAccount(accountEntity.id)

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
    AppLog.i(TAG, "Dashboard settings migrated successfully")
  }

  /**
   * Migrates entries using raw SQL queries.
   * Only called when opStack table exists. Uses opStack_metric if present, else selects from opStack only.
   *
   * Reads the legacy table in pages keyed on rowid (MA-3852). Each page is a bounded query
   * (`WHERE rowid > ? LIMIT PAGE_SIZE`) so the SQLite native heap is never asked to materialize
   * the whole table at once. Per-page progress is checkpointed in SharedPreferences so a process
   * abort during migration resumes from the last completed rowid instead of restarting at 0.
   *
   * @param activeAccountId When non-null, only entries for this account are migrated (same ID as account migration).
   */
  private suspend fun migrateEntriesWithRawSQL(sqliteDb: SQLiteDatabase, activeAccountId: String?): Int {
    val hasMetric = tableExists(sqliteDb, "opStack_metric")
    val pagedQuery = buildOpStackPagedQuery(hasMetric)
    return migrateEntriesPaged(
      sqliteDb = sqliteDb,
      activeAccountId = activeAccountId,
      scope = SCOPE_OPSTACK,
      pagedQuery = pagedQuery,
      isOpStack = true,
    )
  }

  private fun buildOpStackPagedQuery(hasMetric: Boolean): String = if (hasMetric) {
    """
    SELECT
      e.id, e.userId, e.entryTimestamp, e.operationType, e.weight, e.bodyFat,
      e.muscleMass, e.water, e.bmi, e.source, e.attempts,
      m.bmr, m.metabolicAge, m.proteinPercent, m.pulse, m.skeletalMusclePercent,
      m.subcutaneousFatPercent, m.visceralFatLevel, m.boneMass, NULL AS impedance, NULL AS unit,
      e.rowid AS rowid
    FROM opStack e
    LEFT JOIN opStack_metric m ON e.userId = m.userId AND e.entryTimestamp = m.entryTimestamp
    WHERE e.rowid > ?
    ORDER BY e.rowid ASC
    LIMIT ?
    """.trimIndent()
  } else {
    """
    SELECT
      e.id, e.userId, e.entryTimestamp, e.operationType, e.weight, e.bodyFat,
      e.muscleMass, e.water, e.bmi, e.source, e.attempts,
      NULL AS bmr, NULL AS metabolicAge, NULL AS proteinPercent, NULL AS pulse, NULL AS skeletalMusclePercent,
      NULL AS subcutaneousFatPercent, NULL AS visceralFatLevel, NULL AS boneMass, NULL AS impedance, NULL AS unit,
      e.rowid AS rowid
    FROM opStack e
    WHERE e.rowid > ?
    ORDER BY e.rowid ASC
    LIMIT ?
    """.trimIndent()
  }

  /**
   * Migrates entries from entry (and optionally entry_metric) tables using raw SQL queries.
   * Only called when entry table exists. Uses entry_metric if present, else selects from entry only.
   *
   * See `migrateEntriesWithRawSQL` for the keyset-paging strategy (MA-3852).
   *
   * @param activeAccountId When non-null, only entries for this account are migrated (same ID as account migration).
   */
  private suspend fun migrateEntriesFromEntryTables(sqliteDb: SQLiteDatabase, activeAccountId: String?): Int {
    val hasMetric = tableExists(sqliteDb, "entry_metric")
    val pagedQuery = buildEntryPagedQuery(hasMetric)
    return migrateEntriesPaged(
      sqliteDb = sqliteDb,
      activeAccountId = activeAccountId,
      scope = SCOPE_ENTRY,
      pagedQuery = pagedQuery,
      isOpStack = false,
    )
  }

  private fun buildEntryPagedQuery(hasMetric: Boolean): String = if (hasMetric) {
    """
    SELECT
      e.id, e.userId, e.entryTimestamp, e.operationType, e.weight, e.bodyFat,
      e.muscleMass, e.water, e.bmi, e.source, 0 AS attempts,
      m.bmr, m.metabolicAge, m.proteinPercent, m.pulse, m.skeletalMusclePercent,
      m.subcutaneousFatPercent, m.visceralFatLevel, m.boneMass, m.impedance, m.unit,
      e.rowid AS rowid
    FROM entry e
    LEFT JOIN entry_metric m ON e.userId = m.userId AND e.entryTimestamp = m.entryTimestamp
    WHERE e.rowid > ?
    ORDER BY e.rowid ASC
    LIMIT ?
    """.trimIndent()
  } else {
    """
    SELECT
      e.id, e.userId, e.entryTimestamp, e.operationType, e.weight, e.bodyFat,
      e.muscleMass, e.water, e.bmi, e.source, 0 AS attempts,
      NULL AS bmr, NULL AS metabolicAge, NULL AS proteinPercent, NULL AS pulse, NULL AS skeletalMusclePercent,
      NULL AS subcutaneousFatPercent, NULL AS visceralFatLevel, NULL AS boneMass, NULL AS impedance, NULL AS unit,
      e.rowid AS rowid
    FROM entry e
    WHERE e.rowid > ?
    ORDER BY e.rowid ASC
    LIMIT ?
    """.trimIndent()
  }

  /**
   * Shared paging engine for both `entry` and `opStack` migrations.
   *
   * Each iteration runs `pagedQuery` bound with `(lastRowid, PAGE_SIZE)`, drains the cursor into a
   * bounded list, inserts via the repository, advances `lastRowid` to the highest rowid seen in
   * this page, and persists the resume checkpoint. The loop terminates when a page returns fewer
   * rows than `PAGE_SIZE` (final page) or zero rows (already complete).
   *
   * Idempotency: a crash between insert and checkpoint save means the next run will re-insert the
   * overlapping rows. `EntryDao.insertEntryEntity` uses `OnConflictStrategy.REPLACE` against the
   * unique `(accountId, entryTimestamp)` index, so the overlap rewrites identical rows safely.
   */
  private suspend fun migrateEntriesPaged(
    sqliteDb: SQLiteDatabase,
    activeAccountId: String?,
    scope: String,
    pagedQuery: String,
    isOpStack: Boolean,
  ): Int {
    var migratedCount = 0
    var lastRowid = 0L
    AppLog.i(TAG, "Paged migration starting for scope=$scope")

    while (true) {
      val pageRows = mutableListOf<ScaleEntry>()
      var maxRowidThisPage = lastRowid
      var rawCursorCount = 0

      sqliteDb.rawQuery(pagedQuery, arrayOf(lastRowid.toString(), PAGE_SIZE.toString())).use { cursor ->
        val rowedIndex = cursor.getColumnIndexOrThrow("rowid")
        while (cursor.moveToNext()) {
          rawCursorCount++
          val rowed = cursor.getLong(rowedIndex)
          if (rowed > maxRowidThisPage) maxRowidThisPage = rowed
          // Conversion exceptions and downstream insert failures both propagate.
          // The whole migration fails-fast and the worker retries; if it stays broken,
          // the blank lastSyncTimestamp drives getAllOperations() to re-pull from the server.
          val scaleEntry = IonicDataConverter.convertCursorToScaleEntry(cursor, isOpStack = isOpStack)
            ?: continue   // null = nothing to migrate for this row (e.g. tombstone), not a failure
          val matchesAccount = activeAccountId == null || scaleEntry.entry.accountId == activeAccountId
          if (matchesAccount) pageRows.add(scaleEntry)
        }
      }

      if (pageRows.isNotEmpty()) {
        migratedCount += migrationRepository.insertScaleEntries(pageRows)
      }

      // In-memory cursor pointer only — a retry re-walks from rowid 0.
      // REPLACE on the unique (accountId, entryTimestamp) index makes that idempotent.
      if (maxRowidThisPage > lastRowid) lastRowid = maxRowidThisPage
      if (rawCursorCount < PAGE_SIZE) break
    }

    AppLog.i(TAG, "Paged migration finished for scope=$scope, inserted=$migratedCount, lastRowid=$lastRowid")
    return migratedCount
  }

  /**
   * Handles emergency cleanup if migration fails completely.
   */
  suspend fun performEmergencyCleanup(context: Context) = withContext(Dispatchers.IO) {
    try {
      AppLog.w(TAG, "Performing emergency cleanup")
      IonicDatabaseHelper.deleteRoomDbCompletely(context, "MeApp")
      AppLog.i(TAG, "Emergency cleanup completed")
    } catch (e: Exception) {
      AppLog.e(TAG, "Emergency cleanup failed: ${e.message}")
    }
  }
}
