package com.greatergoods.ggInAppMessaging.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.ggInAppMessaging.core.service.GGInAppMessagingService
import com.greatergoods.ggInAppMessaging.core.utilities.IAMLogger
import com.greatergoods.ggInAppMessaging.domain.models.FeedTypes
import com.greatergoods.ggInAppMessaging.util.LinkOpener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.content.Context

/**
 * ViewModel for FeedMessagesScreen
 * Following MVI pattern used in main app
 */
class FeedMessagesViewModel(
  private val ggInAppMessagingService: GGInAppMessagingService,
  private val context: Context
) : ViewModel() {

  private val tag = "FeedMessagesViewModel"

  private val _state = MutableStateFlow(FeedMessagesState())
  val state: StateFlow<FeedMessagesState> = _state.asStateFlow()

  /**
   * Handle intents following MVI pattern
   */
  fun handleIntent(intent: FeedMessagesIntent) {
    val currentState = _state.value
    val newState = FeedMessagesReducer.reduce(currentState, intent)
    _state.value = newState

    when (intent) {
      is FeedMessagesIntent.LoadFeedItems -> {
        loadFeedItems()
      }

      is FeedMessagesIntent.RefreshFeedItems -> {
        loadFeedItems()
      }

      is FeedMessagesIntent.OnFeedItemClick -> {
        handleFeedItemClick(intent.elementId)
      }

      is FeedMessagesIntent.OnSettingsClick -> {
        // This will be handled by the UI layer
      }

      is FeedMessagesIntent.Retry -> {
        loadFeedItems()
      }
    }
  }

  /**
   * Load feed items from service
   */
  private fun loadFeedItems() {
    viewModelScope.launch {
      try {
        IAMLogger.d(tag, "Loading feed items")
        val feedItems = ggInAppMessagingService.getFeedItems()
        IAMLogger.d(tag, "Loaded ${feedItems.size} feed items")

        _state.value = FeedMessagesReducer.onFeedItemsLoaded(feedItems)(_state.value)
      } catch (e: Exception) {
        IAMLogger.e(tag, "Failed to load feed items", e.toString())
        _state.value = FeedMessagesReducer.onError("Failed to load feed items")(_state.value)
      }
    }
  }

  /**
   * Handle feed item click
   */
  private fun handleFeedItemClick(elementId: String) {
    try {
      IAMLogger.d(tag, "Feed item clicked: $elementId")
      // Find the clicked feed item
      val currentState = _state.value
      val clickedItem = currentState.feedItems.find { it.elementId == elementId }
      IAMLogger.d(tag, "Feed type clicked: ${clickedItem?.feedType}")

      if (clickedItem != null) {
        // Get the landing page URL from the feed item
        val landingPageUrl = clickedItem.landingPage?.feedLandingPageId
        if (clickedItem.feedType == FeedTypes.LINK || clickedItem.feedType != FeedTypes.LANDING) {
          IAMLogger.d(tag, "Opening link: $landingPageUrl")
          LinkOpener.openInCustomTab(
            context = context,
            url = clickedItem.linkText,
            toolbarColor = android.graphics.Color.parseColor("#1976D2"), // Material Blue
            showTitle = true,
          )
          // Open the link using custom tabs for better user experience
        } else {
          IAMLogger.w(tag, "No landing page URL found for feed item: $elementId")
        }
      } else {
        IAMLogger.w(tag, "Feed item not found: $elementId")
      }
    } catch (e: Exception) {
      IAMLogger.e(tag, "Failed to handle feed item click", e.toString())
    }
  }
}
