package com.dmdbrands.gurus.weight.features.feed.shared

import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared holder for the currently selected feed item.
 * Used to pass feed item data between screens via Flow.
 */
@Singleton
class SelectedFeedItemHolder @Inject constructor() {
    private val _selectedFeedItem = MutableStateFlow<FeedItem?>(null)
    val selectedFeedItem: StateFlow<FeedItem?> = _selectedFeedItem.asStateFlow()

    /**
     * Sets the selected feed item
     */
    fun setSelectedFeedItem(feedItem: FeedItem?) {
        _selectedFeedItem.value = feedItem
    }

    /**
     * Clears the selected feed item
     */
    fun clearSelectedFeedItem() {
        _selectedFeedItem.value = null
    }
}
