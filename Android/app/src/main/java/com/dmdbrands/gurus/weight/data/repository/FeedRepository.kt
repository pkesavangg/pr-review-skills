package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.IFeedAPI
import com.dmdbrands.gurus.weight.domain.model.feed.FeedItem
import com.dmdbrands.gurus.weight.domain.repository.FeedAction
import com.dmdbrands.gurus.weight.domain.repository.IFeedRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of the IFeedRepository interface.
 * Handles feed operations using API calls.
 */
@Singleton
class FeedRepository @Inject constructor(
  private val feedAPI: IFeedAPI,
  private val accountService: IAccountService,
) : IFeedRepository {
  companion object {
    private const val TAG = "FeedRepository"
  }

  /**
   * Fetches all feed items from the backend.
   * @return List of feed items
   */
  override suspend fun fetchFeedItems(): List<FeedItem> {
    return try {
      val currentAccount = accountService.getCurrentAccount()
      val accountId = currentAccount?.id ?: ""
      val feedItems = feedAPI.fetchFeedItems(accountId)
      AppLog.i(TAG, "Successfully fetched feed items")
      feedItems
    } catch (error: Exception) {
      AppLog.e(TAG, "Failed to fetch feed items", error.toString())
      emptyList()
    }
  }

  /**
   * Updates a feed item's state with the given action.
   * @param feedPostId The ID of the feed item to update
   * @param feedAction The action to perform on the feed item
   */
  override suspend fun updateFeedItem(feedPostId: String, feedAction: FeedAction) {
    try {
      val currentAccount = accountService.getCurrentAccount()
      val accountId = currentAccount?.id ?: ""
      feedAPI.updateFeedItem(feedPostId, feedAction, accountId)
      AppLog.i(TAG, "Successfully updated feed item")
    } catch (error: Exception) {
      AppLog.e(TAG, "Failed to update feed item", error.toString())
    }
  }
}
