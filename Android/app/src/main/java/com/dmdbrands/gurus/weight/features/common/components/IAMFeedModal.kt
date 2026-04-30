package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.greatergoods.ggInAppMessaging.domain.models.FeaturedProduct
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.domain.models.FeedTypes
import com.greatergoods.ggInAppMessaging.domain.models.LandingPage
import com.greatergoods.ggInAppMessaging.ui.components.FeedPopup
import com.greatergoods.ggInAppMessaging.ui.strings.FeedPopupStrings

/**
 * IAM Feed Modal Dialog
 * Wraps the existing FeedPopup composable in a dialog for use in the app's dialog system
 */
@Composable
fun IAMFeedModal(
  feedItem: FeedItem,
  onDismiss: () -> Unit,
  onAction: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  ModalDialog(
    onDismiss = onDismiss,
    config = ModalConfigs.Informational, // Perfect for promotional/informational content
  ) {
    FeedPopup(
      imageUrl = feedItem.titleImage,
      messageType = feedItem.messageTypeText,
      headline = feedItem.titleText,
      supportingText = feedItem.subtitleFeedText,
      primaryButtonText = feedItem.linkText,
      secondaryButtonText = FeedPopupStrings.MessageSettings,
      onPrimaryButtonClick = {
        onAction("buy_now")
        onDismiss()
      },
      onSecondaryButtonClick = {
        onAction("settings")
        onDismiss()
      },
      onCloseClick = onDismiss,
      modifier = modifier,
      expiresAt = feedItem.expiresAt
    )
  }
}

@Preview
@Composable
fun IAMFeedModalReview() {
  val mockItems =
    FeedItem(
      elementId = "mockFromFeedService001",
      titleText = "Special Offer from FeedService!",
      messageTypeText = "LIGHTENING DEAL",
      subtitleFeedText = "Ends in 48 hours",
      subtitleModalText = "This feed item came from the main app's FeedService",
      linkTarget = "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
      feedType = FeedTypes.LINK,
      linkText = "Shop now",
      feedPostId = "mockPost001",
      accountId = "testAccount",
      titleImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
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

  MeAppTheme {
    IAMFeedModal(mockItems, {}, {})
  }
}
