package com.greatergoods.ggInAppMessaging.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.ui.components.FeaturedImage
import com.greatergoods.ggInAppMessaging.ui.components.FeaturedProductVariations
import com.greatergoods.ggInAppMessaging.ui.components.FeaturedProducts
import com.greatergoods.ggInAppMessaging.ui.components.GreaterGoodsLogo
import com.greatergoods.ggInAppMessaging.ui.components.OfferHeader

/**
 * Feed Landing Screen composable
 * Displays the complete feed landing page with all five parts:
 * 1. Offer header part
 * 2. Featured image
 * 3. Featured product variations image carousel
 * 4. Featured products (1-5 products with unique links)
 * 5. Greater Goods logo
 */
@Composable
fun FeedLandingScreen(
  feedItem: FeedItem,
  onPromoCodeClick: (String) -> Unit = {},
  onShopNowClick: (String?) -> Unit = {},
  onProductClick: (String, Int?) -> Unit = { _, _ -> },
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(32.dp),
  ) {
    // Part 1: Offer header part
    OfferHeader(
      feedItem = feedItem,
      onPromoCodeClick = onPromoCodeClick,
      onShopNowClick = onShopNowClick,
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
      onProductClick = onProductClick,
      onPromoCodeClick = onPromoCodeClick,
      onShopNowClick = onShopNowClick,
    )
    // Part 5: Greater Goods logo
    GreaterGoodsLogo()
  }
}

