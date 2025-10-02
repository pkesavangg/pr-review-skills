package com.greatergoods.ggInAppMessaging.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.domain.models.FeedTypes
import com.greatergoods.ggInAppMessaging.domain.models.LandingPage
import com.greatergoods.ggInAppMessaging.features.common.AnnotationPosition
import com.greatergoods.ggInAppMessaging.features.common.ButtonType
import com.greatergoods.ggInAppMessaging.features.common.IAMText
import com.greatergoods.ggInAppMessaging.features.common.IamButton
import com.greatergoods.ggInAppMessaging.features.common.TextType
import com.greatergoods.ggInAppMessaging.theme.ProvideIamTheme
import com.greatergoods.ggInAppMessaging.theme.ThemeColorParser
import com.greatergoods.ggInAppMessaging.ui.screens.strings.FeedLandingScreenStrings
import com.greatergoods.ggInAppMessaging.ui.viewmodel.FeedLandingIntent

/**
 * Reusable composable for the offer header section
 * Displays the main offer title, promo code, and shop now button
 * Uses intents for click handling following MVI pattern
 */
@Composable
fun OfferHeader(
  feedItem: FeedItem,
  isFromFooter: Boolean = false,
  onIntent: (FeedLandingIntent) -> Unit = {},
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    // verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    // Main offer title
    IAMText(
      text = feedItem.titleText,
      textType = TextType.Title,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(24.dp))

    if (!isFromFooter) {
      // Promo code section
      PromoCodeSection(
        feedItem = feedItem,
        onIntent = onIntent,
      )
    }
    if(!isFromFooter){
    Spacer(modifier = Modifier.height(32.dp))
    }
    else {
      Spacer(modifier = Modifier.height( 24.dp))
    }
    // Shop now button and expiration
    ShopNowSection(
      feedItem = feedItem,
      onIntent = onIntent,
    )
  }
}

/**
 * Promo code section with copy functionality
 */
