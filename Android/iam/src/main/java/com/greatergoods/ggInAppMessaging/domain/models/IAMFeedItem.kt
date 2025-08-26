package com.greatergoods.ggInAppMessaging.domain.models

import com.google.gson.annotations.SerializedName

/**
 * Represents a single feed item (notification, message, etc.) for the user.
 * Android equivalent of iOS IAMFeedItem
 */
data class IAMFeedItem(
    /** The unique identifier for the feed post. */
    @SerializedName("feedPostId")
    val feedPostId: String,
    
    /** Unique identifier for the feed element. */
    @SerializedName("elementId")
    val elementId: String,
    
    /** The account ID associated with this feed item. */
    @SerializedName("accountId")
    val accountId: String,
    
    /** Whether the feed item is unread. */
    @SerializedName("isUnread")
    var isUnread: Boolean? = null,
    
    /** The type of message (for display or logic). */
    @SerializedName("messageTypeText")
    val messageTypeText: String,
    
    /** The main title text for the feed item. */
    @SerializedName("titleText")
    val titleText: String,
    
    /** Optional: The subtitle text for modal display. */
    @SerializedName("subtitleModalText")
    val subtitleModalText: String? = null,
    
    /** The subtitle text for feed display. */
    @SerializedName("subtitleFeedText")
    val subtitleFeedText: String,
    
    /** The image URL or asset name for the title. */
    @SerializedName("titleImage")
    val titleImage: String,
    
    /** Optional: The target for a link (URL, route, etc.). */
    @SerializedName("linkTarget")
    val linkTarget: String? = null,
    
    /** The text for the link. */
    @SerializedName("linkText")
    val linkText: String,
    
    /** Optional: The trigger type for the feed item. */
    @SerializedName("trigger")
    val trigger: FeedTrigger? = null,
    
    /** Optional: Expiry date/time for the feed item (ISO8601 string). */
    @SerializedName("expiresAt")
    val expiresAt: String? = null,
    
    /** The type of feed item (link or landing). */
    @SerializedName("feedType")
    val feedType: FeedType,
    
    /** Optional: The landing page data if feedType is landing. */
    @SerializedName("landingPage")
    val landingPage: FeedLandingPage? = null
)

/**
 * Data class to hold updated feed information
 */
data class UpdatedFeedInfo(
    val feedItem: IAMFeedItem,
    val actionType: FeedActionType,
    val variationId: Int? = null
)

/**
 * Enum for possible feed triggers.
 */
enum class FeedTrigger(val value: String) {
    @SerializedName("login")
    LOGIN("login");

    companion object {
        fun fromString(value: String): FeedTrigger? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Enum for feed types.
 */
enum class FeedType(val value: String) {
    @SerializedName("link")
    LINK("link"),
    
    @SerializedName("landing")
    LANDING("landing");

    companion object {
        fun fromString(value: String): FeedType? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Enum for theme colors.
 */
enum class ThemeColor(val value: String) {
    @SerializedName("green")
    GREEN("green"),
    
    @SerializedName("red")
    RED("red"),
    
    @SerializedName("blue")
    BLUE("blue"),
    
    @SerializedName("gray")
    GRAY("gray");

    companion object {
        fun fromString(value: String): ThemeColor? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Enum for feed action types.
 */
enum class FeedActionType(val value: String) {
    @SerializedName("trigger")
    TRIGGER("trigger"),
    
    @SerializedName("read")
    READ("read"),
    
    @SerializedName("click")
    CLICK("click"),
    
    @SerializedName("pageView")
    PAGE_VIEW("pageView"),
    
    @SerializedName("shopNowClick")
    SHOP_NOW_CLICK("shopNowClick"),
    
    @SerializedName("variationClick")
    VARIATION_CLICK("variationClick"),
    
    @SerializedName("promoClick")
    PROMO_CLICK("promoClick");

    companion object {
        fun fromString(value: String): FeedActionType? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Enum for feed variable keys.
 */
enum class FeedVariable(val value: String) {
    @SerializedName("expiresAt")
    EXPIRES_AT("expiresAt");

    companion object {
        fun fromString(value: String): FeedVariable? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Enum for feed text format types.
 */
enum class FeedTextFormatType(val value: String) {
    @SerializedName("bold")
    BOLD("bold"),
    
    @SerializedName("strike")
    STRIKE("strike"),
    
    @SerializedName("italic")
    ITALIC("italic"),
    
    @SerializedName("underline")
    UNDERLINE("underline");

    companion object {
        fun fromString(value: String): FeedTextFormatType? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Represents a landing page for a feed item.
 */
data class FeedLandingPage(
    /** The unique identifier for the landing page. */
    @SerializedName("feedLandingPageId")
    val feedLandingPageId: String,
    
    /** The associated feed post ID. */
    @SerializedName("feedPostId")
    val feedPostId: String,
    
    /** The main title text. */
    @SerializedName("titleText")
    val titleText: String,
    
    /** Optional: The promo code for the landing page. */
    @SerializedName("promoCode")
    val promoCode: String? = null,
    
    /** Optional: The featured image URL or asset name. */
    @SerializedName("featuredImage")
    val featuredImage: String? = null,
    
    /** Optional: The supporting title text. */
    @SerializedName("supportingTitleText")
    val supportingTitleText: String? = null,
    
    /** Optional: The supporting description text. */
    @SerializedName("supportingDescriptionText")
    val supportingDescriptionText: String? = null,
    
    /** Optional: Array of supporting image URLs or asset names. */
    @SerializedName("supportingImage")
    val supportingImage: List<String>? = null,
    
    /** Optional: The featured section title text. */
    @SerializedName("featuredTitleText")
    val featuredTitleText: String? = null,
    
    /** Optional: The theme color for the landing page. */
    @SerializedName("themeColor")
    val themeColor: ThemeColor? = null,
    
    /** Optional: Array of featured products. */
    @SerializedName("featuredProduct")
    val featuredProduct: List<FeaturedProduct>? = null
)

/**
 * Represents a featured product in a feed landing page.
 */
data class FeaturedProduct(
    /** The variation ID of the product. */
    @SerializedName("variationId")
    val variationId: Int,
    
    /** The title text for the product. */
    @SerializedName("titleText")
    val titleText: String,
    
    /** The ID of the associated feed landing page. */
    @SerializedName("feedLandingPageId")
    val feedLandingPageId: String,
    
    /** The text for the product link. */
    @SerializedName("linkText")
    val linkText: String,
    
    /** The target URL or route for the product link. */
    @SerializedName("linkTarget")
    val linkTarget: String,
    
    /** The image URL or asset name for the product. */
    @SerializedName("productImage")
    val productImage: String
)