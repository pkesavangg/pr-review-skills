package com.dmdbrands.gurus.weight.features.feedMessages.model

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import kotlinx.collections.immutable.toImmutableList

/**
 * Reducer for Feed Messages Screen
 */
class FeedMessagesReducer : IReducer<FeedMessagesState, FeedMessagesIntent> {
  override fun reduce(state: FeedMessagesState, intent: FeedMessagesIntent): FeedMessagesState {
    return when (intent) {
      is FeedMessagesIntent.Refresh -> {
        state.copy(isRefreshing = true)
      }
      is FeedMessagesIntent.SetFeedItems -> {
        state.copy(
          feedItems = intent.feedItems.toImmutableList(),
          isLoading = false,
          error = null
        )
      }
      is FeedMessagesIntent.SetError -> {
        state.copy(
          isLoading = false,
          error = intent.error
        )
      }
      is FeedMessagesIntent.ClearError -> {
        state.copy(error = null)
      }
      is FeedMessagesIntent.SetLoading -> {
        state.copy(isLoading = true)
      }
      is FeedMessagesIntent.SetRefreshing -> {
        state.copy(isRefreshing = intent.isRefreshing)
      }
      else -> state
    }
  }
}
