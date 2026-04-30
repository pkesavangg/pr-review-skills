package com.dmdbrands.gurus.weight.features.feedMessages.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.feed.shared.SelectedFeedItemHolder
import com.dmdbrands.gurus.weight.features.feedMessages.model.FeedMessagesIntent
import com.dmdbrands.gurus.weight.features.feedMessages.model.FeedMessagesReducer
import com.dmdbrands.gurus.weight.features.feedMessages.model.FeedMessagesState
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Feed Messages Screen
 * Follows MVI pattern with reducer for state management
 */
@HiltViewModel
class FeedMessagesViewModel @Inject constructor(
  private val feedService: IFeedService,
  private val selectedFeedItemHolder: SelectedFeedItemHolder
) : BaseIntentViewModel<FeedMessagesState, FeedMessagesIntent>(
  reducer = FeedMessagesReducer(),
) {

  override fun provideInitialState(): FeedMessagesState = FeedMessagesState()

  init {
    viewModelScope.launch {
    feedService.fetchFeedItems()
    }
    // Observe feed changes
    feedService.feedsChanged
      .onEach { feedItems ->
        handleIntent(FeedMessagesIntent.SetFeedItems(feedItems))
        AppLog.d("FeedMessagesViewModel", "Received ${feedItems.size} feed items")
      }
      .launchIn(viewModelScope)
  }

  /**
   * Handles incoming intents and updates the state accordingly.
   */
  override fun handleIntent(intent: FeedMessagesIntent) {
    super.handleIntent(intent)
    when (intent) {
      is FeedMessagesIntent.Refresh -> {
        refresh()
      }
      is FeedMessagesIntent.OnBackPress -> navigateBack()
      is FeedMessagesIntent.OnSettingsPress -> navigateToSettings()
      is FeedMessagesIntent.OnNavigateToFeedLanding -> navigateToFeedLanding(intent.feedItem)
      else -> {}
    }
  }

  /**
   * Refreshes the feed messages
   */
  private fun refresh() {
    viewModelScope.launch {
      try {
        handleIntent(FeedMessagesIntent.SetRefreshing(true))
        loadFeedMessages()
      } catch (e: Exception) {
      } finally {
        handleIntent(FeedMessagesIntent.SetRefreshing(false))
      }
    }
  }

  /**
   * Loads feed messages from the service
   */
  private suspend fun loadFeedMessages() {
    try {
      feedService.fetchFeedItems()
    } catch (e: Exception) {
      handleIntent(FeedMessagesIntent.SetError("Failed to load feed messages"))
    }
  }

  /**
   * Navigates back from the feed messages screen
   */
  private fun navigateBack() {
    viewModelScope.launch {
      try {
        navigationService.navigateBack()
      } catch (e: Exception) {
      }
    }
  }

  /**
   * Navigates to feed messages settings
   */
  private fun navigateToSettings() {
    viewModelScope.launch {
      try {
        navigationService.navigateTo(AppRoute.Feed.FeedMessageSetting)
      } catch (e: Exception) {
        AppLog.e("FeedMessagesViewModel", "Failed to navigate to settings", e.toString())
      }
    }
  }

  /**
   * Navigates to feed landing screen with the selected feed item
   */
  private fun navigateToFeedLanding(feedItem: FeedItem) {
    viewModelScope.launch {
      try {
        // Set the selected feed item before navigation
        selectedFeedItemHolder.setSelectedFeedItem(feedItem)
        // Navigate to the feed landing screen
        navigationService.navigateTo(AppRoute.Feed.FeedLanding)
      } catch (e: Exception) {
        AppLog.e("FeedMessagesViewModel", "Failed to navigate to feed landing", e.toString())
      }
    }
  }
}
