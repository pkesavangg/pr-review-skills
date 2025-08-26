package com.greatergoods.ggInAppMessaging.domain.models

import kotlinx.serialization.Serializable

/**
 * Feed trigger events constants
 */
object FeedTriggerEvents {
    const val LOGIN = "login"
}

/**
 * Theme colors for feed items
 */
object ThemeColors {
    const val GREEN = "green"
    const val RED = "red"
    const val BLUE = "blue"
    const val GRAY = "gray"
}

/**
 * Feed types
 */
object FeedTypes {
    const val LINK = "link"
    const val LANDING = "landing"
}

/**
 * Timestamp constants for expiration calculations
 */
object TIMESTAMP {
    const val ONE_DAY = 24 * 60 * 60 * 1000L
    const val TWO_DAYS = 48 * 60 * 60 * 1000L
    const val ONE_HOUR = 60 * 60 * 1000L
    const val ONE_MINUTE = 60 * 1000L
    const val ONE_WEEK = 7 * 24 * 60 * 60 * 1000L
}

/**
 * Feed action types
 */
enum class FeedActionType(val value: String) {
    TRIGGER("trigger"),
    READ("read"),
    CLICK("click"),
    PAGE_VIEW("pageView"),
    SHOP_NOW_CLICK("shopNowClick"),
    VARIATION_CLICK("variationClick"),
    PROMO_CLICK("promoClick")
}

/**
 * Feed text format types
 */
enum class FeedTextFormatType(val value: String) {
    BOLD("bold"),
    STRIKE("strike"),
    ITALIC("italic"),
    UNDERLINE("underline")
}

/**
 * Units of time for display
 */
object UnitsOfTime {
    const val MINUTE = "a minute"
    const val MINUTES = "minutes"
    const val HOURS = "hours"
    const val DAYS = "days"
}

/**
 * Featured product information
 */
@Serializable
data class FeaturedProduct(
    val variationId: Int,
    val titleText: String,
    val feedLandingPageId: String,
    val linkText: String,
    val linkTarget: String,
    val productImage: String
)

/**
 * Landing page information
 */
@Serializable
data class LandingPage(
    val feedLandingPageId: String,
    val feedPostId: String,
    val titleText: String,
    val promoCode: String? = null,
    val featuredImage: String? = null,
    val supportingTitleText: String? = null,
    val supportingDescriptionText: String? = null,
    val supportingImage: List<String>? = null,
    val featuredTitleText: String? = null,
    val themeColor: String? = null,
    val featuredProduct: List<FeaturedProduct>? = null
)

/**
 * Feed item data model
 */
@Serializable
data class FeedItem(
    val feedPostId: String,
    val elementId: String,
    val accountId: String,
    val isUnread: Boolean = true,
    val messageTypeText: String,
    val titleText: String,
    val subtitleModalText: String? = null,
    val subtitleFeedText: String,
    val titleImage: String,
    val linkTarget: String? = null,
    val linkText: String,
    val trigger: String? = null,
    val expiresAt: String? = null,
    val feedType: String,
    val landingPage: LandingPage? = null
)

/**
 * Feed settings configuration
 */
@Serializable
data class FeedSetting(
    val showPopupMessage: Boolean = true,
    val showNotificationBadge: Boolean = true
)

/**
 * Feed action data for tracking
 */
@Serializable
data class FeedAction(
    val action: FeedActionType,
    val osType: String? = null,
    val meta: FeedActionMeta? = null
)

/**
 * Feed action metadata
 */
@Serializable
data class FeedActionMeta(
    val variationId: Int? = null
)

/**
 * Feed info for updates
 */
@Serializable
data class FeedInfo(
    val feedItem: FeedItem,
    val actionType: FeedActionType,
    val variationId: Int? = null
)

/**
 * Feed configuration
 */
@Serializable
data class GGInAppMessagingConfig(
    val baseNavigationPath: String = ""
)

/**
 * Feed variable types
 */
enum class FeedVariable(val value: String) {
    EXPIRES_AT("expiresAt")
}
