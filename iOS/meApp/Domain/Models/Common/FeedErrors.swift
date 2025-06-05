import Foundation

/// Errors that can occur during feed operations
enum FeedError: LocalizedError {
    case failedToEncodeFeedSettings
    case failedToDecodeFeedSettings
    case invalidStorageValue
    
    var errorDescription: String? {
        switch self {
        case .failedToEncodeFeedSettings:
            return "Failed to encode feed settings"
        case .failedToDecodeFeedSettings:
            return "Failed to decode feed settings"
        case .invalidStorageValue:
            return "Invalid storage value"
        }
    }
} 