
/// Enum for possible feed triggers.
enum FeedTrigger: String, Codable, Equatable {
    case login
}

/// Enum for feed action types (read, trigger, click).
enum FeedActionType: String, Codable, Equatable {
    case trigger
    case read
    case click
}

/// Enum for feed variable keys.
enum FeedVariable: String, Codable, Equatable {
    case expiresAt
}