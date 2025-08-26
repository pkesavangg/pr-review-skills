package com.greatergoods.ggInAppMessaging.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.ggInAppMessaging.core.GGInAppMessagingService
import com.greatergoods.ggInAppMessaging.core.NetworkMonitor
import com.greatergoods.ggInAppMessaging.domain.models.IAMFeedItem
import com.greatergoods.ggInAppMessaging.domain.models.FeedActionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for FeedListView
 * Android equivalent of iOS FeedListViewModel
 */
@HiltViewModel
class FeedListViewModel @Inject constructor(
    private val ggIAMService: GGInAppMessagingService,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {
    
    private val tag = "FeedListViewModel"
    
    /**
     * Feed items from the service
     */
    val feeds: StateFlow<List<IAMFeedItem>> = ggIAMService.feeds
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * Network connectivity status
     */
    val isConnected: StateFlow<Boolean> = networkMonitor.isConnected
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )
    
    /**
     * Updates the click status of a feed item
     */
    fun updateClickStatus(feed: IAMFeedItem) {
        viewModelScope.launch {
            try {
                ggIAMService.updateFeedItem(feed, FeedActionType.CLICK)
                Timber.d("[$tag] Updated click status for feed: ${feed.feedPostId}")
            } catch (e: Exception) {
                Timber.e(e, "[$tag] Failed to update click status for feed: ${feed.feedPostId}")
            }
        }
    }
}