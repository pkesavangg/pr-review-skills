package com.greatergoods.ggInAppMessaging.domain.services

import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.domain.models.FeedSetting
import kotlinx.coroutines.flow.Flow

/**
 * Interface for In-App Messaging service
 * Provides abstraction for feed management and messaging functionality
 */
interface IInAppMessagingService {
    /**
     * Set account ID for user-specific settings
     */
    fun setAccountId(accountId: String)

    /**
     * Get feed settings flow for reactive updates (all accounts)
     */
    val feedSettingsFlow: Flow<Map<String, FeedSetting>>

    /**
     * Get feed items flow for reactive updates
     */
    val feedItems: Flow<List<FeedItem>>

    /**
     * Check and initialize stored feed notification settings
     */
    suspend fun checkStoredFeedNotification()

    /**
     * Store feed notification settings
     */
    suspend fun storeFeedNotificationSetting(feedSetting: FeedSetting)

    /**
     * Get stored feed notification settings
     */
    suspend fun getStoredFeedNotificationSetting(): FeedSetting?

    /**
     * Update pop-up message setting
     */
    suspend fun updatePopupMessageSetting(showPopupMessage: Boolean)

    /**
     * Update notification badge setting
     */
    suspend fun updateNotificationBadgeSetting(showNotificationBadge: Boolean)

    /**
     * Get pop-up message setting
     */
    suspend fun getPopupMessageSetting(): Boolean

    /**
     * Get notification badge setting
     */
    suspend fun getNotificationBadgeSetting(): Boolean

    /**
     * Emit feed notification change
     */
    suspend fun emitFeedNotificationChange()

    /**
     * Get unread feed count
     */
    suspend fun getUnreadFeedCount(): Int

    /**
     * Check if feed modal should be triggered
     */
    suspend fun checkFeedModalTrigger(): Boolean

    /**
     * Clear feed data
     */
    fun clearFeedData()

    /**
     * Set feed items received from main app's FeedService
     */
    suspend fun setFeedItems(feedItems: List<FeedItem>)

    /**
     * Get feed items
     */
    suspend fun getFeedItems(): List<FeedItem>

    /**
     * Mark a feed item as read
     * @param elementId The element ID of the feed item to mark as read
     * @param actionType The action type (e.g., "read")
     */
    suspend fun markFeedAsRead(elementId: String, actionType: String = "read")

    /**
     * Send update feed event for tracking
     * @param feedItem The feed item to update
     * @param actionType The action type
     * @param variationId Optional variation ID
     */
    suspend fun emitFeedUpdate(feedItem: FeedItem, actionType: String, variationId: Int? = null)
}
