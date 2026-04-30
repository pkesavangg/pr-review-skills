package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.domain.model.feed.FeedItem

/**
 * Repository interface for feed data operations.
 * Defines the contract for fetching and updating feed items.
 */
interface IFeedRepository {

  /**
   * Fetches all feed items from the backend.
   * @return List of feed items
   */
  suspend fun fetchFeedItems(): List<FeedItem>

  /**
   * Updates a feed item's state with the given action.
   * @param feedPostId The ID of the feed item to update
   * @param feedAction The action to perform on the feed item
   */
  suspend fun updateFeedItem(feedPostId: String, feedAction: FeedAction)
}

/**
 * Represents a feed action with metadata.
 */
data class FeedAction(
  /** The type of action performed. */
  val action: com.greatergoods.ggInAppMessaging.domain.models.FeedActionType,
  /** Optional: The operating system type. */
  val osType: String?,
  /** Optional: Additional metadata for the action. */
  val meta: FeedActionMeta?
)

/**
 * Metadata for a feed action.
 */
data class FeedActionMeta(
  /** The variation ID associated with the action. */
  val variationId: Int?
)
