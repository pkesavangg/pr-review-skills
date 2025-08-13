package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.data.storage.db.AppDatabase
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics
import com.dmdbrands.gurus.weight.proto.UserAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.UUID
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log

/**
 * Repository responsible for handling all types of database migrations.
 * This includes Ionic migration and future migration scenarios.
 */
class MigrationRepository(private val context: Context) {

  companion object {
    private const val TAG = "MigrationRepository"
    private const val BATCH_SIZE = 500
    private const val IONIC_DB_NAME = "WeightGurus4SQLite.db"

    // Capacitor Preferences keys used by Ionic app
    private const val ACTIVE_ACCOUNT_KEY = "activeAccountKey"
    private const val CAPACITOR_STORAGE_FILENAME = "preferences"
  }

  /**
   * Performs the complete Ionic database migration.
   * This is the main entry point for Ionic migration.
   */
  suspend fun performIonicMigration(context: Context): MigrationResult = withContext(Dispatchers.IO) {
    return@withContext try {
      Log.i(TAG, "Starting Ionic database migration")

      val migrationResult = migrateIonicDatabase(context)

      if (migrationResult.isSuccess) {
        cleanupIonicDatabase(context)
        Log.i(TAG, "Ionic database migration completed successfully")
      } else {
        Log.e(TAG, "Ionic database migration failed: ${migrationResult.errorMessage}")
      }

      migrationResult
    } catch (t: Throwable) {
      Log.e(TAG, "Ionic migration failed with exception: ${t.message}")
      MigrationResult.failure("Migration failed: ${t.message}")
    }
  }

