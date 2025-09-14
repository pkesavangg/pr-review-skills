package com.greatergoods.ggInAppMessaging.ui.viewmodel

import com.greatergoods.ggInAppMessaging.domain.models.FeedItem

/**
 * Reducer for FeedLandingViewModel
 * Following MVI pattern
 */
object FeedLandingReducer {

    /**
     * Reduce state based on intent
     */
    fun reduce(state: FeedLandingState, intent: FeedLandingIntent): FeedLandingState {
        return when (intent) {
            is FeedLandingIntent.SetFeedItem -> {
                state.copy(
                    feedItem = intent.feedItem,
                    error = null
                )
            }

            is FeedLandingIntent.OnOfferHeaderShopNowClick -> {
                state.copy(
                    lastAction = "Offer header shop now clicked",
                    error = null
                )
            }

            is FeedLandingIntent.OnPromoCodeCopyClick -> {
                state.copy(
                    lastAction = "Promo code copied",
                    promoCodeCopied = true,
                    error = null
                )
            }

            is FeedLandingIntent.OnFeaturedProductClick -> {
                state.copy(
                    lastAction = "Featured product clicked: ${intent.productIndex}",
                    error = null
                )
            }

            is FeedLandingIntent.OnGreaterGoodsLogoClick -> {
                state.copy(
                    lastAction = "Greater Goods logo clicked",
                    error = null
                )
            }

            is FeedLandingIntent.ClearError -> {
                state.copy(error = null)
            }
        }
    }

    /**
     * Reduce state for error
     */
    fun onError(error: String): (FeedLandingState) -> FeedLandingState = { state ->
        state.copy(error = error)
    }
}
