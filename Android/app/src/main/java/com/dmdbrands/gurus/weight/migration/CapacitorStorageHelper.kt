package com.dmdbrands.gurus.weight.migration

import android.content.Context
import android.util.Log

/**
 * Helper class for reading and managing Capacitor Preferences storage.
 * Handles the interaction with SharedPreferences used by Capacitor/Ionic apps.
 */
object CapacitorStorageHelper {

  private const val TAG = "CapacitorStorageHelper"

  // Capacitor Preferences keys used by Ionic app
  private const val ACTIVE_ACCOUNT_KEY = "activeAccountKey"
  private const val CAPACITOR_STORAGE_FILENAME = "preferences"

  /**
   * Locates and reads account data from Capacitor Preferences storage.
   */
  fun locateAndReadAccountFromCapacitorStorage(context: Context): String? {
    Log.d(TAG, "🔍 Searching for Capacitor Preferences storage...")

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
    return null
  }

  /**
   * Locates and reads theme mode data from Capacitor Preferences storage.
   */
  fun locateAndReadThemeModeFromCapacitorStorage(context: Context): Map<String, String> {
    return try {
      Log.d(TAG, "🔍 Searching for theme mode in Capacitor Preferences storage...")
      val sharedPrefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
      val ionicThemeModeKeys = sharedPrefs.all.filter { it.key.contains("colorMode") }
      val themeModeKeys: Map<String, String> = ionicThemeModeKeys.keys
        .mapNotNull { key ->
          sharedPrefs.getString(key, null)?.let { value ->
            key.removeSuffix("-colorMode") to value
          }
        }
        .toMap()

      Log.d(TAG, "Found ${themeModeKeys.size} theme mode entries")
      themeModeKeys
    } catch (e: Exception) {
      Log.d(TAG, "CapacitorStorage SharedPreferences not found: ${e.message}")
      emptyMap()
    }
  }
}
