package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.feed.FeedItem
import com.dmdbrands.gurus.weight.domain.repository.FeedAction
import com.dmdbrands.gurus.weight.domain.repository.IFeedRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.greatergoods.ggInAppMessaging.core.service.GGInAppMessagingService
import com.greatergoods.ggInAppMessaging.domain.models.FeaturedProduct
import com.greatergoods.ggInAppMessaging.domain.models.FeedActionType
import com.greatergoods.ggInAppMessaging.domain.models.FeedSetting
import com.greatergoods.ggInAppMessaging.domain.models.FeedTypes
import com.greatergoods.ggInAppMessaging.domain.models.LandingPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android equivalent of Angular FeedService
 * Service for managing feed items, including fetching, updating, and managing feed settings.
 */
@Singleton
class FeedService @Inject constructor(
  private val feedRepository: IFeedRepository,
  private val accountService: IAccountService,
  private val ggIAMService: GGInAppMessagingService,
  // private val notificationService: NotificationHelperService // TODO: Add when available
) : IFeedService {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  private val tag = "FeedService"

  // MARK: - Constants (matching Angular service)
  private val feedInfoKey = "feedInfo"
  private val feedLastTriggeredAtKey = "feedLastTriggeredAt"

  // MARK: - Publishers

  /**
   * Emits whenever feedItems changes. Consumers can subscribe without needing GG IAM package.
   */
  private val _feedsChanged = MutableSharedFlow<List<FeedItem>>()
  override val feedsChanged: Flow<List<FeedItem>> = _feedsChanged.asSharedFlow()

  private val _feedSettingsChanged = MutableSharedFlow<FeedSetting?>()
  override val feedSettingsChanged: Flow<FeedSetting?> = _feedSettingsChanged.asSharedFlow()

  private val _notificationBadgeUpdated = MutableSharedFlow<Boolean>()
  override val notificationBadgeUpdated: Flow<Boolean> = _notificationBadgeUpdated.asSharedFlow()

  // MARK: - Computed Properties (matching Angular service)
  private suspend fun accountId() = accountService.activeAccountFlow.first()?.id

  init {
    // Initialize feed settings in a coroutine
    serviceScope.launch {
      val initialFeedSettings = getFeedSettings()
    }

    // Listen for feed notification changes from the GG IAM service
    serviceScope.launch {
      ggIAMService.feedNotificationChangedSubject.collect {
        launch {
          val result = getFeedSettings()
          _feedSettingsChanged.emit(result)
          updateNotificationBadge()
        }
      }
    }
  }

  // MARK: - Feed Items Management

  override suspend fun fetchFeedItems() {
    // TODO: Check network status when Network utility is available
    // val networkStatus = await Network.getStatus()
    // if (networkStatus.connected == true) {
    try {
      ggIAMService.setAccountId(accountService.getCurrentAccount()?.id ?: "")
      val items = feedRepository.fetchFeedItems()

      // Update GG IAM service with new feeds (matching Angular service)
      ggIAMService.setFeedItems(items)

      // Also emit to internal flows
      _feedsChanged.emit(items)
      updateNotificationBadge()
      AppLog.i(tag, "Successfully fetched feed items")
    } catch (error: Exception) {
      AppLog.e(tag, "Failed to fetch feed items", error.toString())

      // Emit empty feeds on error (matching Angular service)
      updateNotificationBadge()
    }
    // }
  }

  override suspend fun updateFeedItem(feedItem: FeedItem, actionType: FeedActionType, variationId: Int?) {
    val action = buildFeedAction(actionType, variationId)
    if (feedItem == null) {
      return
    }

    try {
      feedRepository.updateFeedItem(feedItem.feedPostId, action)

      // If item is read, update local copy (matching Angular service)
      // Note: The IAM service handles feed updates internally
      AppLog.d(tag, "Feed item updated, IAM service will handle local updates")

      AppLog.i(tag, "Successfully updated feed item")
    } catch (error: Exception) {
      AppLog.e(tag, "Failed to update feed item", error.toString())
    }
  }

  override fun getUnreadFeedCount(): Int {
    return ggIAMService.getUnreadFeedCount()
  }

  // MARK: - Feed Settings Management

  override suspend fun getFeedSettings(): FeedSetting? {
    return ggIAMService.getStoredFeedNotificationSetting()
  }

  // MARK: - Feed Modal Management

  override fun checkAndTriggerFeedModal() {
    serviceScope.launch {
      val result = ggIAMService.checkFeedModalTrigger()
      result?.let { feedItem ->
        // TODO: Show modal using notification service
        // notificationService.showModal(...)
        AppLog.d(tag, "Triggering feed modal for item: $feedItem")
      }
    }
  }

  // MARK: - Additional Methods (matching Angular service)

  /**
   * Get the current account ID (matching Angular service's accountId getter)
   */
  suspend fun getCurrentAccountId(): String? = accountId()

  /**
   * Get the feed info offline key (matching Angular service's feedInfoOfflineKey getter)
   */
  suspend fun getFeedInfoOfflineKey(): String =
    "$feedInfoKey${if (getCurrentAccountId() != null) "_${getCurrentAccountId()}" else ""}"

  /**
   * Get the feed last triggered at key (matching Angular service's feedLastTriggeredAt getter)
   */
  suspend fun getFeedLastTriggeredAtKey(): String =
    "$feedLastTriggeredAtKey${if (getCurrentAccountId() != null) "_${getCurrentAccountId()}" else ""}"

  // MARK: - Cleanup

  override fun clearFeedData() {
    ggIAMService.clearFeedData()
    serviceScope.launch {
      updateNotificationBadge()
    }
  }

  // MARK: - Private Helpers

  private fun buildFeedAction(actionType: FeedActionType, variationId: Int?): FeedAction {
    val osType = if (requiresMeta(actionType)) "android" else null
    val meta =
      if (requiresMeta(actionType)) com.dmdbrands.gurus.weight.domain.repository.FeedActionMeta(variationId) else null

    return FeedAction(
      action = actionType,
      osType = osType,
      meta = meta,
    )
  }

  private fun requiresMeta(actionType: FeedActionType): Boolean {
    return !(actionType == FeedActionType.CLICK ||
      actionType == FeedActionType.READ ||
      actionType == FeedActionType.TRIGGER)
  }

  // MARK: - Notification Badge Helper
  private suspend fun updateNotificationBadge() {
    val feedSettings = getFeedSettings()
    val badgeShouldShow = getUnreadFeedCount() > 0 && (feedSettings?.showNotificationBadge ?: true)
    _notificationBadgeUpdated.emit(badgeShouldShow)
  }

  // MARK: - Mock Data for Testing

  /**
   * Set mock feed items for testing purposes
   * This method can be called to provide mock data to the IAM service
   */
  fun setMockFeedItems() {
    serviceScope.launch {
      try {
        // Create mock feed items (you can customize this data)
        val mockItems = listOf(
          FeedItem(
            elementId = "mockFromFeedService001",
            titleText = "Special Offer from FeedService!",
            messageTypeText = "LIGHTENING DEAL",
            subtitleFeedText = "Ends in 48 hours",
            subtitleModalText = "This feed item came from the main app's FeedService",
            feedType = FeedTypes.LINK,
            linkText = "https://shop.greatergoods.com",
            feedPostId = "mockPost001",
            accountId = "testAccount",
            titleImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
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
          FeedItem(
            elementId = "mockFromFeedService002",
            titleText = "Another Test Item",
            titleImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
            messageTypeText = "LIGHTENING DEAL",
            subtitleFeedText = "Ends in 48 hours",
            subtitleModalText = "This is another mock item from FeedService",
            feedType = FeedTypes.LANDING,
            linkText = "https://example.com",
            feedPostId = "mockPost002",
            accountId = "testAccount",
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

        // Set the mock items in the IAM service
        ggIAMService.setFeedItems(mockItems)
        AppLog.d(tag, "Set ${mockItems.size} mock feed items in IAM service")
      } catch (e: Exception) {
        AppLog.e(tag, "Failed to set mock feed items", e.toString())
      }
    }
  }

  fun cleanup() {
    serviceScope.cancel()
  }
}
