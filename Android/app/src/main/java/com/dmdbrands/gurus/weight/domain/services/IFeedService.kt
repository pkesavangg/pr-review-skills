package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.domain.model.feed.FeedItem
import com.greatergoods.ggInAppMessaging.domain.models.FeedActionType
import com.greatergoods.ggInAppMessaging.domain.models.FeedSetting
import kotlinx.coroutines.flow.Flow

interface IFeedService {

    /**
     * Emits whenever feed items are changed
     */
    val feedsChanged: Flow<List<FeedItem>>

    /**
     * Emits when feed settings are updated
     */
    val feedSettingsChanged: Flow<FeedSetting?>

    /**
     * Emits when the notification badge should be updated
     */
    val notificationBadgeUpdated: Flow<Boolean>

    /**
     * Fetches all feed items from the backend and updates local state.
     */
    suspend fun fetchFeedItems()

    /**
     * Updates a feed item's state with the given action.
     * @param feedItem The feed item to update
     * @param actionType The type of action (read, trigger, click, etc.)
     * @param variationId Optional variation ID for certain action types
     */
    suspend fun updateFeedItem(feedItem: FeedItem, actionType: FeedActionType, variationId: Int?)

    /**
     * Gets the count of unread feed items.
     * @return Number of unread feed items
     */
    suspend fun getUnreadFeedCount(): Int

    /**
     * Gets the stored feed notification settings.
     * @return FeedSetting if exists, null otherwise
     */
    suspend fun getFeedSettings(): FeedSetting?

    /**
     * Checks and displays the feed modal if trigger conditions are met.
     */
    suspend fun checkAndTriggerFeedModal(): Boolean

    fun showIAMFeedModal(feedItem: FeedItem)
}
