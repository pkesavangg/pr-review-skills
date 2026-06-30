package com.greatergoods.ggInAppMessaging.core.service

import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.domain.models.FeedSetting
import com.greatergoods.ggInAppMessaging.domain.services.IInAppMessagingService
import com.greatergoods.ggInAppMessaging.core.utilities.IAMLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

/**
 * Main GG In-App Messaging service
 * Manages feed settings and provides reactive updates
 */
@Singleton
class GGInAppMessagingService @Inject constructor(
  private val feedStorageService: FeedStorageService,
  @ApplicationContext private val context: Context
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

  // MARK: - Dialog Events
  private val _dialogEvents = MutableSharedFlow<IAMDialogEvent>()
  val dialogEvents: SharedFlow<IAMDialogEvent> = _dialogEvents.asSharedFlow()

  // MARK: - Feed Update Events
  private val _sendUpdateFeed = MutableSharedFlow<FeedUpdateEvent>()
  override val sendUpdateFeed: SharedFlow<FeedUpdateEvent> = _sendUpdateFeed.asSharedFlow()

  /**
   * Get feed settings flow for reactive updates (all accounts)
   */
  override val feedSettingsFlow: Flow<Map<String, FeedSetting>> = feedStorageService.feedSettingsFlow

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
   * Get unread feed count - matches Angular implementation
   * Filters feed items where isUnread is true and returns the count
   */
  override suspend fun getUnreadFeedCount(): Int {
    return try {
      val currentFeedItems = getFeedItems()
      val unreadFeeds = currentFeedItems.filter { feed -> feed.isUnread }
      unreadFeeds.size
    } catch (e: Exception) {
      IAMLogger.e(tag, "getUnreadFeedCount failed", e.message)
      0
    }
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
   * Store feed notification settings for current account
   */
  override suspend fun storeFeedNotificationSetting(feedSetting: FeedSetting) {
    try {
      feedStorageService.updateFeedSettings(feedSetting, accountId)
      _feedNotificationChangedSubject.tryEmit(Unit)
    } catch (e: Exception) {
      IAMLogger.e(tag, "storeFeedNotificationSetting failed", e.message)
    }
  }

  /**
   * Get stored feed notification settings for current account
   */
  override suspend fun getStoredFeedNotificationSetting(): FeedSetting? {
    return try {
      feedStorageService.getFeedSettings(accountId)
    } catch (e: Exception) {
      IAMLogger.e(tag, "getStoredFeedNotificationSetting failed", e.message)
      null
    }
  }

  /**
   * Update pop-up message setting for current account
   */
  override suspend fun updatePopupMessageSetting(showPopupMessage: Boolean) {
    try {
      feedStorageService.updatePopupMessageSetting(showPopupMessage, accountId)
      _feedNotificationChangedSubject.tryEmit(Unit)
    } catch (e: Exception) {
      IAMLogger.e(tag, "updatePopupMessageSetting failed", e.message)
    }
  }

  /**
   * Update notification badge setting for current account
   */
  override suspend fun updateNotificationBadgeSetting(showNotificationBadge: Boolean) {
    try {
      feedStorageService.updateNotificationBadgeSetting(showNotificationBadge, accountId)
      _feedNotificationChangedSubject.tryEmit(Unit)
    } catch (e: Exception) {
      IAMLogger.e(tag, "updateNotificationBadgeSetting failed", e.message)
    }
  }

  /**
   * Get pop-up message setting for current account
   */
  override suspend fun getPopupMessageSetting(): Boolean {
    return try {
      feedStorageService.getPopupMessageSetting(accountId)
    } catch (e: Exception) {
      IAMLogger.e(tag, "getPopupMessageSetting failed", e.message)
      true // Default to true
    }
  }

  /**
   * Get notification badge setting for current account
   */
  override suspend fun getNotificationBadgeSetting(): Boolean {
    return try {
      feedStorageService.getNotificationBadgeSetting(accountId)
    } catch (e: Exception) {
      IAMLogger.e(tag, "getNotificationBadgeSetting failed", e.message)
      true // Default to true
    }
  }

  /**
   * Emit feed notification change
   */
  override suspend fun emitFeedNotificationChange() {
    _feedNotificationChangedSubject.emit(Unit)
  }

  /**
   * Check if feed modal should be triggered - matches Angular implementation
   * Checks if popup messages are enabled and finds a feed item with login trigger
   */
  override suspend fun checkFeedModalTrigger(): Boolean {
    return try {
      val feedSetting = getStoredFeedNotificationSetting()
      if (feedSetting?.showPopupMessage == true) {
        val currentFeedItems = getFeedItems()
        val feedItemWithLoginTrigger = currentFeedItems.find { feed ->
          feed.trigger == "login"
        }
        if (feedItemWithLoginTrigger != null) {
          return showFeedModal(feedItemWithLoginTrigger)
        } else {
          false
        }
      } else {
        false
      }
    } catch (e: Exception) {
      IAMLogger.e(tag, "checkFeedModalTrigger failed", e.message)
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
        return if (feedModalTriggerCoolDownTime < currentTime) {
          handleFeedModal(feedItem, currentTime)
        } else {
          false
        }
      } else {
        handleFeedModal(feedItem, currentTime)
      }
    } catch (e: Exception) {
      IAMLogger.e(tag, "showFeedModal failed", e.message)
      false
    }
  }

  /**
   * Handle feed modal - matches Angular implementation
   * Manages the modal display process
   */
  private suspend fun handleFeedModal(feedItem: FeedItem, currentTime: Long): Boolean {
    return try {
      // Always show the modal, regardless of whether the image preloads or not
      showFeedModalPopup(feedItem, currentTime)
      true
    } catch (e: Exception) {
      IAMLogger.e(tag, "handleFeedModal failed", e.message)
      false
    }
  }

  /**
   * Show feed modal popup - matches Angular implementation
   * Displays the actual modal and handles the trigger action
   */
  private suspend fun showFeedModalPopup(feedItem: FeedItem, triggerTime: Long) {
    try {
      // Preload image before showing modal - wait for it to complete
      feedItem.titleImage.let { imageUrl ->
          try {
            // Use Coil to preload the image and wait for it to complete
            val imageLoader = coil.ImageLoader(context)
            val request = coil.request.ImageRequest.Builder(context)
              .data(imageUrl)
              .target(
                onStart = {},
                onSuccess = {},
                onError = {}
              )
              .build()

            // Execute and wait for the result
            imageLoader.execute(request)
          } catch (e: Exception) {
            // If image fails to load, don't show modal
            IAMLogger.e(tag, "showFeedModalPopup image preload failed", e.message)
            return
          }
      }

      // Store the trigger time
      storeFeedLastTriggeredAt(triggerTime)
      // Emit dialog event to MeApp
      _dialogEvents.emit(
        IAMDialogEvent.ShowFeedModal(
          feedItem = feedItem,
          triggerTime = triggerTime,
        ),
      )
    } catch (e: Exception) {
      throw e // Re-throw to be caught by caller
    }
  }

  /**
   * Get feed last triggered at timestamp for current account
   * Reads from FeedSettingsDataStore
   */
  private suspend fun getFeedLastTriggeredAt(): Long? {
    return try {
      feedStorageService.getFeedLastTriggeredAt(accountId)
    } catch (e: Exception) {
      IAMLogger.e(tag, "getFeedLastTriggeredAt failed", e.message)
      null
    }
  }

  /**
   * Store feed last triggered at timestamp for current account
   * Writes to FeedSettingsDataStore
   */
  private suspend fun storeFeedLastTriggeredAt(timestamp: Long) {
    try {
      feedStorageService.storeFeedLastTriggeredAt(timestamp, accountId)
    } catch (e: Exception) {
      IAMLogger.e(tag, "storeFeedLastTriggeredAt failed", e.message)
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
    } catch (e: Exception) {
      IAMLogger.e(tag, "clearFeedData failed", e.message)
    }
  }

  /**
   * Mark a feed item as read
   * Emits feed update event to trigger API call in main app
   */
  override suspend fun markFeedAsRead(elementId: String, actionType: String) {
    try {

      // Find the feed item
      val feedItem = _storedFeedItems.find { it.elementId == elementId }
      if (feedItem != null) {
        // Emit feed update event to trigger API call in main app
        emitFeedUpdate(feedItem, actionType)
      }
    } catch (e: Exception) {
      IAMLogger.e(tag, "markFeedAsRead failed", e.message)
    }
  }

  /**
   * Emit feed update event to notify main app to update feed items
   * This is called when feed items need to be updated via API
   */
  override suspend fun emitFeedUpdate(feedItem: FeedItem, actionType: String, variationId: Int?) {
    try {
      val updateEvent = FeedUpdateEvent(
        feedItem = feedItem,
        actionType = actionType,
        variationId = variationId,
      )
      _sendUpdateFeed.emit(updateEvent)
    } catch (e: Exception) {
      IAMLogger.e(tag, "emitFeedUpdate failed", e.message)
    }
  }

  /**
   * Emit promo code copied event to notify main app
   * @param promoCode The promo code that was copied
   */
  override suspend fun emitPromoCodeCopied(promoCode: String) {
    try {
      _dialogEvents.emit(
        IAMDialogEvent.PromoCodeCopied(promoCode = promoCode),
      )
    } catch (e: Exception) {
      // Log error but don't throw to avoid breaking the copy flow
      IAMLogger.e(tag, "emitPromoCodeCopied failed", e.message)
    }
  }
}

/**
 * IAM Dialog Events for communication with MeApp
 */
sealed class IAMDialogEvent {
  data class ShowFeedModal(
    val feedItem: FeedItem,
    val triggerTime: Long
  ) : IAMDialogEvent()

  data class ShowPromoModal(
    val promoData: Any // You can define a proper PromoData class later
  ) : IAMDialogEvent()

  /**
   * Event emitted when promo code is copied to clipboard
   * @param promoCode The promo code that was copied
   */
  data class PromoCodeCopied(
    val promoCode: String
  ) : IAMDialogEvent()
}

/**
 * Feed Update Event for communication with main app
 * Triggers API calls to update feed items
 */
data class FeedUpdateEvent(
  val feedItem: FeedItem,
  val actionType: String,
  val variationId: Int? = null
)
