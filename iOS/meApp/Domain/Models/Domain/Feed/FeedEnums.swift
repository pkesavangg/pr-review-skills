/// Enum for possible feed triggers.
enum FeedTrigger: String, Codable, Equatable {
    case login
}

/// Enum for feed types.
enum FeedType: String, Codable, Equatable {
    case link
    case landing
}

/// Enum for theme colors.
enum ThemeColor: String, Codable, Equatable {
    case green
    case red
    case blue
    case gray
}

/// Enum for feed action types.
enum FeedActionType: String, Codable, Equatable {
    case trigger
    case read
    case click
    case pageView
    case shopNowClick
    case variationClick
    case promoClick
}

/// Enum for feed variable keys.
enum FeedVariable: String, Codable, Equatable {
    case expiresAt
}

/// Enum for feed text format types.
enum FeedTextFormatType: String, Codable, Equatable {
    case bold
    case strike
    case italic
    case underline
}