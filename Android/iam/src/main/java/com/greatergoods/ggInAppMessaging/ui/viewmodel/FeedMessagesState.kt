package com.greatergoods.ggInAppMessaging.ui.viewmodel

import com.greatergoods.ggInAppMessaging.domain.models.FeedItem

/**
 * State for FeedMessagesViewModel
 * Following MVI pattern used in main app
 */
data class FeedMessagesState(
  /** List of feed items to display */
  val feedItems: List<FeedItem> = emptyList(),

  /** Whether data is currently loading */
  val isLoading: Boolean = false,

  /** Error message if any */
  val error: String? = null,

  /** Whether to show empty state */
  val showEmptyState: Boolean = false,

  /** Whether pop-up messages are enabled */
  val popUpMessagesEnabled: Boolean = true,

  /** Whether notification badges are enabled */
  val notificationBadgesEnabled: Boolean = true,

  /** Whether settings are currently loading */
  val isLoadingSettings: Boolean = false
)
