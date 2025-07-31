/// Represents a feed action with metadata.
struct FeedAction: Codable, Equatable {
    /// The type of action performed.
    let action: GGFeedActionType
    /// Optional: The operating system type.
    let osType: String?
    /// Optional: Additional metadata for the action.
    let meta: FeedActionMeta?
}

/// Metadata for a feed action.
struct FeedActionMeta: Codable, Equatable {
    /// The variation ID associated with the action.
    let variationId: Int?
}

enum GGFeedActionType: String, Codable, Equatable {
    case trigger
    case read
    case click
    case pageView
    case shopNowClick
    case variationClick
    case promoClick
}
