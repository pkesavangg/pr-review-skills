package com.dmdbrands.gurus.weight.features.feedMessages.model

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem

/**
 * State for Feed Messages Screen
 */
data class FeedMessagesState(
  val isLoading: Boolean = false,
  val error: String? = null,
  val feedItems: List<FeedItem> = emptyList(),
  val isRefreshing: Boolean = true
) : IReducer.State
