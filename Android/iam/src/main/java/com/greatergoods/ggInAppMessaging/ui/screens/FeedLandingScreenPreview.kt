package com.greatergoods.ggInAppMessaging.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greatergoods.ggInAppMessaging.domain.models.FeaturedProduct
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.domain.models.LandingPage

/**
 * Preview composable for FeedLandingScreen
 */
@Preview(showBackground = true)
@Composable
fun FeedLandingScreenPreview() {
  val sampleFeedItem = FeedItem(
    feedPostId = "sample-feed-post-id",
    elementId = "sample-element-id",
    accountId = "sample-account-id",
    isUnread = true,
    messageTypeText = "LIGHTENING DEAL",
    titleText = "Take 20% OFF Vacuum Sealers",
    subtitleModalText = "Be prepare for the holidays! Offer ends in {{expiresAt}}!",
    subtitleFeedText = "Ends in {{expiresAt}}!",
    titleImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
    linkTarget = "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
    linkText = "BUY NOW",
    trigger = "login",
    expiresAt = "2024-12-30T06:00:00.000Z",
    feedType = "landing",
    landingPage = LandingPage(
      feedLandingPageId = "sample-landing-page-id",
      feedPostId = "sample-feed-post-id",
      titleText = "Vacuum Sealers",
      promoCode = "5ZHTL9M8",
      featuredImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
      supportingTitleText = "One Machine, a Million Uses",
      supportingDescriptionText = "The Greater Goods All-in-One Vacuum Sealer has built-in bag storage and a slicer for hassle-free meal prep!",
      supportingImage = listOf(
        "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
        "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
        "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
      ),
      featuredTitleText = "Three Colors",
      themeColor = "gray",
      featuredProduct = listOf(
        FeaturedProduct(
          variationId = 1,
          titleText = "Stone Blue",
          feedLandingPageId = "sample-landing-page-id",
          linkText = "Shop",
          linkTarget = "https://shop.greatergoods.com/products/stone-blue-vacuum-sealer",
          productImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
        ),
        FeaturedProduct(
          variationId = 2,
          titleText = "Birch White",
          feedLandingPageId = "sample-landing-page-id",
          linkText = "Shop",
          linkTarget = "https://shop.greatergoods.com/products/birch-white-vacuum-sealer",
          productImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
        ),
      ),
    ),
  )

  FeedLandingScreen(
    feedItem = sampleFeedItem,
    onPromoCodeClick = { promoCode ->
      // Handle promo code copy
    },
    onShopNowClick = { link ->
      // Handle shop now click
    },
    onProductClick = { link, variationId ->
      // Handle product click
    },
  )
}

/**
 * Preview for FeedLandingScreen with no featured products (fallback scenario)
 */
@Preview(showBackground = true)
@Composable
fun FeedLandingScreenNoProductsPreview() {
  val sampleFeedItemNoProducts = FeedItem(
    feedPostId = "sample-feed-post-id",
    elementId = "sample-element-id",
    accountId = "sample-account-id",
    isUnread = true,
    messageTypeText = "LIGHTENING DEAL",
    titleText = "For a Limited Time: Get 50% OFF our Coffee Grinder",
    subtitleModalText = "Get a precise grind for the perfect cup with 50% OFF our Morning Groove Coffee Grinder!",
    subtitleFeedText = "Ends in {{expiresAt}}!",
    titleImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
    linkTarget = "https://shop.greatergoods.com/collections/coffee-grinders",
    linkText = "BUY NOW",
    trigger = "login",
    expiresAt = "2024-12-30T06:00:00.000Z",
    feedType = "landing",
    landingPage = LandingPage(
      feedLandingPageId = "sample-landing-page-id",
      feedPostId = "sample-feed-post-id",
      titleText = "Coffee Grinder",
      promoCode = "5ZHTL9M8",
      featuredImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
      supportingTitleText = "Stay on Your Grind",
      supportingDescriptionText = "Get a precise grind for the perfect cup with 50% OFF our Morning Groove Coffee Grinder!\n\nSee all buying options on Amazon and use code Y9FSYRE9 at checkout! But hurry, this deal ends in:",
      supportingImage = listOf(
        "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
      ),
      featuredTitleText = null, // No featured products
      themeColor = "blue",
      featuredProduct = emptyList(), // No featured products - will show offer header container
    ),
  )

  FeedLandingScreen(
    feedItem = sampleFeedItemNoProducts,
    onPromoCodeClick = { promoCode ->
      // Handle promo code copy
    },
    onShopNowClick = { link ->
      // Handle shop now click
    },
    onProductClick = { link, variationId ->
      // Handle product click
    },
  )
}
