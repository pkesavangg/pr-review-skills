package com.greatergoods.ggInAppMessaging.ui.viewmodel

import com.greatergoods.ggInAppMessaging.core.utilities.IAMLogger
import com.greatergoods.ggInAppMessaging.core.viewmodel.BaseIntentViewModel
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.domain.models.FeedTypes
import com.greatergoods.ggInAppMessaging.domain.services.IInAppMessagingService
import com.greatergoods.ggInAppMessaging.domain.services.ILinkService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * ViewModel for FeedLandingScreen
 * Handles all click actions using intents and data from FeedItem
 * Following MVI pattern
 */
@HiltViewModel
class FeedLandingViewModel @Inject constructor(
    private val linkService: ILinkService,
    private val inAppMessagingService: IInAppMessagingService,
    @ApplicationContext private val context: Context
) : BaseIntentViewModel<FeedLandingState, FeedLandingIntent>() {

    private val tag = "FeedLandingViewModel"
    private val greaterGoodsUrl = "https://shop.greatergoods.com/"
    private var pageViewTrackedElementId: String? = null

    override fun provideInitialState(): FeedLandingState = FeedLandingState()

    /**
     * Handle intents following MVI pattern
     */
    override fun handleIntent(intent: FeedLandingIntent) {
        val newState = FeedLandingReducer.reduce(currentState, intent)
        updateState(newState)

        when (intent) {
            is FeedLandingIntent.SetFeedItem -> {
                // Feed item is already set in state by reducer
                IAMLogger.d(tag, "Feed item set: ${intent.feedItem.titleText}")
            }
            is FeedLandingIntent.OnOfferHeaderShopNowClick -> {
                handleOfferHeaderShopNowClick()
            }
            is FeedLandingIntent.OnPromoCodeCopyClick -> {
                handlePromoCodeCopyClick()
            }
            is FeedLandingIntent.OnFeaturedProductClick -> {
                handleFeaturedProductClick(intent.productIndex)
            }
            is FeedLandingIntent.OnGreaterGoodsLogoClick -> {
                handleGreaterGoodsLogoClick()
            }
            is FeedLandingIntent.ClearError -> {
                // State already updated by reducer
            }
        }
    }

    /**
     * Set the feed item for the landing screen
     */
    fun setFeedItem(feedItem: FeedItem) {
        handleIntent(FeedLandingIntent.SetFeedItem(feedItem))
        if (feedItem.feedType == FeedTypes.LANDING && pageViewTrackedElementId != feedItem.elementId) {
            pageViewTrackedElementId = feedItem.elementId
            launch {
                inAppMessagingService.emitFeedUpdate(feedItem, "pageView")
            }
        }
    }

    private fun handleOfferHeaderShopNowClick() {
        launch {
            try {
                val feedItem = currentState.feedItem
                if (feedItem != null) {
                    val linkTarget = feedItem.linkTarget
                    IAMLogger.d(tag, "Offer header shop now clicked: $linkTarget")
                    inAppMessagingService.emitFeedUpdate(feedItem, "shopNowClick")

                    if (!linkTarget.isNullOrEmpty()) {
                        linkService.openInCustomTab(
                            url = linkTarget,
                            showTitle = true
                        )
                    } else {
                        IAMLogger.w(tag, "No link target found in feed item")
                        updateState(FeedLandingReducer.onError("No shop now link available")(currentState))
                    }
                } else {
                    IAMLogger.w(tag, "No feed item available")
                    updateState(FeedLandingReducer.onError("No feed item available")(currentState))
                }
            } catch (e: Exception) {
                IAMLogger.e(tag, "Failed to handle offer header shop now click", e.toString())
                updateState(FeedLandingReducer.onError("Failed to open shop now link")(currentState))
            }
        }
    }


    private fun handlePromoCodeCopyClick() {
        launch {
            try {
                val feedItem = currentState.feedItem
                if (feedItem != null) {
                    val promoCode = feedItem.landingPage?.promoCode ?: feedItem.promoCode
                    IAMLogger.d(tag, "Promo code copy clicked: $promoCode")

                    if (!promoCode.isNullOrEmpty()) {
                        inAppMessagingService.emitFeedUpdate(feedItem, "promoClick")
                        // Copy to clipboard
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Promo Code", promoCode)
                        clipboard.setPrimaryClip(clip)
                        IAMLogger.d(tag, "Promo code copied to clipboard: $promoCode")

                        // Emit event to notify main app to show toast
                        inAppMessagingService.emitPromoCodeCopied(promoCode)
                    } else {
                        IAMLogger.w(tag, "No promo code found in feed item")
                        updateState(FeedLandingReducer.onError("No promo code available")(currentState))
                    }
                } else {
                    IAMLogger.w(tag, "No feed item available")
                    updateState(FeedLandingReducer.onError("No feed item available")(currentState))
                }
            } catch (e: Exception) {
                IAMLogger.e(tag, "Failed to copy promo code", e.toString())
                updateState(FeedLandingReducer.onError("Failed to copy promo code")(currentState))
            }
        }
    }

    /**
     * Handle featured product click
     */
    fun onFeaturedProductClick(productIndex: Int) {
        handleIntent(FeedLandingIntent.OnFeaturedProductClick(productIndex))
    }

    private fun handleFeaturedProductClick(productIndex: Int) {
        launch {
            try {
                val feedItem = currentState.feedItem
                if (feedItem != null) {
                    val featuredProducts = feedItem.landingPage?.featuredProduct
                    if (!featuredProducts.isNullOrEmpty() && productIndex < featuredProducts.size) {
                        val product = featuredProducts[productIndex]
                        val productLink = product.linkTarget

                        IAMLogger.d(tag, "Featured product clicked: $productIndex, link: $productLink")
                        inAppMessagingService.emitFeedUpdate(feedItem, "variationClick", product.variationId)

                        if (!productLink.isNullOrEmpty()) {
                            linkService.openInCustomTab(
                                url = productLink,
                                showTitle = true
                            )
                        } else {
                            IAMLogger.w(tag, "No product link found for product at index: $productIndex")
                            updateState(FeedLandingReducer.onError("No product link available")(currentState))
                        }
                    } else {
                        IAMLogger.w(tag, "No featured products available or invalid index: $productIndex")
                        updateState(FeedLandingReducer.onError("No featured products available")(currentState))
                    }
                } else {
                    IAMLogger.w(tag, "No feed item available")
                    updateState(FeedLandingReducer.onError("No feed item available")(currentState))
                }
            } catch (e: Exception) {
                IAMLogger.e(tag, "Failed to handle featured product click", e.toString())
                updateState(FeedLandingReducer.onError("Failed to open product link")(currentState))
            }
        }
    }

    /**
     * Handle Greater Goods logo click
     */
    fun onGreaterGoodsLogoClick() {
        handleIntent(FeedLandingIntent.OnGreaterGoodsLogoClick)
    }

    private fun handleGreaterGoodsLogoClick() {
        launch {
            try {
                IAMLogger.d(tag, "Greater Goods logo clicked: $greaterGoodsUrl")
                IAMLogger.d(tag, "About to call linkService.openInCustomTab")

                linkService.openInCustomTab(
                    url = greaterGoodsUrl,
                    showTitle = true
                )

                IAMLogger.d(tag, "Successfully called linkService.openInCustomTab")
            } catch (e: Exception) {
                IAMLogger.e(tag, "Failed to handle Greater Goods logo click", e.toString())
                updateState(FeedLandingReducer.onError("Failed to open Greater Goods website: ${e.message}")(currentState))
            }
        }
    }

    /**
     * Clear error state
     */
    fun clearError() {
        handleIntent(FeedLandingIntent.ClearError)
    }
}

/**
 * State for FeedLandingScreen
 */
data class FeedLandingState(
    val feedItem: FeedItem? = null,
    val lastAction: String? = null,
    val promoCodeCopied: Boolean = false,
    val error: String? = null
)
