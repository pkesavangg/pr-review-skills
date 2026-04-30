package com.greatergoods.ggInAppMessaging.ui.viewmodel

/**
 * Intent actions for FeedMessagesViewModel
 * Following MVI pattern used in main app
 */
sealed class FeedMessagesIntent {
  /** Load feed items from service */
  object LoadFeedItems : FeedMessagesIntent()

  /** Refresh feed items */
  object RefreshFeedItems : FeedMessagesIntent()

  /** Handle feed item click */
  data class OnFeedItemClick(val elementId: String) : FeedMessagesIntent()

  /** Handle settings button click */
  object OnSettingsClick : FeedMessagesIntent()

  /** Handle retry after error */
  object Retry : FeedMessagesIntent()

  /** Load feed settings */
  object LoadFeedSettings : FeedMessagesIntent()

  /** Toggle pop-up messages setting */
  data class TogglePopUpMessages(val enabled: Boolean) : FeedMessagesIntent()

  /** Toggle notification badges setting */
  data class ToggleNotificationBadges(val enabled: Boolean) : FeedMessagesIntent()
}
