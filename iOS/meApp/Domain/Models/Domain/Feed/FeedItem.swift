import Foundation

/// Represents a single feed item (notification, message, etc.) for the user.
struct FeedItem: Codable, Equatable {
    /// The unique identifier for the feed post.
    let feedPostId: String
    /// Unique identifier for the feed element.
    let elementId: String
    /// The account ID associated with this feed item.
    let accountId: String
    /// Whether the feed item is unread.
    var isUnread: Bool?
    /// The type of message (for display or logic).
    let messageTypeText: String
    /// The main title text for the feed item.
    let titleText: String
    /// Optional: The subtitle text for modal display.
    let subtitleModalText: String?
    /// The subtitle text for feed display.
    let subtitleFeedText: String
    /// The image URL or asset name for the title.
    let titleImage: String
    /// Optional: The target for a link (URL, route, etc.).
    let linkTarget: String?
    /// The text for the link.
    let linkText: String
    /// Optional: The trigger type for the feed item.
    let trigger: FeedTrigger?
    /// Optional: Expiry date/time for the feed item (ISO8601 string).
    let expiresAt: String?
    /// The type of feed item (link or landing).
    let feedType: FeedType
    /// Optional: The landing page data if feedType is landing.
    let landingPage: LandingPage?
}



