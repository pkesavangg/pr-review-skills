package com.dmdbrands.gurus.weight.features.feedMessages.model

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * State for Feed Messages Screen
 */
@Stable
data class FeedMessagesState(
  val isLoading: Boolean = false,
  val error: String? = null,
  val feedItems: ImmutableList<FeedItem> = persistentListOf(),
  val isRefreshing: Boolean = false
) : IReducer.State
