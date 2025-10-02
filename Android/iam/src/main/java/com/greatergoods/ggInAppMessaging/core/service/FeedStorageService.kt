package com.greatergoods.ggInAppMessaging.core.service

import com.greatergoods.ggInAppMessaging.core.storage.FeedSettingsDataStore
import com.greatergoods.ggInAppMessaging.core.utilities.IAMLogger
import com.greatergoods.ggInAppMessaging.domain.models.FeedSetting
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Feed storage service for managing local feed data using Proto DataStore
 * Provides type-safe storage for feed settings with reactive updates
 */
class FeedStorageService @Inject constructor(
  private val feedSettingsDataStore: FeedSettingsDataStore
) {

  private val tag = "FeedStorageService"

  /**
   * Get feed settings flow for reactive updates (all accounts)
   */
  val feedSettingsFlow: Flow<Map<String, FeedSetting>> = feedSettingsDataStore.feedSettingsFlow

  /**
   * Get feed settings for a specific account
   * @param accountId The account ID to get settings for
   */
  suspend fun getFeedSettings(accountId: String): FeedSetting = feedSettingsDataStore.getFeedSettings(accountId)

  /**
   * Update feed settings for a specific account
   * @param feedSetting The new feed settings to store
   * @param accountId The account ID for which these settings apply
   */
  suspend fun updateFeedSettings(feedSetting: FeedSetting, accountId: String) {
    try {
      IAMLogger.d(tag, "Updating feed settings for account: $accountId")
      feedSettingsDataStore.updateFeedSettings(feedSetting, accountId)
    } catch (e: Exception) {
      IAMLogger.e(tag, "Failed to update feed settings for account: $accountId", e.toString())
      throw e
    }
  }

  /**
   * Update pop-up message setting for a specific account
   * @param showPopupMessage Whether to show pop-up messages
   * @param accountId The account ID for which this setting applies
   */
  suspend fun updatePopupMessageSetting(showPopupMessage: Boolean, accountId: String) {
    try {
      IAMLogger.d(tag, "Updating popup message setting: $showPopupMessage for account: $accountId")
      feedSettingsDataStore.updatePopupMessageSetting(showPopupMessage, accountId)
    } catch (e: Exception) {
      IAMLogger.e(tag, "Failed to update popup message setting for account: $accountId", e.toString())
      throw e
    }
  }

  /**
   * Update notification badge setting for a specific account
   * @param showNotificationBadge Whether to show notification badges
   * @param accountId The account ID for which this setting applies
   */
  suspend fun updateNotificationBadgeSetting(showNotificationBadge: Boolean, accountId: String) {
    try {
      IAMLogger.d(tag, "Updating notification badge setting: $showNotificationBadge for account: $accountId")
      feedSettingsDataStore.updateNotificationBadgeSetting(showNotificationBadge, accountId)
    } catch (e: Exception) {
      IAMLogger.e(tag, "Failed to update notification badge setting for account: $accountId", e.toString())
      throw e
    }
  }

  /**
   * Get pop-up message setting for a specific account
   * @param accountId The account ID to get the setting for
   */
  suspend fun getPopupMessageSetting(accountId: String): Boolean = feedSettingsDataStore.getPopupMessageSetting(accountId)

  /**
   * Get notification badge setting for a specific account
   * @param accountId The account ID to get the setting for
   */
  suspend fun getNotificationBadgeSetting(accountId: String): Boolean = feedSettingsDataStore.getNotificationBadgeSetting(accountId)

  /**
   * Get the last time a feed modal was triggered for a specific account
   * @param accountId The account ID to get the timestamp for
   */
  suspend fun getFeedLastTriggeredAt(accountId: String): Long? = feedSettingsDataStore.getFeedLastTriggeredAt(accountId)

  /**
   * Store the last time a feed modal was triggered for a specific account
   * @param timestamp The timestamp when the feed modal was last triggered
   * @param accountId The account ID for which this applies
   */
  suspend fun storeFeedLastTriggeredAt(timestamp: Long, accountId: String) {
    try {
      IAMLogger.d(tag, "Storing feed last triggered at: $timestamp for account: $accountId")
      feedSettingsDataStore.storeFeedLastTriggeredAt(timestamp, accountId)
    } catch (e: Exception) {
      IAMLogger.e(tag, "Failed to store feed last triggered at for account: $accountId", e.toString())
      throw e
    }
  }

  /**
   * Clear feed settings for a specific account
   * @param accountId The account ID to clear settings for
   */
  suspend fun clearAccountSettings(accountId: String): Boolean {
    return try {
      IAMLogger.d(tag, "Clearing feed settings for account: $accountId")
      feedSettingsDataStore.clearAccountSettings(accountId)
      true
    } catch (e: Exception) {
      IAMLogger.e(tag, "Failed to clear feed settings for account: $accountId", e.toString())
      false
    }
  }

  /**
   * Get all account IDs that have feed settings
   * @return List of account IDs with settings
   */
  suspend fun getAccountIds(): List<String> = feedSettingsDataStore.getAccountIds()

  /**
   * Check if settings exist for a specific account
   * @param accountId The account ID to check
   * @return True if settings exist for the account
   */
  suspend fun hasAccountSettings(accountId: String): Boolean = feedSettingsDataStore.hasAccountSettings(accountId)

  /**
   * Clear all feed settings (all accounts)
   */
  suspend fun clearAll(): Boolean {
    return try {
      IAMLogger.d(tag, "Clearing all feed settings")
      feedSettingsDataStore.clearData()
      true
    } catch (e: Exception) {
      IAMLogger.e(tag, "Failed to clear all feed settings", e.toString())
      false
    }
  }
}
