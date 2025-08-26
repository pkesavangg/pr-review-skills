package com.greatergoods.ggInAppMessaging.core

import com.greatergoods.ggInAppMessaging.domain.models.IAMFeedItem
import com.greatergoods.ggInAppMessaging.domain.models.FeedSetting
import com.greatergoods.ggInAppMessaging.domain.models.FeedActionType
import com.greatergoods.ggInAppMessaging.domain.models.FeedTrigger
import com.greatergoods.ggInAppMessaging.domain.models.UpdatedFeedInfo
import com.greatergoods.ggInAppMessaging.domain.models.FeedType
import com.greatergoods.ggInAppMessaging.core.storage.IAMKvStorageService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import timber.log.Timber

/**
 * Android equivalent of iOS GGInAppMessagingService
 * Public façade for in-app messaging functionality
 */
@Singleton
class GGInAppMessagingService @Inject constructor(
    private val kvStorage: IAMKvStorageService,
    private val gson: Gson
) {
    
    // MARK: - Published feed list
    private val _feeds = MutableStateFlow<List<IAMFeedItem>>(emptyList())
    val feeds: StateFlow<List<IAMFeedItem>> = _feeds.asStateFlow()
    
    private var accountId: String? = null
    
    // MARK: - Key/Value Storage Keys
    private val feedInfoKey = "feedInfo"
    private val feedLastTriggeredAtKey = "feedLastTriggeredAt"
    
    // Event indicating feed-notification settings changed
    private val _feedNotificationChanged = MutableSharedFlow<Unit>()
    val feedNotificationChanged: SharedFlow<Unit> = _feedNotificationChanged.asSharedFlow()
    
    // Subject emitting the entire feed list whenever it changes (load or update)
    private val _feedsChanged = MutableSharedFlow<List<IAMFeedItem>>()
    val feedsChanged: SharedFlow<List<IAMFeedItem>> = _feedsChanged.asSharedFlow()
    
    // Subject emitting feed updates
    private val _feedsUpdated = MutableSharedFlow<UpdatedFeedInfo>()
    val feedsUpdated: SharedFlow<UpdatedFeedInfo> = _feedsUpdated.asSharedFlow()
    
    // Subject for promo code copied events
    private val _promoCodeCopied = MutableSharedFlow<Boolean>()
    val promoCodeCopied: SharedFlow<Boolean> = _promoCodeCopied.asSharedFlow()
    
    private val tag = "GGInAppMessagingService"
    
    // MARK: - Computed Keys
    private val feedInfoOfflineKey: String?
        get() = accountId?.let { "${feedInfoKey}_$it" }
    
    private val feedLastTriggeredAt: String?
        get() = accountId?.let { "${feedLastTriggeredAtKey}_$it" }
    
    // MARK: - Public API
    fun setAccountId(accountId: String) {
        this.accountId = accountId
    }
    
    suspend fun load(feeds: List<IAMFeedItem>) {
        // TODO: Remove this once we have real feeds from the API
        val sampleFeed = listOf(
            IAMFeedItem(
                feedPostId = "TvCN6AV5b781rXLSldOziI",
                elementId = "yeIfo8WA5BLb9GuCVQnKcU",
                accountId = "4FK8P8ApdYr9teilgn5vQV",
                isUnread = true,
                messageTypeText = "LIGHTNING DEAL",
                titleText = "Kitchen Scales 40% Off",
                subtitleModalText = "Ends in {{expiresAt}} hurry {{bold[15$]}} 14$ {{strike-bold-italic[15$]}} {{foo[ddd]}}",
                subtitleFeedText = "Ends in {{expiresAt}}!",
                titleImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
                linkTarget = "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
                linkText = "BUY NOW",
                trigger = FeedTrigger.LOGIN,
                expiresAt = "2025-10-01T00:00:00.000Z",
                feedType = FeedType.LANDING,
                landingPage = null
            )
        )
        
        val feedsToUse = if (feeds.isEmpty()) sampleFeed else feeds
        _feeds.value = feedsToUse
        
        // Broadcast the latest feed list to subscribers
        _feedsChanged.emit(feedsToUse)
    }
    
    suspend fun updateFeedItem(feed: IAMFeedItem, action: FeedActionType, variationId: Int? = null) {
        val updatedFeed = UpdatedFeedInfo(feed, action, variationId)
        
        if (action == FeedActionType.READ) {
            val currentFeeds = _feeds.value.toMutableList()
            val index = currentFeeds.indexOfFirst { it.elementId == feed.elementId }
            if (index != -1) {
                val updatedItem = currentFeeds[index].copy(isUnread = false)
                currentFeeds[index] = updatedItem
                _feeds.value = currentFeeds
            }
        }
        
        // Notify listeners that the feed list itself has been modified
        _feedsChanged.emit(_feeds.value)
        _feedsUpdated.emit(updatedFeed)
    }
    
    fun clearFeedData() {
        try {
            feedInfoOfflineKey?.let { kvStorage.clearValue(it) }
            feedLastTriggeredAt?.let { kvStorage.clearValue(it) }
            _feeds.value = emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear feed data")
        }
    }
    
    // MARK: - Feed Counts
    fun getUnreadFeedCount(): Int {
        return _feeds.value.count { it.isUnread == true }
    }
    
    // MARK: - Feed Notification Settings
    suspend fun checkStoredFeedNotification() {
        val key = feedInfoOfflineKey ?: return
        if (kvStorage.getValue(key) == null) {
            val defaultSetting = FeedSetting(
                showPopupMessage = true,
                showNotificationBadge = true
            )
            storeFeedNotificationSetting(defaultSetting)
        }
    }
    
    suspend fun storeFeedNotificationSetting(setting: FeedSetting) {
        val key = feedInfoOfflineKey ?: return
        try {
            val json = gson.toJson(setting)
            kvStorage.setValue(json, key)
            Timber.d("[$tag] Stored FeedSetting: $setting")
            _feedNotificationChanged.emit(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[$tag] Failed to encode FeedSetting")
        }
    }
    
    fun getStoredFeedNotificationSetting(): FeedSetting? {
        val key = feedInfoOfflineKey ?: return null
        return try {
            val json = kvStorage.getValue(key) ?: return null
            val type = object : TypeToken<FeedSetting>() {}.type
            val result = gson.fromJson<FeedSetting>(json, type)
            Timber.d("[$tag] get FeedSetting getStoredFeedNotificationSetting: $result")
            result
        } catch (e: Exception) {
            Timber.e(e, "[$tag] Failed to parse FeedSetting")
            null
        }
    }
    
    // MARK: - Feed Modal Trigger
    suspend fun checkFeedModalTrigger(): IAMFeedItem? {
        val settings = getStoredFeedNotificationSetting()
        if (settings?.showPopupMessage != true) {
            return null
        }
        
        val loginFeed = _feeds.value.firstOrNull { it.trigger == FeedTrigger.LOGIN }
            ?: return null
            
        return showFeedModal(loginFeed)
    }
    
    private suspend fun showFeedModal(feed: IAMFeedItem): IAMFeedItem? {
        val now = System.currentTimeMillis().toDouble()
        val triggerKey = feedLastTriggeredAt ?: return null
        
        // Retrieve the last trigger time as either Double or String (legacy data)
        var lastTriggerTime: Double? = null
        val storedValue = kvStorage.getValue(triggerKey)
        if (storedValue != null) {
            try {
                lastTriggerTime = storedValue.toDouble()
            } catch (e: NumberFormatException) {
                // Handle legacy string data
                Timber.w("[$tag] Failed to parse trigger time as double: $storedValue")
            }
        }
        
        return if (lastTriggerTime == null) {
            handleFeedModal(feed, now, triggerKey)
        } else {
            val oneWeek = 7 * 24 * 60 * 60 * 1000.0 // milliseconds
            if (lastTriggerTime + oneWeek < now) {
                handleFeedModal(feed, now, triggerKey)
            } else {
                null
            }
        }
    }
    
    private suspend fun handleFeedModal(feed: IAMFeedItem, currentTime: Double, key: String): IAMFeedItem {
        // Persist the current trigger time (milliseconds since epoch)
        kvStorage.setValue(currentTime.toString(), key)
        
        // Inform listeners that the feed was triggered so the host app can react (e.g., present its own modal).
        updateFeedItem(feed, FeedActionType.TRIGGER)
        
        return feed
    }
}