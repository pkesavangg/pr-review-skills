package com.greatergoods.ggInAppMessaging.core.service

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import android.content.SharedPreferences

/**
 * Feed storage service for managing local feed data
 * Android equivalent of Angular feed-storage.service.ts
 */
@Singleton
class FeedStorageService @Inject constructor(
  @ApplicationContext private val context: Context
) {

  private val tag = "FeedStorageService"
  private val prefs: SharedPreferences = context.getSharedPreferences(
    FEED_PREFERENCES_NAME,
    Context.MODE_PRIVATE,
  )

  companion object {
    private const val FEED_PREFERENCES_NAME = "gg_feed_preferences"
  }

  /**
   * Set a value in local storage
   */
  suspend fun setValue(key: String, value: String): Boolean {
    return try {
      prefs.edit().putString(key, value).apply()
      true
    } catch (e: Exception) {
      false
    }
  }

  /**
   * Get a value from local storage
   */
  suspend fun getValue(key: String): FeedStorageResult? {
    return try {
      val value = prefs.getString(key, null)
      if (value != null) {
        FeedStorageResult(key, value)
      } else {
        null
      }
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Remove a value from local storage
   */
  suspend fun removeValue(key: String): Boolean {
    return try {
      prefs.edit().remove(key).apply()
      true
    } catch (e: Exception) {
      false
    }
  }

  /**
   * Clear all stored values
   */
  suspend fun clearAll(): Boolean {
    return try {
      prefs.edit().clear().apply()
      true
    } catch (e: Exception) {
      false
    }
  }
}

/**
 * Result class for storage operations
 */
data class FeedStorageResult(
  val key: String,
  val value: String
)
