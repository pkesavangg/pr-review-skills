import Foundation

/// Errors that can occur during feed operations
enum FeedError: LocalizedError {
    // Local Storage Errors
    case localStorageEncodingFailed
    case localStorageDecodingFailed
    case localStorageInvalidValue
    case networkError(Error)
    case unknown(Error)
    
    var errorDescription: String? {
        switch self {
        case .localStorageEncodingFailed:
            return "Failed to encode feed settings for local storage"
        case .localStorageDecodingFailed:
            return "Failed to decode feed settings from local storage"
        case .localStorageInvalidValue:
            return "Invalid value found in local storage"
        case .networkError(let error):
            return "Network error: \(error.localizedDescription)"
        case .unknown(let error):
            return "Unknown error: \(error.localizedDescription)"
        }
    }
}
