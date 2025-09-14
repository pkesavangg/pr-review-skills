package com.dmdbrands.gurus.weight.features.feed.mock

import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.domain.models.FeedTypes
import com.greatergoods.ggInAppMessaging.domain.models.LandingPage
import com.greatergoods.ggInAppMessaging.domain.models.ThemeColors

/**
 * Mock feed data for testing the landing screen
 */
object MockFeedData {

  /**
   * Creates a mock feed item with landing type for testing
   */
  fun createMockLandingFeedItem(): FeedItem {
    return FeedItem(
      feedPostId = "mock-feed-post-001",
      elementId = "mock-element-001",
      accountId = "mock-account-123",
      isUnread = true,
      messageTypeText = "Special Offer",
      titleText = "Summer Sale - Up to 50% Off!",
      subtitleModalText = "Don't miss out on our biggest sale of the year!",
      subtitleFeedText = "Limited time offer on all products",
      titleImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
      linkTarget = null,
      linkText = "Learn More",
      trigger = "login",
      expiresAt = "2024-12-31T23:59:59Z",
      feedType = FeedTypes.LANDING,
      landingPage = LandingPage(
        feedLandingPageId = "mock-landing-001",
        feedPostId = "mock-feed-post-001",
        titleText = "Summer Sale - Up to 50% Off!",
        promoCode = "SUMMER50",
        featuredImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
        supportingTitleText = "Featured Products",
        supportingDescriptionText = "Check out our best-selling items with amazing discounts",
        supportingImage = listOf(
          "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
          // "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
          // "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
        ),
        featuredTitleText = "Best Sellers",
        themeColor = ThemeColors.BLUE,
        featuredProduct = null,
      ),
    )
  }

  /**
   * Creates a mock feed item with link type for comparison
   */
  fun createMockLinkFeedItem(): FeedItem {
    return FeedItem(
      feedPostId = "mock-feed-post-002",
      elementId = "mock-element-002",
      accountId = "mock-account-123",
      isUnread = true,
      messageTypeText = "News Update",
      titleText = "New Feature Available",
      subtitleModalText = "Check out our latest feature!",
      subtitleFeedText = "We've added new functionality",
      titleImage = "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
      linkTarget = "https://example.com/new-feature",
      linkText = "Learn More",
      trigger = "login",
      expiresAt = "2024-12-31T23:59:59Z",
      feedType = FeedTypes.LINK,
      landingPage = null,
    )
  }

  /**
   * Creates a list of mock feed items for testing
   */
  fun createMockFeedItems(): List<FeedItem> {
    return listOf(
      createMockLandingFeedItem(),
      createMockLinkFeedItem(),
    )
  }
}
