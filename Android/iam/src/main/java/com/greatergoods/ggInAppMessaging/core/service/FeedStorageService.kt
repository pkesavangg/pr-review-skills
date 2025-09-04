package com.greatergoods.ggInAppMessaging.core.service

import com.greatergoods.ggInAppMessaging.core.storage.FeedSettingsDataStore
import com.greatergoods.ggInAppMessaging.core.utilities.IAMLogger
import com.greatergoods.ggInAppMessaging.domain.models.FeedSetting
import kotlinx.coroutines.flow.Flow

/**
 * Feed storage service for managing local feed data using Proto DataStore
 * Provides type-safe storage for feed settings with reactive updates
 */
class FeedStorageService(
  private val feedSettingsDataStore: FeedSettingsDataStore
) {

  private val tag = "FeedStorageService"

  /**
   * Get feed settings flow for reactive updates
   */
  val feedSettingsFlow: Flow<FeedSetting> = feedSettingsDataStore.feedSettingsFlow

  /**
   * Get current feed settings
   */
  suspend fun getFeedSettings(): FeedSetting = feedSettingsDataStore.getFeedSettings()

  /**
   * Update feed settings
   */
  suspend fun updateFeedSettings(feedSetting: FeedSetting, accountId: String = "") {
    try {
      IAMLogger.d(tag, "Updating feed settings for account: $accountId")
      feedSettingsDataStore.updateFeedSettings(feedSetting, accountId)
    } catch (e: Exception) {
      IAMLogger.e(tag, "Failed to update feed settings", e.toString())
      throw e
    }
  }

  /**
   * Update pop-up message setting
   */
  suspend fun updatePopupMessageSetting(showPopupMessage: Boolean, accountId: String = "") {
    try {
      IAMLogger.d(tag, "Updating popup message setting: $showPopupMessage")
      feedSettingsDataStore.updatePopupMessageSetting(showPopupMessage, accountId)
    } catch (e: Exception) {
      IAMLogger.e(tag, "Failed to update popup message setting", e.toString())
      throw e
    }
  }

  /**
   * Update notification badge setting
   */
  suspend fun updateNotificationBadgeSetting(showNotificationBadge: Boolean, accountId: String = "") {
    try {
      IAMLogger.d(tag, "Updating notification badge setting: $showNotificationBadge")
      feedSettingsDataStore.updateNotificationBadgeSetting(showNotificationBadge, accountId)
    } catch (e: Exception) {
      IAMLogger.e(tag, "Failed to update notification badge setting", e.toString())
      throw e
    }
  }

  /**
   * Get pop-up message setting
   */
  suspend fun getPopupMessageSetting(): Boolean = feedSettingsDataStore.getPopupMessageSetting()

  /**
   * Get notification badge setting
   */
  suspend fun getNotificationBadgeSetting(): Boolean = feedSettingsDataStore.getNotificationBadgeSetting()

  /**
   * Clear all feed settings
   */
  suspend fun clearAll(): Boolean {
    return try {
      IAMLogger.d(tag, "Clearing all feed settings")
      feedSettingsDataStore.clearData()
      true
    } catch (e: Exception) {
      IAMLogger.e(tag, "Failed to clear feed settings", e.toString())
      false
    }
  }
}
