package com.dmdbrands.gurus.weight.features.feed.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.feed.model.FeedLandingIntent
import com.dmdbrands.gurus.weight.features.feed.model.FeedLandingReducer
import com.dmdbrands.gurus.weight.features.feed.model.FeedLandingState
import com.dmdbrands.gurus.weight.features.feed.shared.SelectedFeedItemHolder
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Feed Landing Screen
 * Follows MVI pattern with reducer for state management
 */
@HiltViewModel
class FeedLandingViewModel @Inject constructor(
  private val feedService: IFeedService,
  private val appNavigationService: IAppNavigationService,
  private val selectedFeedItemHolder: SelectedFeedItemHolder
) : BaseIntentViewModel<FeedLandingState, FeedLandingIntent>(
  reducer = FeedLandingReducer(),
) {

  override fun provideInitialState(): FeedLandingState = FeedLandingState()

  init {
    // Observe selected feed item changes
    selectedFeedItemHolder.selectedFeedItem
      .onEach { feedItem ->
        if (feedItem != null) {
          setFeedItem(feedItem)
          AppLog.d("FeedLandingViewModel", "Received selected feed item: ${feedItem.titleText}")
        }
      }
      .launchIn(viewModelScope)
  }

  private var onNavigateToFeedLanding: ((FeedItem) -> Unit)? = null
  private var onNavigateToFAQ: (() -> Unit)? = null

  /**
   * Handles incoming intents and updates the state accordingly.
   * @param intent The intent to handle.
   */
  override fun handleIntent(intent: FeedLandingIntent) {
    super.handleIntent(intent)
    when (intent) {
      is FeedLandingIntent.OpenFAQ -> navigateToFAQ()
      else -> null
    }
  }

  /**
   * Sets the navigation callback for feed landing
   */
  fun setNavigationCallback(onNavigateToFeedLanding: (FeedItem) -> Unit) {
    this.onNavigateToFeedLanding = onNavigateToFeedLanding
  }

  /**
   * Sets the navigation callback for FAQ
   */
  fun setFAQNavigationCallback(onNavigateToFAQ: () -> Unit) {
    this.onNavigateToFAQ = onNavigateToFAQ
  }

  /**
   * Sets the feed item for display
   */
  fun setFeedItem(feedItem: FeedItem) {
    handleIntent(FeedLandingIntent.SetFeedItem(feedItem))
    AppLog.d("FeedLandingViewModel", "Feed item set: ${feedItem.titleText}")
  }

  fun navigateToFAQ() {
    viewModelScope.launch {
      appNavigationService.navigateTo(AppRoute.FeedFAQ)
    }
  }

  /**
   * Handles promo code copy action
   */
  fun onPromoCodeClick(promoCode: String) {
    viewModelScope.launch {
      try {
        handleIntent(FeedLandingIntent.OnPromoCodeClick(promoCode))

        // Copy promo code to clipboard
        // TODO: Implement clipboard functionality
        AppLog.d("FeedLandingViewModel", "Promo code copied: $promoCode")

        // Mark feed as read when promo code is copied
        // currentState.feedItem?.let { feedItem ->
        //     feedService.markFeedAsRead(feedItem.elementId, "promo_code_copied")
        // }
      } catch (e: Exception) {
        AppLog.e("FeedLandingViewModel", "Failed to copy promo code", e.toString())
        // handleIntent(FeedLandingIntent.SetError("Failed to copy promo code"))
      }
    }
  }

  /**
   * Handles shop now button click
   */
  fun onShopNowClick(link: String?) {
    viewModelScope.launch {
      try {
        handleIntent(FeedLandingIntent.OnShopNowClick(link))

        // Get the current state from the reducer
        val currentState = state.value
        currentState.feedItem?.let { feedItem ->
          AppLog.d("FeedLandingViewModel", "Shop now clicked for: ${feedItem.titleText}")

          // Use FeedService to handle shop now click with proper feed type checking
          feedService.handleShopNowClick(
            feedItem = feedItem,
            onNavigateToFeedLanding = { clickedFeedItem ->
              AppLog.d("FeedLandingViewModel", "Navigating to landing screen for: ${clickedFeedItem.titleText}")
              onNavigateToFeedLanding?.invoke(clickedFeedItem)
            },
            onOpenExternalLink = { externalLink ->
              AppLog.d("FeedLandingViewModel", "Opening external link: $externalLink")
              // TODO: Open external link
            },
          )
        }
      } catch (e: Exception) {
        AppLog.e("FeedLandingViewModel", "Failed to handle shop now click", e.toString())
        handleIntent(FeedLandingIntent.SetError("Failed to handle shop now click"))
      }
    }
  }

  /**
   * Handles product click action
   */
  fun onProductClick(link: String, variationId: Int?) {
    viewModelScope.launch {
      try {
        handleIntent(FeedLandingIntent.OnProductClick(link, variationId))

        AppLog.d("FeedLandingViewModel", "Product clicked: $link, variationId: $variationId")

        // Mark feed as read when product is clicked
        // currentState.feedItem?.let { feedItem ->
        //     feedService.markFeedAsRead(feedItem.elementId, "product_clicked")
        // }

        // TODO: Navigate to product page
        // This should be handled by the parent composable via callback
      } catch (e: Exception) {
        AppLog.e("FeedLandingViewModel", "Failed to handle product click", e.toString())
        handleIntent(FeedLandingIntent.SetError("Failed to handle product click"))
      }
    }
  }
}