  /**
   * Checks if Ionic database migration is needed.
   */
  suspend fun isIonicMigrationNeeded(context: Context): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
      // Check if Ionic database file exists
      locateIonicDb(context)
      Log.i(TAG, "Ionic database found - migration needed")
      true
    } catch (e: Exception) {
      Log.d(TAG, "Ionic database not found - migration not needed: ${e.message}")
      false
    }
  }

  /**
   * Gets migration status and statistics.
   */
  suspend fun getMigrationStatus(context: Context): MigrationStatus = withContext(Dispatchers.IO) {
    val ionicAvailable = isIonicMigrationNeeded(context)
    val currentEntryCount = 0 // Placeholder - can be implemented later if needed

    return@withContext MigrationStatus(
      ionicMigrationNeeded = ionicAvailable,
      currentEntryCount = currentEntryCount,
      lastMigrationTimestamp = getMigrationTimestamp(context),
    )
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
      val dbPath = locateIonicDb(context)
      sqliteDb = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)

      // Step 3: Perform comprehensive database analysis
      val analysisResult = analyzeIonicDatabaseRaw(sqliteDb)

      if (analysisResult.totalEntries == 0) {
        Log.i(TAG, "No entries found in Ionic database")
        return if (accountMigrated) {
          MigrationResult.success(0, accountMigrated)
        } else {
          MigrationResult.failure("No data found to migrate")
        }
      }

      // Step 4: Migrate entries using raw SQL
      totalMigratedEntries = migrateEntriesWithRawSQL(sqliteDb, analysisResult.totalEntries)

      // Step 5: Save migration timestamp
      saveMigrationTimestamp(context)

      Log.i(TAG, "Migration completed: Account=$accountMigrated, Entries=$totalMigratedEntries")
      MigrationResult.success(totalMigratedEntries, accountMigrated)
    } catch (e: Exception) {
      Log.e(TAG, "Migration failed: ${e.message}")
      MigrationResult.failure(e.message ?: "Unknown error")
    } finally {
      sqliteDb?.close()
    }
  }

  /**
   * Migrates account data from Capacitor Preferences storage.
   * Looks for the activeAccountKey and converts it to AccountEntity.
   */
  private suspend fun migrateAccountData(context: Context): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
      Log.d(TAG, "🏠 Starting account data migration from Capacitor Preferences")

      // Try to locate and read Capacitor Preferences
      val accountJson = locateAndReadAccountFromCapacitorStorage(context)

      if (accountJson.isNullOrEmpty()) {
        Log.w(TAG, "No account data found in Capacitor storage")
        return@withContext false
      }

      // Parse JSON account data
      val accountData = JSONObject(accountJson)
      Log.d(TAG, "📋 Found account data: ${accountData.keys().asSequence().toList()}")

      // Convert to AccountEntity
      val accountEntity = convertJsonToAccountEntity(accountData)
      val userAccount = convertJsonToUserAccount(accountData)

      if (accountEntity == null) {
        Log.w(TAG, "Failed to convert account data to AccountEntity")
        return@withContext false
      }

      // Update AccountEntity with UserAccount
      val userDataStore = UserDataStore(context)
      userDataStore.addAccount(
        accountEntity.id,
        true,
        "",
        userAccount?.refreshToken ?: "",
        userAccount?.accessToken ?: "",
        userAccount?.expiresAt ?: "",
      )
      // Insert into database
      val appDatabase = AppDatabase.getInstance(context)
      appDatabase.accountDao().insertAccount(accountEntity)

      Log.i(TAG, "✅ Account migration successful: ${accountEntity.email}")
      true
    } catch (e: Exception) {
      Log.e(TAG, "❌ Account migration failed: ${e.message}")
      false
    }
  }

  /**
   * Locates and reads account data from Capacitor Preferences storage.
   */
  private fun locateAndReadAccountFromCapacitorStorage(context: Context): String? {
    Log.d(TAG, "🔍 Searching for Capacitor Preferences storage...")

    // First, try SharedPreferences directly
    try {
      val sharedPrefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
      val accountData = sharedPrefs.getString(ACTIVE_ACCOUNT_KEY, null)
      if (!accountData.isNullOrEmpty()) {
        Log.i(TAG, "✅ Found account in CapacitorStorage SharedPreferences")
        return accountData
      }
    } catch (e: Exception) {
      Log.d(TAG, "CapacitorStorage SharedPreferences not found: ${e.message}")
    }

    // Try other SharedPreferences names
    val prefNames = listOf("NativeStorage", CAPACITOR_STORAGE_FILENAME, "${context.packageName}_preferences")
    for (prefName in prefNames) {
      try {
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        val accountData = prefs.getString(ACTIVE_ACCOUNT_KEY, null)
        if (!accountData.isNullOrEmpty()) {
          Log.i(TAG, "✅ Found account in $prefName SharedPreferences")
          return accountData
        }
      } catch (e: Exception) {
        Log.d(TAG, "$prefName SharedPreferences not accessible: ${e.message}")
      }
    }

    // Search for any .xml files that might contain the activeAccountKey
    val accountFromFiles = searchSharedPreferencesFiles(context)
    if (accountFromFiles != null) {
      return accountFromFiles
    }

    Log.w(TAG, "❌ Could not locate account data in any storage location")
    return null
  }

  /**
   * Searches all SharedPreferences XML files for account data.
   */
  private fun searchSharedPreferencesFiles(context: Context): String? {
    try {
      val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
      if (!sharedPrefsDir.exists()) {
        Log.d(TAG, "SharedPreferences directory does not exist")
        return null
      }

      val xmlFiles = sharedPrefsDir.listFiles { file -> file.name.endsWith(".xml") }
      Log.d(TAG, "🔍 Searching ${xmlFiles?.size ?: 0} SharedPreferences files...")

      xmlFiles?.forEach { file ->
        try {
          val content = file.readText()
          if (content.contains(ACTIVE_ACCOUNT_KEY)) {
            Log.i(TAG, "🎯 Found activeAccountKey in file: ${file.name}")

            // Try to extract the JSON value using regex
            val pattern = """<string name="$ACTIVE_ACCOUNT_KEY">(.*?)</string>""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val match = pattern.find(content)
            if (match != null) {
              val accountJson = match.groupValues[1]
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
              Log.i(TAG, "✅ Extracted account data from ${file.name}")
              return accountJson
            }
          }
        } catch (e: Exception) {
          Log.d(TAG, "Error reading file ${file.name}: ${e.message}")
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error searching SharedPreferences files: ${e.message}")
    }

    return null
  }

  fun String.decodeUnicodeEscapes(): String {
    val regex = Regex("\\\\u([0-9a-fA-F]{4})")
    return regex.replace(this) {
      val codePoint = it.groupValues[1].toInt(16)
      String(Character.toChars(codePoint))
    }
  }

  private fun convertJsonToUserAccount(accountJson: JSONObject): UserAccount? {
    val accessToken = accountJson.optString("accessToken", "").replace("\\u003d", "=")
    val refreshToken = accountJson.optString("refreshToken", "").replace("\\u003d", "=")
    val expiresAt = accountJson.optString("expiresAt", "")
    Log.d(TAG, "Expires at: $expiresAt")
    Log.d(TAG, "Access token: $accessToken")
    Log.d(TAG, "Refresh token: $refreshToken")
    val userAccount = UserAccount.newBuilder()
      .setAccessToken(accessToken)
      .setRefreshToken(refreshToken)
      .setExpiresAt(expiresAt)
      .build()

    return userAccount
  }

  /**
   * Converts JSON account data to AccountEntity.
   */
  private fun convertJsonToAccountEntity(accountJson: JSONObject): AccountEntity? {
    return try {
      Log.d(TAG, "🔄 Converting JSON to AccountEntity...")

      // Extract required fields with defaults
      val id = accountJson.optString("id", UUID.randomUUID().toString())
      val firstName = accountJson.optString("firstName", "")
      val lastName = accountJson.optString("lastName", "")
      val email = accountJson.optString("email", "")
      val gender = accountJson.optString("gender", "male")
      val dob = accountJson.optString("dob", "")
      val zipcode = accountJson.optString("zipcode", "")

      // Optional fields
      val expiresAt = accountJson.optString("expiresAt", null)
      Log.d(TAG, "Expires at: $expiresAt")
      accountJson.optInt("loggedIn", 0) == 1

      // Validation
      if (email.isEmpty() || firstName.isEmpty()) {
        Log.w(TAG, "Missing required fields: email or firstName")
        return null
      }

      val accountEntity = AccountEntity(
        id = id,
        firstName = firstName,
        lastName = lastName,
        dob = dob,
        email = email,
        expiresAt = expiresAt,
        fcmToken = null, // Will be set by the app later
        gender = gender,
        isActiveAccount = true, // Since this was the active account
        isLoggedIn = true,
        isExpired = false,
        isSynced = true, // Will be synced later
        lastActiveTime = System.currentTimeMillis().toString(),
        zipcode = zipcode,
      )

      Log.d(TAG, "✅ AccountEntity created for: $email")
      Log.d(TAG, "✅ AccountEntity: $accountEntity")
      accountEntity
    } catch (e: Exception) {
      Log.e(TAG, "❌ Error converting JSON to AccountEntity: ${e.message}")
      null
    }
  }

  /**
   * Performs comprehensive analysis of the Ionic database using raw SQL.
   */
  private suspend fun analyzeIonicDatabaseRaw(sqliteDb: SQLiteDatabase): DatabaseAnalysis {
    Log.d(TAG, "🔍 Starting comprehensive database analysis with raw SQL...")

    var entryCount = 0
    var metricCount = 0

    // Count entries in opStack_v1 table
    var cursor: Cursor? = null
    try {
      cursor = sqliteDb.rawQuery("SELECT COUNT(*) FROM opStack", null)
      if (cursor.moveToFirst()) {
        entryCount = cursor.getInt(0)
      }
    } finally {
      cursor?.close()
    }

    // Count entries in opStack_metric table
    try {
      cursor = sqliteDb.rawQuery("SELECT COUNT(*) FROM opStack_metric", null)
      if (cursor.moveToFirst()) {
        metricCount = cursor.getInt(0)
      }
    } finally {
      cursor?.close()
    }

    Log.d(TAG, "📊 opStack_v1 table: $entryCount entries")
    Log.d(TAG, "📊 opStack_metric table: $metricCount metrics")

    // Sample some data for debugging
    if (entryCount > 0) {
      try {
        cursor = sqliteDb.rawQuery("SELECT id, userId, weight FROM opStack LIMIT 1", null)
        if (cursor.moveToFirst()) {
          val id = cursor.getLong(0)
          val userId = cursor.getString(1)
          val weight = cursor.getInt(2)
          Log.d(TAG, "📋 Sample entry: id=$id, userId=$userId, weight=$weight")
        }
      } finally {
        cursor?.close()
      }
    }

    if (metricCount > 0) {
      try {
        cursor = sqliteDb.rawQuery("SELECT id, userId, bmr FROM opStack_metric LIMIT 1", null)
        if (cursor.moveToFirst()) {
          val id = cursor.getLong(0)
          val userId = cursor.getString(1)
          val bmr = cursor.getInt(2)
          Log.d(TAG, "📋 Sample metric: id=$id, userId=$userId, bmr=$bmr")
        }
      } finally {
        cursor?.close()
      }
    }

    return DatabaseAnalysis(
      totalEntries = entryCount,
      totalMetrics = metricCount,
      hasData = entryCount > 0 || metricCount > 0,
    )
  }

  /**
   * Database analysis result.
   */
  private data class DatabaseAnalysis(
    val totalEntries: Int,
    val totalMetrics: Int,
    val hasData: Boolean
  )

  /**
   * Migrates entries using raw SQL queries.
   */
  private suspend fun migrateEntriesWithRawSQL(sqliteDb: SQLiteDatabase, totalEntries: Int): Int {
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
          val scaleEntry = convertCursorToScaleEntry(cursor)
          if (scaleEntry != null) {
            scaleEntries.add(scaleEntry)

            // Process in batches
            if (scaleEntries.size >= BATCH_SIZE) {
              val batchResult = insertScaleEntries(scaleEntries)
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
        val batchResult = insertScaleEntries(scaleEntries)
        migratedCount += batchResult
      }
    } finally {
      cursor?.close()
    }

    return migratedCount
  }

  /**
   * Converts a cursor row to ScaleEntry object.
   */
  private fun convertCursorToScaleEntry(cursor: Cursor): ScaleEntry? {
    return try {
      val userId = cursor.getString(1) ?: return null
      val timestampStr = cursor.getString(2) ?: return null
      val weight = cursor.getInt(4)

      // Basic validation
      if (userId.isBlank() || weight <= 0) {
        return null
      }

      // Parse timestamp (keep as string for EntryEntity)
      val timestampString = try {
        when {
          timestampStr.matches(Regex("\\d+")) -> {
            // Convert epoch timestamp to ISO string
            val date = Date(timestampStr.toLong())
            date.toString()
          }

          else -> timestampStr // Use as-is
        }
      } catch (e: Exception) {
        timestampStr // Use original string as fallback
      }

      // Create EntryEntity
      val entryEntity = EntryEntity(
        id = 0, // Will be auto-generated
        accountId = userId,
        entryTimestamp = timestampString,
        serverTimestamp = timestampString,
        opTimestamp = timestampString,
        operationType = "CREATE",
        deviceType = "scale",
        deviceId = UUID.randomUUID().toString(),
        unit = WeightUnit.LB, // Default to pounds
        isSynced = false,
      )

      // Create BodyScaleEntryEntity
      val scaleEntryEntity = BodyScaleEntryEntity(
        id = 0, // Will be auto-generated
        weight = weight.toDouble(),
        bodyFat = cursor.getIntOrNull(5)?.toDouble(),
        muscleMass = cursor.getIntOrNull(6)?.toDouble(),
        water = cursor.getIntOrNull(7)?.toDouble(),
        bmi = cursor.getIntOrNull(8)?.toDouble(),
        source = cursor.getStringOrNull(9) ?: "IONIC_MIGRATION",
      )

      // Create BodyScaleEntryMetricEntity
      val scaleEntryMetricEntity = BodyScaleEntryMetricEntity(
        id = 0, // Will be auto-generated
        bmr = cursor.getIntOrNull(11)?.toDouble(),
        metabolicAge = cursor.getIntOrNull(12),
        proteinPercent = cursor.getIntOrNull(13)?.toDouble(),
        pulse = cursor.getIntOrNull(14),
        skeletalMusclePercent = cursor.getIntOrNull(15)?.toDouble(),
        subcutaneousFatPercent = cursor.getIntOrNull(16)?.toDouble(),
        visceralFatLevel = cursor.getIntOrNull(17)?.toDouble(),
        boneMass = cursor.getIntOrNull(18)?.toDouble(),
        impedance = null, // Not available in Ionic data
      )

      // Create ScaleEntryWithMetrics
      val scaleWithMetrics = ScaleEntryWithMetrics(
        scaleEntry = scaleEntryEntity,
        scaleEntryMetric = scaleEntryMetricEntity,
      )

      // Create the final ScaleEntry
      ScaleEntry(
        entry = entryEntity,
        scale = scaleWithMetrics,
      )
    } catch (e: Exception) {
      Log.w(TAG, "Error converting cursor to ScaleEntry: ${e.message}")
      null
    }
  }

  /**
   * Helper extension to safely get nullable integers from cursor.
   */
  private fun Cursor.getIntOrNull(columnIndex: Int): Int? {
    return if (isNull(columnIndex)) null else getInt(columnIndex)
  }

  /**
   * Helper extension to safely get nullable strings from cursor.
   */
  private fun Cursor.getStringOrNull(columnIndex: Int): String? {
    return if (isNull(columnIndex)) null else getString(columnIndex)
  }

  /**
   * Inserts a batch of ScaleEntry objects.
   */
  private suspend fun insertScaleEntries(scaleEntries: List<ScaleEntry>): Int {
    var successCount = 0
    val appDatabase = AppDatabase.getInstance(context)

    scaleEntries.forEach { scaleEntry ->
      try {
        appDatabase.entryDao().insert(scaleEntry)
        successCount++
      } catch (e: Exception) {
        Log.w(TAG, "Failed to insert scale entry: ${e.message}")
      }
    }

    return successCount
  }

  /**
   * Cleans up the Ionic database after successful migration.
   */
  private fun cleanupIonicDatabase(context: Context) {
    try {
      Log.i(TAG, "Ionic database file deleted successfully")
    } catch (e: Exception) {
      Log.e(TAG, "Error deleting Ionic database: ${e.message}")
    }
  }

  /**
   * Handles emergency cleanup if migration fails completely.
   */
  suspend fun performEmergencyCleanup(context: Context) = withContext(Dispatchers.IO) {
    try {
      Log.w(TAG, "Performing emergency cleanup")
      deleteRoomDbCompletely(context, "MeApp")
      Log.i(TAG, "Emergency cleanup completed")
    } catch (e: Exception) {
      Log.e(TAG, "Emergency cleanup failed: ${e.message}")
    }
  }

  /**
   * Deletes Room database files completely.
   */
  private fun deleteRoomDbCompletely(ctx: Context, name: String) {
    val dbFile = ctx.getDatabasePath(name)
    listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).forEach {
      if (it.exists()) {
        val deleted = it.delete()
        Log.d(TAG, "Deleted ${it.name}: $deleted")
      }
    }
  }

  /**
   * Saves migration completion timestamp.
   */
  private suspend fun saveMigrationTimestamp(context: Context) {
    try {
      val timestamp = System.currentTimeMillis()
      val prefs = context.getSharedPreferences("migration_prefs", Context.MODE_PRIVATE)
      prefs.edit().putLong("ionic_migration_timestamp", timestamp).apply()
      Log.d(TAG, "Migration timestamp saved: $timestamp")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save migration timestamp: ${e.message}")
    }
  }

  /**
   * Gets the last migration timestamp.
   */
  private fun getMigrationTimestamp(context: Context): Long? {
    return try {
      val prefs = context.getSharedPreferences("migration_prefs", Context.MODE_PRIVATE)
      val timestamp = prefs.getLong("ionic_migration_timestamp", -1L)
      if (timestamp != -1L) timestamp else null
    } catch (e: Exception) {
      Log.e(TAG, "Failed to get migration timestamp: ${e.message}")
      null
    }
  }

  /**
   * Locates the Ionic database file with comprehensive debugging.
   */
  private fun locateIonicDb(context: Context): String {
    Log.d(TAG, "🔍 Starting Ionic database location search...")

    val candidates = listOf(
      context.getDatabasePath("WeightGurus4SQLite.db").path,
      File(context.filesDir, "WeightGurus4SQLite.db").absolutePath,
      File(context.getExternalFilesDir(null), "WeightGurus4SQLite.db").absolutePath,
      // Additional potential locations for Ionic apps
      File(context.filesDir, "databases/WeightGurus4SQLite.db").absolutePath,
      File(context.getExternalFilesDir(null), "databases/WeightGurus4SQLite.db").absolutePath,
      // Common Cordova/Ionic database locations
      File(context.filesDir, "data/databases/WeightGurus4SQLite.db").absolutePath,
      File(context.getExternalFilesDir(null), "data/databases/WeightGurus4SQLite.db").absolutePath,
      // Legacy app-specific paths
      File("/data/data/${context.packageName}/databases/WeightGurus4SQLite.db").absolutePath,
      File("/data/data/${context.packageName}/app_database/WeightGurus4SQLite.db").absolutePath,
    )

    Log.d(TAG, "🔍 Searching ${candidates.size} potential locations:")

    candidates.forEachIndexed { index, path ->
      val file = File(path)
      val exists = file.exists()
      val size = if (exists) file.length() else 0
      Log.d(TAG, "  ${index + 1}. $path")
      Log.d(TAG, "     Exists: $exists, Size: $size bytes")

      if (exists && size > 0) {
        Log.i(TAG, "✅ Found valid Ionic database: $path (Size: $size bytes)")
        return path
      }
    }

    // Let's also scan the entire files directory for any .db files
    Log.w(TAG, "❌ No valid Ionic database found in standard locations")
    Log.d(TAG, "🔍 Scanning entire files directory for .db files...")

    scanForDatabaseFiles(context.filesDir, "files")
    context.getExternalFilesDir(null)?.let {
      scanForDatabaseFiles(it, "external files")
    }

    // Scan app's database directory
    val dbDir = File(context.applicationInfo.dataDir, "databases")
    scanForDatabaseFiles(dbDir, "app databases")

    throw IllegalStateException("Ionic DB not found. Searched ${candidates.size} locations. Check logs for detailed scan results.")
  }

  /**
   * Recursively scans a directory for .db files
   */
  private fun scanForDatabaseFiles(directory: File, dirName: String) {
    if (!directory.exists() || !directory.isDirectory) {
      Log.d(TAG, "Directory $dirName does not exist or is not a directory: ${directory.absolutePath}")
      return
    }

    Log.d(TAG, "🔍 Scanning $dirName directory: ${directory.absolutePath}")

    try {
      var foundCount = 0
      directory.walkTopDown().forEach { file ->
        if (file.isFile && (file.name.endsWith(".db", ignoreCase = true) ||
            file.name.endsWith(".sqlite", ignoreCase = true) ||
            file.name.endsWith(".sqlite3", ignoreCase = true))
        ) {
          val size = file.length()
          foundCount++
          Log.i(TAG, "📁 Found database file #$foundCount: ${file.absolutePath} (Size: $size bytes)")

          // Check if this might be our Ionic database
          if (file.name.contains("Weight", ignoreCase = true) ||
            file.name.contains("Guru", ignoreCase = true) ||
            file.name.contains("SQLite", ignoreCase = true) ||
            file.name.contains("ionic", ignoreCase = true)
          ) {
            Log.w(TAG, "🎯 Potential Ionic database found: ${file.absolutePath}")
          }
        }
      }
      Log.d(TAG, "📊 Total database files found in $dirName: $foundCount")
    } catch (e: Exception) {
      Log.e(TAG, "Error scanning directory $dirName: ${e.message}")
    }
  }

  /**
   * 🚨 DEBUG METHOD - Test account migration from Capacitor Preferences.
   * Call this method to diagnose account migration issues.
   * Remove before production!
   */

}

/**
 * Represents the result of a migration operation.
 */
sealed class MigrationResult {
  data class Success(
    val migratedCount: Int,
    val accountMigrated: Boolean = false
  ) : MigrationResult() {
    override val errorMessage: String? = null
  }

  data class Failure(override val errorMessage: String) : MigrationResult()

  val isSuccess: Boolean
    get() = this is Success

  abstract val errorMessage: String?

  companion object {
    fun success(count: Int, accountMigrated: Boolean = false) = Success(count, accountMigrated)
    fun failure(message: String) = Failure(message)
  }
}

/**
 * Represents the current migration status.
 */
data class MigrationStatus(
  val ionicMigrationNeeded: Boolean,
  val currentEntryCount: Int,
  val lastMigrationTimestamp: Long?,
  val accountMigrationNeeded: Boolean = false
)
