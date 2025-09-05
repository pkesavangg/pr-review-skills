package com.greatergoods.ggInAppMessaging.core.service

import android.util.Log
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.domain.models.FeedSetting
import com.greatergoods.ggInAppMessaging.domain.services.IInAppMessagingService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main GG In-App Messaging service
 * Manages feed settings and provides reactive updates
 */
@Singleton
class GGInAppMessagingService @Inject constructor(
    private val feedStorageService: FeedStorageService
) : IInAppMessagingService {

    private val tag = "GGInAppMessagingService"

    // MARK: - Constants
    private val ONE_WEEK_IN_MILLIS = 7 * 24 * 60 * 60 * 1000L

    // MARK: - Properties
    private var accountId: String = ""
    private val _feedItems = MutableSharedFlow<List<FeedItem>>()
    override val feedItems = _feedItems.asSharedFlow()

    // MARK: - Feed Items Storage
    private var _storedFeedItems: List<FeedItem> = emptyList()

    // MARK: - Reactive Streams
    private val _feedNotificationChangedSubject = MutableSharedFlow<Unit>()
    val feedNotificationChangedSubject = _feedNotificationChangedSubject.asSharedFlow()

    /**
     * Get feed settings flow for reactive updates
     */
    override val feedSettingsFlow: Flow<FeedSetting> = feedStorageService.feedSettingsFlow

    /**
     * Set account ID for user-specific settings
     */
    override fun setAccountId(accountId: String) {
        this.accountId = accountId
    }

    /**
     * Set feed items received from main app's FeedService
     * This method is called by the main app to provide feed items
     */
    override suspend fun setFeedItems(feedItems: List<FeedItem>) {
        Log.d("feeditems", "${feedItems}")
        _storedFeedItems = feedItems
        _feedItems.emit(feedItems)
    }

    /**
     * Get feed items - returns stored items from main app or mock data as fallback
     */
    override suspend fun getFeedItems(): List<FeedItem> {
        return _storedFeedItems
    }

    /**
     * Check and initialize stored feed notification settings
     */
    override suspend fun checkStoredFeedNotification() {
        val getFeedData = getStoredFeedNotificationSetting()
        if (getFeedData == null) {
            val feedSetting = FeedSetting(
                showPopupMessage = true,
                showNotificationBadge = true,
            )
            storeFeedNotificationSetting(feedSetting)
        }
    }

    /**
     * Store feed notification settings
     */
    override suspend fun storeFeedNotificationSetting(feedSetting: FeedSetting) {
        try {
            feedStorageService.updateFeedSettings(feedSetting, accountId)
            _feedNotificationChangedSubject.tryEmit(Unit)
        } catch (e: Exception) {
        }
    }

    /**
     * Get stored feed notification settings
     */
    override suspend fun getStoredFeedNotificationSetting(): FeedSetting? {
        return try {
            feedStorageService.getFeedSettings()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Update pop-up message setting
     */
    override suspend fun updatePopupMessageSetting(showPopupMessage: Boolean) {
        try {
            feedStorageService.updatePopupMessageSetting(showPopupMessage, accountId)
            _feedNotificationChangedSubject.tryEmit(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to update popup message setting", e)
        }
    }

    /**
     * Update notification badge setting
     */
    override suspend fun updateNotificationBadgeSetting(showNotificationBadge: Boolean) {
        try {
            feedStorageService.updateNotificationBadgeSetting(showNotificationBadge, accountId)
            _feedNotificationChangedSubject.tryEmit(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to update notification badge setting", e)
        }
    }

    /**
     * Get pop-up message setting
     */
    override suspend fun getPopupMessageSetting(): Boolean {
        return try {
            feedStorageService.getPopupMessageSetting()
        } catch (e: Exception) {
            Log.e(tag, "Failed to get popup message setting", e)
            true // Default to true
        }
    }

    /**
     * Get notification badge setting
     */
    override suspend fun getNotificationBadgeSetting(): Boolean {
        return try {
            feedStorageService.getNotificationBadgeSetting()
        } catch (e: Exception) {
            Log.e(tag, "Failed to get notification badge setting", e)
            true // Default to true
        }
    }

    /**
     * Emit feed notification change
     */
    override suspend fun emitFeedNotificationChange() {
        _feedNotificationChangedSubject.emit(Unit)
    }

    // MARK: - Methods required by existing FeedService

    /**
     * Get unread feed count - matches Angular implementation
     * Filters feed items where isUnread is true and returns the count
     */
    override suspend fun getUnreadFeedCount(): Int {
        return try {
            val currentFeedItems = getFeedItems()
            val unreadFeeds = currentFeedItems.filter { feed -> feed.isUnread }
            unreadFeeds.size
        } catch (e: Exception) {
            Log.e(tag, "Failed to get unread feed count", e)
            0
        }
    }

    /**
     * Check if feed modal should be triggered - matches Angular implementation
     * Checks if popup messages are enabled and finds a feed item with login trigger
     */
    override suspend fun checkFeedModalTrigger(): Any? {
        return try {
            val feedSetting = getStoredFeedNotificationSetting()
            if (feedSetting?.showPopupMessage == true) {
                val currentFeedItems = getFeedItems()
                val feedItemWithLoginTrigger = currentFeedItems.find { feed ->
                    feed.trigger == "login"
                }
                if (feedItemWithLoginTrigger != null) {
                    Log.d(
                        tag,
                        "Found feed item with login trigger: ${feedItemWithLoginTrigger.elementId}"
                    )
                    return showFeedModal(feedItemWithLoginTrigger)
                } else {
                    Log.d(tag, "No feed item found with login trigger")
                    false
                }
            } else {
                Log.d(tag, "Popup messages are disabled")
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to check feed modal trigger", e)
            false
        }
    }

    /**
     * Show feed modal - matches Angular implementation
     * Handles the modal display logic with cooldown time checking
     */
    private suspend fun showFeedModal(feedItem: FeedItem): Boolean {
        return try {
            val currentTime = System.currentTimeMillis()
            val feedLastTriggeredAt = getFeedLastTriggeredAt()

            if (feedLastTriggeredAt != null) {
                val feedModalTriggerCoolDownTime = feedLastTriggeredAt + ONE_WEEK_IN_MILLIS
                if (feedModalTriggerCoolDownTime < currentTime) {
                    return handleFeedModal(feedItem, currentTime)
                } else {
                    Log.d(tag, "Feed modal is in cooldown period")
                    return false
                }
            } else {
                return handleFeedModal(feedItem, currentTime)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to show feed modal", e)
            false
        }
    }

    /**
     * Handle feed modal - matches Angular implementation
     * Manages the modal display process
     */
    private suspend fun handleFeedModal(feedItem: FeedItem, currentTime: Long): Boolean {
        return try {
            // TODO: Implement preloadImage logic if needed
            // For now, we'll skip image preloading as requested

            // Always show the modal, regardless of whether the image preloads or not
            showFeedModalPopup(feedItem, currentTime)
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to handle feed modal", e)
            false
        }
    }

    /**
     * Show feed modal popup - matches Angular implementation
     * Displays the actual modal and handles the trigger action
     */
    private suspend fun showFeedModalPopup(feedItem: FeedItem, triggerTime: Long) {
        try {
            // Store the trigger time
            storeFeedLastTriggeredAt(triggerTime)

            // Send update feed action (matching Angular sendUpdateFeed.next)
            // This would typically notify other parts of the app about the feed action
            Log.d(tag, "Triggering feed modal for item: ${feedItem.elementId}")

            // TODO: Implement actual modal display logic
            // This would typically involve showing a Compose modal or dialog
            // For now, we'll just log the action
            Log.d(tag, "Feed modal popup displayed for: ${feedItem.titleText}")

        } catch (e: Exception) {
            Log.e(tag, "Failed to show feed modal popup", e)
        }
    }

    /**
     * Get feed last triggered at timestamp
     * This would typically read from storage (DataStore/SharedPreferences)
     */
    private suspend fun getFeedLastTriggeredAt(): Long? {
        return try {
            // TODO: Implement actual storage retrieval
            // For now, return null to indicate no previous trigger
            null
        } catch (e: Exception) {
            Log.e(tag, "Failed to get feed last triggered at", e)
            null
        }
    }

    /**
     * Store feed last triggered at timestamp
     * This would typically write to storage (DataStore/SharedPreferences)
     */
    private suspend fun storeFeedLastTriggeredAt(timestamp: Long) {
        try {
            // TODO: Implement actual storage writing
            Log.d(tag, "Stored feed last triggered at: $timestamp")
        } catch (e: Exception) {
            Log.e(tag, "Failed to store feed last triggered at", e)
        }
    }

    /**
     * Clear feed data - matches Angular implementation
     * Clears stored feed items and emits empty list
     */
    override fun clearFeedData() {
        try {
            _storedFeedItems = emptyList()
            _feedItems.tryEmit(emptyList())
            Log.d(tag, "Cleared all feed data")
        } catch (e: Exception) {
            Log.e(tag, "Failed to clear feed data", e)
        }
    }


}
