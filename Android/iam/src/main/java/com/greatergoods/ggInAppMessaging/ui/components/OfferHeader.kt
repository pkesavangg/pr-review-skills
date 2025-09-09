package com.greatergoods.ggInAppMessaging.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.features.common.AnnotationPosition
import com.greatergoods.ggInAppMessaging.features.common.ButtonType
import com.greatergoods.ggInAppMessaging.features.common.IAMText
import com.greatergoods.ggInAppMessaging.features.common.IamButton
import com.greatergoods.ggInAppMessaging.features.common.TextType
import com.greatergoods.ggInAppMessaging.ui.screens.strings.FeedLandingScreenStrings

/**
 * Reusable composable for the offer header section
 * Displays the main offer title, promo code, and shop now button
 */
@Composable
fun OfferHeader(
  feedItem: FeedItem,
  isFromFooter: Boolean = false,
  onPromoCodeClick: (String) -> Unit = {},
  onShopNowClick: (String?) -> Unit = {},
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    // Main offer title
    IAMText(
      text = feedItem.titleText,
      textType = TextType.Title,
    )
    if (!isFromFooter) {
      // Promo code section
      PromoCodeSection(
        feedItem = feedItem,
        onPromoCodeClick = onPromoCodeClick,
      )
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
    IAMText(
      text = FeedLandingScreenStrings.UsePromoCodeAtCheckout,
      textType = TextType.SubHeading,
      annotatedText = FeedLandingScreenStrings.PromoCode,
      annotationPosition = AnnotationPosition.Middle,
      spanStyle = SpanStyle(fontWeight = FontWeight.Bold),
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
    IamButton(
      label = FeedLandingScreenStrings.ShopNow,
      type = ButtonType.PrimaryFilled,
      enabled = true,
      // onClick = { onShopNowClick(feedItem.landingPage?.url) },
    ) { }

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
private fun PromoCodeContainer(
  promoCode: String,
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
      // Promo code text area with light pink background
      Box(
        modifier = Modifier
          .weight(1f)
          .height(56.dp)
          .background(
            color = Color(0xFFF5F5F5), // Light pink/beige background
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
        )
      }

      // Copy button with filled background and rounded right corners
      Box(
        modifier = Modifier
          .width(80.dp)
          .height(56.dp)
          .background(
            color = Color(0xFF8B4513), // Reddish-brown background
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
        color = Color(0xFF8B4513),
        style = Stroke(
          width = strokeWidth,
          pathEffect = dottedPathEffect,
        ),
      )
    }
  }
}
