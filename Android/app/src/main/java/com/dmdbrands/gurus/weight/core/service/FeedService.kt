package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.feed.FeedItem
import com.dmdbrands.gurus.weight.domain.repository.FeedAction
import com.dmdbrands.gurus.weight.domain.repository.IFeedRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.greatergoods.ggInAppMessaging.core.GGInAppMessagingService
import com.greatergoods.ggInAppMessaging.domain.models.FeedActionType
import com.greatergoods.ggInAppMessaging.domain.models.FeedSetting
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
  private val accountId: String?
    get() = accountService.activeAccountFlow.first().id

  private val feedInfoOfflineKey: String
    get() = "$feedInfoKey${if (accountId != null) "_$accountId" else ""}"

  private val feedLastTriggeredAt: String
    get() = "$feedLastTriggeredAtKey${if (accountId != null) "_$accountId" else ""}"

  init {
    val initialFeedSettings = getFeedSettings()

    // Listen for feed updates from the GG IAM service (matching Angular service)
    serviceScope.launch {
      ggIAMService.sendUpdateFeed.collect { feedInfo ->
        updateFeedItem(feedInfo.feedItem, feedInfo.actionType, feedInfo.variationId)
      }
    }

    // Listen for promo code copied events (matching Angular service)
    serviceScope.launch {
      ggIAMService.promoCodeCopiedSubject.collect { isCopied ->
        if (isCopied) {
          // TODO: Show toast notification when notification service is available
          // notificationService.showToast(message = "Promo code copied", duration = 3000)
          AppLog.i(tag, "Promo code copied")
        }
      }
    }

    // Listen for full-feed changes from the GG IAM service and propagate them internally
    serviceScope.launch {
      ggIAMService.feedsChanged.collect { newFeeds ->
        _feedsChanged.emit(newFeeds)
        updateNotificationBadge()
      }
    }

    serviceScope.launch {
      ggIAMService.feedNotificationChanged.collect {
        val result = getFeedSettings()
        _feedSettingsChanged.emit(result)
        updateNotificationBadge()
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
      ggIAMService.feedsUpdatedSubject.tryEmit(items)
      ggIAMService.feedNotificationChangedSubject.tryEmit(Unit)

      // Also emit to internal flows
      ggIAMService.load(items)
      _feedsChanged.emit(items)
      updateNotificationBadge()
      AppLog.i(tag, "Successfully fetched feed items")
    } catch (error: Exception) {
      AppLog.e(tag, "Failed to fetch feed items", error.toString())

      // Emit empty feeds on error (matching Angular service)
      ggIAMService.feedsUpdatedSubject.tryEmit(emptyList())
      ggIAMService.load(emptyList())
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
      val feedItemToUpdate = ggIAMService.feedsUpdatedSubject.value.find { item ->
        item?.elementId == feedItem?.elementId
      }

      if (feedItemToUpdate != null) {
        when (actionType) {
          FeedActionType.READ -> {
            feedItemToUpdate.isUnread = false
            feedItemToUpdate.trigger = null
            ggIAMService.feedNotificationChangedSubject.tryEmit(Unit)
          }

          FeedActionType.TRIGGER -> {
            feedItemToUpdate.trigger = null
          }

          else -> { /* No local update needed for other action types */
          }
        }
      }

      AppLog.i(tag, "Successfully updated feed item")
    } catch (error: Exception) {
      AppLog.e(tag, "Failed to update feed item", error.toString())
    }
  }

  override fun getUnreadFeedCount(): Int {
    return ggIAMService.getUnreadFeedCount()
  }

  // MARK: - Feed Settings Management

  override fun getFeedSettings(): FeedSetting? {
    return ggIAMService.getStoredFeedNotificationSetting()
  }

  // MARK: - Feed Modal Management

  override fun checkAndTriggerFeedModal() {
    serviceScope.launch {
      val result = ggIAMService.checkFeedModalTrigger()
      result?.let { feedItem ->
        // TODO: Show modal using notification service
        // notificationService.showModal(...)
        AppLog.d(tag, "Triggering feed modal for item: ${feedItem.feedPostId}")
      }
    }
  }

  // MARK: - Additional Methods (matching Angular service)

  /**
   * Get the current account ID (matching Angular service's accountId getter)
   */
  fun getCurrentAccountId(): String? = accountId

  /**
   * Get the feed info offline key (matching Angular service's feedInfoOfflineKey getter)
   */
  fun getFeedInfoOfflineKey(): String = feedInfoOfflineKey

  /**
   * Get the feed last triggered at key (matching Angular service's feedLastTriggeredAt getter)
   */
  fun getFeedLastTriggeredAtKey(): String = feedLastTriggeredAt

  // MARK: - Cleanup

  override fun clearFeedData() {
    ggIAMService.clearFeedData()
    serviceScope.launch {
      updateNotificationBadge()
    }
  }

  // MARK: - Private Helpers

  private fun buildFeedAction(actionType: FeedActionType, variationId: Int?): FeedAction {
    val action = FeedAction(action = actionType)

    if (requiresMeta(actionType)) {
      action.osType = "android" // Matching Angular service's AppStatus.devicePlatform
      action.meta = com.dmdbrands.gurus.weight.domain.repository.FeedActionMeta(variationId)
    }

    return action
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

  fun cleanup() {
    serviceScope.cancel()
  }
}
