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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class FeedService @Inject constructor(
  private val feedRepository: IFeedRepository,
  private val accountService: IAccountService,
  private val ggIAMService: GGInAppMessagingService,
  connectivityObserver: IConnectivityObserver,
  dialogQueueService: IDialogQueueService,
  appNavigationService: IAppNavigationService,
  private val selectedFeedItemHolder: SelectedFeedItemHolder,
  @ApplicationContext private val context: Context
) : BaseService(connectivityObserver, dialogQueueService, appNavigationService),
  IFeedService {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val TAG = "FeedService"

  private val _feedsChanged = MutableSharedFlow<List<FeedItem>>()
  override val feedsChanged: Flow<List<FeedItem>> = _feedsChanged.asSharedFlow()

  private val _feedSettingsChanged = MutableSharedFlow<FeedSetting?>()
  override val feedSettingsChanged: Flow<FeedSetting?> = _feedSettingsChanged.asSharedFlow()

  private val _notificationBadgeUpdated = MutableSharedFlow<Boolean>()
  override val notificationBadgeUpdated: Flow<Boolean> = _notificationBadgeUpdated.asSharedFlow()

  /**
   * Local storage for feed items maintained by FeedService.
   * This is the single source of truth for feed items in this service.
   */
  private val _localFeedItems = MutableStateFlow<List<FeedItem>>(emptyList())
  private val localFeedItems: Flow<List<FeedItem>> = _localFeedItems.asStateFlow()


  init {

    serviceScope.launch {
      getFeedSettings()
      ggIAMService.feedNotificationChangedSubject.collect {
        launch {
          val result = getFeedSettings()
          _feedSettingsChanged.emit(result)
          updateNotificationBadge()
        }
      }
    }

    serviceScope.launch {
      ggIAMService.sendUpdateFeed.collect { updateEvent ->
        launch {
          val feedActionType = convertStringToFeedActionType(updateEvent.actionType)
          updateFeedItem(updateEvent.feedItem, feedActionType, updateEvent.variationId)
        }
      }
    }
  }

  override suspend fun fetchFeedItems() {
    try {
      // Check internet connectivity first - follow AccountService pattern
      if (!isNetworkAvailable()) {
        AppLog.w(TAG, "No internet connection available, using local feed items")
        // Use local items when offline
        val localItems = _localFeedItems.value
        _feedsChanged.emit(localItems)
        updateNotificationBadge()
        return
      }

      ggIAMService.setAccountId(accountService.getCurrentAccount()?.id ?: "")
      val backendItems = feedRepository.fetchFeedItems()
      val mergedItems = mergeFeedItemsWithLocalStorage(backendItems)
      _localFeedItems.value = mergedItems
      // Sync with IAM service
      ggIAMService.setFeedItems(mergedItems)
      // Emit updated items
      _feedsChanged.emit(mergedItems)
      updateNotificationBadge()
    } catch (error: Exception) {
      AppLog.e(TAG, "Failed to fetch feed items", error.toString())
      // On error, use local items if available
      val localItems = _localFeedItems.value
      _feedsChanged.emit(localItems)
      updateNotificationBadge()
    }
  }

  /**
   * Merges backend feed items with local storage, preserving read/unread status from backend.
   * Backend response takes precedence for read/unread status, but local items are preserved if not in backend.
   */
  private fun mergeFeedItemsWithLocalStorage(backendItems: List<FeedItem>): List<FeedItem> {
    val currentLocalItems = _localFeedItems.value
    val backendItemsMap = backendItems.associateBy { it.elementId }

    // Update existing local items with backend data (especially read/unread status)
    val updatedLocalItems = currentLocalItems.map { localItem ->
      backendItemsMap[localItem.elementId]?.let { backendItem ->
        // Backend response has the source of truth for read/unread status
        localItem.copy(
          isUnread = backendItem.isUnread,
          trigger = backendItem.trigger,
          expiresAt = backendItem.expiresAt,
          // Update other fields from backend if they changed
          titleText = backendItem.titleText,
          subtitleFeedText = backendItem.subtitleFeedText,
          subtitleModalText = backendItem.subtitleModalText,
          messageTypeText = backendItem.messageTypeText,
          linkTarget = backendItem.linkTarget,
          linkText = backendItem.linkText,
          feedType = backendItem.feedType,
          landingPage = backendItem.landingPage,
          promoCode = backendItem.promoCode,
        )
      } ?: localItem // Keep local item if not in backend response
    }

    // Add new items from backend that don't exist locally
    val newBackendItems = backendItems.filter { it.elementId !in currentLocalItems.map { item -> item.elementId } }

    return (updatedLocalItems + newBackendItems).distinctBy { it.elementId }
  }
  /**
   * Updates a feed item's state and syncs with backend and local storage.
   * Updates the local storage maintained by FeedService.
   */
  override suspend fun updateFeedItem(
    feedItem: FeedItem,
    actionType: FeedActionType,
    variationId: Int?
  ) {
    val action = buildFeedAction(actionType, variationId)

    try {
      // Update backend first
      feedRepository.updateFeedItem(feedItem.feedPostId, action)

      // Update local storage
      val currentItems = _localFeedItems.value
      val itemIndex = currentItems.indexOfFirst { it.elementId == feedItem.elementId }

      if (itemIndex != -1) {
        val itemToUpdate = currentItems[itemIndex]
        val updatedItem = when (actionType) {
          FeedActionType.read -> {
            // Mark as read and clear trigger
            itemToUpdate.copy(
              isUnread = false,
              trigger = null,
            )
          }
          FeedActionType.trigger -> {
            // Clear trigger only
            itemToUpdate.copy(trigger = null)
          }
          else -> itemToUpdate
        }

        // Update local storage
        val updatedItems = currentItems.toMutableList()
        updatedItems[itemIndex] = updatedItem
        _localFeedItems.value = updatedItems

        // Sync with IAM service
        ggIAMService.setFeedItems(updatedItems)

        // Notify that feed notification changed
        if (actionType == FeedActionType.read) {
          ggIAMService.emitFeedNotificationChange()
        }

        // Emit updated items
        _feedsChanged.emit(updatedItems)
        AppLog.d(TAG, "Updated feed item in local storage: ${feedItem.elementId}, action: $actionType")
      } else {
        AppLog.w(TAG, "Feed item not found in local storage: ${feedItem.elementId}")
      }

    } catch (error: Exception) {
      AppLog.e(TAG, "Failed to update feed item: ${feedItem.elementId}", error.toString())
    }
  }

  private fun buildFeedAction(actionType: FeedActionType, variationId: Int?): FeedAction {
    val osType = if (requiresMeta(actionType)) "Android" else null
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
    return !(actionType == FeedActionType.click ||
      actionType == FeedActionType.read ||
      actionType == FeedActionType.trigger)
  }

  override suspend fun getUnreadFeedCount(): Int {
    // Use local storage as the source of truth
    return _localFeedItems.value.count { it.isUnread }
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
      AppLog.d(TAG, "Showing IAM feed modal for item: ${feedItem.elementId}")
      val dialog = DialogModel.Custom(
        contentKey = DialogType.IAMFeedModal,
        params = mapOf(
          "feedItem" to feedItem,
          "elementId" to feedItem.elementId, // Store elementId for callback identification
        ),
        customPriority = 3, // Medium priority for IAM dialogs
        customDelayMillis = 0L,
        onDismiss = {
          dialogQueueService.dismissCurrent()
        },
        onConfirm = { actionType: Any ->
          serviceScope.launch {
            handleFeedModalAction(feedItem, actionType.toString())
          }
        },
      )

      dialogQueueService.showDialog(dialog)
      AppLog.d(TAG, "Display IAM feed modal for item: ${feedItem.elementId}")

      // Track feed modal open event
      serviceScope.launch {
        updateFeedItem(feedItem, FeedActionType.trigger, null)
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to show IAM feed modal", e.toString())
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
              // Track click (shop now / link tap) before opening link
              try {
                updateFeedItem(feedItem, FeedActionType.shopNowClick, null)
                AppLog.d(TAG, "Feed modal link click tracked for: ${feedItem.titleText}")
              } catch (e: Exception) {
                AppLog.e(TAG, "Failed to track feed modal link click", e.toString())
              }
              // Open external link on main thread so Custom Tabs / intent fires
              AppLog.d(TAG, "Opening external link for: ${feedItem.titleText}")
              feedItem.linkTarget?.let { link ->
                try {
                  withContext(Dispatchers.Main) {
                    val linkOpener = com.greatergoods.ggInAppMessaging.util.LinkOpener
                    linkOpener.openInCustomTab(
                      context = context,
                      url = link,
                      showTitle = true
                    )
                  }
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
   * Convert string action type to FeedActionType enum
   * Maps common action types from IAM service to FeedActionType
   */
  private fun convertStringToFeedActionType(actionType: String): FeedActionType {
    return when (actionType.trim()) {
      "trigger" -> FeedActionType.trigger
      "read" -> FeedActionType.read
      "click" -> FeedActionType.click
      "pageView" -> FeedActionType.pageView
      "shopNowClick" -> FeedActionType.shopNowClick
      "variationClick" -> FeedActionType.variationClick
      "promoClick" -> FeedActionType.promoClick
      else -> {
        AppLog.w(TAG, "Unknown action type: $actionType, defaulting to click")
        FeedActionType.click
      }
    }
  }

  /**
   * Generates dynamic mock feed items for testing purposes.
   * Creates feed items with varying content, IDs, and read/unread statuses.
   * If local items already exist, this adds mock items only if local storage is empty.
   */
  fun setMockFeedItems() {
    serviceScope.launch {
      try {
        val currentLocalItems = _localFeedItems.value

        // Only generate mock items if local storage is empty
        if (currentLocalItems.isNotEmpty()) {
          AppLog.d(TAG, "Local feed items already exist (${currentLocalItems.size} items), skipping mock generation")
          return@launch
        }

        // Generate dynamic mock feed items with varying content
        val mockItems = generateDynamicMockFeedItems()

        // Update local storage
        _localFeedItems.value = mockItems

        // Set the mock items in the IAM service
        ggIAMService.setFeedItems(mockItems)
        _feedsChanged.emit(mockItems)
        AppLog.d(TAG, "Generated and set ${mockItems.size} dynamic mock feed items")
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to set mock feed items", e.toString())
      }
    }
  }

  /**
   * Generates dynamic mock feed items with varying content, read/unread statuses, and IDs.
   */
  private suspend fun generateDynamicMockFeedItems(): List<FeedItem> {
    val currentTime = System.currentTimeMillis()
    val accountId = accountService.getCurrentAccount()?.id ?: "testAccount"

    // Dynamic product titles and variations
    val productTitles = listOf(
      "Special Offer from FeedService!",
      "For a Limited Time: Get 50% OFF our Coffee Grinder",
      "Exclusive Deal: Digital Kitchen Scale",
      "Flash Sale: Vacuum Sealer Bundle",
      "Weekend Special: Food Scale Pro"
    )

    val messageTypes = listOf("LIGHTENING DEAL", "SPECIAL OFFER", "FLASH SALE", "EXCLUSIVE DEAL")
    val feedTypes = listOf(FeedTypes.LINK, FeedTypes.LANDING)
    val linkTexts = listOf("Shop now", "shop now", "Buy Now", "Learn More", "Shop Today")

    // Generate 2-3 dynamic mock items
    val mockItemsCount = (2..3).random()
    val mockItems = mutableListOf<FeedItem>()

    for (i in 0 until mockItemsCount) {
      val titleIndex = i % productTitles.size
      val messageType = messageTypes[i % messageTypes.size]
      val feedType = feedTypes[i % feedTypes.size]
      val linkText = linkTexts[i % linkTexts.size]

      // Vary read/unread status dynamically
      val isUnread = i % 2 == 0 // Alternate between read/unread

      // Generate unique IDs based on timestamp
      val uniqueId = "${currentTime}_${i}_${(1000..9999).random()}"
      val elementId = "mockFromFeedService_$uniqueId"
      val feedPostId = "mockPost_$uniqueId"

      // Generate dynamic subtitle based on time
      val hoursRemaining = (24..72).random()
      val subtitleFeedText = "Ends in $hoursRemaining hours"
      val subtitleModalText = "This feed item was dynamically generated by FeedService (Item ${i + 1})"

      // Create landing page only for LANDING type
      val landingPage = if (feedType == FeedTypes.LANDING) {
        createDynamicLandingPage(feedPostId, i)
      } else null

      val mockItem = FeedItem(
        elementId = elementId,
        titleText = productTitles[titleIndex],
        messageTypeText = messageType,
        subtitleFeedText = subtitleFeedText,
        subtitleModalText = subtitleModalText,
        linkTarget = "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
        feedType = feedType,
        linkText = linkText,
        feedPostId = feedPostId,
        accountId = accountId,
        titleImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
        trigger = if (i == 0) "login" else null,
        isUnread = isUnread,
        landingPage = landingPage,
      )

      mockItems.add(mockItem)
    }

    return mockItems
  }

  /**
   * Creates a dynamic landing page for mock feed items.
   */
  private fun createDynamicLandingPage(feedPostId: String, index: Int): LandingPage {
    val productColors = listOf("Stone Blue", "Charcoal Gray", "Ivory White", "Cherry Red")
    val themeColors = listOf("red", "blue", "green", "purple")

    val color = productColors[index % productColors.size]
    val themeColor = themeColors[index % themeColors.size]

    // Generate dynamic variation IDs
    val variationIds = (1..3).map { (10000 + index * 10 + it) }

    return LandingPage(
      feedLandingPageId = "ZjsgSDU56trZrcrRnGgaHr",
      feedPostId = feedPostId,
      titleText = "Vacuum Sealers",
      promoCode = generateRandomPromoCode(),
      featuredImage = null,
      supportingTitleText = "One Machine, a Million Uses",
      supportingDescriptionText = "The Greater Goods {{bold[All-in-One Vacuum Sealer]}} has built-in bag storage and a slicer for hassle-free meal prep!",
      supportingImage = (1..(2..6).random()).map {
        "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg"
      },
      featuredTitleText = "Three Colors",
      themeColor = themeColor,
      featuredProduct = variationIds.map { variationId ->
        FeaturedProduct(
          variationId = variationId,
          titleText = color,
          feedLandingPageId = "ZjsgSDU56trZrcrRnGgaHr",
          linkText = "Shop",
          linkTarget = "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
          productImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
        )
      },
    )
  }

  /**
   * Generates a random promo code for mock feed items.
   */
  private fun generateRandomPromoCode(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..8).map { chars.random() }.joinToString("")
  }
}
