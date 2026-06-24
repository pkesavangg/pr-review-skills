package com.greatergoods.ggInAppMessaging.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.greatergoods.ggInAppMessaging.domain.models.FeaturedProduct
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.features.common.ButtonType
import com.greatergoods.ggInAppMessaging.features.common.IAMText
import com.greatergoods.ggInAppMessaging.features.common.IamButton
import com.greatergoods.ggInAppMessaging.features.common.TextType
import com.greatergoods.ggInAppMessaging.features.resources.AppIcons
import com.greatergoods.ggInAppMessaging.ui.screens.strings.FeedLandingScreenStrings
import com.greatergoods.ggInAppMessaging.ui.viewmodel.FeedLandingIntent

/**
 * Reusable composable for featured products section (Fourth part)
 * Displays 1-5 products in a horizontal scrollable image container
 * If no featured products available, shows offer header container as fallback
 * Each product has unique link and can be variants or collection
 * Uses intents for click handling following MVI pattern
 */
@Composable
fun FeaturedProducts(
  feedItem: FeedItem,
  onIntent: (FeedLandingIntent) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val featuredProducts = feedItem.landingPage?.featuredProduct ?: emptyList()

  if (featuredProducts.isEmpty()) {
    // Show offer header container as fallback when no featured products
    OfferHeader(
      feedItem = feedItem,
      isFromFooter = true,
      onIntent = onIntent,
    )
    return
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    // Featured products title
    feedItem.landingPage?.featuredTitleText?.let { title ->
      IAMText(
        text = title,
        textType = TextType.Subtitle2,
      )
    }

    // Products horizontal scrollable container
    ProductsHorizontalScrollContainer(
      products = featuredProducts,
      onIntent = onIntent,
    )
  }
}

/**
 * Horizontal scrollable container for products
 * Always uses horizontal scroll regardless of product count
 */
@Composable
private fun ProductsHorizontalScrollContainer(
  products: List<FeaturedProduct>,
  onIntent: (FeedLandingIntent) -> Unit
) {
  LazyRow(
    horizontalArrangement = Arrangement.spacedBy(40.dp),
  ) {
    itemsIndexed(products, key = { index, _ -> index }) { index, product ->
      ProductCard(
        product = product,
        productIndex = index,
        onIntent = onIntent,
        modifier = Modifier.width(200.dp),
      )
    }
  }
}

/**
 * Individual product card
 * Matches Figma design with proper spacing and styling
 */
@Composable
private fun ProductCard(
  product: FeaturedProduct,
  productIndex: Int,
  onIntent: (FeedLandingIntent) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    // Product image with white border for dark mode
    Box(
      modifier = Modifier
        .size(200.dp)
        .clip(RoundedCornerShape(8.dp)),
    ) {
      AsyncImage(
        model = product.productImage,
        contentDescription = product.titleText,
        modifier = Modifier.fillMaxWidth(),
        contentScale = ContentScale.Crop,
        placeholder = painterResource(id = AppIcons.Iam.placeholderImage),
        error = painterResource(id = AppIcons.Iam.placeholderImage),
      )
    }

    // Product details
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Product title
      IAMText(
        text = product.titleText,
        textType = TextType.Subtitle2,
      )

      IamButton(
        label = FeedLandingScreenStrings.Shop.uppercase(),
        type = ButtonType.TertiaryFilled,
        onClick = { onIntent(FeedLandingIntent.OnFeaturedProductClick(productIndex)) },
      )
    }
  }
}

