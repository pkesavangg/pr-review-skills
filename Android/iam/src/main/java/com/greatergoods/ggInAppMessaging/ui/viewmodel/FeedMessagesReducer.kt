package com.greatergoods.ggInAppMessaging.ui.viewmodel

import com.greatergoods.ggInAppMessaging.domain.models.FeedItem

/**
 * Reducer for FeedMessagesViewModel
 * Following MVI pattern used in main app
 */
object FeedMessagesReducer {

  /**
   * Reduce state based on intent
   */
  fun reduce(state: FeedMessagesState, intent: FeedMessagesIntent): FeedMessagesState {
    return when (intent) {
      is FeedMessagesIntent.LoadFeedItems -> {
        state.copy(
          isLoading = true,
          error = null
        )
      }

      is FeedMessagesIntent.RefreshFeedItems -> {
        state.copy(
          isLoading = true,
          error = null
        )
      }

      is FeedMessagesIntent.OnFeedItemClick -> {
        // No state change needed for click events
        state.copy()
      }

      is FeedMessagesIntent.OnSettingsClick -> {
        // No state change needed for settings click
        state
      }

      is FeedMessagesIntent.Retry -> {
        state.copy(
          isLoading = true,
          error = null
        )
      }

      is FeedMessagesIntent.LoadFeedSettings -> {
        state.copy(
          isLoadingSettings = true,
          error = null
        )
      }

      is FeedMessagesIntent.TogglePopUpMessages -> {
        state.copy(
          popUpMessagesEnabled = intent.enabled
        )
      }

      is FeedMessagesIntent.ToggleNotificationBadges -> {
        state.copy(
          notificationBadgesEnabled = intent.enabled
        )
      }
    }
  }

  /**
   * Reduce state for successful feed items load
   */
  fun onFeedItemsLoaded(feedItems: List<FeedItem>): (FeedMessagesState) -> FeedMessagesState = { state ->
    state.copy(
      feedItems = feedItems,
      isLoading = false,
      error = null,
      showEmptyState = feedItems.isEmpty()
    )
  }

  /**
   * Reduce state for error
   */
  fun onError(error: String): (FeedMessagesState) -> FeedMessagesState = { state ->
    state.copy(
      isLoading = false,
      error = error,
      showEmptyState = state.feedItems.isEmpty()
    )
  }

  /**
   * Reduce state for successful settings load
   */
  fun onSettingsLoaded(
    popUpMessagesEnabled: Boolean,
    notificationBadgesEnabled: Boolean
  ): (FeedMessagesState) -> FeedMessagesState = { state ->
    state.copy(
      popUpMessagesEnabled = popUpMessagesEnabled,
      notificationBadgesEnabled = notificationBadgesEnabled,
      isLoadingSettings = false,
      error = null
    )
  }

  /**
   * Reduce state for settings error
   */
  fun onSettingsError(error: String): (FeedMessagesState) -> FeedMessagesState = { state ->
    state.copy(
      isLoadingSettings = false,
      error = error
    )
  }
}
