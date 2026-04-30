package com.dmdbrands.gurus.weight.features.feedMessages.model

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem

/**
 * Intents for Feed Messages Screen
 */
sealed class FeedMessagesIntent : IReducer.Intent {
  data object Initialize : FeedMessagesIntent()
  data object Refresh : FeedMessagesIntent()
  data object OnBackPress : FeedMessagesIntent()
  data object OnSettingsPress : FeedMessagesIntent()
  data class OnNavigateToFeedLanding(val feedItem: FeedItem) : FeedMessagesIntent()
  data class SetFeedItems(val feedItems: List<FeedItem>) : FeedMessagesIntent()
  data class SetError(val error: String) : FeedMessagesIntent()
  data object ClearError : FeedMessagesIntent()
  data object SetLoading : FeedMessagesIntent()
  data class SetRefreshing(val isRefreshing: Boolean) : FeedMessagesIntent()
}
