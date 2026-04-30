package com.dmdbrands.gurus.weight.migration.helper

import android.content.Context
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog

/**
 * Helper class for reading and managing Capacitor Preferences storage.
 * Handles the interaction with SharedPreferences used by Capacitor/Ionic apps.
 */
object CapacitorStorageHelper {

  private const val TAG = "CapacitorStorageHelper"

  // Capacitor Preferences keys used by Ionic app
  const val ACTIVE_ACCOUNT_KEY = "activeAccountKey"
  private const val PAIRED_SCALES_KEY = "pairedScalesKey"
  private const val CAPACITOR_STORAGE_FILENAME = "preferences"

  /**
   * Locates and reads account data from Capacitor Preferences storage.
   */
  fun locateAndReadAccountFromCapacitorStorage(context: Context): String? {
    AppLog.d(TAG, "Searching for Capacitor Preferences storage")

    try {
      val sharedPrefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
      val accountData = sharedPrefs.getString(ACTIVE_ACCOUNT_KEY, null)
      if (!accountData.isNullOrEmpty()) {
        AppLog.i(TAG, "Found account in CapacitorStorage SharedPreferences")
        return accountData
      }
    } catch (e: Exception) {
      AppLog.w(TAG, "CapacitorStorage SharedPreferences not found: ${e.message}")
    }
    return null
  }

  fun locateAndReadIntegrationSettings(context: Context, integrationKey: String): Map<String, String> {
    return try {
      val sharedPrefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
      val resultMap = sharedPrefs.all.filter { it.key.contains(integrationKey) }
      val result = resultMap.keys
        .mapNotNull { key ->
          sharedPrefs.getString(key, null)?.let { value ->
            key.removeSuffix("-${integrationKey}") to value
          }
        }
        .toMap()
      AppLog.d(TAG, "Found ${result.size} integration entries for key: $integrationKey")
      result
    } catch (e: Exception) {
      AppLog.w(TAG, "CapacitorStorage SharedPreferences not found: ${e.message}")
      emptyMap()
    }
  }

  fun locateAndReadPairedScalesFromCapacitorStorage(context: Context): Map<String, String> {
    return try {
      val sharedPrefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
      val pairedScalesKey = sharedPrefs.all.filter { it.key.contains(PAIRED_SCALES_KEY) }
      val pairedScales = pairedScalesKey.keys
        .mapNotNull { key ->
          sharedPrefs.getString(key, null)?.let { value ->
            key.removeSuffix("-pairedScalesKey") to value
          }
        }
        .toMap()
      AppLog.d(TAG, "Found ${pairedScales.size} paired scales entries")
      pairedScales
    } catch (e: Exception) {
      AppLog.w(TAG, "CapacitorStorage SharedPreferences not found: ${e.message}")
      emptyMap()
    }
  }

  /**
   * Locates and reads all timestampkey entries from Capacitor Storage.
   * Ionic stores keys as "timestampkey-{userId}" (prefix). Returns map of userId -> timestamp string.
   */
  fun locateAndReadTimestampKeyFromCapacitorStorage(context: Context): Map<String, String> {
    return try {
      val sharedPrefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
      val prefix = "timestampkey-"
      val result = sharedPrefs.all.keys
        .filter { it.startsWith(prefix) }
        .mapNotNull { key ->
          sharedPrefs.getString(key, null)?.takeIf { it.isNotBlank() }?.let { timestamp ->
            key.removePrefix(prefix) to timestamp
          }
        }
        .toMap()
      AppLog.d(TAG, "Found ${result.size} timestampkey entries")
      result
    } catch (e: Exception) {
      AppLog.w(TAG, "CapacitorStorage SharedPreferences not found: ${e.message}")
      emptyMap()
    }
  }

  /**
   * Reads the last sync timestamp for the given account from Capacitor Storage.
   * Ionic stores this as key "timestampkey-{accountId}".
   * @return The timestamp string, or null if not found.
   */
  fun getLastSyncTimestampForAccount(context: Context, accountId: String): String? {
    return try {
      val sharedPrefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
      val key = "timestampkey-$accountId"
      sharedPrefs.getString(key, null)?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
      AppLog.w(TAG, "Could not read timestampkey for $accountId: ${e.message}")
      null
    }
  }

  /**
   * Locates and reads theme mode data from Capacitor Preferences storage.
   */
  fun locateAndReadThemeModeFromCapacitorStorage(context: Context): Map<String, String> {
    return try {
      AppLog.d(TAG, "Searching for theme mode in Capacitor Preferences storage")
      val sharedPrefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
      val ionicThemeModeKeys = sharedPrefs.all.filter { it.key.contains("colorMode") }
      val themeModeKeys: Map<String, String> = ionicThemeModeKeys.keys
        .mapNotNull { key ->
          sharedPrefs.getString(key, null)?.let { value ->
            key.removeSuffix("-colorMode") to value
          }
        }
        .toMap()

      AppLog.d(TAG, "Found ${themeModeKeys.size} theme mode entries")
      themeModeKeys
    } catch (e: Exception) {
      AppLog.w(TAG, "CapacitorStorage SharedPreferences not found: ${e.message}")
      emptyMap()
    }
  }
}
