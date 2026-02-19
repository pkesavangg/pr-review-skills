package com.greatergoods.ggInAppMessaging.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.ggInAppMessaging.core.utilities.IAMLogger
import com.greatergoods.ggInAppMessaging.core.viewmodel.BaseIntentViewModel
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.domain.models.FeedTypes
import com.greatergoods.ggInAppMessaging.domain.services.IInAppMessagingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * ViewModel for FeedMessagesScreen
 * Following MVI pattern used in main app
 */
@HiltViewModel
class FeedMessagesViewModel @Inject constructor(
  private val inAppMessagingService: IInAppMessagingService,
) : BaseIntentViewModel<FeedMessagesState, FeedMessagesIntent>() {

  private val tag = "FeedMessagesViewModel"
  private val readSentElementIds = mutableSetOf<String>()

  override fun provideInitialState(): FeedMessagesState = FeedMessagesState()

  init {
    subscribeToFeedItems()
  }

  fun subscribeToFeedItems() {
    inAppMessagingService.feedItems
      .onEach { feedItems ->
        updateState(FeedMessagesReducer.onFeedItemsLoaded(feedItems)(currentState))
        markAllFeedItemsAsRead(feedItems)
      }
      .launchIn(viewModelScope)
  }

  /**
   * Handle intents following MVI pattern
   */
  override fun handleIntent(intent: FeedMessagesIntent) {
    val newState = FeedMessagesReducer.reduce(currentState, intent)
    updateState(newState)

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

      is FeedMessagesIntent.LoadFeedSettings -> {
        loadFeedSettings()
      }

      is FeedMessagesIntent.TogglePopUpMessages -> {
        togglePopUpMessages(intent.enabled)
      }

      is FeedMessagesIntent.ToggleNotificationBadges -> {
        toggleNotificationBadges(intent.enabled)
      }
    }
  }

  /**
   * Load feed items from service
   */
  private fun loadFeedItems() {
    launch {
      try {
        IAMLogger.d(tag, "Loading feed items")
        val feedItems = inAppMessagingService.getFeedItems()
        IAMLogger.d(tag, "Loaded ${feedItems.size} feed items")
        updateState(FeedMessagesReducer.onFeedItemsLoaded(feedItems)(currentState))
        markAllFeedItemsAsRead(feedItems)
      } catch (e: Exception) {
        IAMLogger.e(tag, "Failed to load feed items", e.toString())
        updateState(FeedMessagesReducer.onError("Failed to load feed items")(currentState))
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
      val currentState = state.value
      val clickedItem = currentState.feedItems.find { it.elementId == elementId }
      IAMLogger.d(tag, "Feed type clicked: ${clickedItem?.feedType}")

      if (clickedItem != null) {
        if (clickedItem.feedType == FeedTypes.LANDING) {
          IAMLogger.d(tag, "Landing feed clicked. Skipping click action; landing screen will emit pageView")
          return
        }

        launch {
          inAppMessagingService.emitFeedUpdate(clickedItem, "click")
        }
      } else {
        IAMLogger.w(tag, "Feed item not found: $elementId")
      }
    } catch (e: Exception) {
      IAMLogger.e(tag, "Failed to handle feed item click", e.toString())
    }
  }

  /**
   * Load feed settings from service
   */
  private fun loadFeedSettings() {
    launch {
      try {
        IAMLogger.d(tag, "Loading feed settings")

        // First, ensure settings are initialized with default values if they don't exist
        inAppMessagingService.checkStoredFeedNotification()

        val popUpEnabled = inAppMessagingService.getPopupMessageSetting()
        val notificationEnabled = inAppMessagingService.getNotificationBadgeSetting()

        IAMLogger.d(
          tag,
          "Loaded settings - PopUp: $popUpEnabled, Notification: $notificationEnabled",
        )

        updateState(FeedMessagesReducer.onSettingsLoaded(
          popUpMessagesEnabled = popUpEnabled,
          notificationBadgesEnabled = notificationEnabled,
        )(currentState))
      } catch (e: Exception) {
        IAMLogger.e(tag, "Failed to load feed settings", e.toString())
        updateState(FeedMessagesReducer.onSettingsError("Failed to load settings")(currentState))
      }
    }
  }

  /**
   * Toggle pop-up messages setting
   */
  private fun togglePopUpMessages(enabled: Boolean) {
    launch {
      try {
        IAMLogger.d(tag, "Toggling pop-up messages: $enabled")
        inAppMessagingService.updatePopupMessageSetting(enabled)
        IAMLogger.d(tag, "Successfully updated pop-up messages setting")
      } catch (e: Exception) {
        IAMLogger.e(tag, "Failed to toggle pop-up messages", e.toString())
        updateState(FeedMessagesReducer.onSettingsError("Failed to update pop-up messages setting")(currentState))
      }
    }
  }

  /**
   * Toggle notification badges setting
   */
  private fun toggleNotificationBadges(enabled: Boolean) {
    launch {
      try {
        IAMLogger.d(tag, "Toggling notification badges: $enabled")
        inAppMessagingService.updateNotificationBadgeSetting(enabled)
        IAMLogger.d(tag, "Successfully updated notification badges setting")
      } catch (e: Exception) {
        IAMLogger.e(tag, "Failed to toggle notification badges", e.toString())
        updateState(FeedMessagesReducer.onSettingsError("Failed to update notification badges setting")(currentState))
      }
    }
  }

  /**
   * Mark all feed items as read when user opens the messages screen
   * This updates the unread status via API and triggers feed notification changes
   */
  private fun markAllFeedItemsAsRead(feedItems: List<FeedItem>) {
    launch {
      try {
        val feedItemsToMarkRead = feedItems.filter { readSentElementIds.add(it.elementId) }

        if (feedItemsToMarkRead.isNotEmpty()) {
          IAMLogger.d(tag, "Marking ${feedItemsToMarkRead.size} feed items as read")

          // Mark each feed item as read when feed list view is opened
          feedItemsToMarkRead.forEach { feedItem ->
            markFeedItemAsRead(feedItem.elementId)
          }

          // Trigger feed notification change to update indicators
          inAppMessagingService.emitFeedNotificationChange()

          IAMLogger.d(tag, "Successfully marked feed items as read")
        }
      } catch (e: Exception) {
        IAMLogger.e(tag, "Failed to mark feed items as read", e.toString())
      }
    }
  }

  /**
   * Mark a specific feed item as read
   * Calls the service to update the read status via API
   */
  private suspend fun markFeedItemAsRead(elementId: String) {
    try {
      IAMLogger.d(tag, "Marking feed item as read: $elementId")

      // Call the service to mark the feed item as read
      inAppMessagingService.markFeedAsRead(elementId, "read")

      IAMLogger.d(tag, "Feed item marked as read: $elementId")
    } catch (e: Exception) {
      IAMLogger.e(tag, "Failed to mark feed item as read: $elementId", e.toString())
    }
  }
}
