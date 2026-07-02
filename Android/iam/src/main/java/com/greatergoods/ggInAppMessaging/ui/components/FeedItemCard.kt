package com.greatergoods.ggInAppMessaging.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.greatergoods.ggInAppMessaging.domain.models.FeaturedProduct
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.domain.models.LandingPage
import com.greatergoods.ggInAppMessaging.features.common.ButtonSize
import com.greatergoods.ggInAppMessaging.features.common.ButtonType
import com.greatergoods.ggInAppMessaging.features.common.IAMText
import com.greatergoods.ggInAppMessaging.features.common.IamButton
import com.greatergoods.ggInAppMessaging.features.common.TextType
import com.greatergoods.ggInAppMessaging.features.resources.AppIcons
import com.greatergoods.ggInAppMessaging.theme.IamTheme
import com.greatergoods.ggInAppMessaging.theme.ProvideIamTheme

/**
 * Feed item card component matching the Figma design
 * Displays a product image on the left and content on the right
 */
@Composable
fun FeedItemCard(
  feedItem: FeedItem,
  onItemClick: (FeedItem) -> Unit,
  modifier: Modifier = Modifier,
  showTopDivider: Boolean = true,
  showBottomDivider: Boolean = true
) {
  Column(
    modifier = modifier.fillMaxWidth(),
  ) {
    // Top divider
      HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 0.5.dp,
        color = IamTheme.colors.utility,
      )

    // Main content container
    FeedItemCardContent(
      feedItem = feedItem,
      onItemClick = onItemClick,
    )

    // Bottom divider
    if (showBottomDivider) {
      HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 0.5.dp,
        color = IamTheme.colors.utility,
      )
    }
  }
}

/**
 * Main content row: product image on the left and text/CTA on the right
 */
@Composable
private fun FeedItemCardContent(
  feedItem: FeedItem,
  onItemClick: (FeedItem) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Image container (160dp width, 230dp height)
    Box(
      modifier = Modifier
        .size(width = 160.dp, height = 230.dp)
        .clip(RoundedCornerShape(0.dp)),
    ) {
      AsyncImage(
        model = feedItem.titleImage,
        contentDescription = feedItem.titleText,
        modifier = Modifier
          .fillMaxWidth()
          .fillMaxHeight(),
        contentScale = ContentScale.Crop,
        placeholder = painterResource(id = AppIcons.Iam.placeholderImage),
        error = painterResource(id = AppIcons.Iam.placeholderImage),
      )
    }

    // Text container
    Column(
      modifier = Modifier
        .fillMaxWidth().padding(start = 24.dp, end = 16.dp),
      verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
      // Message container
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // Message type text (LIGHTENING DEAL)
        IAMText(
          text = feedItem.messageTypeText.lowercase(),
          textType = TextType.SubHeading,
          modifier = Modifier.fillMaxWidth(),
        )

        // Headline text
        IAMText(
          text = feedItem.titleText,
          textType = TextType.ListTitle1,
        )

        // Subtitle text (Ends in X hours) with rich text formatting
        IAMText(
          text = feedItem.subtitleFeedText,
          textType = TextType.Body,
          color = IamTheme.colors.textBody,
          enableRichText = true,
          expiresAt = feedItem.expiresAt,
        )
      }
      IamButton(label = feedItem.linkText, size = ButtonSize.Small, type = ButtonType.InlineTextPrimary, onClick = { onItemClick.invoke(feedItem) }, modifier = Modifier.padding(0.dp))
    }
  }
}

@Preview(showBackground = true)
@Composable
fun FeedItemCardPreview() {
  val mockFeedItem = mockFeedItemCardItem()
  ProvideIamTheme {
    FeedItemCard(
      feedItem = mockFeedItem,
      onItemClick = { },
    )
  }
}

private fun mockFeedItemCardItem(): FeedItem {
  return FeedItem(
    elementId = "mockUUID0002",
    titleText = "Here's a headline that's 40 characters.",
    subtitleModalText = "Be prepare for the holidays! Offer ends in {{expiresAt}}!",
    subtitleFeedText = "Ends in 48 hours",
    messageTypeText = "LIGHTENING DEAL",
    titleImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
    linkTarget = "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
    linkText = "SHOP NOW",
    trigger = null,
    isUnread = false,
    expiresAt = "2024-12-30T06:00:00.000Z",
    feedPostId = "TvCN6AV5b781rXLSldOziI",
    accountId = "TvCN6AV5b781rXLSldOziI",
    feedType = "TvCN6AV5b781rXLSldOziI",
    landingPage = LandingPage(
      feedLandingPageId = "ZjsgSDU56trZrcrRnGgaHr",
      feedPostId = "TvCN6AV5b781rXLSldOziI",
      titleText = "Vacuum Sealers",
      promoCode = "5ZHTL9M8",
      featuredImage = null,
      supportingTitleText = "One Machine, a Million Uses",
      supportingDescriptionText = "The Greater Goods {{bold[All-in-One Vacuum Sealer]}} has built-in bag storage and a slicer for hassle-free meal prep!",
      supportingImage = listOf(
        "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
        "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
      ),
      featuredTitleText = "Three Colors",
      themeColor = "red",
      featuredProduct = listOf(
        FeaturedProduct(
          variationId = 10001,
          titleText = "Stone Blue",
          feedLandingPageId = "ZjsgSDU56trZrcrRnGgaHr",
          linkText = "Shop",
          linkTarget = "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
          productImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
        ),
        FeaturedProduct(
          variationId = 10002,
          titleText = "Stone Blue",
          feedLandingPageId = "ZjsgSDU56trZrcrRnGgaHr",
          linkText = "Shop",
          linkTarget = "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
          productImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
        ),
        FeaturedProduct(
          variationId = 10003,
          titleText = "Stone Blue",
          feedLandingPageId = "ZjsgSDU56trZrcrRnGgaHr",
          linkText = "Shop",
          linkTarget = "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
          productImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
        ),
      ),
    ),
  )
}
