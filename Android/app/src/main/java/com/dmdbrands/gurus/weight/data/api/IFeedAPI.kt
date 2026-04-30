package com.dmdbrands.gurus.weight.data.api

import com.dmdbrands.gurus.weight.core.network.HttpClient
import com.dmdbrands.gurus.weight.domain.model.feed.FeedItem
import com.dmdbrands.gurus.weight.domain.repository.FeedAction
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * API interface for feed data operations.
 * Supports token-based authentication for different accounts using X-Account-ID header.
 */
interface IFeedAPI {
  companion object {
    const val FEED = "feed"
    const val IAM = "/iam"
    const val FEED_IAM = FEED + IAM // "feed/iam"
  }

  /**
   * Fetches all feed items from the backend. (GET /feed/iam)
   * @return List of feed items
   */
  @GET(FEED + IAM)
  suspend fun fetchFeedItems(
    @Header(HttpClient.ACCOUNT_ID_HEADER) accountId: String,
  ): List<FeedItem>

  /**
   * Updates a feed item's state with the given action. (POST /feed/iam/{feedPostId})
   * @param feedPostId The ID of the feed item to update
   * @param feedAction The action to perform on the feed item
   */
  @POST("$FEED$IAM/{feedPostId}")
  suspend fun updateFeedItem(
    @Path("feedPostId") feedPostId: String,
    @Body feedAction: FeedAction,
    @Header(HttpClient.ACCOUNT_ID_HEADER) accountId: String,
  )
}
