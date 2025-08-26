package com.greatergoods.ggInAppMessaging.core.service

import com.google.gson.Gson
import com.greatergoods.ggInAppMessaging.domain.models.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main GG In-App Messaging service
 * Android equivalent of Angular gg-in-app-messaging.service.ts
 */
@Singleton
class GGInAppMessagingService @Inject constructor(
  private val feedStorageService: FeedStorageService
) {

  private val tag = "GGInAppMessagingService"

  // MARK: - Constants
  private val feedInfoKey = "feedInfo"
  private val feedLastTriggeredAtKey = "feedLastTriggeredAt"
  private val feedLandingPagePath = "/feed-landing-page"

  // MARK: - Properties
  private var accountId: String = ""
  private var libConfig: GGInAppMessagingConfig = GGInAppMessagingConfig()

  // MARK: - Subjects (BehaviorSubject equivalents)
  private val _feedsUpdatedSubject = MutableStateFlow<List<FeedItem>>(emptyList())
  val feedsUpdatedSubject: StateFlow<List<FeedItem>> = _feedsUpdatedSubject.asStateFlow()

  private val _sendUpdateFeed = MutableSharedFlow<FeedInfo>()
  val sendUpdateFeed = _sendUpdateFeed.asSharedFlow()

  private val _feedNotificationChangedSubject = MutableSharedFlow<Unit>()
  val feedNotificationChangedSubject = _feedNotificationChangedSubject.asSharedFlow()

  private val _promoCodeCopiedSubject = MutableStateFlow(false)
  val promoCodeCopiedSubject: StateFlow<Boolean> = _promoCodeCopiedSubject.asStateFlow()

  private val _darkModeChangedSubject = MutableStateFlow(false)
  val darkModeChangedSubject: StateFlow<Boolean> = _darkModeChangedSubject.asStateFlow()

  // MARK: - Computed Properties
  val feedInfoOfflineKey: String
    get() = "$feedInfoKey${if (accountId.isNotEmpty()) "_$accountId" else ""}"

  val feedLastTriggeredAt: String
    get() = "$feedLastTriggeredAtKey${if (accountId.isNotEmpty()) "_$accountId" else ""}"

  // MARK: - Public Methods

  /**
   * Set account ID
   */
  fun setAccountId(accountId: String) {
    this.accountId = accountId
    AppLog.d(tag, "Account ID set to: $accountId")
  }

  /**
   * Set library configuration
   */
  fun setLibConfig(config: GGInAppMessagingConfig) {
    this.libConfig = config
    AppLog.d(tag, "Library config updated")
  }

  /**
   * Get unread feed count
   */
  fun getUnreadFeedCount(): Int {
    val unreadFeeds = _feedsUpdatedSubject.value.filter { it.isUnread }
    return unreadFeeds.size
  }

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
    val feedValue = Gson().toJson(feedSetting)
    val success = feedStorageService.setValue(feedInfoOfflineKey, feedValue)
    if (success) {
      _feedNotificationChangedSubject.tryEmit(Unit)
      AppLog.d(tag, "Feed notification settings stored successfully")
    }
  }

  /**
   * Get stored feed notification settings
   */
  suspend fun getStoredFeedNotificationSetting(): FeedSetting? {
    val feedInfoObject = feedStorageService.getValue(feedInfoOfflineKey)
    return if (feedInfoObject?.value != null) {
      try {
        Gson().fromJson(feedInfoObject.value, FeedSetting::class.java)
      } catch (e: Exception) {
        AppLog.e(tag, "Failed to parse feed settings", e.toString())
        null
      }
    } else {
      null
    }
  }

  /**
   * Check if feed modal should be triggered
   */
  suspend fun checkFeedModalTrigger(): FeedItem? {
    val feedSetting = getStoredFeedNotificationSetting()
    if (feedSetting?.showPopupMessage == true) {
      val feedItem = _feedsUpdatedSubject.value.find {
        it.trigger == FeedTriggerEvents.LOGIN
      }
      if (feedItem != null) {
        AppLog.d(tag, "Feed modal trigger found for item: ${feedItem.feedPostId}")
        return feedItem
      }
    }
    return null
  }

  /**
   * Load feeds into the service
   */
  fun load(feeds: List<FeedItem>) {
    _feedsUpdatedSubject.value = feeds
    AppLog.d(tag, "Loaded ${feeds.size} feeds")
  }

  /**
   * Clear all feed data
   */
  fun clearFeedData() {
    _feedsUpdatedSubject.value = emptyList()
    AppLog.d(tag, "Feed data cleared")
  }

  /**
   * Set promo code copied state
   */
  fun setPromoCodeCopied(isCopied: Boolean) {
    _promoCodeCopiedSubject.value = isCopied
    AppLog.d(tag, "Promo code copied state set to: $isCopied")
  }

  /**
   * Set dark mode state
   */
  fun setDarkMode(isDarkMode: Boolean) {
    _darkModeChangedSubject.value = isDarkMode
    AppLog.d(tag, "Dark mode state set to: $isDarkMode")
  }

  /**
   * Emit feed update
   */
  suspend fun emitFeedUpdate(feedInfo: FeedInfo) {
    _sendUpdateFeed.emit(feedInfo)
    AppLog.d(tag, "Feed update emitted for item: ${feedInfo.feedItem.feedPostId}")
  }

  /**
   * Emit feed notification change
   */
  suspend fun emitFeedNotificationChange() {
    _feedNotificationChangedSubject.emit(Unit)
    AppLog.d(tag, "Feed notification change emitted")
  }

  // MARK: - Navigation Methods (Android equivalents)

  /**
   * Navigate to FAQ (Android equivalent)
   */
  fun navigateToFAQ() {
    val navigationPath = "${libConfig.baseNavigationPath}$feedLandingPagePath/faq"
    AppLog.d(tag, "Navigation to FAQ requested: $navigationPath")
    // TODO: Implement navigation using Navigation Component or deep linking
  }

  /**
   * Navigate to feed landing page (Android equivalent)
   */
  fun navigateFeedLandingPage(feedItem: FeedItem, isFromModal: Boolean = false) {
    val navigationPath = "${libConfig.baseNavigationPath}$feedLandingPagePath"
    AppLog.d(tag, "Navigation to feed landing page requested: $navigationPath")
    // TODO: Implement navigation using Navigation Component or deep linking
  }

  /**
   * Show feed modal (Android equivalent)
   */
  suspend fun showFeedModal(feedItem: FeedItem): Boolean {
    AppLog.d(tag, "Show feed modal requested for item: ${feedItem.feedPostId}")
    // TODO: Implement modal display using Dialog or BottomSheet
    return true
  }
}
