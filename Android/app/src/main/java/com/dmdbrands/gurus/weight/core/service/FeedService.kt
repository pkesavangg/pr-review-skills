package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.feed.FeedItem
import com.dmdbrands.gurus.weight.domain.repository.FeedAction
import com.dmdbrands.gurus.weight.domain.repository.IFeedRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.feed.shared.SelectedFeedItemHolder
import com.greatergoods.ggInAppMessaging.core.service.GGInAppMessagingService
import com.greatergoods.ggInAppMessaging.domain.models.FeaturedProduct
import com.greatergoods.ggInAppMessaging.domain.models.FeedActionType
import com.greatergoods.ggInAppMessaging.domain.models.FeedSetting
import com.greatergoods.ggInAppMessaging.domain.models.FeedTypes
import com.greatergoods.ggInAppMessaging.domain.models.LandingPage
import dagger.hilt.android.qualifiers.ApplicationContext
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
import android.content.Context

/**
 * Android equivalent of Angular FeedService
 * Service for managing feed items, including fetching, updating, and managing feed settings.
 */
@Singleton
class FeedService @Inject constructor(
  private val feedRepository: IFeedRepository,
  private val accountService: IAccountService,
  private val ggIAMService: GGInAppMessagingService,
  private val connectivityObserver: IConnectivityObserver,
  private val dialogQueueService: IDialogQueueService,
  private val appNavigationService: IAppNavigationService,
  private val selectedFeedItemHolder: SelectedFeedItemHolder,
  @ApplicationContext private val context: Context
) : IFeedService {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val TAG = "FeedService"

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

    // Listen for feed update events from the GG IAM service
    serviceScope.launch {
      ggIAMService.sendUpdateFeed.collect { updateEvent ->
        launch {
          AppLog.d(TAG, "Received feed update event: ${updateEvent.actionType} for ${updateEvent.feedItem.elementId}")
          val feedActionType = convertStringToFeedActionType(updateEvent.actionType)
          updateFeedItem(updateEvent.feedItem, feedActionType, updateEvent.variationId)
        }
      }
    }
  }

  // MARK: - Feed Items Management

  override suspend fun fetchFeedItems() {
    // TODO: Check network status when Network utility is available
    try {
      ggIAMService.setAccountId(accountService.getCurrentAccount()?.id ?: "")
      val items = feedRepository.fetchFeedItems()
      // TODO: Need to uncomment the setfeeditems
      ggIAMService.setFeedItems(items)
      // setMockFeedItems()
      _feedsChanged.emit(items)
      updateNotificationBadge()
      AppLog.i(TAG, "Successfully fetched feed items")
    } catch (error: Exception) {
      AppLog.e(TAG, "Failed to fetch feed items", error.toString())
      updateNotificationBadge()
    }
  }

  override suspend fun updateFeedItem(
    feedItem: FeedItem,
    actionType: FeedActionType,
    variationId: Int?
  ) {
    val action = buildFeedAction(actionType, variationId)

    try {
      feedRepository.updateFeedItem(feedItem.feedPostId, action)
      val feedItemToUpdate = ggIAMService.getFeedItems().find { item ->
        item.elementId == feedItem.elementId
      }
      if (feedItemToUpdate != null) {
        when (actionType) {
          FeedActionType.READ -> {
            // Update the local copy to mark as read
            feedItemToUpdate.copy(
              isUnread = false,
              trigger = null,
            )
            // Update the IAM service's stored items
            // Notify that feed notification changed
            ggIAMService.emitFeedNotificationChange()
            AppLog.d(TAG, "Marked feed item as read: ${feedItem.elementId}")
          }

          FeedActionType.TRIGGER -> {
            // Update the local copy to clear trigger
            feedItemToUpdate.copy(trigger = null)
            AppLog.d(TAG, "Cleared trigger for feed item: ${feedItem.elementId}")
          }

          else -> {
            AppLog.d(
              TAG,
              "Updated feed item: ${feedItem.elementId} with action: $actionType",
            )
          }
        }
      } else {
        AppLog.w(TAG, "Feed item not found in local storage: ${feedItem.elementId}")
      }

      AppLog.i(TAG, "Successfully updated feed item: ${feedItem.elementId}")
    } catch (error: Exception) {
      AppLog.e(TAG, "Failed to update feed item: ${feedItem.elementId}", error.toString())
    }
  }

  private fun buildFeedAction(actionType: FeedActionType, variationId: Int?): FeedAction {
    val osType = if (requiresMeta(actionType)) "android" else null
    val meta =
      if (requiresMeta(actionType)) com.dmdbrands.gurus.weight.domain.repository.FeedActionMeta(
        variationId,
      ) else null

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

  override suspend fun getUnreadFeedCount(): Int {
    return ggIAMService.getUnreadFeedCount()
  }

  override suspend fun getFeedSettings(): FeedSetting? {
    return ggIAMService.getStoredFeedNotificationSetting()
  }

  override suspend fun checkAndTriggerFeedModal(): Boolean {
    return try {
      val result = ggIAMService.checkFeedModalTrigger()
      AppLog.d(TAG, "Feed modal trigger check result: $result")
      result
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to check feed modal trigger", e.toString())
      false
    }
  }

  // MARK: - Notification Badge Helper
  private suspend fun updateNotificationBadge() {
    val feedSettings = getFeedSettings()
    val badgeShouldShow =
      getUnreadFeedCount() > 0 && (feedSettings?.showNotificationBadge ?: true)
    _notificationBadgeUpdated.emit(badgeShouldShow)
  }

  /**
   * Shows IAM feed modal using the app's dialog system
   */
  override fun showIAMFeedModal(feedItem: FeedItem) {
    try {
      val dialog = DialogModel.Custom(
        contentKey = DialogType.IAMFeedModal,
        params = mapOf(
          "feedItem" to feedItem,
          "elementId" to feedItem.elementId, // Store elementId for callback identification
        ),
        customPriority = 3, // Medium priority for IAM dialogs
        customDelayMillis = 0L,
        onDismiss = {
          handleFeedModalDismissal(feedItem)
        },
        onConfirm = { actionType: Any ->
          serviceScope.launch {
            handleFeedModalAction(feedItem, actionType.toString())
          }
        },
      )

      dialogQueueService.enqueue(dialog)
      AppLog.d(TAG, "Enqueued IAM feed modal for item: ${feedItem.elementId}")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to show IAM feed modal", e.toString())
    }
  }

  /**
   * Handles feed modal dismissal
   */
  private fun handleFeedModalDismissal(feedItem: com.greatergoods.ggInAppMessaging.domain.models.FeedItem) {
    try {
      // Mark as read, update analytics, etc.
      AppLog.d(TAG, "Feed modal dismissed for: ${feedItem.titleText}")
      // TODO: Add any additional dismissal logic here
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to handle feed modal dismissal", e.toString())
    }
  }

  /**
   * Handles feed modal actions
   * Navigates based on feed type: LINK opens external link, LANDING navigates to feed landing screen
   */
  private suspend fun handleFeedModalAction(
    feedItem: com.greatergoods.ggInAppMessaging.domain.models.FeedItem,
    actionType: String
  ) {
    try {
      AppLog.d(TAG, "Handling feed modal action: $actionType for ${feedItem.titleText} (${feedItem.feedType})")

      when (actionType) {
        "buy_now", "learn_more" -> {
          // Handle navigation based on feed type
          when (feedItem.feedType) {
            FeedTypes.LINK -> {
              // Open external link
              AppLog.d(TAG, "Opening external link for: ${feedItem.titleText}")
              feedItem.linkTarget?.let { link ->
                try {
                  // Use LinkOpener to open external link
                  val linkOpener = com.greatergoods.ggInAppMessaging.util.LinkOpener
                  linkOpener.openInCustomTab(
                    context = context,
                    url = link,
                    showTitle = true
                  )
                  AppLog.d(TAG, "Successfully opened external link: $link")
                } catch (e: Exception) {
                  AppLog.e(TAG, "Failed to open external link: $link", e.toString())
                }
              } ?: run {
                AppLog.w(TAG, "No link target found for feed item: ${feedItem.titleText}")
              }
            }

            FeedTypes.LANDING -> {
              // Set the selected feed item before navigating
              AppLog.d(TAG, "Setting selected feed item and navigating to feed landing for: ${feedItem.titleText}")
              try {
                // Set the feed item in the holder so the landing screen can access it
                selectedFeedItemHolder.setSelectedFeedItem(feedItem)
                AppLog.d(TAG, "Set selected feed item: ${feedItem.elementId}")

                // Navigate to feed landing page
                appNavigationService.navigateTo(AppRoute.Feed.FeedLanding)
                AppLog.d(TAG, "Successfully navigated to feed landing")
              } catch (e: Exception) {
                AppLog.e(TAG, "Failed to navigate to feed landing", e.toString())
              }
            }

            else -> {
              AppLog.w(TAG, "Unknown feed type: ${feedItem.feedType} for feed item: ${feedItem.titleText}")
            }
          }
        }

        "settings" -> {
          // Navigate to feed messages settings
          AppLog.d(TAG, "Navigating to feed messages settings from modal")
          try {
            appNavigationService.navigateTo(AppRoute.Feed.FeedMessageSetting)
            AppLog.d(TAG, "Successfully navigated to feed messages settings")
          } catch (e: Exception) {
            AppLog.e(TAG, "Failed to navigate to feed messages settings", e.toString())
          }
        }

        else -> {
          AppLog.d(TAG, "Unknown action type: $actionType for feed item: ${feedItem.titleText}")
        }
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to handle feed modal action", e.toString())
    }
  }

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
            linkTarget = "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
            feedType = FeedTypes.LINK,
            linkText = "Shop now",
            feedPostId = "mockPost001",
            accountId = "testAccount",
            titleImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
            trigger = "login",
            isUnread = true,
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
            titleText = "For a Limited Time: Get 50% OFF our Coffee Grinder",
            titleImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
            messageTypeText = "LIGHTENING DEAL",
            subtitleFeedText = "Ends in 48 hours",
            subtitleModalText = "This is another mock item from FeedService",
            feedType = FeedTypes.LANDING,
            linkTarget = "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
            linkText = "shop now",
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
                "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
                "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
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
        AppLog.d(TAG, "Set ${mockItems.size} mock feed items in IAM service")
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to set mock feed items", e.toString())
      }
    }
  }

  override suspend fun handleFeedItemClick(
    feedItem: FeedItem,
    onNavigateToFeedLanding: (FeedItem) -> Unit,
    onOpenExternalLink: (String) -> Unit
  ) {
    try {
      AppLog.d(TAG, "Handling feed item click: ${feedItem.titleText} (${feedItem.feedType})")

      when (feedItem.feedType) {
        FeedTypes.LANDING -> {
          AppLog.d(TAG, "Landing feed item clicked: ${feedItem.titleText}")
          onNavigateToFeedLanding(feedItem)
        }

        FeedTypes.LINK -> {
          AppLog.d(TAG, "Link feed item clicked: ${feedItem.titleText}")
          feedItem.linkTarget?.let { link ->
            onOpenExternalLink(link)
          }
        }

        else -> {
          AppLog.w(TAG, "Unknown feed type: ${feedItem.feedType}")
        }
      }

      // Mark feed as read regardless of type
      markFeedAsRead(feedItem.elementId, "clicked")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to handle feed item click", e.toString())
    }
  }

  /**
   * Handles shop now button click - checks feed type and navigates accordingly
   */
  override suspend fun handleShopNowClick(
    feedItem: FeedItem,
    onNavigateToFeedLanding: (FeedItem) -> Unit,
    onOpenExternalLink: (String) -> Unit
  ) {
    try {
      AppLog.d(TAG, "Handling shop now click: ${feedItem.titleText} (${feedItem.feedType})")

      when (feedItem.feedType) {
        FeedTypes.LANDING -> {
          AppLog.d(TAG, "Shop now clicked for landing feed item: ${feedItem.titleText}")
          onNavigateToFeedLanding(feedItem)
        }

        FeedTypes.LINK -> {
          AppLog.d(TAG, "Shop now clicked for link feed item: ${feedItem.titleText}")
          // Open external link
          feedItem.linkTarget?.let { link ->
            onOpenExternalLink(link)
          }
        }

        else -> {
          AppLog.w(TAG, "Unknown feed type for shop now click: ${feedItem.feedType}")
        }
      }

      // Mark feed as read
      markFeedAsRead(feedItem.elementId, "shop_now_clicked")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to handle shop now click", e.toString())
    }
  }

  override suspend fun markFeedAsRead(elementId: String, actionType: String) {
    try {
      AppLog.d(TAG, "Marking feed as read: $elementId, action: $actionType")

      // Get the feed item to update
      val feedItems = feedsChanged.first()
      val feedItem = feedItems.find { it.elementId == elementId }

      if (feedItem != null) {
        // Update the feed item with the appropriate action type
        val actionTypeEnum = convertStringToFeedActionType(actionType)
        updateFeedItem(feedItem, actionTypeEnum, null)
      } else {
        AppLog.w(TAG, "Feed item not found for elementId: $elementId")
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to mark feed as read", e.toString())
    }
  }

  override fun clearFeedData() {
    serviceScope.launch {
      try {
        AppLog.d(TAG, "Clearing feed data")
        ggIAMService.clearFeedData()
        _feedsChanged.emit(emptyList())
        _feedSettingsChanged.emit(null)
        _notificationBadgeUpdated.emit(false)
        AppLog.d(TAG, "Feed data cleared successfully")
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to clear feed data", e.toString())
      }
    }
  }

  fun cleanup() {
    serviceScope.cancel()
  }

  /**
   * Convert string action type to FeedActionType enum
   * Maps common action types from IAM service to FeedActionType
   */
  private fun convertStringToFeedActionType(actionType: String): FeedActionType {
    return when (actionType.lowercase()) {
      "read" -> FeedActionType.READ
      "click" -> FeedActionType.CLICK
      "trigger" -> FeedActionType.TRIGGER
      "view" -> FeedActionType.VIEW
      "dismiss" -> FeedActionType.DISMISS
      else -> {
        AppLog.w(TAG, "Unknown action type: $actionType, defaulting to READ")
        FeedActionType.READ
      }
    }
  }
}
