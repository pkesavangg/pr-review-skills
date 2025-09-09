package com.greatergoods.ggInAppMessaging.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.ggInAppMessaging.core.utilities.IAMLogger
import com.greatergoods.ggInAppMessaging.domain.models.FeedTypes
import com.greatergoods.ggInAppMessaging.domain.services.IInAppMessagingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for FeedMessagesScreen
 * Following MVI pattern used in main app
 */
@HiltViewModel
class FeedMessagesViewModel @Inject constructor(
  private val inAppMessagingService: IInAppMessagingService,
) : ViewModel() {

  private val tag = "FeedMessagesViewModel"

  private val _state = MutableStateFlow(FeedMessagesState())
  val state: StateFlow<FeedMessagesState> = _state.asStateFlow()

  init {
    subscribeToFeedItems()
    markAllFeedItemsAsRead()
  }

  fun subscribeToFeedItems() {
    viewModelScope.launch {
      inAppMessagingService.feedItems.collect { feedItems ->
        _state.value = FeedMessagesReducer.onFeedItemsLoaded(feedItems)(_state.value)
      }
    }
  }

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
    viewModelScope.launch {
      try {
        IAMLogger.d(tag, "Loading feed items")
        val feedItems = inAppMessagingService.getFeedItems()
        IAMLogger.d(tag, "Loaded ${feedItems.size} feed items")
        _state.value = FeedMessagesReducer.onFeedItemsLoaded(feedItems)(_state.value)
      } catch (e: Exception) {
        IAMLogger.e(tag, "Failed to load feed items", e.toString())
        _state.value =
          FeedMessagesReducer.onError("Failed to load feed items")(_state.value)
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
        if (clickedItem.feedType == FeedTypes.LINK || clickedItem.feedType != FeedTypes.LANDING) {
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

  /**
   * Load feed settings from service
   */
  private fun loadFeedSettings() {
    viewModelScope.launch {
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

        _state.value = FeedMessagesReducer.onSettingsLoaded(
          popUpMessagesEnabled = popUpEnabled,
          notificationBadgesEnabled = notificationEnabled,
        )(_state.value)
      } catch (e: Exception) {
        IAMLogger.e(tag, "Failed to load feed settings", e.toString())
        _state.value =
          FeedMessagesReducer.onSettingsError("Failed to load settings")(_state.value)
      }
    }
  }

  /**
   * Toggle pop-up messages setting
   */
  private fun togglePopUpMessages(enabled: Boolean) {
    viewModelScope.launch {
      try {
        IAMLogger.d(tag, "Toggling pop-up messages: $enabled")
        inAppMessagingService.updatePopupMessageSetting(enabled)
        IAMLogger.d(tag, "Successfully updated pop-up messages setting")
      } catch (e: Exception) {
        IAMLogger.e(tag, "Failed to toggle pop-up messages", e.toString())
        _state.value =
          FeedMessagesReducer.onSettingsError("Failed to update pop-up messages setting")(
            _state.value,
          )
      }
    }
  }

  /**
   * Toggle notification badges setting
   */
  private fun toggleNotificationBadges(enabled: Boolean) {
    viewModelScope.launch {
      try {
        IAMLogger.d(tag, "Toggling notification badges: $enabled")
        inAppMessagingService.updateNotificationBadgeSetting(enabled)
        IAMLogger.d(tag, "Successfully updated notification badges setting")
      } catch (e: Exception) {
        IAMLogger.e(tag, "Failed to toggle notification badges", e.toString())
        _state.value =
          FeedMessagesReducer.onSettingsError("Failed to update notification badges setting")(
            _state.value,
          )
      }
    }
  }

  /**
   * Mark all feed items as read when user opens the messages screen
   * This updates the unread status via API and triggers feed notification changes
   */
  private fun markAllFeedItemsAsRead() {
    viewModelScope.launch {
      try {
        IAMLogger.d(tag, "Marking all feed items as read")

        // Get current feed items
        val currentFeedItems = inAppMessagingService.getFeedItems()
        val unreadItems = currentFeedItems.filter { it.isUnread }

        if (unreadItems.isNotEmpty()) {
          IAMLogger.d(tag, "Found ${unreadItems.size} unread items to mark as read")

          // Mark each unread item as read
          unreadItems.forEach { feedItem ->
            markFeedItemAsRead(feedItem.elementId)
          }

          // Trigger feed notification change to update indicators
          inAppMessagingService.emitFeedNotificationChange()

          IAMLogger.d(tag, "Successfully marked all feed items as read")
        } else {
          IAMLogger.d(tag, "No unread items found")
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
