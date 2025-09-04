package com.greatergoods.ggInAppMessaging.core.service

import com.greatergoods.ggInAppMessaging.domain.models.FeaturedProduct
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.domain.models.FeedSetting
import com.greatergoods.ggInAppMessaging.domain.models.FeedTypes
import com.greatergoods.ggInAppMessaging.domain.models.GGInAppMessagingConfig
import com.greatergoods.ggInAppMessaging.domain.models.LandingPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Main GG In-App Messaging service
 * Manages feed settings and provides reactive updates
 */
class GGInAppMessagingService(
  private val feedStorageService: FeedStorageService
) {

  private val tag = "GGInAppMessagingService"

  // MARK: - Properties
  private var accountId: String = ""
  private var libConfig: GGInAppMessagingConfig = GGInAppMessagingConfig()

  // MARK: - Reactive Streams
  private val _feedNotificationChangedSubject = MutableSharedFlow<Unit>()
  val feedNotificationChangedSubject = _feedNotificationChangedSubject.asSharedFlow()

  // MARK: - Feed Items Storage
  private var _storedFeedItems: List<FeedItem> = emptyList()

  // MARK: - Public Methods

  /**
   * Set account ID for user-specific settings
   */
  fun setAccountId(accountId: String) {
    this.accountId = accountId
  }

  /**
   * Set library configuration
   */
  fun setLibConfig(config: GGInAppMessagingConfig) {
    this.libConfig = config
  }

  /**
   * Get feed settings flow for reactive updates
   */
  val feedSettingsFlow: Flow<FeedSetting> = feedStorageService.feedSettingsFlow

  /**
   * Check and initialize stored feed notification settings
   */
  suspend fun checkStoredFeedNotification() {
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
  suspend fun storeFeedNotificationSetting(feedSetting: FeedSetting) {
    try {
      feedStorageService.updateFeedSettings(feedSetting, accountId)
      _feedNotificationChangedSubject.tryEmit(Unit)
    } catch (e: Exception) {
      android.util.Log.e(tag, "Failed to store feed notification setting", e)
    }
  }

  /**
   * Get stored feed notification settings
   */
  suspend fun getStoredFeedNotificationSetting(): FeedSetting? {
    return try {
      feedStorageService.getFeedSettings()
    } catch (e: Exception) {
      android.util.Log.e(tag, "Failed to get stored feed notification setting", e)
      null
    }
  }

  /**
   * Update pop-up message setting
   */
  suspend fun updatePopupMessageSetting(showPopupMessage: Boolean) {
    try {
      feedStorageService.updatePopupMessageSetting(showPopupMessage, accountId)
      _feedNotificationChangedSubject.tryEmit(Unit)
    } catch (e: Exception) {
      android.util.Log.e(tag, "Failed to update popup message setting", e)
    }
  }

  /**
   * Update notification badge setting
   */
  suspend fun updateNotificationBadgeSetting(showNotificationBadge: Boolean) {
    try {
      feedStorageService.updateNotificationBadgeSetting(showNotificationBadge, accountId)
      _feedNotificationChangedSubject.tryEmit(Unit)
    } catch (e: Exception) {
      android.util.Log.e(tag, "Failed to update notification badge setting", e)
    }
  }

  /**
   * Get pop-up message setting
   */
  suspend fun getPopupMessageSetting(): Boolean {
    return try {
      feedStorageService.getPopupMessageSetting()
    } catch (e: Exception) {
      android.util.Log.e(tag, "Failed to get popup message setting", e)
      true // Default to true
    }
  }

  /**
   * Get notification badge setting
   */
  suspend fun getNotificationBadgeSetting(): Boolean {
    return try {
      feedStorageService.getNotificationBadgeSetting()
    } catch (e: Exception) {
      android.util.Log.e(tag, "Failed to get notification badge setting", e)
      true // Default to true
    }
  }

  /**
   * Emit feed notification change
   */
  suspend fun emitFeedNotificationChange() {
    _feedNotificationChangedSubject.emit(Unit)
  }

  // MARK: - Methods required by existing FeedService

  /**
   * Get unread feed count (placeholder implementation)
   */
  fun getUnreadFeedCount(): Int {
    // TODO: Implement actual unread count logic
    return 0
  }

  /**
   * Check if feed modal should be triggered (placeholder implementation)
   */
  suspend fun checkFeedModalTrigger(): Any? {
    // TODO: Implement actual modal trigger logic
    return null
  }

  /**
   * Clear feed data (placeholder implementation)
   */
  fun clearFeedData() {
    // TODO: Implement actual clear logic
  }

  /**
   * Set feed items received from main app's FeedService
   * This method is called by the main app to provide feed items
   */
  fun setFeedItems(feedItems: List<FeedItem>) {
    _storedFeedItems = feedItems
  }

  /**
   * Get feed items - returns stored items from main app or mock data as fallback
   * Reference: https://github.com/dmdbrands/balance-server?tab=readme-ov-file#get-bpmv2feed
   */
  suspend fun getFeedItems(): List<FeedItem> {
    return _storedFeedItems
  }

  /**
   * Create mock feed items (fallback implementation)
   */
  private fun createMockFeedItems(): List<FeedItem> {
    return listOf(
      FeedItem(
        elementId = "mockUUID0002",
        titleText = "Here's a headline that's 40 characters.",
        subtitleModalText = "Be prepare for the holidays! Offer ends in {{expiresAt}}!",
        subtitleFeedText = "Ends in 48 hours",
        messageTypeText = "LIGHTENING DEAL",
        titleImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
        linkTarget = "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
        linkText = "SHOP NOW",
        trigger = null,
        isUnread = false,
        expiresAt = "2024-12-30T06:00:00.000Z",
        feedPostId = "TvCN6AV5b781rXLSldOziI",
        accountId = "TvCN6AV5b781rXLSldOziI",
        feedType = FeedTypes.LINK,
        landingPage = LandingPage(
          feedLandingPageId = "ZjsgSDU56trZrcrRnGgaHr",
          feedPostId = "TvCN6AV5b781rXLSldOziI",
          titleText = "Vacuum Sealers",
          promoCode = "5ZHTL9M8",
          featuredImage = null,
          supportingTitleText = "One Machine, a Million Uses",
          supportingDescriptionText = "The Greater Goods {{bold[All-in-One Vacuum Sealer]}} has built-in bag storage and a slicer for hassle-free meal prep!",
          supportingImage = listOf(
            "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
            "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
          ),
          featuredTitleText = "Three Colors",
          themeColor = "red",
          featuredProduct = listOf(
            FeaturedProduct(
              variationId = 10001,
              titleText = "Stone Blue",
              feedLandingPageId = "ZjsgSDU56trZrcrRnGgaHr",
              linkText = "Shop",
              linkTarget = "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
              productImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
            ),
            FeaturedProduct(
              variationId = 10002,
              titleText = "Stone Blue",
              feedLandingPageId = "ZjsgSDU56trZrcrRnGgaHr",
              linkText = "Shop",
              linkTarget = "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
              productImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
            ),
            FeaturedProduct(
              variationId = 10003,
              titleText = "Stone Blue",
              feedLandingPageId = "ZjsgSDU56trZrcrRnGgaHr",
              linkText = "Shop",
              linkTarget = "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
              productImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
            ),
          ),
        ),
      ),
    )
  }
}
