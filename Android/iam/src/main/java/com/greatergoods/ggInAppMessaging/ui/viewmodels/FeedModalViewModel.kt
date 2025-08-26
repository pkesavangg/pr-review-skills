package com.greatergoods.ggInAppMessaging.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.ggInAppMessaging.core.GGInAppMessagingService
import com.greatergoods.ggInAppMessaging.domain.models.IAMFeedItem
import com.greatergoods.ggInAppMessaging.domain.models.FeedActionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for IAMFeedModalView
 * Android equivalent of iOS FeedModalViewModel
 */
@HiltViewModel
class FeedModalViewModel @Inject constructor(
    private val ggIAMService: GGInAppMessagingService
) : ViewModel() {
    
    private val tag = "FeedModalViewModel"
    
    private val _shouldCloseModal = MutableStateFlow(false)
    val shouldCloseModal: StateFlow<Boolean> = _shouldCloseModal.asStateFlow()
    
    private val _showProductBrowser = MutableStateFlow(false)
    val showProductBrowser: StateFlow<Boolean> = _showProductBrowser.asStateFlow()
    
    private val _productURL = MutableStateFlow<String?>(null)
    val productURL: StateFlow<String?> = _productURL.asStateFlow()
    
    private var feedItem: IAMFeedItem? = null
    
    /**
     * Initialize the view model with a feed item
     */
    fun initialize(feedItem: IAMFeedItem) {
        this.feedItem = feedItem
    }
    
    /**
     * Handles primary CTA button click
     */
    fun handlePrimaryCTA() {
        viewModelScope.launch {
            val item = feedItem ?: return@launch
            
            try {
                // Update feed item with click action
                ggIAMService.updateFeedItem(item, FeedActionType.SHOP_NOW_CLICK)
                
                // Open product URL if available
                item.linkTarget?.let { url ->
                    _productURL.value = url
                    _showProductBrowser.value = true
                }
                
                Timber.d("[$tag] Handled primary CTA for feed: ${item.feedPostId}")
            } catch (e: Exception) {
                Timber.e(e, "[$tag] Failed to handle primary CTA for feed: ${item?.feedPostId}")
            }
        }
    }
    
    /**
     * Requests to close the modal
     */
    fun requestClose() {
        _shouldCloseModal.value = true
    }
    
    /**
     * Evaluates popup setting and closes modal if needed
     */
    fun evaluatePopupSetting() {
        val settings = ggIAMService.getStoredFeedNotificationSetting()
        if (settings?.showPopupMessage != true) {
            requestClose()
        }
    }
    
    /**
     * Dismisses the product browser
     */
    fun dismissProductBrowser() {
        _showProductBrowser.value = false
        _productURL.value = null
    }
}