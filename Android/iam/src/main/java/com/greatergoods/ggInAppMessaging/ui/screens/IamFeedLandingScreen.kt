package com.greatergoods.ggInAppMessaging.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.ggInAppMessaging.core.utilities.IAMLogger
import com.greatergoods.ggInAppMessaging.domain.models.FeaturedProduct
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.domain.models.FeedTypes
import com.greatergoods.ggInAppMessaging.domain.models.LandingPage
import com.greatergoods.ggInAppMessaging.theme.ProvideIamTheme
import com.greatergoods.ggInAppMessaging.ui.components.FeaturedImage
import com.greatergoods.ggInAppMessaging.ui.components.FeaturedProductVariations
import com.greatergoods.ggInAppMessaging.ui.components.FeaturedProducts
import com.greatergoods.ggInAppMessaging.ui.components.GreaterGoodsLogo
import com.greatergoods.ggInAppMessaging.ui.components.OfferHeader
import com.greatergoods.ggInAppMessaging.ui.viewmodel.FeedLandingIntent
import com.greatergoods.ggInAppMessaging.ui.viewmodel.FeedLandingViewModel

/**
 * Feed Landing Screen composable
 * Displays the complete feed landing page with all five parts:
 * 1. Offer header part
 * 2. Featured image
 * 3. Featured product variations image carousel
 * 4. Featured products (1-5 products with unique links)
 * 5. Greater Goods logo
 *
 * Uses ViewModel for handling all click actions with data from FeedItem
 * No callback parameters needed - all data comes from FeedItem
 */
@Composable
fun IamFeedLandingScreen(
  feedItem: FeedItem,
) {
  val viewModel: FeedLandingViewModel = hiltViewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()

  // Set the feed item when the screen is first composed
  LaunchedEffect(feedItem) {
    viewModel.setFeedItem(feedItem)
  }

  // Intent dispatcher function
  val dispatchIntent: (FeedLandingIntent) -> Unit = { intent ->
    IAMLogger.d("IamFeedLandingScreen", "Received intent: $intent")
    viewModel.handleIntent(intent)
  }

  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    // Part 1: Offer header part
    OfferHeader(
      feedItem = feedItem,
      onIntent = dispatchIntent,
    )
    // Part 2: Featured image (same as feed item image)
    FeaturedImage(feedItem = feedItem)
    // Part 3: Featured product variations slideshow
    // If single image, shows full container image
    // If multiple images, shows horizontal slideshow with pagination dots
    FeaturedProductVariations(
      feedItem = feedItem,
    )

    // Part 4: Featured products horizontal scrollable container
    // If no featured products available, shows offer header container as fallback
    // Each product has unique link and can be variants or collection
    // For dark mode, small white border (5px) added to images
    FeaturedProducts(
      feedItem = feedItem,
      onIntent = dispatchIntent,
    )
    // Part 5: Greater Goods logo
    GreaterGoodsLogo(
      onIntent = dispatchIntent,
    )
  }
}

// ===== PREVIEW COMPOSABLE =====

@Preview(showBackground = true)
@Composable
fun IamFeedLandingScreenPreview() {
  ProvideIamTheme {
    IamFeedLandingScreen(
      feedItem = createMockFeedItemForLanding()
    )
  }
}

// ===== MOCK DATA =====

/**
 * Creates comprehensive mock FeedItem data for landing screen preview
 */
private fun createMockFeedItemForLanding(): FeedItem {
  return FeedItem(
    elementId = "landingPreview001",
    titleText = "Take 20% OFF Vacuum Sealers",
    messageTypeText = "LIGHTNING DEAL",
    subtitleFeedText = "Supporting text that can be customized up to 60 characters.",
    subtitleModalText = "The Greater Goods {{bold[All-in-One Vacuum Sealer]}} has built-in bag storage and a slicer for hassle-free meal prep!",
    linkTarget = "https://shop.greatergoods.com/collections/vacuum-sealers",
    feedType = FeedTypes.LINK,
    linkText = "Shop Now",
    feedPostId = "landingPost001",
    accountId = "landingAccount",
    titleImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
    landingPage = LandingPage(
      feedLandingPageId = "landingPage001",
      feedPostId = "landingPost001",
      titleText = "Vacuum Sealers",
      promoCode = "SAVE20NOW",
      featuredImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
      supportingTitleText = "One Machine, a Million Uses",
      supportingDescriptionText = "The Greater Goods {{bold[All-in-One Vacuum Sealer]}} has built-in bag storage and a slicer for hassle-free meal prep!",
      supportingImage = listOf(
        "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
        "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
        "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg"
      ),
      featuredTitleText = "Three Colors Available",
      themeColor = "blue",
      featuredProduct = listOf(
        FeaturedProduct(
          variationId = 10001,
          titleText = "Stone Blue",
          feedLandingPageId = "landingPage001",
          linkText = "Shop Blue",
          linkTarget = "https://shop.greatergoods.com/collections/vacuum-sealers/products/stone-blue",
          productImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg"
        ),
        FeaturedProduct(
          variationId = 10002,
          titleText = "Stainless Steel",
          feedLandingPageId = "landingPage001",
          linkText = "Shop Steel",
          linkTarget = "https://shop.greatergoods.com/collections/vacuum-sealers/products/stainless-steel",
          productImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg"
        ),
        FeaturedProduct(
          variationId = 10003,
          titleText = "Black",
          feedLandingPageId = "landingPage001",
          linkText = "Shop Black",
          linkTarget = "https://shop.greatergoods.com/collections/vacuum-sealers/products/black",
          productImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg"
        )
      )
    )
  )
}

