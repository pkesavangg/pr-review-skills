package com.greatergoods.ggInAppMessaging.ui.viewmodel

import com.greatergoods.ggInAppMessaging.domain.models.FeedItem

/**
 * Intent actions for FeedLandingViewModel
 * Following MVI pattern
 */
sealed class FeedLandingIntent {
    /** Set the feed item for the landing screen */
    data class SetFeedItem(val feedItem: FeedItem) : FeedLandingIntent()

    /** Handle offer header shop now button click */
    object OnOfferHeaderShopNowClick : FeedLandingIntent()

    /** Handle promo code copy click */
    object OnPromoCodeCopyClick : FeedLandingIntent()

    /** Handle featured product click */
    data class OnFeaturedProductClick(val productIndex: Int) : FeedLandingIntent()

    /** Handle Greater Goods logo click */
    object OnGreaterGoodsLogoClick : FeedLandingIntent()

    /** Clear error state */
    object ClearError : FeedLandingIntent()
}
