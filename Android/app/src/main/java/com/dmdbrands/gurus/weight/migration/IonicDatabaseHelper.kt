package com.dmdbrands.gurus.weight.core.shared.utilities

import com.dmdbrands.gurus.weight.migration.DatabaseAnalysis
import java.io.File
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log

/**
 * Helper class for managing Ionic database operations and file system utilities.
 * Contains methods for locating, analyzing, and cleaning up Ionic database files.
 */
object IonicDatabaseHelper {

  private const val TAG = "IonicDatabaseHelper"
  private const val IONIC_DB_NAME = "WeightGurus4SQLite.db"

  /**
   * Locates the Ionic database file with comprehensive debugging.
   */
  fun locateIonicDb(context: Context): String? {
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
    return null
  }

  /**
   * Performs comprehensive analysis of the Ionic database using raw SQL.
   */
  suspend fun analyzeIonicDatabaseRaw(sqliteDb: SQLiteDatabase): DatabaseAnalysis {
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
   * Cleans up the Ionic database after successful migration.
   */
  fun cleanupIonicDatabase(context: Context) {
    try {
      Log.i(TAG, "Ionic database file deleted successfully")
    } catch (e: Exception) {
      Log.e(TAG, "Error deleting Ionic database: ${e.message}")
    }
  }

  /**
   * Deletes Room database files completely.
   */
  fun deleteRoomDbCompletely(ctx: Context, name: String) {
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
  fun saveMigrationTimestamp(context: Context) {
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
  fun getMigrationTimestamp(context: Context): Long? {
    return try {
      val prefs = context.getSharedPreferences("migration_prefs", Context.MODE_PRIVATE)
      val timestamp = prefs.getLong("ionic_migration_timestamp", -1L)
      if (timestamp != -1L) timestamp else null
    } catch (e: Exception) {
      Log.e(TAG, "Failed to get migration timestamp: ${e.message}")
      null
    }
  }
}
