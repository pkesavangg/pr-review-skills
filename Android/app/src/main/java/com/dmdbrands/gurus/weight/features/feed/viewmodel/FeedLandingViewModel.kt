package com.dmdbrands.gurus.weight.features.feed.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.feed.model.FeedLandingIntent
import com.dmdbrands.gurus.weight.features.feed.model.FeedLandingReducer
import com.dmdbrands.gurus.weight.features.feed.model.FeedLandingState
import com.dmdbrands.gurus.weight.features.feed.shared.SelectedFeedItemHolder
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Feed Landing Screen
 * Follows MVI pattern with reducer for state management
 */
@HiltViewModel
class FeedLandingViewModel @Inject constructor(
  private val feedService: IFeedService,
  private val appNavigationService: IAppNavigationService,
  private val selectedFeedItemHolder: SelectedFeedItemHolder
) : BaseIntentViewModel<FeedLandingState, FeedLandingIntent>(
  reducer = FeedLandingReducer(),
) {

  override fun provideInitialState(): FeedLandingState = FeedLandingState()
  val TAG = "FeedLandingViewModel"

  init {
    // Observe selected feed item changes
    selectedFeedItemHolder.selectedFeedItem
      .onEach { feedItem ->
        if (feedItem != null) {
          setFeedItem(feedItem)
          AppLog.d("FeedLandingViewModel", "Received selected feed item: ${feedItem.titleText}")
        }
      }
      .launchIn(viewModelScope)
  }

  /**
   * Handles incoming intents and updates the state accordingly.
   * @param intent The intent to handle.
   */
  override fun handleIntent(intent: FeedLandingIntent) {
    super.handleIntent(intent)
    when (intent) {
      is FeedLandingIntent.OnBackPress -> navigateBack()
      is FeedLandingIntent.OpenFAQ -> navigateToFAQ()
      else -> null
    }
  }

  /**
   * Sets the feed item for display
   */
  fun setFeedItem(feedItem: FeedItem) {
    handleIntent(FeedLandingIntent.SetFeedItem(feedItem))
    AppLog.d("FeedLandingViewModel", "Feed item set: ${feedItem.titleText}")
  }

  /**
   * Navigate back to previous screen
   */
  private fun navigateBack() {
    viewModelScope.launch {
      try {
        appNavigationService.navigateBack()
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to navigate back", e.toString())
      }
    }
  }

  fun navigateToFAQ() {
    viewModelScope.launch {
      appNavigationService.navigateTo(AppRoute.Feed.FeedFAQ)
    }
  }
}
