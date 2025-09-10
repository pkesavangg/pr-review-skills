package com.greatergoods.ggInAppMessaging.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.greatergoods.ggInAppMessaging.domain.models.FeaturedProduct
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.features.common.ButtonType
import com.greatergoods.ggInAppMessaging.features.common.IAMText
import com.greatergoods.ggInAppMessaging.features.common.IamButton
import com.greatergoods.ggInAppMessaging.features.common.TextType
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
    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
  ) {
    itemsIndexed(products) { index, product ->
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
        .clip(RoundedCornerShape(8.dp))
        .border(
          width = 5.dp,
          color = Color.White, // White border for dark mode compatibility
          shape = RoundedCornerShape(8.dp),
        ),
    ) {
      AsyncImage(
        model = product.productImage,
        contentDescription = product.titleText,
        modifier = Modifier.fillMaxWidth(),
        contentScale = ContentScale.Crop,
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
        type = ButtonType.PrimaryFilled,
        onClick = { onIntent(FeedLandingIntent.OnFeaturedProductClick(productIndex)) },
      )
    }
  }
}

/**
 * Offer header container for when no featured products are available
 * Reuses the same content as the header offer container
 */
@Composable
private fun OfferHeaderContainer(
  feedItem: FeedItem,
  isFromFooter: Boolean = false,
  onPromoCodeClick: (String) -> Unit = {},
  onShopNowClick: (String?) -> Unit = {}
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    // Main offer title
    Text(
      text = feedItem.titleText,
      fontSize = 24.sp,
      fontWeight = FontWeight.Bold,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      color = Color(0xFF2C2827),
    )
    if (!isFromFooter) {
      // Promo code section (if available)
      feedItem.landingPage?.promoCode?.let { promoCode ->
        PromoCodeSection(
          feedItem = feedItem,
          onPromoCodeClick = onPromoCodeClick,
        )
      }
    }

    // Shop now button and expiration
    ShopNowSection(
      feedItem = feedItem,
      onShopNowClick = onShopNowClick,
    )
  }
}

/**
 * Promo code section with copy functionality
 */
@Composable
private fun PromoCodeSection(
  feedItem: FeedItem,
  onPromoCodeClick: (String) -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Promo code instruction text
    Text(
      text = FeedLandingScreenStrings.UsePromoCodeAtCheckout,
      fontSize = 16.sp,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      color = Color(0xFF424242),
    )

    // Promo code container
    feedItem.landingPage?.promoCode?.let { promoCode ->
      PromoCodeContainer(
        promoCode = promoCode,
        onCopyClick = { onPromoCodeClick(promoCode) },
      )
    }
  }
}

/**
 * Shop now button and expiration date section
 */
@Composable
private fun ShopNowSection(
  feedItem: FeedItem,
  onShopNowClick: (String?) -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Shop now button
    Button(
      onClick = { onShopNowClick(feedItem.linkTarget) },
      modifier = Modifier
        .width(200.dp)
        .height(40.dp),
    ) {
      Text(
        text = FeedLandingScreenStrings.ShopNow.uppercase(),
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
      )
    }

    // Expiration date
    Text(
      text = FeedLandingScreenStrings.OfferValidThrough,
      fontSize = 14.sp,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      color = Color(0xFF7B726E),
    )
  }
}

/**
 * Promo code container with copy button
 */
@Composable
private fun PromoCodeContainer(
  promoCode: String,
  onCopyClick: () -> Unit
) {
  androidx.compose.foundation.layout.Row(
    modifier = Modifier
      .width(300.dp)
      .height(47.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Promo code text area
    Box(
      modifier = Modifier
        .weight(1f)
        .height(47.dp)
        .padding(horizontal = 16.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = promoCode,
        fontSize = 20.sp,
        color = Color(0xFF2C2827),
      )
    }

    // Copy button
    Button(
      onClick = onCopyClick,
      modifier = Modifier
        .width(100.dp)
        .height(47.dp),
    ) {
      Text(
        text = FeedLandingScreenStrings.Copy.uppercase(),
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}