@Composable
private fun PromoCodeSection(
  feedItem: FeedItem,
  onIntent: (FeedLandingIntent) -> Unit
) {
  val marketingColors = ThemeColorParser.parseMarketingColors(feedItem.landingPage?.themeColor)

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Promo code instruction text
    IAMText(
      text = FeedLandingScreenStrings.UsePromoCodeAtCheckout,
      textType = TextType.SubHeading,
      annotatedText = FeedLandingScreenStrings.PromoCode,
      annotationPosition = AnnotationPosition.Middle,
      spanStyle = SpanStyle(fontWeight = FontWeight.Bold),
      // color = marketingColors.primary
    )

    // Promo code container
    feedItem.landingPage?.promoCode?.let { promoCode ->
      PromoCodeContainer(
        promoCode = promoCode,
        marketingColors = marketingColors,
        onCopyClick = { onIntent(FeedLandingIntent.OnPromoCodeCopyClick) },
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
  onIntent: (FeedLandingIntent) -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.padding(top = 8.dp)
  ) {
    // Shop now button
    IamButton(
      label = FeedLandingScreenStrings.ShopNow,
      type = ButtonType.TertiaryFilled,
      enabled = true,
      onClick = { onIntent(FeedLandingIntent.OnOfferHeaderShopNowClick) },
    )

    // Expiration date
    IAMText(
      text = FeedLandingScreenStrings.OfferValidThrough,
      textType = TextType.SubHeading,
    )
  }
}

/**
 * Promo code container with copy button
 * Designed to match the screenshot with dotted borders and filled background colors
 */
@Composable
 fun PromoCodeContainer(
  promoCode: String,
  marketingColors: ThemeColorParser.MarketingColors,
  onCopyClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .width(320.dp)
      .height(56.dp),
  ) {
    Row(
      modifier = Modifier
        .width(320.dp)
        .height(56.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Promo code text area with dynamic marketing primary background
      Box(
        modifier = Modifier
          .weight(1f)
          .height(56.dp)
          .background(
            color = marketingColors.primary, // Dynamic marketing primary background
            shape = RoundedCornerShape(
              topStart = 8.dp,
              bottomStart = 8.dp,
              topEnd = 0.dp,
              bottomEnd = 0.dp,
            ),
          )
          .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart, // Left-aligned text
      ) {
        IAMText(
          text = promoCode,
          textType = TextType.Body,
          // color = marketingColors.primaryAction, // Dynamic marketing primary action color for text
        )
      }

      // Copy button with dynamic marketing primary action background
      Box(
        modifier = Modifier
          .width(80.dp)
          .height(56.dp)
          .background(
            color = marketingColors.primaryAction, // Dynamic marketing primary action background
            shape = RoundedCornerShape(
              topStart = 0.dp,
              bottomStart = 0.dp,
              topEnd = 8.dp,
              bottomEnd = 8.dp,
            ),
          )
          .clickable { onCopyClick() }
          .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
      ) {
        IAMText(
          text = "COPY",
          textType = TextType.Body,
          canApplyUppercaseStyle = true,
          color = Color.White, // White text on colored background
        )
      }
    }

    // Dotted border overlay for the entire container
    Canvas(
      modifier = Modifier
        .width(320.dp)
        .height(56.dp),
    ) {
      val strokeWidth = 2.dp.toPx()
      val dotLength = 4.dp.toPx()
      val dotSpacing = 2.dp.toPx()
      val cornerRadius = 8.dp.toPx()

      // Draw dotted border around the entire container
      val dottedPathEffect = PathEffect.dashPathEffect(
        floatArrayOf(dotLength, dotSpacing),
        0f,
      )

      val dottedPath = Path().apply {
        // Start from top-left with rounded corner
        moveTo(cornerRadius, 0f)
        lineTo(size.width - cornerRadius, 0f) // Top edge
        // Top-right rounded corner
        arcTo(
          rect = androidx.compose.ui.geometry.Rect(
            left = size.width - cornerRadius * 2,
            top = 0f,
            right = size.width,
            bottom = cornerRadius * 2,
          ),
          startAngleDegrees = 270f,
          sweepAngleDegrees = 90f,
          forceMoveTo = false,
        )
        lineTo(size.width, size.height - cornerRadius) // Right edge
        // Bottom-right rounded corner
        arcTo(
          rect = androidx.compose.ui.geometry.Rect(
            left = size.width - cornerRadius * 2,
            top = size.height - cornerRadius * 2,
            right = size.width,
            bottom = size.height,
          ),
          startAngleDegrees = 0f,
          sweepAngleDegrees = 90f,
          forceMoveTo = false,
        )
        lineTo(cornerRadius, size.height) // Bottom edge
        // Bottom-left rounded corner
        arcTo(
          rect = androidx.compose.ui.geometry.Rect(
            left = 0f,
            top = size.height - cornerRadius * 2,
            right = cornerRadius * 2,
            bottom = size.height,
          ),
          startAngleDegrees = 90f,
          sweepAngleDegrees = 90f,
          forceMoveTo = false,
        )
        lineTo(0f, cornerRadius) // Left edge
        // Top-left rounded corner
        arcTo(
          rect = androidx.compose.ui.geometry.Rect(
            left = 0f,
            top = 0f,
            right = cornerRadius * 2,
            bottom = cornerRadius * 2,
          ),
          startAngleDegrees = 180f,
          sweepAngleDegrees = 90f,
          forceMoveTo = false,
        )
        close()
      }

      drawPath(
        path = dottedPath,
        color = marketingColors.primaryAction, // Dynamic marketing primary action color for border
        style = Stroke(
          width = strokeWidth,
          pathEffect = dottedPathEffect,
        ),
      )
    }
  }
}

// ===== PREVIEW COMPOSABLES =====

@Preview(showBackground = true)
@Composable
fun OfferHeaderPreview() {
  ProvideIamTheme {
    OfferHeader(
      feedItem = createMockFeedItem(),
      onIntent = {}
    )
  }
}

@Preview(showBackground = true)
@Composable
fun OfferHeaderFromFooterPreview() {
  ProvideIamTheme {
    OfferHeader(
      feedItem = createMockFeedItem(),
      isFromFooter = true,
      onIntent = {}
    )
  }
}

@Preview(showBackground = true)
@Composable
fun PromoCodeSectionPreview() {
  ProvideIamTheme {
    PromoCodeSection(
      feedItem = createMockFeedItem(),
      onIntent = {}
    )
  }
}

@Preview(showBackground = true)
@Composable
fun ShopNowSectionPreview() {
  ProvideIamTheme {
    ShopNowSection(
      feedItem = createMockFeedItem(),
      onIntent = {}
    )
  }
}

@Preview(showBackground = true)
@Composable
fun PromoCodeContainerPreview() {
  ProvideIamTheme {
    PromoCodeContainer(
      promoCode = "SAVE20NOW",
      marketingColors = ThemeColorParser.parseMarketingColors("red" ),
      onCopyClick = {}
    )
  }
}

// ===== MOCK DATA =====

/**
 * Creates mock FeedItem data for previews
 */
private fun createMockFeedItem(): FeedItem {
  return FeedItem(
    elementId = "preview001",
    titleText = "Take 20% OFF Vacuum Sealers",
    messageTypeText = "LIGHTNING DEAL",
    subtitleFeedText = "Supporting text that can be customized up to 60 characters.",
    subtitleModalText = "The Greater Goods {{bold[All-in-One Vacuum Sealer]}} has built-in bag storage and a slicer for hassle-free meal prep!",
    linkTarget = "https://shop.greatergoods.com/collections/vacuum-sealers",
    feedType = FeedTypes.LINK,
    linkText = "Shop Now",
    feedPostId = "previewPost001",
    accountId = "previewAccount",
    titleImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
    landingPage = LandingPage(
      feedLandingPageId = "previewLanding001",
      feedPostId = "previewPost001",
      titleText = "Vacuum Sealers",
      promoCode = "SAVE20NOW",
      featuredImage = null,
      supportingTitleText = "One Machine, a Million Uses",
      supportingDescriptionText = "The Greater Goods {{bold[All-in-One Vacuum Sealer]}} has built-in bag storage and a slicer for hassle-free meal prep!",
      supportingImage = listOf(
        "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg"
      ),
      featuredTitleText = "Three Colors Available",
      themeColor = "blue",
      featuredProduct = emptyList()
    )
  )
}
