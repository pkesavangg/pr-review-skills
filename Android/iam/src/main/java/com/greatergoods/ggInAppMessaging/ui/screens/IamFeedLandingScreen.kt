package com.greatergoods.ggInAppMessaging.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.ggInAppMessaging.core.utilities.IAMLogger
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
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
  val state by viewModel.state.collectAsState()

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
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(32.dp),
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

